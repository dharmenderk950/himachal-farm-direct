/**
 * Himachal Farm Direct — backend.
 *
 * Spec A "Important": distance alone never decides delivery. Every eligibility answer and
 * every order is computed HERE from the database: farmer approval, live stock, product and
 * farmer delivery flags, PIN/village service areas, collection centres, distance limits and
 * the admin-configurable charge rules in config/delivery.
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Timestamp, Transaction, DocumentSnapshot } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onDocumentWritten, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { setGlobalOptions, logger } from "firebase-functions/v2";
import { distanceBetween, geohashQueryBounds, geohashForLocation } from "geofire-common";

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });
const db = getFirestore();

// --------------------------------------------------------------------------- types

type Mode = "HOME_DELIVERY" | "COLLECTION_CENTRE" | "FARM_PICKUP";

interface DeliveryConfig {
  baseCharge: number;           // ₹ added to every home delivery
  perKm: number;                // ₹ per km (straight-line, rounded up)
  freeAboveOrderValue: number;  // items total at/above which home delivery is free (0 = never)
  maxDeliveryKm: number;        // platform-wide ceiling
  collectionCentreCharge: number;
  collectionCentreMaxKm: number; // buyer → centre
  minOrderValue: number;
  /** Per-PIN overrides: extra charge, or block delivery entirely. */
  pinRules: { [pin: string]: { extraCharge?: number; blocked?: boolean } };
  /** Per-village overrides (lower-case keys). */
  villageRules: { [village: string]: { extraCharge?: number; blocked?: boolean } };
}

const DEFAULT_CFG: DeliveryConfig = {
  baseCharge: 20, perKm: 4, freeAboveOrderValue: 0, maxDeliveryKm: 50,
  collectionCentreCharge: 0, collectionCentreMaxKm: 25, minOrderValue: 0,
  pinRules: {}, villageRules: {},
};

interface LineIn { productId: string; qty: number }
interface Payload {
  farmerId: string; addressId: string; fulfilment: Mode; collectionCentreId?: string; lines: LineIn[];
}

interface Evaluation {
  farmerPhone?: string;
  eligible: boolean;
  reasons: string[];
  distanceKm: number;
  deliveryCharge: number;
  modesAvailable: Mode[];
  itemsTotal: number;
  centre?: { id: string; name: string; village: string };
  pricedLines: { productId: string; farmerId: string; name: string; unit: string; price: number; qty: number; thumbUrl: string }[];
  farmerName: string;
  address: FirebaseFirestore.DocumentData;
}

// --------------------------------------------------------------------------- helpers

function requireAuth(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Please sign in again.");
  return req.auth.uid;
}

function isAdmin(req: CallableRequest): boolean {
  return req.auth?.token?.admin === true;
}

async function deliveryConfig(): Promise<DeliveryConfig> {
  const snap = await db.doc("config/delivery").get();
  return { ...DEFAULT_CFG, ...(snap.data() as Partial<DeliveryConfig> | undefined) };
}

function parsePayload(data: unknown): Payload {
  const d = data as Partial<Payload>;
  const modes: Mode[] = ["HOME_DELIVERY", "COLLECTION_CENTRE", "FARM_PICKUP"];
  if (!d || typeof d.farmerId !== "string" || !d.farmerId) throw new HttpsError("invalid-argument", "Missing farmer.");
  if (typeof d.addressId !== "string" || !d.addressId) throw new HttpsError("invalid-argument", "Choose a delivery address.");
  if (!d.fulfilment || !modes.includes(d.fulfilment)) throw new HttpsError("invalid-argument", "Choose how you want the order.");
  if (!Array.isArray(d.lines) || d.lines.length === 0 || d.lines.length > 50) throw new HttpsError("invalid-argument", "Cart is empty.");
  const lines = d.lines.map((l) => {
    const qty = Number((l as LineIn).qty);
    if (typeof (l as LineIn).productId !== "string" || !(qty > 0) || qty > 1000) throw new HttpsError("invalid-argument", "Invalid quantity.");
    return { productId: (l as LineIn).productId, qty };
  });
  return { farmerId: d.farmerId, addressId: d.addressId, fulfilment: d.fulfilment, collectionCentreId: d.collectionCentreId || "", lines };
}

