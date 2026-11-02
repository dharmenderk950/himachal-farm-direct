// Himachal Farm Direct — admin dashboard. Talks directly to Firestore; every write is checked
// by firestore.rules (only admins may approve farmers, reply to tickets, edit config, etc.).
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import { getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, onAuthStateChanged, signOut }
  from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore, collection, doc, onSnapshot, query, orderBy, limit, updateDoc, setDoc, addDoc, deleteDoc,
  getDoc, getDocs, serverTimestamp,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

const $ = (s) => document.querySelector(s);
const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const rs = (n) => "₹" + (Number(n || 0) % 1 === 0 ? Number(n || 0).toLocaleString("en-IN") : Number(n).toFixed(2));
const when = (t) => (t && t.toDate ? t.toDate().toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }) : "—");
const toast = (m) => { const t = $("#toast"); t.textContent = m; t.classList.remove("hidden"); clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.add("hidden"), 3200); };

if (!window.FIREBASE_CONFIG) {
  document.body.innerHTML = '<p style="padding:24px;font:16px system-ui">Firebase configuration missing. Deploy this dashboard with the “Deploy Firebase” GitHub workflow.</p>';
  throw new Error("no config");
}
const app = initializeApp(window.FIREBASE_CONFIG);
const auth = getAuth(app);
const db = getFirestore(app);

// ------------------------------------------------------------------ auth
$("#gbtn").onclick = async () => {
  $("#loginErr").textContent = "";
  const p = new GoogleAuthProvider(); p.setCustomParameters({ prompt: "select_account" });
  try { await signInWithPopup(auth, p); }
  catch (e) {
    if (e.code === "auth/popup-blocked" || e.code === "auth/operation-not-supported-in-this-environment") return signInWithRedirect(auth, p);
    if (e.code !== "auth/popup-closed-by-user") $("#loginErr").textContent = e.message;
  }
};
$("#logout").onclick = () => signOut(auth);
$("#menuBtn").onclick = () => $("#nav").classList.toggle("open");
$("#mClose").onclick = closeModal;
$("#modal").onclick = (e) => { if (e.target.id === "modal") closeModal(); };

const unsubs = [];
onAuthStateChanged(auth, async (u) => {
  unsubs.splice(0).forEach((f) => f());
  if (!u) { $("#login").classList.remove("hidden"); $("#shell").classList.add("hidden"); return; }
  // Prove admin access by reading something only admins can read.
  try { await getDoc(doc(db, "config", "support")); }
  catch {
    $("#loginErr").innerHTML = `<b>${esc(u.email)}</b> is not an administrator.`;
    await signOut(auth); return;
  }
  $("#who").textContent = u.email;
  $("#login").classList.add("hidden"); $("#shell").classList.remove("hidden");
  startListeners();
  go(location.hash.slice(1) || "overview");
});

// ------------------------------------------------------------------ live data
const S = { farmers: [], products: [], orders: [], tickets: [], users: [], banners: [], centres: [], delivery: {}, support: {} };
function listen(name, q, map) {
  unsubs.push(onSnapshot(q, (snap) => {
    S[name] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    if (map) S[name] = map(S[name]);
    badges(); render();
  }, (err) => { console.error(name, err); toast(`Couldn't load ${name}: ${err.code}`); }));
}
function startListeners() {
  listen("farmers", collection(db, "farmers"));
  listen("products", collection(db, "products"));
  listen("orders", query(collection(db, "orders"), orderBy("createdAt", "desc"), limit(500)));
  listen("tickets", query(collection(db, "supportTickets"), orderBy("createdAt", "desc"), limit(300)));
  listen("users", query(collection(db, "users"), limit(1000)));
  listen("banners", collection(db, "banners"), (a) => a.sort((x, y) => (x.order || 0) - (y.order || 0)));
  listen("centres", collection(db, "collectionCentres"));
  unsubs.push(onSnapshot(doc(db, "config", "delivery"), (d) => { S.delivery = d.data() || {}; render(); }));
  unsubs.push(onSnapshot(doc(db, "config", "support"), (d) => { S.support = d.data() || {}; render(); }));
}
const pendingFarmers = () => S.farmers.filter((f) => !f.approved && !f.rejected);
const openOrders = () => S.orders.filter((o) => ["PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP"].includes(o.status));
const openTickets = () => S.tickets.filter((t) => ["OPEN", "IN_PROGRESS"].includes(t.status));
function badge(id, n) { const b = $(id); b.textContent = n; b.classList.toggle("hidden", !n); }
function badges() { badge("#bFarmers", pendingFarmers().length); badge("#bOrders", S.orders.filter((o) => o.status === "PLACED").length); badge("#bTickets", S.tickets.filter((t) => t.status === "OPEN").length); }

