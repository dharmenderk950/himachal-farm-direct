package com.dk.himachalfarmdirect.data.repo

import com.dk.himachalfarmdirect.data.model.Product
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val open: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp? = null,
)

/** Wishlist and the in-app notification inbox (both private to the user). */
@Singleton
class ExtrasRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun userDoc() = auth.currentUser?.uid?.let { db.collection("users").document(it) }

    fun wishlist(): Flow<List<Product>> {
        val doc = userDoc() ?: return flowOf(emptyList())
        return callbackFlow {
            val reg = doc.collection("wishlist").addSnapshotListener { s, _ ->
                trySend(s?.toObjects(Product::class.java).orEmpty())
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun toggleWishlist(p: Product, add: Boolean) {
        val col = userDoc()?.collection("wishlist") ?: return
        if (add) col.document(p.id).set(p).await() else col.document(p.id).delete().await()
    }

    fun notifications(): Flow<List<AppNotification>> {
        val doc = userDoc() ?: return flowOf(emptyList())
        return callbackFlow {
            val reg = doc.collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener { s, _ ->
                    trySend(s?.documents?.mapNotNull { d -> d.toObject(AppNotification::class.java)?.copy(id = d.id) }.orEmpty())
                }
            awaitClose { reg.remove() }
        }
    }

    suspend fun markAllRead(ids: List<String>) {
        val col = userDoc()?.collection("notifications") ?: return
        val batch = db.batch()
        ids.forEach { batch.update(col.document(it), mapOf("read" to true, "readAt" to FieldValue.serverTimestamp())) }
        batch.commit().await()
    }
}