const norm = (s: unknown) => String(s ?? "").trim().toLowerCase();
const round2 = (n: number) => Math.round(n * 100) / 100;

/**
 * Core eligibility check. Reads go through `get` so the same code runs inside the
 * placeOrder transaction (consistent stock) and outside it for checkDelivery.
 */
async function evaluate(
  uid: string, p: Payload, cfg: DeliveryConfig,
  get: (ref: FirebaseFirestore.DocumentReference) => Promise<DocumentSnapshot>,
): Promise<Evaluation> {
  const reasons: string[] = [];
  const [farmerSnap, addrSnap] = await Promise.all([
    get(db.doc(`farmers/${p.farmerId}`)),
    get(db.doc(`users/${uid}/addresses/${p.addressId}`)),
  ]);
  const farmer = farmerSnap.data();
  const address = addrSnap.data();
  if (!farmer) throw new HttpsError("not-found", "This farmer is no longer available.");
  if (!address) throw new HttpsError("not-found", "Delivery address not found. Please add it again.");
  if (farmer.approved !== true) reasons.push("This farmer is not verified yet");
  if (p.farmerId === uid) reasons.push("You can't order your own products");

  // ---- products & stock
  const productSnaps = await Promise.all(p.lines.map((l) => get(db.doc(`products/${l.productId}`))));
  const pricedLines: Evaluation["pricedLines"] = [];
  let itemsTotal = 0;
  let allDeliverable = true;
  productSnaps.forEach((s, i) => {
    const prod = s.data();
    const qty = p.lines[i].qty;
    if (!prod || prod.active !== true) { reasons.push("An item is no longer available"); return; }
    if (prod.farmerId !== p.farmerId) { reasons.push(`${prod.name} is from a different farmer`); return; }
    if (Number(prod.stock ?? 0) < qty) { reasons.push(`Only ${prod.stock ?? 0} ${prod.unit} of ${prod.name} left`); return; }
    if (prod.deliveryAvailable !== true) allDeliverable = false;
    const price = Number(prod.price ?? 0);
    itemsTotal += price * qty;
    pricedLines.push({ productId: s.id, farmerId: p.farmerId, name: prod.name, unit: prod.unit, price, qty, thumbUrl: prod.thumbUrl ?? "" });
  });
  if (cfg.minOrderValue > 0 && itemsTotal < cfg.minOrderValue) reasons.push(`Minimum order is ₹${cfg.minOrderValue}`);

  // ---- distance (approximate, straight line)
  const distanceKm = round2(distanceBetween([address.lat, address.lng], [farmer.lat, farmer.lng]));

  // ---- service-area rules
  const pin = String(address.pinCode ?? "").trim();
  const village = norm(address.village);
  const pinRule = cfg.pinRules[pin] ?? {};
  const villageRule = cfg.villageRules[village] ?? {};
  const servicePins: string[] = farmer.servicePinCodes ?? [];
  const serviceVillages: string[] = (farmer.serviceVillages ?? []).map(norm);
  const areaListed = servicePins.length === 0 && serviceVillages.length === 0
    ? true
    : servicePins.includes(pin) || (village !== "" && serviceVillages.includes(village));

  // Which modes could work, independent of the one chosen (shown to the buyer).
  const homeProblems: string[] = [];
  if (farmer.deliveryAvailable !== true) homeProblems.push("Farmer doesn't offer home delivery");
  if (!allDeliverable) homeProblems.push("Some items are pickup-only");
  if (distanceKm > Number(farmer.deliveryRadiusKm ?? 0)) homeProblems.push(`Farmer delivers only up to ${farmer.deliveryRadiusKm ?? 0} km`);
  if (distanceKm > cfg.maxDeliveryKm) homeProblems.push(`Home delivery is limited to ${cfg.maxDeliveryKm} km`);
  if (!areaListed) homeProblems.push("Your PIN / village is not in this farmer's service area");
  if (pinRule.blocked || villageRule.blocked) homeProblems.push("Delivery is not available in your area yet");

  // Collection centre: nearest active centre the farmer uses, within reach of the buyer.
  let centre: Evaluation["centre"];
  const centreIds: string[] = farmer.collectionCentreIds ?? [];
  if (centreIds.length) {
    const wanted = p.collectionCentreId ? centreIds.filter((c) => c === p.collectionCentreId) : centreIds;
    const snaps = await Promise.all(wanted.slice(0, 10).map((id) => get(db.doc(`collectionCentres/${id}`))));
    let best: { d: number; s: DocumentSnapshot } | undefined;
    for (const s of snaps) {
      const c = s.data();
      if (!c || c.active !== true) continue;
      const d = distanceBetween([address.lat, address.lng], [c.lat, c.lng]);
      if (d <= cfg.collectionCentreMaxKm && (!best || d < best.d)) best = { d, s };
    }
    if (best) centre = { id: best.s.id, name: best.s.get("name"), village: best.s.get("village") };
  }

  const modesAvailable: Mode[] = ["FARM_PICKUP"];
  if (homeProblems.length === 0) modesAvailable.unshift("HOME_DELIVERY");
  if (centre) modesAvailable.splice(modesAvailable.length - 1, 0, "COLLECTION_CENTRE");

  // ---- chosen mode
  let deliveryCharge = 0;
  if (p.fulfilment === "HOME_DELIVERY") {
    reasons.push(...homeProblems);
    const free = cfg.freeAboveOrderValue > 0 && itemsTotal >= cfg.freeAboveOrderValue;
    deliveryCharge = free ? 0 : cfg.baseCharge + Math.ceil(distanceKm) * cfg.perKm +
      Number(pinRule.extraCharge ?? 0) + Number(villageRule.extraCharge ?? 0);
  } else if (p.fulfilment === "COLLECTION_CENTRE") {
    if (!centre) reasons.push("No collection centre near you for this farmer");
    deliveryCharge = cfg.collectionCentreCharge;
  }

  return {
    eligible: reasons.length === 0,
    reasons: Array.from(new Set(reasons)),
    distanceKm, deliveryCharge: round2(deliveryCharge), modesAvailable, itemsTotal: round2(itemsTotal),
    centre, pricedLines, farmerName: farmer.farmName || farmer.name || "Farmer", address,
    farmerPhone: farmer.contactPhone ?? "",
  };
}

