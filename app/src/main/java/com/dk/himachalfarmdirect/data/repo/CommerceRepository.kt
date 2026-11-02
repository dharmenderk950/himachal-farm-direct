package com.dk.himachalfarmdirect.data.repo

import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.data.model.CartLine
import com.dk.himachalfarmdirect.data.model.DeliveryQuote
import com.dk.himachalfarmdirect.data.model.Order
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.data.model.SupportTicket
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory cart. Orders are split per farmer at checkout. */
@Singleton
class CartRepository @Inject constructor() {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines

    fun add(p: Product, qty: Double = 1.0) = _lines.update { cur ->
        val existing = cur.firstOrNull { it.productId == p.id }
        if (existing != null) cur.map { if (it.productId == p.id) it.copy(qty = it.qty + qty) else it }
        else cur + CartLine(p.id, p.farmerId, p.name, p.unit, p.price, qty, p.thumbUrl)
    }

    fun setQty(productId: String, qty: Double) = _lines.update { cur ->
        if (qty <= 0) cur.filterNot { it.productId == productId }
        else cur.map { if (it.productId == productId) it.copy(qty = qty) else it }
    }

    fun clearFarmer(farmerId: String) = _lines.update { cur -> cur.filterNot { it.farmerId == farmerId } }
}

/**
 * Spec A "Important": the client never decides delivery eligibility. These callables run on
 * the backend, which re-reads stock, service areas, the farmer's delivery settings and the
 * distance/charge rules before answering or accepting an order.
 */
@Singleton
class OrderRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) {
    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.toQuote() = DeliveryQuote(
        eligible = this["eligible"] as? Boolean ?: false,
        reasons = (this["reasons"] as? List<String>).orEmpty(),
        distanceKm = (this["distanceKm"] as? Number)?.toDouble() ?: 0.0,
        deliveryCharge = (this["deliveryCharge"] as? Number)?.toDouble() ?: 0.0,
        modesAvailable = (this["modesAvailable"] as? List<String>).orEmpty(),
    )

    private fun payload(farmerId: String, lines: List<CartLine>, addressId: String, mode: String, centreId: String?) =
        hashMapOf(
            "farmerId" to farmerId,
            "addressId" to addressId,
            "fulfilment" to mode,
            "collectionCentreId" to (centreId ?: ""),
            "lines" to lines.map { hashMapOf("productId" to it.productId, "qty" to it.qty) },
        )

    suspend fun checkDelivery(farmerId: String, lines: List<CartLine>, address: Address, mode: String, centreId: String? = null): DeliveryQuote {
        val res = functions.getHttpsCallable("checkDelivery")
            .call(payload(farmerId, lines, address.id, mode, centreId)).await()
        return (res.getData() as? Map<*, *>)?.toQuote() ?: DeliveryQuote(reasons = listOf("No response from server"))
    }

    /** Returns the new order id, or throws with the server's reason. */
    suspend fun placeOrder(farmerId: String, lines: List<CartLine>, address: Address, mode: String, centreId: String? = null): String {
        val res = functions.getHttpsCallable("placeOrder")
            .call(payload(farmerId, lines, address.id, mode, centreId)).await()
        return (res.getData() as? Map<*, *>)?.get("orderId") as? String
            ?: error("Order could not be placed")
    }

    fun myOrders(): Flow<List<Order>> = ordersWhere("buyerId")
    fun farmOrders(): Flow<List<Order>> = ordersWhere("farmerId")

    private fun ordersWhere(field: String): Flow<List<Order>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val reg = db.collection("orders").whereEqualTo(field, uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener { s, _ ->
                    trySend(s?.documents?.mapNotNull { d -> d.toObject(Order::class.java)?.copy(id = d.id) }.orEmpty())
                }
            awaitClose { reg.remove() }
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        functions.getHttpsCallable("updateOrderStatus")
            .call(hashMapOf("orderId" to orderId, "status" to status)).await()
    }
}

