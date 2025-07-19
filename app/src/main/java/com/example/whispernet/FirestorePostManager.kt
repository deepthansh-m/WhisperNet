package com.example.whispernet

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.math.*

class FirestorePostManager {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("whispers")

    fun postWhisper(whisper: Whisper, onComplete: (Boolean) -> Unit = {}) {
        collection.add(whisper)
            .addOnSuccessListener { doc ->
                Log.d("FirestorePost", "Whisper added: ${doc.id}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("FirestorePost", "Error adding whisper", e)
                onComplete(false)
            }
    }

    fun listenNearbyWhispers(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        onUpdate: (List<Whisper>) -> Unit
    ): ListenerRegistration {
        val oneHourAgo = System.currentTimeMillis() - 3_600_000
        return collection
            .whereGreaterThan("timestamp", oneHourAgo)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("FirestoreListen", "Listen failed", error)
                    onUpdate(emptyList()); return@addSnapshotListener
                }
                val nearby = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        val base = doc.toObject(Whisper::class.java) ?: return@mapNotNull null
                        val w = base.copy(docId = doc.id)
                        val dist = haversine(lat, lon, w.latitude, w.longitude)
                        if (dist <= radiusKm) w else null
                    } catch (e: Exception) {
                        Log.e("FirestoreFilter", "Bad doc ${doc.id}", e); null
                    }
                }?.sortedWith(compareByDescending<Whisper> { it.isPriority }.thenByDescending { it.timestamp }) ?: emptyList()
                onUpdate(nearby)
            }
    }

    fun addReaction(docId: String, field: String) {
        if (docId.isBlank()) {
            Log.e("FirestoreReaction", "Blank docId; cannot react")
            return
        }
        Log.d("FirestoreReaction", "Incrementing $field on $docId")
        collection.document(docId)
            .update(field, FieldValue.increment(1))
            .addOnFailureListener { e -> Log.e("FirestoreReaction", "Failed to update", e) }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}