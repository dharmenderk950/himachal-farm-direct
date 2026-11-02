# Himachal Farm Direct — Android app (single APK) + Firebase backend

Buyer and Seller in one APK · GPS/manual location marketplace · Home page · Help Centre with tickets.
Kotlin · Jetpack Compose · Hilt · Firebase (Auth, Firestore, Functions, FCM, Remote Config) · Google Maps · geohash search.

---

## 1. What's in this project

| Folder | Contents |
|---|---|
| `app/` | Android app (package `com.dk.himachalfarmdirect`, min Android 7.0) |
| `functions/` | Cloud Functions (TypeScript): delivery checks, orders, tickets, notifications |
| `firestore.rules`, `firestore.indexes.json` | Security rules and query indexes |
| `remoteconfig.template.json` | Admin contact details & radii (editable without a new APK) |
| `backend-seed/config-delivery.json` | Delivery-charge rules to paste into Firestore |

**Every screen reads real Firestore data. There are no dummy farmers, products or locations.**
Until you add farmers and products, the app correctly shows empty states.

---

## 2. One-time setup (about 30–45 minutes)

### 2.1 Firebase project
1. Go to <https://console.firebase.google.com> → **Add project** → name it *Himachal Farm Direct*.
2. Upgrade to the **Blaze** (pay-as-you-go) plan — required for Cloud Functions. Small apps usually stay in the free quota.
3. **Firestore Database** → Create → location **asia-south1 (Mumbai)** → production mode.
4. **Authentication** → Sign-in method → enable **Phone**.
5. **Project settings → Add app → Android**, package name `com.dk.himachalfarmdirect`.
   Add your **SHA-1 and SHA-256** fingerprints (Android Studio → Gradle → *signingReport*). Phone OTP will not work without them.
6. Download **`google-services.json`** and place it in the **`app/`** folder.

### 2.2 Google Maps key
1. <https://console.cloud.google.com> (same project) → APIs & Services → enable **Maps SDK for Android**.
2. Credentials → **Create API key** → restrict to Android apps, package `com.dk.himachalfarmdirect` + your SHA-1.
3. In the project's `local.properties` add:  `MAPS_API_KEY=AIza...your key...`

### 2.3 Deploy the backend
```bash
npm install -g firebase-tools
firebase login
firebase use --add            # pick your project
cd functions && npm install && cd ..
firebase deploy --only firestore,functions,remoteconfig
```

### 2.4 Delivery rules (spec A-10, A-12)
Firestore → start collection **`config`** → document **`delivery`** → add the fields from
`backend-seed/config-delivery.json` (`baseCharge`, `perKm`, `freeAboveOrderValue`, `maxDeliveryKm`,
`collectionCentreCharge`, `collectionCentreMaxKm`, `minOrderValue`, `pinRules`, `villageRules`).
Change them any time; the next checkout uses the new values.

### 2.5 Make yourself administrator
1. Install the app and sign in once with your mobile number.
2. Firebase → Authentication → copy your **UID**.
3. Firebase → Project settings → Service accounts → **Generate new private key** → save as `functions/service-account.json` (never share it).
4. ```bash
   cd functions && npm run build
   GOOGLE_APPLICATION_CREDENTIALS=service-account.json npm run set-admin -- YOUR_UID
   ```
   Sign out and back in. You now receive new-ticket alerts and can approve farmers.

### 2.6 Build the APK
Open the folder in **Android Studio (Ladybug or newer)** → let Gradle sync → **Build → Build APK(s)**.
The APK is in `app/build/outputs/apk/debug/`. For a release APK use *Build → Generate Signed App Bundle/APK*.