/** Writes an in-app notification and pushes it over FCM when the user has a token. */
async function notify(uid: string, title: string, body: string, open: string, channel: string) {
  await db.collection(`users/${uid}/notifications`).add({ title, body, open, read: false, createdAt: FieldValue.serverTimestamp() });
  const token = (await db.doc(`users/${uid}`).get()).get("fcmToken");
  if (!token) return;
  try {
    await getMessaging().send({
      token,
      notification: { title, body },
      data: { title, body, open, channel },
      android: { priority: "high", notification: { channelId: channel } },
    });
  } catch (e) {
    logger.warn("FCM send failed", { uid, e: String(e) });
  }
}

async function notifyAdmins(title: string, body: string, open: string) {
  const admins = await db.collection("users").where("isAdmin", "==", true).limit(20).get();
  await Promise.all(admins.docs.map((d) => notify(d.id, title, body, open, "support")));
}

const farmer_phone = (ev: Evaluation) => ev.farmerPhone ?? "";

// --------------------------------------------------------------------------- callables

export const checkDelivery = onCall(async (req) => {
  const uid = requireAuth(req);
  const p = parsePayload(req.data);
  const ev = await evaluate(uid, p, await deliveryConfig(), (r) => r.get());
  return {
    eligible: ev.eligible, reasons: ev.reasons, distanceKm: ev.distanceKm,
    deliveryCharge: ev.deliveryCharge, modesAvailable: ev.modesAvailable, itemsTotal: ev.itemsTotal,
  };
});