// ------------------------------------------------------------------ navigation
let tab = "overview";
const TITLES = { overview: "Overview", farmers: "Farmers", products: "Products", orders: "Orders", tickets: "Support tickets", users: "Users", banners: "Home banners", centres: "Collection centres", settings: "Settings" };
document.querySelectorAll("nav a").forEach((a) => (a.onclick = () => go(a.dataset.tab)));
window.addEventListener("hashchange", () => go(location.hash.slice(1)));
function go(t) {
  if (!TITLES[t]) t = "overview";
  tab = t; if (location.hash.slice(1) !== t) location.hash = t;
  document.querySelectorAll("nav a").forEach((a) => a.classList.toggle("on", a.dataset.tab === t));
  $("#title").textContent = TITLES[t]; $("#nav").classList.remove("open");
  filters = {}; document.querySelectorAll("#view form[data-dirty]").forEach((f) => f.removeAttribute("data-dirty")); render();
}
let filters = {};
let renderQueued = false;
// Live data must not wipe a form the admin is typing in: edited forms are left alone
// until they are submitted or the admin switches tab.
document.addEventListener("input", (e) => e.target.closest?.("#view form")?.setAttribute("data-dirty", "1"));
function render() {
  if (document.querySelector("#view form[data-dirty]")) return;
  if (renderQueued) return; renderQueued = true;
  requestAnimationFrame(() => {
    renderQueued = false;
    const focused = document.activeElement?.id, pos = document.activeElement?.selectionStart;
    $("#view").innerHTML = VIEWS[tab]();
    bind[tab]?.();
    if (focused && $("#" + focused)) { const el = $("#" + focused); el.focus(); try { el.setSelectionRange(pos, pos); } catch {} }
  });
}

// ------------------------------------------------------------------ helpers
const ST = {
  PLACED: ["New", "p-wait"], ACCEPTED: ["Accepted", "p-ok"], PACKED: ["Packed", "p-ok"], OUT_FOR_DELIVERY: ["Out for delivery", "p-ok"],
  READY_FOR_PICKUP: ["Ready for pickup", "p-ok"], DELIVERED: ["Delivered", "p-gray"], CANCELLED: ["Cancelled", "p-bad"], REJECTED: ["Rejected", "p-bad"],
  OPEN: ["Open", "p-wait"], IN_PROGRESS: ["In progress", "p-ok"], RESOLVED: ["Resolved", "p-gray"], CLOSED: ["Closed", "p-gray"],
};
const pill = (s) => { const [l, c] = ST[s] || [s, "p-gray"]; return `<span class="pill ${c}">${esc(l)}</span>`; };
const farmerStatus = (f) => f.approved ? `<span class="pill p-ok">Approved</span>` : f.rejected ? `<span class="pill p-bad">Rejected</span>` : `<span class="pill p-wait">Pending</span>`;
const userName = (uid) => { const u = S.users.find((x) => x.id === uid); return u ? (u.name || u.email || u.phone || uid.slice(0, 6)) : uid.slice(0, 6); };
const match = (q, ...fields) => !q || fields.some((f) => String(f ?? "").toLowerCase().includes(q.toLowerCase()));
const tel = (p) => p ? `<a href="tel:+91${esc(String(p).slice(-10))}">+91 ${esc(String(p).slice(-10))}</a>` : "—";

// Writes to the user's in-app inbox. Throws on failure so the admin sees a real error.
async function notify(uid, title, body, open = "") {
  if (!uid) throw new Error("no user");
  await addDoc(collection(db, "users", uid, "notifications"), { title, body, open, read: false, createdAt: serverTimestamp() });
}
// Status-change notices shouldn't block the main action; report but carry on.
async function notifySoft(uid, title, body, open = "") {
  try { await notify(uid, title, body, open); } catch (e) { console.warn("notify", e); toast("Saved, but the in-app message could not be sent: " + (e.code || e.message)); }
}
async function act(fn, ok) {
  try {
    await fn(); if (ok) toast(ok);
    document.querySelectorAll("#view form[data-dirty]").forEach((f) => f.removeAttribute("data-dirty")); render();
  } catch (e) { console.error(e); toast("Failed: " + (e.code === "permission-denied" ? "not allowed (check admin access)" : (e.code || e.message))); }
}
function openModal(title, html, onBind) { $("#mTitle").textContent = title; $("#mBody").innerHTML = html; $("#modal").classList.remove("hidden"); onBind?.(); }
function closeModal() { $("#modal").classList.add("hidden"); }
const on = (sel, ev, fn) => document.querySelectorAll(sel).forEach((el) => el.addEventListener(ev, fn));
const searchBox = (ph) => `<input type="search" id="q" placeholder="${ph}" value="${esc(filters.q || "")}">`;
function bindSearch() { const q = $("#q"); if (q) q.oninput = () => { filters.q = q.value; render(); }; on("[data-f]", "click", (e) => { filters.s = e.currentTarget.dataset.f; render(); }); }
const chips = (opts) => opts.map(([v, l]) => `<button class="chip ${(filters.s ?? opts[0][0]) === v ? "on" : ""}" data-f="${v}">${l}</button>`).join("");

// tiny geohash encoder (collection centres need it for distance search)
function geohash(lat, lng, precision = 10) {
  const b32 = "0123456789bcdefghjkmnpqrstuvwxyz"; let idx = 0, bit = 0, even = true, out = "";
  let la = [-90, 90], lo = [-180, 180];
  while (out.length < precision) {
    const r = even ? lo : la, v = even ? lng : lat, mid = (r[0] + r[1]) / 2;
    if (v >= mid) { idx = idx * 2 + 1; r[0] = mid; } else { idx = idx * 2; r[1] = mid; }
    even = !even; if (++bit === 5) { out += b32[idx]; bit = 0; idx = 0; }
  }
  return out;
}

// ------------------------------------------------------------------ views
const VIEWS = {};
const bind = {};