> I could not compile the Android app in my workspace (Google's Android SDK servers were not reachable from it), so the first Gradle sync on your PC is the first real build. The Cloud Functions code was compiled and type-checked successfully. If Android Studio shows any error, send me the message and I'll fix it.

---

## 3. Administrator tasks

| Task | Where |
|---|---|
| Approve a farmer | Firestore → `farmers/{id}` → set `approved` = `true` (or `rejected` = `true`). The farmer is notified, their account gets Seller mode, and buyers within 20 km who opted in get a "new farmer near you" alert. |
| Feature a farmer | `farmers/{id}` → `featured` = `true` |
| Reply to a ticket | Firestore → `supportTickets/{id}` → fill `adminReply`, set `status` (`IN_PROGRESS`, `RESOLVED`, `CLOSED`). The user is notified automatically. |
| Banners | Collection `banners`: `title`, `subtitle`, `imageUrl`, `categoryId`, `active` (true), `order` (number) |
| Collection centres | Collection `collectionCentres`: `name`, `village`, `pinCode`, `lat`, `lng`, `openHours`, `active` |
| Contact details | Firebase → **Remote Config** → edit `support_email`, `support_whatsapp_number`, `support_hours` → **Publish** |

### WhatsApp support (spec E)
- The owner ID **dkc@1978** is shown as text only — it is never treated as a phone number.
- The supplied number **+91 94180 31378** is pre-filled in Remote Config, but the **WhatsApp button stays hidden**
  while `support_whatsapp_verified` is `false`.
- After confirming that number receives WhatsApp messages, set `support_whatsapp_verified` → **true** and **Publish**.
  The button appears for all users within about an hour (or on next app start).

Optional: install the **Trigger Email** Firebase extension (collection `mail`) to also receive every new ticket by email at
`dharmenderk950@hotmail.com` (change via Firestore `config/support.adminEmail`).

---

## 4. How the spec is implemented

**A. Location marketplace**
- Permission asked only when the user taps *Use my current location*, after a plain-language explanation; denial and "don't ask again" lead to manual choice or app settings.
- GPS off → system "turn on location" dialog; weak signal → 15 s timeout, last known fix, then manual fallback.
- Manual selection: village/PIN search (geocoding), draggable map pin (reverse geocoding), saved addresses.
- Farm location registered by GPS or map pin; geohash stored for scalable radius search (5/10/20/50 km, configurable in Remote Config).
- Distance shown everywhere is approximate straight-line distance.
- **Delivery eligibility is decided only by the `checkDelivery` / `placeOrder` functions**, which re-check farmer approval, live stock, product and farmer delivery flags, farmer radius, platform maximum, PIN/village service lists, blocked areas, collection-centre reach — then compute the charge from `config/delivery`. Orders deduct stock inside a transaction.
- Privacy: exact addresses live only in `users/{uid}/addresses` (owner-only). For alerts only a ~5 km geohash cell is stored. Farmers see the buyer's village/PIN, and the exact address only for an order placed with them.
- *Browse without location* / Account toggle turns discovery off and switches the home page to category browsing.

**B. Home page** — logo & name, current delivery location, search, categories, banners, radius & delivery filters, nearby farmers, vegetables, fruits, pulses & grains, honey, recently added, best sellers, featured farmers, offers, wishlist, cart badge, notifications badge, Help Centre card. Green & white theme, large text and buttons. 100 MB Firestore offline cache, 150 MB image disk cache, 400 px thumbnails, lazy lists, offline banner, auto-reload when internet returns.

**C. Buyer & Seller mode** — same APK and account. Seller mode unlocks only after admin approval; the same 4 tabs then show Dashboard, My products, Orders to fulfil, Account. A farmer in Buyer mode can buy from other farmers (ordering from yourself is blocked).

**D. Help Centre** — from Home, Account and the login screen; Buyer, Seller and General sections with all listed topics.

**E. Admin contact** — email button opens the email app with subject and device details prefilled; WhatsApp as above; support form → ticket number `HFD-2026-00001`, ticket status list, push + in-app notification on admin reply. Contact details come from Remote Config.

### Please review before launch
- Help Centre policy wording (verification time, refund days, commission and settlement schedule) is a sensible starting text — edit `ui/help/HelpContent.kt` to match your actual policy.
- Product photos: the app displays `thumbUrl` / `imageUrl`. In-app photo upload by farmers (Firebase Storage + compression) is **not built yet**; for now add image URLs in Firestore. Tell me if you want it added.
- Payments are cash/UPI on delivery; no online payment gateway is integrated.

---

## 5. Final acceptance tests (spec F)

| # | Test | How to check |
|---|---|---|
| 1 | GPS permission | Fresh install → Home → location → *Use my current location* → explanation dialog → Allow → pin moves to you. Repeat with Deny → manual options still work. |
| 2 | Manual location | Search a village or PIN; drag the map; pick a saved address. |
| 3 | Nearby farmers | Approve 2 test farmers at known places; they appear under *Farmers near you* with distances. |
| 4 | Search radius | Switch 5 → 50 km; farmers outside the radius disappear/appear. |
| 5 | Delivery-area validation | Address outside the farmer's PIN list or radius → red ✗ reason in cart, *Place order* disabled for that farmer. |
| 6 | Buyer/Seller in one APK | Approved farmer: Account → Seller ↔ Buyer switch changes tabs; buy from another farmer in Buyer mode. |
| 7 | Real backend products | Add a product in Seller mode → it appears on a nearby buyer's Home. |
| 8 | Help Centre | Open from Home, Account and login; every topic opens. |
| 9 | Email support | *Email support* opens the email app addressed to dharmenderk950@hotmail.com with a subject. |
| 10 | WhatsApp after verification | Button hidden while `support_whatsapp_verified` = false; appears after setting true and publishing. |
| 11 | Tickets reach admin | Submit a request → ticket number shown → admin gets a notification and the doc in `supportTickets`. Reply → user notified. |
| 12 | Slow internet | Phone in airplane mode after browsing → saved products still show with the offline banner; Android Studio emulator *Network: EDGE* for slow-link testing. |

---

## 7. Get the APK built by GitHub (no Android Studio needed)

1. Push this project to your GitHub repository (Claude can do this for you).
2. Repository → **Settings → Secrets and variables → Actions → New repository secret**:
   - `GOOGLE_SERVICES_JSON` → paste the whole text of `google-services.json` from Firebase.
   - `MAPS_API_KEY` → your Maps key (optional).
3. Repository → **Actions → Build APK → Run workflow** (it also runs on every push to `main`).
4. When the run turns green (about 8–12 minutes), open it → **Artifacts → HimachalFarmDirect-APK** → download the zip, which contains the APK.
5. Copy the APK to your phone and install it (allow "Install unknown apps" when asked).

If the run is red, open it, copy the red error text and send it to Claude.

---

App by **DK** · dharmenderk950@gmail.com · WhatsApp: dkc@1978