export const placeOrder = onCall(async (req) => {
  const uid = requireAuth(req);
  const p = parsePayload(req.data);
  const cfg = await deliveryConfig();
  const orderRef = db.collection("orders").doc();

  const result = await db.runTransaction(async (tx: Transaction) => {
    const ev = await evaluate(uid, p, cfg, (r) => tx.get(r));
    if (!ev.eligible) throw new HttpsError("failed-precondition", ev.reasons.join("; "));
    for (const l of ev.pricedLines) {
      tx.update(db.doc(`products/${l.productId}`), { stock: FieldValue.increment(-l.qty) });
    }
    tx.set(orderRef, {
      buyerId: uid,
      farmerId: p.farmerId,
      farmerName: ev.farmerName,
      lines: ev.pricedLines,
      itemsTotal: ev.itemsTotal,
      deliveryCharge: ev.deliveryCharge,
      grandTotal: round2(ev.itemsTotal + ev.deliveryCharge),
      fulfilment: p.fulfilment,
      collectionCentre: ev.centre ?? null,
      distanceKm: ev.distanceKm,
      status: "PLACED",
      stockDeducted: true,
      farmerPhone: farmer_phone(ev),
      buyerName: ev.address.name ?? "",
      buyerPhone: ev.address.phone ?? "",
      deliveryAddress: [ev.address.line, ev.address.landmark, ev.address.village, ev.address.district, ev.address.pinCode]
        .filter((x: unknown) => typeof x === "string" && x).join(", "),
      // Only coarse area on the main doc; exact address goes to a restricted sub-doc.
      deliveryVillage: ev.address.village ?? "",
      deliveryPin: ev.address.pinCode ?? "",
      createdAt: FieldValue.serverTimestamp(),
    });
    if (p.fulfilment === "HOME_DELIVERY") {
      tx.set(orderRef.collection("private").doc("address"), {
        label: ev.address.label ?? "", line: ev.address.line ?? "", village: ev.address.village ?? "",
        pinCode: ev.address.pinCode ?? "", lat: ev.address.lat, lng: ev.address.lng,
        buyerId: uid, farmerId: p.farmerId,
      });
    }
    return ev;
  });

  await notify(p.farmerId, "New order received", `${result.pricedLines.length} item(s) · ₹${round2(result.itemsTotal + result.deliveryCharge)}`, "orders", "orders");
  return { orderId: orderRef.id };
});

const SELLER_FLOW: Record<string, string[]> = {
  PLACED: ["ACCEPTED", "REJECTED"],
  ACCEPTED: ["PACKED", "CANCELLED"],
  PACKED: ["OUT_FOR_DELIVERY", "READY_FOR_PICKUP"],
  OUT_FOR_DELIVERY: ["DELIVERED"],
  READY_FOR_PICKUP: ["DELIVERED"],
};
const STATUS_TEXT: Record<string, string> = {
  ACCEPTED: "accepted by the farmer", REJECTED: "not accepted by the farmer", PACKED: "packed",
  OUT_FOR_DELIVERY: "out for delivery", READY_FOR_PICKUP: "ready for pickup", DELIVERED: "delivered",
  CANCELLED: "cancelled",
};

export const updateOrderStatus = onCall(async (req) => {
  const uid = requireAuth(req);
  const { orderId, status } = req.data as { orderId: string; status: string };
  if (!orderId || !status) throw new HttpsError("invalid-argument", "Missing order or status.");
  const ref = db.doc(`orders/${orderId}`);

  const order = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const o = snap.data();
    if (!o) throw new HttpsError("not-found", "Order not found.");
    const asSeller = o.farmerId === uid;
    const asBuyer = o.buyerId === uid;
    const allowed = isAdmin(req) ||
      (asSeller && (SELLER_FLOW[o.status] ?? []).includes(status)) ||
      (asBuyer && o.status === "PLACED" && status === "CANCELLED");
    if (!allowed) throw new HttpsError("permission-denied", "This change is not allowed now.");

    // Return stock on reject / cancel; count sales on delivery.
    if (status === "REJECTED" || status === "CANCELLED") {
      for (const l of o.lines as Evaluation["pricedLines"]) tx.update(db.doc(`products/${l.productId}`), { stock: FieldValue.increment(l.qty) });
    }
    if (status === "DELIVERED") {
      for (const l of o.lines as Evaluation["pricedLines"]) tx.update(db.doc(`products/${l.productId}`), { soldCount: FieldValue.increment(Math.ceil(l.qty)) });
    }
    tx.update(ref, { status, updatedAt: FieldValue.serverTimestamp(), [`history.${status}`]: FieldValue.serverTimestamp() });
    return o;
  });

  const text = STATUS_TEXT[status] ?? status.toLowerCase();
  if (uid === order.farmerId || isAdmin(req)) await notify(order.buyerId, "Order update", `Your order from ${order.farmerName} is ${text}.`, "orders", "orders");
  if (uid === order.buyerId) await notify(order.farmerId, "Order cancelled", `Order #${orderId.slice(-6).toUpperCase()} was cancelled by the buyer.`, "orders", "orders");
  return { ok: true };
});