VIEWS.overview = () => {
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const todays = S.orders.filter((o) => o.createdAt?.toDate?.() >= today);
  const delivered = S.orders.filter((o) => o.status === "DELIVERED");
  const lowStock = S.products.filter((p) => p.active && Number(p.stock) <= 2);
  return `
  <div class="cards">
    <div class="stat ${pendingFarmers().length ? "alert" : ""}" data-go="farmers"><div class="n">${pendingFarmers().length}</div><div class="l">Farmers waiting for approval</div></div>
    <div class="stat ${S.orders.filter((o) => o.status === "PLACED").length ? "alert" : ""}" data-go="orders"><div class="n">${S.orders.filter((o) => o.status === "PLACED").length}</div><div class="l">New orders</div></div>
    <div class="stat ${S.tickets.filter((t) => t.status === "OPEN").length ? "alert" : ""}" data-go="tickets"><div class="n">${S.tickets.filter((t) => t.status === "OPEN").length}</div><div class="l">Open support tickets</div></div>
    <div class="stat" data-go="orders"><div class="n">${todays.length}</div><div class="l">Orders today</div></div>
    <div class="stat" data-go="orders"><div class="n">${rs(delivered.reduce((s, o) => s + Number(o.itemsTotal || 0), 0))}</div><div class="l">Delivered sales (all time)</div></div>
    <div class="stat" data-go="farmers"><div class="n">${S.farmers.filter((f) => f.approved).length}</div><div class="l">Approved farmers</div></div>
    <div class="stat" data-go="products"><div class="n">${S.products.filter((p) => p.active && p.stock > 0).length}</div><div class="l">Products live</div></div>
    <div class="stat" data-go="users"><div class="n">${S.users.length}</div><div class="l">Registered users</div></div>
  </div>
  <div class="panel"><h3>⏳ Farmers waiting for approval</h3>${farmerTable(pendingFarmers().slice(0, 8), true)}</div>
  <div class="panel"><h3>📦 Latest orders</h3>${orderTable(S.orders.slice(0, 8))}</div>
  ${lowStock.length ? `<div class="panel"><h3>⚠️ Low stock</h3><div class="tablewrap"><table><tr><th>Product</th><th>Farmer</th><th>Stock</th></tr>
    ${lowStock.map((p) => `<tr><td>${esc(p.name)}</td><td>${esc(p.farmerName)}</td><td>${esc(p.stock)} ${esc(p.unit)}</td></tr>`).join("")}</table></div></div>` : ""}`;
};
bind.overview = () => { on("[data-go]", "click", (e) => go(e.currentTarget.dataset.go)); bindFarmerActions(); bindOrderActions(); };

// ---- farmers
function farmerTable(list, compact = false) {
  if (!list.length) return `<div class="empty">Nothing here.</div>`;
  return `<div class="tablewrap"><table><tr><th>Farmer</th><th>Village / PIN</th><th>Mobile</th><th>Delivery</th><th>Status</th><th></th></tr>
  ${list.map((f) => `<tr>
    <td><b>${esc(f.farmName || f.name)}</b><div class="muted">${esc(f.name)} · ${when(f.createdAt)}</div></td>
    <td>${esc(f.village)}, ${esc(f.district)}<div class="muted">PIN ${esc(f.pinCode)}</div></td>
    <td>${tel(f.contactPhone)}</td>
    <td>${f.deliveryAvailable ? `Up to ${esc(f.deliveryRadiusKm)} km` : "Pickup only"}</td>
    <td>${farmerStatus(f)} ${f.featured ? '<span class="pill p-ok">★ Featured</span>' : ""}</td>
    <td><div class="acts">
      ${!f.approved ? `<button class="btn pri" data-approve="${f.id}">Approve</button>` : ""}
      ${!f.rejected && !compact ? `<button class="btn bad" data-reject="${f.id}">${f.approved ? "Suspend" : "Reject"}</button>` : ""}
      ${f.approved && !compact ? `<button class="btn" data-feature="${f.id}">${f.featured ? "Unfeature" : "Feature"}</button>` : ""}
      <button class="btn" data-fview="${f.id}">Details</button>
    </div></td></tr>`).join("")}</table></div>`;
}
function bindFarmerActions() {
  on("[data-approve]", "click", (e) => { const id = e.currentTarget.dataset.approve;
    act(async () => { await updateDoc(doc(db, "farmers", id), { approved: true, rejected: false });
      await notifySoft(id, "You're verified! 🎉", "You can now switch to Seller mode in Account and add your products."); }, "Farmer approved"); });
  on("[data-reject]", "click", (e) => { const id = e.currentTarget.dataset.reject;
    const reason = prompt("Reason (sent to the farmer):", "Details could not be verified. Please update your farm details.");
    if (reason === null) return;
    act(async () => { await updateDoc(doc(db, "farmers", id), { approved: false, rejected: true, rejectReason: reason });
      await notifySoft(id, "Seller application update", reason); }, "Farmer rejected"); });
  on("[data-feature]", "click", (e) => { const f = S.farmers.find((x) => x.id === e.currentTarget.dataset.feature);
    act(() => updateDoc(doc(db, "farmers", f.id), { featured: !f.featured }), f.featured ? "Removed from featured" : "Featured on home page"); });
  on("[data-fview]", "click", (e) => {
    const f = S.farmers.find((x) => x.id === e.currentTarget.dataset.fview);
    const prods = S.products.filter((p) => p.farmerId === f.id);
    const sales = S.orders.filter((o) => o.farmerId === f.id && o.status === "DELIVERED").reduce((s, o) => s + Number(o.itemsTotal || 0), 0);
    openModal(f.farmName || f.name, `<div class="kv">
      <div>Name</div><div>${esc(f.name)}</div>
      <div>Mobile</div><div>${tel(f.contactPhone)}</div>
      <div>Account email</div><div>${esc(S.users.find((u) => u.id === f.id)?.email || "—")}</div>
      <div>Village</div><div>${esc(f.village)}, ${esc(f.district)} — PIN ${esc(f.pinCode)}</div>
      <div>Farm location</div><div>${f.lat ? `<a target="_blank" href="https://www.openstreetmap.org/?mlat=${f.lat}&mlon=${f.lng}#map=15/${f.lat}/${f.lng}">Open map (${Number(f.lat).toFixed(4)}, ${Number(f.lng).toFixed(4)})</a>` : "Not set"}</div>
      <div>Delivery</div><div>${f.deliveryAvailable ? `Yes, up to ${esc(f.deliveryRadiusKm)} km` : "No (pickup only)"}</div>
      <div>Service PINs</div><div>${esc((f.servicePinCodes || []).join(", ") || "Any within distance")}</div>
      <div>Service villages</div><div>${esc((f.serviceVillages || []).join(", ") || "—")}</div>
      <div>Status</div><div>${farmerStatus(f)} ${f.rejectReason ? `<div class="muted">${esc(f.rejectReason)}</div>` : ""}</div>
      <div>Registered</div><div>${when(f.createdAt)}</div>
      <div>Products</div><div>${prods.length} (${prods.filter((p) => p.active).length} live)</div>
      <div>Delivered sales</div><div>${rs(sales)}</div></div>`);
  });
}
VIEWS.farmers = () => {
  const s = filters.s || "pending";
  const list = S.farmers.filter((f) => (s === "all") || (s === "pending" && !f.approved && !f.rejected) || (s === "approved" && f.approved) || (s === "rejected" && f.rejected))
    .filter((f) => match(filters.q, f.name, f.farmName, f.village, f.district, f.pinCode, f.contactPhone))
    .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
  return `<div class="toolbar">${chips([["pending", `Pending (${pendingFarmers().length})`], ["approved", "Approved"], ["rejected", "Rejected"], ["all", "All"]])}${searchBox("Search name, village, PIN, mobile…")}</div>
    <div class="panel">${farmerTable(list)}</div>`;
};
bind.farmers = () => { bindSearch(); bindFarmerActions(); };

