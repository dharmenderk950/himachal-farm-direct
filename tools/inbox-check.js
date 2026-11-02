// Diagnostic: lists users and how many inbox messages each has (no message text printed).
const admin = require("firebase-admin");
admin.initializeApp();
(async () => {
  const db = admin.firestore();
  const users = await db.collection("users").get();
  for (const u of users.docs) {
    const n = await u.ref.collection("notifications").get();
    const d = u.data();
    const last = n.docs.map((x) => x.data()).sort((a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0))[0];
    console.log(`${u.id.slice(0, 6)}… name=${d.name || "-"} hasEmail=${!!d.email} inbox=${n.size}` +
      (last ? ` lastKeys=${Object.keys(last).sort().join(",")} createdAt=${last.createdAt ? "ok" : "MISSING"}` : ""));
  }
  const groups = await db.collectionGroup("notifications").get();
  console.log("total notifications:", groups.size);
})().catch((e) => { console.error(e); process.exit(1); });
