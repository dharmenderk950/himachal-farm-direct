/**
 * One-time script: make a signed-in user an administrator.
 *   1. Sign in once in the app with the admin's phone number.
 *   2. In Firebase console → Authentication, copy that user's UID.
 *   3. Run:  GOOGLE_APPLICATION_CREDENTIALS=service-account.json npm run set-admin -- <UID>
 */
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

async function main() {
  const uid = process.argv[2];
  if (!uid) { console.error("Usage: npm run set-admin -- <UID>"); process.exit(1); }
  initializeApp();
  await getAuth().setCustomUserClaims(uid, { admin: true });
  await getFirestore().doc(`users/${uid}`).set({ isAdmin: true }, { merge: true });
  console.log(`✔ ${uid} is now an admin. They must sign out and in again.`);
}
main().catch((e) => { console.error(e); process.exit(1); });