// ---- products
VIEWS.products = () => {
  const s = filters.s || "live";
  const list = S.products.filter((p) => s === "all" || (s === "live" && p.active && p.stock > 0) || (s === "out" && p.stock <= 0) || (s === "hidden" && !p.active))
    .filter((p) => match(filters.q, p.name, p.nameHindi, p.farmerName, p.category))
    .sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0));
  return `<div class="toolbar">${chips([["live", "Live"], ["out", "Out of stock"], ["hidden", "Hidden"], ["all", "All"]])}${searchBox("Search product or farmer…")}</div>
  <div class="panel">${!list.length ? `<div class="empty">No products.</div>` : `<div class="tablewrap"><table>
    <tr><th>Product</th><th>Farmer</th><th>Price</th><th>Stock</th><th>Sold</th><th>Status</th><th></th></tr>
    ${list.map((p) => `<tr>
      <td><b>${esc(p.name)}</b> <span class="muted">${esc(p.nameHindi)}</span><div class="muted">${esc(p.category)}${p.deliveryAvailable ? " · delivers" : ""}</div></td>
      <td>${esc(p.farmerName)}</td>
      <td>${rs(p.price)}/${esc(p.unit)}${p.mrp > p.price ? `<div class="muted"><s>${rs(p.mrp)}</s></div>` : ""}</td>
      <td>${esc(p.stock)} ${esc(p.unit)}</td><td>${esc(p.soldCount || 0)}</td>
      <td>${p.active ? '<span class="pill p-ok">Live</span>' : '<span class="pill p-gray">Hidden</span>'}</td>
      <td><div class="acts"><button class="btn" data-pedit="${p.id}">Edit</button>
        <button class="btn" data-ptoggle="${p.id}">${p.active ? "Hide" : "Show"}</button>
        <button class="btn bad" data-pdel="${p.id}">Delete</button></div></td></tr>`).join("")}</table></div>`}</div>`;
};
bind.products = () => {
  bindSearch();
  on("[data-ptoggle]", "click", (e) => { const p = S.products.find((x) => x.id === e.currentTarget.dataset.ptoggle);
    act(() => updateDoc(doc(db, "products", p.id), { active: !p.active }), p.active ? "Hidden from buyers" : "Shown to buyers"); });
  on("[data-pdel]", "click", (e) => { const p = S.products.find((x) => x.id === e.currentTarget.dataset.pdel);
    if (confirm(`Delete “${p.name}” permanently?`)) act(() => deleteDoc(doc(db, "products", p.id)), "Product deleted"); });
  on("[data-pedit]", "click", (e) => {
    const p = S.products.find((x) => x.id === e.currentTarget.dataset.pedit);
    openModal("Edit " + p.name, `<form class="form" id="pf" style="padding:0">
      <label>Price ₹<input name="price" type="number" step="0.5" min="0" value="${esc(p.price)}" required></label>
      <label>Market price ₹<input name="mrp" type="number" step="0.5" min="0" value="${esc(p.mrp)}"></label>
      <label>Stock (${esc(p.unit)})<input name="stock" type="number" step="0.5" min="0" value="${esc(p.stock)}" required></label>
      <label>Photo URL (small)<input name="thumbUrl" value="${esc(p.thumbUrl)}"></label>
      <label class="full">Large photo URL<input name="imageUrl" value="${esc(p.imageUrl)}"></label>
      <div class="row"><button class="btn pri">Save</button></div></form>`, () => {
      $("#pf").onsubmit = (ev) => { ev.preventDefault(); const d = Object.fromEntries(new FormData(ev.target));
        act(async () => { await updateDoc(doc(db, "products", p.id), { price: +d.price, mrp: Math.max(+d.mrp || 0, +d.price), stock: +d.stock,
          onOffer: (+d.mrp || 0) > +d.price, thumbUrl: d.thumbUrl.trim(), imageUrl: d.imageUrl.trim(), updatedAt: serverTimestamp() }); closeModal(); }, "Product saved"); };
    });
  });
};