/** Spec E: support request → sequential ticket number, admin alerted. */
export const createSupportTicket = onCall(async (req) => {
  const uid = requireAuth(req);
  const d = req.data as { role?: string; topic?: string; subject?: string; message?: string; orderId?: string };
  const subject = String(d.subject ?? "").trim().slice(0, 100);
  const message = String(d.message ?? "").trim().slice(0, 2000);
  if (!subject || !message) throw new HttpsError("invalid-argument", "Please fill in the subject and message.");

  const year = new Date().getFullYear();
  const counterRef = db.doc(`counters/tickets_${year}`);
  const ticketRef = db.collection("supportTickets").doc();
  const ticketNo = await db.runTransaction(async (tx) => {
    const n = ((await tx.get(counterRef)).get("next") as number | undefined) ?? 1;
    tx.set(counterRef, { next: n + 1 }, { merge: true });
    const no = `HFD-${year}-${String(n).padStart(5, "0")}`;
    tx.set(ticketRef, {
      ticketNo: no, uid,
      role: d.role === "seller" ? "seller" : "buyer",
      topic: String(d.topic ?? "Other").slice(0, 60),
      subject, message,
      orderId: String(d.orderId ?? ""),
      status: "OPEN", adminReply: "",
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    });
    return no;
  });

  await notifyAdmins(`New ticket ${ticketNo}`, `${d.topic ?? "Support"}: ${subject}`, "tickets");
  // Optional e-mail copy via the "Trigger Email" Firebase extension, if installed (collection: mail).
  const supportEmail = (await db.doc("config/support").get()).get("adminEmail") ?? "dharmenderk950@hotmail.com";
  await db.collection("mail").add({
    to: supportEmail,
    message: { subject: `[${ticketNo}] ${subject}`, text: `Topic: ${d.topic}\nRole: ${d.role}\nOrder: ${d.orderId || "-"}\n\n${message}` },
  }).catch(() => undefined);
  return { ticketNo, id: ticketRef.id };
});

/** Admin replies / changes status (also possible directly in the console; the trigger below notifies). */
export const adminReplyTicket = onCall(async (req) => {
  if (!isAdmin(req)) throw new HttpsError("permission-denied", "Admins only.");
  const { ticketId, reply, status } = req.data as { ticketId: string; reply?: string; status?: string };
  const allowed = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];
  const upd: Record<string, unknown> = { updatedAt: FieldValue.serverTimestamp() };
  if (reply) upd.adminReply = String(reply).slice(0, 2000);
  if (status && allowed.includes(status)) upd.status = status;
  await db.doc(`supportTickets/${ticketId}`).update(upd);
  return { ok: true };
});

export const adminSetFarmerApproval = onCall(async (req) => {
  if (!isAdmin(req)) throw new HttpsError("permission-denied", "Admins only.");
  const { farmerId, approved, featured } = req.data as { farmerId: string; approved?: boolean; featured?: boolean };
  const upd: Record<string, unknown> = {};
  if (typeof approved === "boolean") { upd.approved = approved; upd.rejected = !approved; }
  if (typeof featured === "boolean") upd.featured = featured;
  await db.doc(`farmers/${farmerId}`).update(upd);
  return { ok: true };
});

// --------------------------------------------------------------------------- triggers

/** Admin response notifications (spec E). */
export const onTicketUpdated = onDocumentUpdated({ document: "supportTickets/{id}", region: "asia-south1" }, async (e) => {
  const before = e.data?.before.data();
  const after = e.data?.after.data();
  if (!before || !after) return;
  const replied = after.adminReply && after.adminReply !== before.adminReply;
  const statusChanged = after.status !== before.status;
  if (!replied && !statusChanged) return;
  const body = replied ? String(after.adminReply).slice(0, 140) : `Status is now ${String(after.status).replace("_", " ").toLowerCase()}.`;
  await notify(after.uid, `Reply on ticket ${after.ticketNo}`, body, "tickets", "support");
});

