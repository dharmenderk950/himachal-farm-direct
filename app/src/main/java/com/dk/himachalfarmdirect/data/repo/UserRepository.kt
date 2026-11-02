package com.dk.himachalfarmdirect.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.SellerStatus
import com.dk.himachalfarmdirect.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val Context.modeStore by preferencesDataStore("app_mode")

enum class AppMode { BUYER, SELLER }

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    private val modeKey = stringPreferencesKey("mode")
    private val roleChosenKey = androidx.datastore.preferences.core.booleanPreferencesKey("role_chosen")

    /** Whether the Buyer / Seller start page has been answered on this phone. */
    val roleChosen: Flow<Boolean> = context.modeStore.data.map { it[roleChosenKey] ?: false }

    suspend fun chooseRole(mode: AppMode) {
        context.modeStore.edit { it[modeKey] = mode.name; it[roleChosenKey] = true }
    }

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    val uid: String? get() = auth.currentUser?.uid

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val profile: Flow<UserProfile?> = authState.flatMapLatest { user ->
        if (user == null) flowOf(null) else callbackFlow {
            val reg = db.collection("users").document(user.uid).addSnapshotListener { s, _ ->
                trySend(s?.toObject(UserProfile::class.java)?.copy(uid = user.uid))
            }
            awaitClose { reg.remove() }
        }
    }

    /**
     * Spec C: one APK, two modes. Seller Mode is only reachable when the backend has set
     * sellerStatus = APPROVED; otherwise the effective mode is always BUYER. A seller in
     * Buyer Mode shops from other farmers with the same account.
     */
    /**
     * Seller status read straight from the user's own farmers/{uid} doc, so approval works
     * even before the backend functions are deployed: approved=true → APPROVED,
     * rejected=true → REJECTED, doc exists → PENDING, no doc → NONE.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sellerStatus: Flow<SellerStatus> = authState.flatMapLatest { user ->
        if (user == null) flowOf(SellerStatus.NONE) else callbackFlow {
            val reg = db.collection("farmers").document(user.uid).addSnapshotListener { s, _ ->
                trySend(
                    when {
                        s == null || !s.exists() -> SellerStatus.NONE
                        s.getBoolean("approved") == true -> SellerStatus.APPROVED
                        s.getBoolean("rejected") == true -> SellerStatus.REJECTED
                        else -> SellerStatus.PENDING
                    }
                )
            }
            awaitClose { reg.remove() }
        }
    }

    val mode: Flow<AppMode> = combine(
        sellerStatus,
        context.modeStore.data.map { runCatching { AppMode.valueOf(it[modeKey] ?: "BUYER") }.getOrDefault(AppMode.BUYER) },
    ) { status, stored ->
        if (status == SellerStatus.APPROVED) stored else AppMode.BUYER
    }

    suspend fun setMode(mode: AppMode) {
        context.modeStore.edit { it[modeKey] = mode.name }
    }

    /** Creates the users/{uid} doc on first sign-in (role fields are never written by the client). */
    suspend fun ensureProfile(name: String? = null) {
        val user = auth.currentUser ?: return
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        val data = buildMap<String, Any> {
            user.phoneNumber?.takeIf { it.isNotBlank() }?.let { put("phone", it) }
            user.email?.takeIf { it.isNotBlank() }?.let { put("email", it) }
            (name?.takeIf { it.isNotBlank() } ?: user.displayName?.takeIf { it.isNotBlank() })?.let { put("name", it) }
            token?.let { put("fcmToken", it) }
            put("lastSeen", FieldValue.serverTimestamp())
        }
        db.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
    }

    suspend fun updateFcmToken(token: String) {
        val id = uid ?: return
        db.collection("users").document(id).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    /**
     * Spec A-13 / A-15: stores only a ~5 km geohash cell for "new seller nearby" alerts.
     * Passing null (discovery off) clears it.
     */
    suspend fun updateDiscovery(enabled: Boolean, notifyNewSellers: Boolean, around: LatLngPoint?) {
        val id = uid ?: return
        db.collection("users").document(id).set(
            mapOf(
                "locationDiscoveryEnabled" to enabled,
                "notifyNewSellers" to notifyNewSellers,
                "coarseGeohash" to if (enabled && notifyNewSellers && around != null) Geo.coarseHash(around) else "",
            ),
            SetOptions.merge(),
        ).await()
    }

    // ---- Saved delivery addresses (private sub-collection, spec A-4) ----

    fun addresses(): Flow<List<Address>> {
        val id = uid ?: return flowOf(emptyList())
        return callbackFlow {
            val reg = db.collection("users").document(id).collection("addresses")
                .addSnapshotListener { s, _ ->
                    trySend(s?.documents?.mapNotNull { d -> d.toObject(Address::class.java)?.copy(id = d.id) }.orEmpty())
                }
            awaitClose { reg.remove() }
        }
    }

    suspend fun saveAddress(a: Address) {
        val id = uid ?: return
        val col = db.collection("users").document(id).collection("addresses")
        val ref = if (a.id.isBlank()) col.document() else col.document(a.id)
        if (a.defaultAddress) {
            col.whereEqualTo("defaultAddress", true).get().await().documents
                .filter { it.id != ref.id }
                .forEach { it.reference.update("defaultAddress", false).await() }
        }
        ref.set(a.copy(id = ref.id)).await()
    }

    suspend fun deleteAddress(addressId: String) {
        val id = uid ?: return
        db.collection("users").document(id).collection("addresses").document(addressId).delete().await()
    }

    // ---- Farmer registration (spec A-5, A-10) ----

    /**
     * Submits / updates the farm profile. The doc is created with approved=false; Firestore
     * rules block the client from setting approved/featured, and the admin approves it.
     */
    suspend fun saveFarmProfile(f: Farmer) {
        val id = uid ?: return
        val existing = db.collection("farmers").document(id).get().await()
        val data = mutableMapOf<String, Any>(
            "id" to id,
            "name" to f.name,
            "farmName" to f.farmName,
            "village" to f.village,
            "district" to f.district,
            "pinCode" to f.pinCode,
            "lat" to f.lat,
            "lng" to f.lng,
            "geohash" to Geo.hash(LatLngPoint(f.lat, f.lng)),
            "deliveryAvailable" to f.deliveryAvailable,
            "deliveryRadiusKm" to f.deliveryRadiusKm,
            "servicePinCodes" to f.servicePinCodes.map { it.trim() }.filter { it.length == 6 },
            "serviceVillages" to f.serviceVillages.map { it.trim().lowercase() }.filter { it.isNotBlank() },
            "collectionCentreIds" to f.collectionCentreIds,
            "contactPhone" to f.contactPhone.filter(Char::isDigit).takeLast(10),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!existing.exists()) {
            data["approved"] = false
            data["featured"] = false
            data["createdAt"] = FieldValue.serverTimestamp()
        }
        db.collection("farmers").document(id).set(data, SetOptions.merge()).await()
        db.collection("users").document(id).set(
            mapOf("sellerApplied" to true), SetOptions.merge()
        ).await()
    }

    suspend fun myFarm(): Farmer? {
        val id = uid ?: return null
        return db.collection("farmers").document(id).get().await().toObject(Farmer::class.java)
    }

    fun signOut() = auth.signOut()
}
