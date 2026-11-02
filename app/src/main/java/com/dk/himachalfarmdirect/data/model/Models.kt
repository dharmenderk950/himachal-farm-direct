package com.dk.himachalfarmdirect.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

/**
 * Firestore models. Every class has default values so Firestore can deserialize it.
 *
 * Privacy rule (spec A-15): a customer's precise coordinates and address live ONLY in
 * users/{uid}/addresses (owner-readable). Public / shared documents carry at most a
 * coarse geohash (5 chars ≈ 5 km cell), never an exact point.
 */

enum class Category(val id: String, val label: String, val emoji: String) {
    VEGETABLES("vegetables", "Fresh Vegetables", "🥬"),
    FRUITS("fruits", "Seasonal Fruits", "🍎"),
    PULSES("pulses", "Pulses & Grains", "🌾"),
    HONEY("honey", "Local Honey", "🍯"),
    DAIRY("dairy", "Milk & Dairy", "🥛"),
    SPICES("spices", "Spices & Herbs", "🌿"),
    OTHER("other", "Other", "🧺");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** Public farmer profile: farmers/{farmerId} (farmerId == seller's uid). */
data class Farmer(
    val id: String = "",
    val name: String = "",
    val farmName: String = "",
    val village: String = "",
    val district: String = "",
    val pinCode: String = "",
    /** Farm location is a business location the farmer chooses to publish. */
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val geohash: String = "",
    val photoUrl: String = "",
    val approved: Boolean = false,
    val featured: Boolean = false,
    val deliveryAvailable: Boolean = false,
    /** Max road-agnostic radius the farmer is willing to deliver to (km). */
    val deliveryRadiusKm: Double = 0.0,
    /** PIN codes the farmer serves. Empty + villages empty = pickup only. */
    val servicePinCodes: List<String> = emptyList(),
    /** Village names (lower-case) the farmer serves. */
    val serviceVillages: List<String> = emptyList(),
    val collectionCentreIds: List<String> = emptyList(),
    val rating: Double = 0.0,
    val createdAt: Timestamp? = null,
)

data class Product(
    val id: String = "",
    val farmerId: String = "",
    val farmerName: String = "",
    val name: String = "",
    val nameHindi: String = "",
    val category: String = Category.OTHER.id,
    val unit: String = "kg",
    val price: Double = 0.0,
    val mrp: Double = 0.0,
    val stock: Double = 0.0,
    /** Small (≤400px, WebP) image for lists — keeps data use low on rural networks. */
    val thumbUrl: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val seasonal: Boolean = false,
    val active: Boolean = true,
    val deliveryAvailable: Boolean = false,
    /** Copied from the farmer so nearby product queries need no join. */
    val geohash: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val soldCount: Long = 0,
    val onOffer: Boolean = false,
    val searchKeywords: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
) {
    @get:Exclude
    val discountPercent: Int
        get() = if (mrp > price && mrp > 0) (((mrp - price) / mrp) * 100).toInt() else 0
}

/** Private: users/{uid}/addresses/{id} */
data class Address(
    val id: String = "",
    val label: String = "Home",
    val line: String = "",
    val village: String = "",
    val district: String = "",
    val pinCode: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** Not named isDefault: Firestore would map an is-prefixed getter to the field "default". */
    val defaultAddress: Boolean = false,
)

data class CollectionCentre(
    val id: String = "",
    val name: String = "",
    val village: String = "",
    val pinCode: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val geohash: String = "",
    val openHours: String = "",
    val active: Boolean = true,
)

enum class SellerStatus { NONE, PENDING, APPROVED, REJECTED }

/** users/{uid} — owner-readable. Role flags are written only by Cloud Functions/admin. */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val sellerStatus: String = SellerStatus.NONE.name,
    val locationDiscoveryEnabled: Boolean = true,
    val notifyNewSellers: Boolean = true,
    /** 5-char geohash (~5 km). Used only for "new seller nearby" alerts. */
    val coarseGeohash: String = "",
    val fcmToken: String = "",
)

data class CartLine(
    val productId: String = "",
    val farmerId: String = "",
    val name: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    val qty: Double = 1.0,
    val thumbUrl: String = "",
)

enum class FulfilmentMode { HOME_DELIVERY, COLLECTION_CENTRE, FARM_PICKUP }

data class Order(
    val id: String = "",
    val buyerId: String = "",
    val farmerId: String = "",
    val farmerName: String = "",
    val lines: List<CartLine> = emptyList(),
    val itemsTotal: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val grandTotal: Double = 0.0,
    val fulfilment: String = FulfilmentMode.HOME_DELIVERY.name,
    val status: String = "PLACED",
    /** Address snapshot is visible only to buyer, the assigned farmer and admin. */
    val deliveryVillage: String = "",
    val deliveryPin: String = "",
    val createdAt: Timestamp? = null,
)

/** Result returned by the backend checkDelivery / placeOrder callables. */
data class DeliveryQuote(
    val eligible: Boolean = false,
    val reasons: List<String> = emptyList(),
    val distanceKm: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val modesAvailable: List<String> = emptyList(),
)

data class SupportTicket(
    val id: String = "",
    val ticketNo: String = "",
    val uid: String = "",
    val role: String = "buyer",
    val topic: String = "",
    val subject: String = "",
    val message: String = "",
    val orderId: String = "",
    val status: String = "OPEN",
    val adminReply: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

data class Banner(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    val categoryId: String = "",
    val active: Boolean = true,
    val order: Long = 0,
)