// ---- orders
function orderTable(list) {
  if (!list.length) return `<div class="empty">No orders.</div>`;
  return `<div class="tablewrap"><table><tr><th>Order</th><th>Buyer</th><th>Farmer</th><th>Items</th><th>Total</th><th>Status</th><th></th></tr>
  ${list.map((o) => `<tr>
    <td><b>#${esc(o.id.slice(-6).toUpperCase())}</b><div class="muted">${when(o.createdAt)}</div></td>
    <td>${esc(o.buyerName || userName(o.buyerId))}<div class="muted">${esc(o.deliveryVillage)} ${esc(o.deliveryPin)}</div></td>
    <td>${esc(o.farmerName)}</td>
    <td>${esc((o.lines || []).map((l) => `${l.name} × ${l.qty}`).join(", "))}</td>
    <td>${rs(o.grandTotal)}</td><td>${pill(o.status)}</td>
    <td><button class="btn" data-oview="${o.id}">Open</button></td></tr>`).join("")}</table></div>`;
}
function bindOrderActions() {
  on("[data-oview]", "click", async (e) => {
    const o = S.orders.find((x) => x.id === e.currentTarget.dataset.oview);
    let addr = o.deliveryAddress || "";
    if (!addr) { try { const a = (await getDoc(doc(db, "orders", o.id, "private", "address"))).data(); if (a) addr = [a.line, a.village, a.pinCode].filter(Boolean).join(", "); } catch {} }
    const statuses = ["PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP", "DELIVERED", "CANCELLED", "REJECTED"];
    openModal("Order #" + o.id.slice(-6).toUpperCase(), `<div class="kv">
      <div>Placed</div><div>${when(o.createdAt)}</div>
      <div>Status</div><div>${pill(o.status)}</div>
      <div>Buyer</div><div>${esc(o.buyerName || userName(o.buyerId))} · ${tel(o.buyerPhone)}</div>
      <div>Deliver to</div><div>${esc(addr || `${o.deliveryVillage} ${o.deliveryPin}`)}</div>
      <div>Farmer</div><div>${esc(o.farmerName)} · ${tel(o.farmerPhone || S.farmers.find((f) => f.id === o.farmerId)?.contactPhone)}</div>
      <div>How</div><div>${esc(String(o.fulfilment || "").replaceAll("_", " ").toLowerCase())}${o.collectionCentreName ? " — " + esc(o.collectionCentreName) : ""}</div>
      <div>Items</div><div>${(o.lines || []).map((l) => `${esc(l.name)} × ${esc(l.qty)} ${esc(l.unit)} @ ${rs(l.price)}`).join("<br>")}</div>
      <div>Items total</div><div>${rs(o.itemsTotal)}</div>
      <div>Delivery</div><div>Included in price</div>
      <div><b>Grand total</b></div><div><b>${rs(o.grandTotal)}</b></div></div>
      <div class="toolbar" style="margin-top:16px"><select id="ostat">${statuses.map((s) => `<option value="${s}" ${s === o.status ? "selected" : ""}>${ST[s][0]}</option>`).join("")}</select>
      <button class="btn pri" id="osave">Change status</button></div>
      <p class="muted">Changing status here notifies the buyer. Stock is not adjusted automatically when an admin cancels — edit it under Products if needed.</p>`, () => {
      $("#osave").onclick = () => { const s = $("#ostat").value; if (s === o.status) return closeModal();
        act(async () => { await updateDoc(doc(db, "orders", o.id), { status: s, updatedAt: serverTimestamp() });
          await notifySoft(o.buyerId, "Order update", `Your order from ${o.farmerName} is now: ${ST[s][0].toLowerCase()}.`, "orders");
          await notifySoft(o.farmerId, "Order update by admin", `Order #${o.id.slice(-6).toUpperCase()} is now: ${ST[s][0].toLowerCase()}.`, "orders");
          closeModal(); }, "Status updated"); };
    });
  });
}
VIEWS.orders = () => {
  const s = filters.s || "open";
  const list = S.orders.filter((o) => s === "all" || (s === "open" && openOrders().includes(o)) || o.status === s)
    .filter((o) => match(filters.q, o.id, o.buyerName, o.farmerName, o.deliveryVillage, o.deliveryPin, o.buyerPhone));
  const total = list.reduce((a, o) => a + Number(o.grandTotal || 0), 0);
  return `<div class="toolbar">${chips([["open", `Open (${openOrders().length})`], ["PLACED", "New"], ["DELIVERED", "Delivered"], ["CANCELLED", "Cancelled"], ["REJECTED", "Rejected"], ["all", "All"]])}${searchBox("Search order no, buyer, farmer, village…")}</div>
    <p class="muted">${list.length} orders · ${rs(total)}</p><div class="panel">${orderTable(list)}</div>`;
};
bind.orders = () => { bindSearch(); bindOrderActions(); };

