// Read-only diagnostic: prints recent in-app notifications per user (titles only, no private data).
const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
initializeApp({ credential: cert(require(process.env.GOOGLE_APPLICATION_CREDENTIALS)) });
const db = getFirestore();
(async () => {
  const users = await db.collection("users").get();
  console.log("users:", users.size);
  for (const u of users.docs) {
    const n = await u.ref.collection("notifications").get();
    const d = u.data();
    console.log(`user ${u.id.slice(0, 6)} name=${d.name || "-"} notifications=${n.size}`);
    n.docs.slice(0, 5).forEach((x) => { const v = x.data();
      console.log(`   - "${v.title}" read=${v.read} createdAt=${v.createdAt ? (v.createdAt.toDate ? v.createdAt.toDate().toISOString() : JSON.stringify(v.createdAt)) : "MISSING"}`); });
  }
})().catch((e) => { console.error(e); process.exit(1); });
