package com.dk.himachalfarmdirect.data.repo

import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.model.Banner
import com.dk.himachalfarmdirect.data.model.CollectionCentre
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class Nearby<T>(val item: T, val distanceKm: Double)

/**
 * All marketplace reads. Everything comes from Firestore — there are no dummy farmers or
 * simulated locations anywhere in the app (spec F). Firestore's offline cache means the
 * last results still show when the network drops.
 */
@Singleton
class MarketRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    private val farmers get() = db.collection("farmers")
    private val products get() = db.collection("products")

    /**
     * Geohash radius search (spec A-7): one range query per geohash cell covering the
     * circle, then an exact distance filter to drop the corners.
     */
    suspend fun nearbyFarmers(
        center: LatLngPoint,
        radiusKm: Double,
        deliveryOnly: Boolean = false,
    ): List<Nearby<Farmer>> = coroutineScope {
        Geo.queryBounds(center, radiusKm).map { b ->
            async {
                farmers.whereEqualTo("approved", true)
                    .orderBy("geohash").startAt(b.startHash).endAt(b.endHash)
                    .get().await().toObjects(Farmer::class.java)
            }
        }.awaitAll().flatten()
            .distinctBy { it.id }
            .map { Nearby(it, Geo.distanceKm(center, LatLngPoint(it.lat, it.lng))) }
            .filter { it.distanceKm <= radiusKm }
            .filter { !deliveryOnly || it.item.deliveryAvailable }
            .sortedBy { it.distanceKm }
    }

    /** Nearby in-stock products, optionally only those the farmer offers for delivery (spec A-8, A-9). */
    suspend fun nearbyProducts(
        center: LatLngPoint,
        radiusKm: Double,
        category: String? = null,
        deliveryOnly: Boolean = false,
    ): List<Nearby<Product>> = coroutineScope {
        Geo.queryBounds(center, radiusKm).map { b ->
            async {
                products.whereEqualTo("active", true)
                    .orderBy("geohash").startAt(b.startHash).endAt(b.endHash)
                    .limit(200)
                    .get().await().toObjects(Product::class.java)
            }
        }.awaitAll().flatten()
            .distinctBy { it.id }
            .filter { it.stock > 0 }
            .filter { category == null || it.category == category }
            .filter { !deliveryOnly || it.deliveryAvailable }
            .map { Nearby(it, Geo.distanceKm(center, LatLngPoint(it.lat, it.lng))) }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    // ---- Manual browsing (location discovery off, or no location chosen) ----

    suspend fun productsByCategory(category: String, limit: Long = 40): List<Product> =
        products.whereEqualTo("active", true).whereEqualTo("category", category)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .get().await().toObjects(Product::class.java).filter { it.stock > 0 }

    suspend fun recentProducts(limit: Long = 20): List<Product> =
        products.whereEqualTo("active", true)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .get().await().toObjects(Product::class.java).filter { it.stock > 0 }

    suspend fun bestSellers(limit: Long = 20): List<Product> =
        products.whereEqualTo("active", true)
            .orderBy("soldCount", Query.Direction.DESCENDING).limit(limit)
            .get().await().toObjects(Product::class.java).filter { it.stock > 0 }

    suspend fun offers(limit: Long = 20): List<Product> =
        products.whereEqualTo("active", true).whereEqualTo("onOffer", true).limit(limit)
            .get().await().toObjects(Product::class.java).filter { it.stock > 0 }

    suspend fun featuredFarmers(limit: Long = 10): List<Farmer> =
        farmers.whereEqualTo("approved", true).whereEqualTo("featured", true).limit(limit)
            .get().await().toObjects(Farmer::class.java)

    suspend fun farmersByDistrict(district: String): List<Farmer> =
        farmers.whereEqualTo("approved", true).whereEqualTo("district", district).limit(50)
            .get().await().toObjects(Farmer::class.java)

    suspend fun banners(): List<Banner> =
        db.collection("banners").whereEqualTo("active", true).orderBy("order").limit(6)
            .get().await().toObjects(Banner::class.java)

    suspend fun collectionCentres(center: LatLngPoint?, radiusKm: Double = 30.0): List<Nearby<CollectionCentre>> {
        val all = db.collection("collectionCentres").whereEqualTo("active", true).limit(100)
            .get().await().toObjects(CollectionCentre::class.java)
        return all.map { Nearby(it, center?.let { c -> Geo.distanceKm(c, LatLngPoint(it.lat, it.lng)) } ?: 0.0) }
            .filter { center == null || it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    /**
     * Search by name prefix. Product docs store a lower-case `searchKeywords` array
     * (written by the onProductWrite Cloud Function) so this works without a search server.
     */
    suspend fun search(text: String, limit: Long = 40): List<Product> {
        val token = text.trim().lowercase().split(" ").firstOrNull { it.length >= 2 } ?: return emptyList()
        return products.whereEqualTo("active", true)
            .whereArrayContains("searchKeywords", token).limit(limit)
            .get().await().toObjects(Product::class.java).filter { it.stock > 0 }
    }

    suspend fun product(id: String): Product? =
        products.document(id).get().await().toObject(Product::class.java)

    suspend fun farmer(id: String): Farmer? =
        farmers.document(id).get().await().toObject(Farmer::class.java)

    suspend fun productsOfFarmer(farmerId: String): List<Product> =
        products.whereEqualTo("farmerId", farmerId).get().await().toObjects(Product::class.java)
}