// ---- tickets
VIEWS.tickets = () => {
  const s = filters.s || "active";
  const list = S.tickets.filter((t) => s === "all" || (s === "active" && openTickets().includes(t)) || t.status === s)
    .filter((t) => match(filters.q, t.ticketNo, t.subject, t.message, t.topic, userName(t.uid)));
  return `<div class="toolbar">${chips([["active", `Open (${openTickets().length})`], ["RESOLVED", "Resolved"], ["CLOSED", "Closed"], ["all", "All"]])}${searchBox("Search ticket no, subject…")}</div>
  <div class="panel">${!list.length ? `<div class="empty">No tickets.</div>` : `<div class="tablewrap"><table>
    <tr><th>Ticket</th><th>From</th><th>Topic / subject</th><th>Status</th><th></th></tr>
    ${list.map((t) => `<tr><td><b>${esc(t.ticketNo)}</b><div class="muted">${when(t.createdAt)}</div></td>
      <td>${esc(userName(t.uid))}<div class="muted">${esc(t.role)}</div></td>
      <td><b>${esc(t.topic)}</b> — ${esc(t.subject)}<div class="muted">${esc(String(t.message || "").slice(0, 90))}${String(t.message || "").length > 90 ? "…" : ""}</div></td>
      <td>${pill(t.status)}</td><td><button class="btn pri" data-tview="${t.id}">${t.adminReply ? "View" : "Reply"}</button></td></tr>`).join("")}</table></div>`}</div>`;
};
bind.tickets = () => {
  bindSearch();
  on("[data-tview]", "click", (e) => {
    const t = S.tickets.find((x) => x.id === e.currentTarget.dataset.tview);
    const u = S.users.find((x) => x.id === t.uid);
    openModal(`${t.ticketNo} — ${t.subject}`, `<div class="kv">
      <div>From</div><div>${esc(userName(t.uid))} (${esc(t.role)}) ${u?.email ? "· " + esc(u.email) : ""} ${u?.phone ? "· " + tel(u.phone) : ""}</div>
      <div>Topic</div><div>${esc(t.topic)}</div><div>Order</div><div>${t.orderId ? "#" + esc(t.orderId.slice(-6).toUpperCase()) : "—"}</div>
      <div>Received</div><div>${when(t.createdAt)}</div></div>
      <p style="white-space:pre-wrap;background:var(--bg);padding:12px;border-radius:10px">${esc(t.message)}</p>
      <form class="form" id="tf" style="padding:0">
        <label class="full">Your reply (the user sees this in the app)<textarea name="reply" rows="5">${esc(t.adminReply)}</textarea></label>
        <label>Status<select name="status">${["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"].map((s) => `<option value="${s}" ${s === t.status ? "selected" : ""}>${ST[s][0]}</option>`).join("")}</select></label>
        <div class="row"><button class="btn pri">Send reply</button></div></form>`, () => {
      $("#tf").onsubmit = (ev) => { ev.preventDefault(); const d = Object.fromEntries(new FormData(ev.target));
        act(async () => {
          await updateDoc(doc(db, "supportTickets", t.id), { adminReply: d.reply.trim(), status: d.status, updatedAt: serverTimestamp() });
          await notifySoft(t.uid, `Reply on ticket ${t.ticketNo}`, d.reply.trim().slice(0, 140) || `Status: ${ST[d.status][0]}`, "tickets");
          closeModal(); }, "Reply sent"); };
    });
  });
};

// ---- users
VIEWS.users = () => {
  const list = S.users.filter((u) => match(filters.q, u.name, u.email, u.phone))
    .sort((a, b) => (b.lastSeen?.seconds || 0) - (a.lastSeen?.seconds || 0));
  const farmerIds = new Set(S.farmers.map((f) => f.id));
  return `<div class="toolbar">${searchBox("Search name, email, mobile…")}</div>
  <p class="muted">${S.users.length} users · ${S.farmers.filter((f) => f.approved).length} approved sellers</p>
  <div class="panel"><div class="tablewrap"><table><tr><th>Name</th><th>Email / mobile</th><th>Role</th><th>Orders</th><th>Last seen</th><th></th></tr>
  ${list.map((u) => { const f = S.farmers.find((x) => x.id === u.id); return `<tr>
    <td><b>${esc(u.name || "—")}</b></td><td>${esc(u.email || "")}<div class="muted">${esc(u.phone || "")}</div></td>
    <td>${farmerIds.has(u.id) ? `Seller ${farmerStatus(f)}` : "Buyer"}${u.isAdmin ? ' <span class="pill p-ok">Admin</span>' : ""}</td>
    <td>${S.orders.filter((o) => o.buyerId === u.id).length}</td><td>${when(u.lastSeen)}</td>
    <td><button class="btn" data-msg="${u.id}">Message</button></td></tr>`; }).join("")}</table></div></div>`;
};
bind.users = () => {
  bindSearch();
  on("[data-msg]", "click", (e) => openMessage(e.currentTarget.dataset.msg));
};

