package com.dk.himachalfarmdirect.data.repo

import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.data.model.CollectionCentre
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.FulfilmentMode
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
import com.google.firebase.functions.FirebaseFunctionsException
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
 * Orders. When the Cloud Functions backend is deployed (Blaze plan) every check and order goes
 * through it. Until then — or whenever a function is missing — the app falls back to doing the
 * same checks itself against live Firestore data, and the farmer confirms each order by
 * accepting it (stock is deducted at that moment).
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

    /** True when the callable simply doesn't exist yet (backend not deployed). */
    private fun Throwable.backendMissing(): Boolean =
        this is FirebaseFunctionsException && (code == FirebaseFunctionsException.Code.NOT_FOUND ||
            code == FirebaseFunctionsException.Code.UNIMPLEMENTED || code == FirebaseFunctionsException.Code.INTERNAL)

    suspend fun checkDelivery(farmerId: String, lines: List<CartLine>, address: Address, mode: String, centreId: String? = null): DeliveryQuote =
        try {
            val res = functions.getHttpsCallable("checkDelivery").call(payload(farmerId, lines, address.id, mode, centreId)).await()
            (res.getData() as? Map<*, *>)?.toQuote() ?: evaluateLocal(farmerId, lines, address, mode).quote
        } catch (e: Exception) {
            if (e.backendMissing()) evaluateLocal(farmerId, lines, address, mode).quote else throw e
        }

    /** Returns the new order id, or throws with the reason. */
    suspend fun placeOrder(farmerId: String, lines: List<CartLine>, address: Address, mode: String, centreId: String? = null): String =
        try {
            val res = functions.getHttpsCallable("placeOrder").call(payload(farmerId, lines, address.id, mode, centreId)).await()
            (res.getData() as? Map<*, *>)?.get("orderId") as? String ?: error("Order could not be placed")
        } catch (e: Exception) {
            if (e.backendMissing()) placeOrderLocal(farmerId, lines, address, mode) else throw e
        }

    // ------------------------------------------------------------ local (no-backend) path

    private data class LocalEval(
        val quote: DeliveryQuote,
        val farmer: Farmer?,
        val priced: List<CartLine>,
        val itemsTotal: Double,
        val centreName: String,
    )

    private suspend fun deliveryRules(): Map<String, Any?> =
        runCatching { db.collection("config").document("delivery").get().await().data }.getOrNull().orEmpty()

    private fun num(m: Map<String, Any?>, k: String, d: Double) = (m[k] as? Number)?.toDouble() ?: d

    /** Same checks as the backend: approval, live stock, delivery flags, radius, PIN/village areas, charges. */
    private suspend fun evaluateLocal(farmerId: String, lines: List<CartLine>, address: Address, mode: String): LocalEval {
        val reasons = mutableListOf<String>()
        val farmer = db.collection("farmers").document(farmerId).get().await().toObject(Farmer::class.java)
            ?: return LocalEval(DeliveryQuote(reasons = listOf("This farmer is no longer available")), null, emptyList(), 0.0, "")
        if (!farmer.approved) reasons += "This farmer is not verified yet"
        if (farmerId == auth.currentUser?.uid) reasons += "You can't order your own products"

        var allDeliverable = true
        val priced = mutableListOf<CartLine>()
        var itemsTotal = 0.0
        for (l in lines) {
            val p = db.collection("products").document(l.productId).get().await().toObject(Product::class.java)
            when {
                p == null || !p.active -> reasons += "${l.name} is no longer available"
                p.stock < l.qty -> reasons += "Only ${p.stock.clean()} ${p.unit} of ${p.name} left"
                else -> {
                    if (!p.deliveryAvailable) allDeliverable = false
                    itemsTotal += p.price * l.qty
                    priced += l.copy(price = p.price, name = p.name, unit = p.unit)
                }
            }
        }
        val cfg = deliveryRules()
        val minOrder = num(cfg, "minOrderValue", 0.0)
        if (minOrder > 0 && itemsTotal < minOrder) reasons += "Minimum order is ₹${minOrder.clean()}"

        val hasCoords = address.lat != 0.0 && farmer.lat != 0.0
        val distance = if (hasCoords) Geo.distanceKm(LatLngPoint(address.lat, address.lng), LatLngPoint(farmer.lat, farmer.lng)) else 0.0
        val pin = address.pinCode.trim()
        val village = address.village.trim().lowercase()
        val areaListed = (farmer.servicePinCodes.isEmpty() && farmer.serviceVillages.isEmpty()) ||
            pin in farmer.servicePinCodes || (village.isNotBlank() && village in farmer.serviceVillages)

        @Suppress("UNCHECKED_CAST")
        val pinRule = ((cfg["pinRules"] as? Map<String, Any?>)?.get(pin) as? Map<String, Any?>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val villageRule = ((cfg["villageRules"] as? Map<String, Any?>)?.get(village) as? Map<String, Any?>).orEmpty()
        val maxKm = num(cfg, "maxDeliveryKm", 50.0)

        val homeProblems = buildList {
            if (!farmer.deliveryAvailable) add("Farmer doesn't offer home delivery — choose farm pickup")
            if (!allDeliverable) add("Some items are pickup-only")
            if (hasCoords && distance > farmer.deliveryRadiusKm) add("Farmer delivers only up to ${farmer.deliveryRadiusKm.clean()} km (you are ${Geo.formatKm(distance)} away)")
            if (hasCoords && distance > maxKm) add("Home delivery is limited to ${maxKm.clean()} km")
            if (!areaListed) add("Your PIN / village is not in this farmer's delivery area")
            if (pinRule["blocked"] == true || villageRule["blocked"] == true) add("Delivery is not available in your area yet")
        }

        // Collection centre: nearest active centre this farmer uses, within reach of the buyer.
        var centreName = ""
        if (mode == FulfilmentMode.COLLECTION_CENTRE.name) {
            val reach = num(cfg, "collectionCentreMaxKm", 25.0)
            val best = farmer.collectionCentreIds.take(10).mapNotNull { id ->
                db.collection("collectionCentres").document(id).get().await().toObject(CollectionCentre::class.java)
            }.filter { it.active }
                .map { c -> c to (if (address.lat != 0.0) Geo.distanceKm(LatLngPoint(address.lat, address.lng), LatLngPoint(c.lat, c.lng)) else 0.0) }
                .filter { it.second <= reach }
                .minByOrNull { it.second }
            if (best == null) reasons += "No collection centre near you for this farmer — choose farm pickup"
            else centreName = "${best.first.name}, ${best.first.village}"
        }

        var charge = 0.0
        when (mode) {
            FulfilmentMode.HOME_DELIVERY.name -> {
                reasons += homeProblems
                val freeAbove = num(cfg, "freeAboveOrderValue", 0.0)
                charge = if (freeAbove > 0 && itemsTotal >= freeAbove) 0.0
                else num(cfg, "baseCharge", 20.0) + kotlin.math.ceil(distance) * num(cfg, "perKm", 4.0) +
                    ((pinRule["extraCharge"] as? Number)?.toDouble() ?: 0.0) + ((villageRule["extraCharge"] as? Number)?.toDouble() ?: 0.0)
            }
            FulfilmentMode.COLLECTION_CENTRE.name -> charge = num(cfg, "collectionCentreCharge", 0.0)
        }
        val modes = buildList {
            if (homeProblems.isEmpty()) add(FulfilmentMode.HOME_DELIVERY.name)
            if (farmer.collectionCentreIds.isNotEmpty()) add(FulfilmentMode.COLLECTION_CENTRE.name)
            add(FulfilmentMode.FARM_PICKUP.name)
        }
        return LocalEval(
            DeliveryQuote(reasons.isEmpty(), reasons.distinct(), (distance * 100).toLong() / 100.0, charge, modes),
            farmer, priced, itemsTotal, centreName,
        )
    }

    private suspend fun placeOrderLocal(farmerId: String, lines: List<CartLine>, address: Address, mode: String): String {
        val uid = auth.currentUser?.uid ?: error("Please sign in again")
        val ev = evaluateLocal(farmerId, lines, address, mode)
        if (!ev.quote.eligible) error(ev.quote.reasons.joinToString("; "))
        val f = ev.farmer!!
        val ref = db.collection("orders").document()
        val fullAddress = listOf(address.line, address.landmark, address.village, address.district, address.pinCode)
            .filter { it.isNotBlank() }.joinToString(", ")
        ref.set(
            mapOf(
                "buyerId" to uid,
                "farmerId" to farmerId,
                "farmerName" to f.farmName.ifBlank { f.name },
                "farmerPhone" to f.contactPhone,
                "lines" to ev.priced,
                "itemsTotal" to ev.itemsTotal,
                "deliveryCharge" to ev.quote.deliveryCharge,
                "grandTotal" to ev.itemsTotal + ev.quote.deliveryCharge,
                "fulfilment" to mode,
                "collectionCentreName" to ev.centreName,
                "distanceKm" to ev.quote.distanceKm,
                "status" to "PLACED",
                "stockDeducted" to false,
                "deliveryVillage" to address.village,
                "deliveryPin" to address.pinCode,
                // Visible only to this buyer and this farmer (Firestore rules).
                "buyerName" to address.name,
                "buyerPhone" to address.phone,
                "deliveryAddress" to fullAddress,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        return ref.id
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
        try {
            functions.getHttpsCallable("updateOrderStatus").call(hashMapOf("orderId" to orderId, "status" to status)).await()
        } catch (e: Exception) {
            if (e.backendMissing()) updateStatusLocal(orderId, status) else throw e
        }
    }

    /**
     * Farmer accepting an order is the confirmation step: stock is re-checked and deducted in
     * one transaction. Reject/cancel after acceptance puts the stock back.
     */
    private suspend fun updateStatusLocal(orderId: String, status: String) {
        val ref = db.collection("orders").document(orderId)
        db.runTransaction { tx ->
            val o = tx.get(ref).toObject(Order::class.java) ?: error("Order not found")
            val products = o.lines.map { db.collection("products").document(it.productId) }
            val snaps = products.map { tx.get(it) }
            val upd = mutableMapOf<String, Any>("status" to status, "updatedAt" to FieldValue.serverTimestamp())
            if (status == "ACCEPTED" && !o.stockDeducted) {
                o.lines.forEachIndexed { i, l ->
                    val stock = snaps[i].getDouble("stock") ?: 0.0
                    if (stock < l.qty) error("Only ${stock.clean()} ${l.unit} of ${l.name} left — update stock or reject")
                }
                o.lines.forEachIndexed { i, l -> tx.update(products[i], "stock", FieldValue.increment(-l.qty)) }
                upd["stockDeducted"] = true
            }
            if ((status == "CANCELLED" || status == "REJECTED") && o.stockDeducted && o.farmerId == auth.currentUser?.uid) {
                o.lines.forEachIndexed { i, l -> tx.update(products[i], "stock", FieldValue.increment(l.qty)) }
                upd["stockDeducted"] = false
            }
            if (status == "DELIVERED" && o.farmerId == auth.currentUser?.uid) {
                o.lines.forEachIndexed { i, l -> tx.update(products[i], "soldCount", FieldValue.increment(kotlin.math.ceil(l.qty).toLong())) }
            }
            tx.update(ref, upd)
        }.await()
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)

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
        try {
            val res = functions.getHttpsCallable("createSupportTicket").call(
                hashMapOf("role" to role, "topic" to topic, "subject" to subject, "message" to message, "orderId" to orderId)
            ).await()
            return (res.getData() as? Map<*, *>)?.get("ticketNo") as? String ?: error("Ticket not created")
        } catch (e: Exception) {
            val missing = e is FirebaseFunctionsException && (e.code == FirebaseFunctionsException.Code.NOT_FOUND ||
                e.code == FirebaseFunctionsException.Code.UNIMPLEMENTED || e.code == FirebaseFunctionsException.Code.INTERNAL)
            if (!missing) throw e
        }
        // No backend yet: create the ticket directly with a date-based number the admin sees in Firestore.
        val uid = auth.currentUser?.uid ?: error("Please sign in again")
        val ticketNo = "HFD-" + java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US).format(java.util.Date()) +
            "-" + (1000..9999).random()
        db.collection("supportTickets").add(
            mapOf(
                "ticketNo" to ticketNo, "uid" to uid, "role" to role, "topic" to topic.take(60),
                "subject" to subject.take(100), "message" to message.take(2000), "orderId" to orderId,
                "status" to "OPEN", "adminReply" to "",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        return ticketNo
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