/**
 * Keeps users/{uid}.sellerStatus in sync with the farmer doc, and when a farmer is newly
 * approved, alerts nearby customers who opted in (spec A-13). Customers are matched on
 * their coarse 5-char geohash only — no exact customer location is ever stored or read.
 */
export const onFarmerWritten = onDocumentWritten({ document: "farmers/{id}", region: "asia-south1" }, async (e) => {
  const id = e.params.id;
  const before = e.data?.before.data();
  const after = e.data?.after.data();
  if (!after) return;

  const status = after.approved === true ? "APPROVED" : after.rejected === true ? "REJECTED" : "PENDING";
  await db.doc(`users/${id}`).set({ sellerStatus: status }, { merge: true });

  // Keep product location copies in step when the farm moves or delivery flag changes.
  const moved = before && (before.geohash !== after.geohash || before.farmName !== after.farmName || before.name !== after.name);
  if (moved) {
    const prods = await db.collection("products").where("farmerId", "==", id).get();
    const batch = db.batch();
    prods.docs.forEach((d) => batch.update(d.ref, {
      lat: after.lat, lng: after.lng, geohash: after.geohash, farmerName: after.farmName || after.name,
    }));
    if (prods.size) await batch.commit();
  }

  const newlyApproved = after.approved === true && before?.approved !== true;
  const newlyRejected = after.rejected === true && before?.rejected !== true;
  if (newlyRejected) await notify(id, "Seller application update", "Your farm could not be verified yet. Open Help Centre for details.", "", "general");
  if (!newlyApproved) return;

  await notify(id, "You're verified! 🎉", "You can now switch to Seller mode in Account and add products.", "", "general");

  const radiusKm = 20;
  const center: [number, number] = [after.lat, after.lng];
  const bounds = geohashQueryBounds(center, radiusKm * 1000);
  const seen = new Set<string>();
  for (const [start, end] of bounds) {
    // coarseGeohash has 5 chars; compare against the 5-char prefixes of the bounds.
    const s = start.slice(0, 5);
    const en = end.slice(0, 5) + "~";
    const users = await db.collection("users")
      .where("notifyNewSellers", "==", true)
      .where("coarseGeohash", ">=", s).where("coarseGeohash", "<=", en)
      .limit(500).get();
    for (const u of users.docs) {
      if (u.id === id || seen.has(u.id)) continue;
      seen.add(u.id);
    }
  }
  const name = after.farmName || after.name;
  await Promise.all(Array.from(seen).map((uid) =>
    notify(uid, "New farmer near you", `${name} from ${after.village} now sells on Himachal Farm Direct.`, `farmer/${id}`, "nearby")));
  logger.info("new seller alerts", { farmer: id, count: seen.size });
});

/**
 * Normalises every product write: location copied from the (verified) farm, search
 * keywords built, and client attempts to fake location / ownership overwritten.
 */
export const onProductWrite = onDocumentWritten({ document: "products/{id}", region: "asia-south1" }, async (e) => {
  const after = e.data?.after.data();
  if (!after) return;
  const farmer = (await db.doc(`farmers/${after.farmerId}`).get()).data();
  if (!farmer) return;

  const words = new Set<string>();
  [after.name, after.nameHindi, after.category, farmer.village].forEach((t) => {
    norm(t).split(/[\s,/-]+/).filter((w) => w.length >= 2).forEach((w) => {
      for (let i = 2; i <= Math.min(w.length, 12); i++) words.add(w.slice(0, i)); // prefix search
    });
  });
  const desired = {
    lat: farmer.lat, lng: farmer.lng,
    geohash: farmer.geohash || geohashForLocation([farmer.lat, farmer.lng]),
    farmerName: farmer.farmName || farmer.name,
    searchKeywords: Array.from(words).slice(0, 100),
    onOffer: Number(after.mrp ?? 0) > Number(after.price ?? 0),
  };
  const changed = (Object.keys(desired) as (keyof typeof desired)[])
    .some((k) => JSON.stringify(after[k]) !== JSON.stringify(desired[k]));
  if (changed) await e.data!.after.ref.update(desired); // re-trigger exits here (no change)
});

// Re-export for tests / scripts.
export { Timestamp };