async function openMessage(id) {
  const u = S.users.find((x) => x.id === id) || {};
  openModal("Message " + userName(id), `<form class="form" id="mf" style="padding:0">
      <label class="full">Title<input name="title" required value="Message from Himachal Farm Direct"></label>
      <label class="full">Message<textarea name="body" rows="4" required></textarea></label>
      <div class="row"><button class="btn pri">Send</button></div></form>
    <p class="muted">It pops up the next time ${esc(u.name || "the user")} opens the app (Home page), and stays under 🔔 Notifications.</p>
    <h4 style="margin:16px 0 6px">Messages to this user</h4><div id="mhist" class="muted">Loading…</div>`, () => {
    const load = async () => {
      try {
        const snap = await getDocs(query(collection(db, "users", id, "notifications"), orderBy("createdAt", "desc"), limit(20)));
        $("#mhist").innerHTML = snap.empty ? "None yet." : `<table>${snap.docs.map((d) => { const n = d.data(); return `<tr>
          <td><b>${esc(n.title)}</b><div class="muted">${esc(n.body)}</div></td><td>${when(n.createdAt)}</td>
          <td>${n.read ? `<span class="pill p-ok">✓ Read ${n.readAt ? when(n.readAt) : ""}</span>` : '<span class="pill p-wait">Not read yet</span>'}</td></tr>`; }).join("")}</table>`;
      } catch (err) { $("#mhist").textContent = "Couldn't load history: " + (err.code || err.message); }
    };
    load();
    $("#mf").onsubmit = (ev) => { ev.preventDefault(); const d = Object.fromEntries(new FormData(ev.target));
      act(async () => { await notify(id, d.title.trim(), d.body.trim()); ev.target.reset(); await load(); }, "Sent ✓ — the user will see it when they open the app"); };
  });
}

// ---- banners
VIEWS.banners = () => `<div class="panel"><h3>Add banner</h3>
  <form class="form" id="bf">
    <label>Title<input name="title" required placeholder="Fresh apples are here!"></label>
    <label>Subtitle<input name="subtitle" placeholder="From Kullu & Shimla farmers"></label>
    <label>Image URL<input name="imageUrl" placeholder="https://…"></label>
    <label>Opens category<select name="categoryId"><option value="">— none —</option>
      ${["vegetables", "fruits", "pulses", "honey", "dairy", "spices", "other"].map((c) => `<option>${c}</option>`).join("")}</select></label>
    <label>Order (1 = first)<input name="order" type="number" value="${S.banners.length + 1}"></label>
    <div class="row"><button class="btn pri">Add banner</button></div></form></div>
  <div class="panel"><h3>Banners on the home page</h3>${!S.banners.length ? `<div class="empty">No banners yet.</div>` : `<div class="tablewrap"><table>
    <tr><th>#</th><th>Title</th><th>Category</th><th>Status</th><th></th></tr>
    ${S.banners.map((b) => `<tr><td>${esc(b.order)}</td><td><b>${esc(b.title)}</b><div class="muted">${esc(b.subtitle)}</div></td><td>${esc(b.categoryId || "—")}</td>
      <td>${b.active ? '<span class="pill p-ok">Showing</span>' : '<span class="pill p-gray">Hidden</span>'}</td>
      <td><div class="acts"><button class="btn" data-btog="${b.id}">${b.active ? "Hide" : "Show"}</button><button class="btn bad" data-bdel="${b.id}">Delete</button></div></td></tr>`).join("")}</table></div>`}</div>`;
bind.banners = () => {
  $("#bf").onsubmit = (ev) => { ev.preventDefault(); const d = Object.fromEntries(new FormData(ev.target));
    act(() => addDoc(collection(db, "banners"), { title: d.title.trim(), subtitle: d.subtitle.trim(), imageUrl: d.imageUrl.trim(), categoryId: d.categoryId, order: +d.order || 1, active: true }), "Banner added"); };
  on("[data-btog]", "click", (e) => { const b = S.banners.find((x) => x.id === e.currentTarget.dataset.btog); act(() => updateDoc(doc(db, "banners", b.id), { active: !b.active })); });
  on("[data-bdel]", "click", (e) => { if (confirm("Delete this banner?")) act(() => deleteDoc(doc(db, "banners", e.currentTarget.dataset.bdel)), "Deleted"); });
};