/** Seller-side product and stock management (spec D seller help topics map to these). */
@Singleton
class SellerRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    fun myProducts(): Flow<List<Product>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val reg = db.collection("products").whereEqualTo("farmerId", uid)
                .addSnapshotListener { s, _ ->
                    trySend(s?.documents?.mapNotNull { d -> d.toObject(Product::class.java)?.copy(id = d.id) }.orEmpty())
                }
            awaitClose { reg.remove() }
        }
    }

    /**
     * Location fields (geohash/lat/lng) and farmerName are copied from the farm profile by
     * the onProductWrite Cloud Function, so a seller cannot place a product somewhere else.
     */
    suspend fun saveProduct(p: Product) {
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("products")
        val ref = if (p.id.isBlank()) col.document() else col.document(p.id)
        val data = mutableMapOf<String, Any>(
            "id" to ref.id,
            "farmerId" to uid,
            "name" to p.name.trim(),
            "nameHindi" to p.nameHindi.trim(),
            "category" to p.category,
            "unit" to p.unit,
            "price" to p.price,
            "mrp" to maxOf(p.mrp, p.price),
            "stock" to p.stock,
            "description" to p.description,
            "seasonal" to p.seasonal,
            "active" to p.active,
            "deliveryAvailable" to p.deliveryAvailable,
            "onOffer" to (p.mrp > p.price),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (p.id.isBlank()) data["createdAt"] = FieldValue.serverTimestamp()
        // Copy the farm's location + name onto the product so nearby search finds it
        // (the backend does this too once Cloud Functions are deployed).
        db.collection("farmers").document(uid).get().await().let { f ->
            val lat = f.getDouble("lat"); val lng = f.getDouble("lng")
            if (lat != null && lng != null) {
                data["lat"] = lat; data["lng"] = lng
                data["geohash"] = f.getString("geohash")?.takeIf { it.isNotBlank() }
                    ?: com.dk.himachalfarmdirect.data.geo.Geo.hash(com.dk.himachalfarmdirect.data.geo.LatLngPoint(lat, lng))
            }
            data["farmerName"] = (f.getString("farmName")?.takeIf { it.isNotBlank() } ?: f.getString("name")).orEmpty()
            data["searchKeywords"] = searchKeywords(p.name, p.nameHindi, p.category, f.getString("village").orEmpty())
        }
        if (p.thumbUrl.isNotBlank()) data["thumbUrl"] = p.thumbUrl
        if (p.imageUrl.isNotBlank()) data["imageUrl"] = p.imageUrl
        ref.set(data, SetOptions.merge()).await()
    }

    suspend fun updateStock(productId: String, stock: Double) {
        db.collection("products").document(productId).update("stock", stock).await()
    }
}

/** Lower-case word prefixes (2+ letters) so the search box finds "app" → "apple". */
internal fun searchKeywords(vararg texts: String): List<String> {
    val words = linkedSetOf<String>()
    texts.forEach { t ->
        t.lowercase().split(Regex("[\\s,/-]+")).filter { it.length >= 2 }.forEach { w ->
            for (n in 2..minOf(w.length, 12)) words += w.take(n)
        }
    }
    return words.take(100)
}

/** Help Centre tickets (spec E). Ticket numbers are issued by the backend. */
@Singleton
class SupportRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) {
    suspend fun createTicket(role: String, topic: String, subject: String, message: String, orderId: String): String {
        val res = functions.getHttpsCallable("createSupportTicket").call(
            hashMapOf("role" to role, "topic" to topic, "subject" to subject, "message" to message, "orderId" to orderId)
        ).await()
        return (res.getData() as? Map<*, *>)?.get("ticketNo") as? String ?: error("Ticket not created")
    }

    fun myTickets(): Flow<List<SupportTicket>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val reg = db.collection("supportTickets").whereEqualTo("uid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
                .addSnapshotListener { s, _ ->
                    trySend(s?.documents?.mapNotNull { d -> d.toObject(SupportTicket::class.java)?.copy(id = d.id) }.orEmpty())
                }
            awaitClose { reg.remove() }
        }
    }
}