// ---- collection centres
VIEWS.centres = () => `<div class="panel"><h3>Add collection centre</h3>
  <form class="form" id="cf">
    <label>Name<input name="name" required placeholder="Karsog Bus Stand Shop"></label>
    <label>Village / town<input name="village" required></label>
    <label>PIN<input name="pinCode" pattern="[0-9]{6}" required></label>
    <label>Open hours<input name="openHours" placeholder="9 AM – 7 PM"></label>
    <label>Latitude<input name="lat" type="number" step="any" required placeholder="31.38"></label>
    <label>Longitude<input name="lng" type="number" step="any" required placeholder="77.20"></label>
    <p class="muted full" style="margin:0">Tip: on Google Maps / OpenStreetMap long-press the spot and copy the two numbers.</p>
    <div class="row"><button class="btn pri">Add centre</button></div></form></div>
  <div class="panel"><h3>Centres</h3>${!S.centres.length ? `<div class="empty">No collection centres yet.</div>` : `<div class="tablewrap"><table>
    <tr><th>Name</th><th>Place</th><th>Hours</th><th>Farmers using</th><th>Status</th><th></th></tr>
    ${S.centres.map((c) => `<tr><td><b>${esc(c.name)}</b></td><td>${esc(c.village)} ${esc(c.pinCode)}
      <div class="muted"><a target="_blank" href="https://www.openstreetmap.org/?mlat=${c.lat}&mlon=${c.lng}#map=16/${c.lat}/${c.lng}">map</a></div></td>
      <td>${esc(c.openHours)}</td><td>${S.farmers.filter((f) => (f.collectionCentreIds || []).includes(c.id)).length}</td>
      <td>${c.active ? '<span class="pill p-ok">Active</span>' : '<span class="pill p-gray">Closed</span>'}</td>
      <td><div class="acts"><button class="btn" data-ctog="${c.id}">${c.active ? "Close" : "Open"}</button><button class="btn bad" data-cdel="${c.id}">Delete</button></div></td></tr>`).join("")}</table></div>`}</div>`;
bind.centres = () => {
  $("#cf").onsubmit = (ev) => { ev.preventDefault(); const d = Object.fromEntries(new FormData(ev.target)); const lat = +d.lat, lng = +d.lng;
    act(() => addDoc(collection(db, "collectionCentres"), { name: d.name.trim(), village: d.village.trim(), pinCode: d.pinCode.trim(), openHours: d.openHours.trim(),
      lat, lng, geohash: geohash(lat, lng), active: true }), "Centre added"); };
  on("[data-ctog]", "click", (e) => { const c = S.centres.find((x) => x.id === e.currentTarget.dataset.ctog); act(() => updateDoc(doc(db, "collectionCentres", c.id), { active: !c.active })); });
  on("[data-cdel]", "click", (e) => { if (confirm("Delete this centre?")) act(() => deleteDoc(doc(db, "collectionCentres", e.currentTarget.dataset.cdel)), "Deleted"); });
};

// ---- settings
VIEWS.settings = () => {
  const d = { baseCharge: 20, perKm: 4, freeAboveOrderValue: 500, maxDeliveryKm: 50, collectionCentreCharge: 0, collectionCentreMaxKm: 25, minOrderValue: 0, ...S.delivery };
  const pinRules = Object.entries(d.pinRules || {}).filter(([, r]) => r.blocked).map(([pin]) => pin).join(", ");
  const F = (k, l) => `<label>${l}<input name="${k}" type="number" step="any" min="0" value="${esc(d[k])}"></label>`;
  return `<div class="panel"><h3>🚚 Delivery limits</h3>
  <form class="form" id="df">
    <p class="muted full" style="margin:0">No delivery charge is added at checkout — each seller's selling price already includes delivery.</p>
    ${F("maxDeliveryKm", "Max home-delivery distance km")}
    ${F("collectionCentreMaxKm", "Max buyer → centre km")}${F("minOrderValue", "Minimum order ₹")}
    <label class="full">Blocked PIN codes (no home delivery), e.g. <code>175001, 175002</code><input name="pinRules" value="${esc(pinRules)}"></label>
    <div class="row"><button class="btn pri">Save delivery settings</button></div></form></div>
  <div class="panel"><h3>✉️ Support</h3>
  <form class="form" id="sf">
    <label>Admin email for ticket copies<input name="adminEmail" type="email" value="${esc(S.support.adminEmail || "dharmenderk950@hotmail.com")}"></label>
    <div class="row"><button class="btn pri">Save</button></div></form>
  <p class="muted" style="padding:0 16px 16px">Help Centre email, WhatsApp number and its “verified” switch live in
  <a target="_blank" href="https://console.firebase.google.com/project/${esc(window.FIREBASE_CONFIG.projectId)}/config">Firebase Remote Config</a>
  (set <code>support_whatsapp_verified</code> to true once +91 94180 31378 is confirmed on WhatsApp).</p></div>`;
};
bind.settings = () => {
  $("#df").onsubmit = (ev) => { ev.preventDefault(); const f = Object.fromEntries(new FormData(ev.target)); const pinRules = {};
    String(f.pinRules || "").split(/[,\s]+/).map((x) => x.trim()).filter((x) => /^\d{6}$/.test(x)).forEach((pin) => { pinRules[pin] = { blocked: true }; });
    const num = (k) => +f[k] || 0;
    act(() => setDoc(doc(db, "config", "delivery"), { baseCharge: 0, perKm: 0, freeAboveOrderValue: 0, collectionCentreCharge: 0,
      maxDeliveryKm: num("maxDeliveryKm"), collectionCentreMaxKm: num("collectionCentreMaxKm"),
      minOrderValue: num("minOrderValue"), pinRules, villageRules: S.delivery.villageRules || {} }), "Delivery settings saved — used from the next checkout"); };
  $("#sf").onsubmit = (ev) => { ev.preventDefault(); const f = Object.fromEntries(new FormData(ev.target));
    act(() => setDoc(doc(db, "config", "support"), { adminEmail: f.adminEmail.trim() }, { merge: true }), "Saved"); };
};
