package com.example.whispernet

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlin.math.*

class FirestorePostManager {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("whispers")
    private val TAG = "FirestorePostManager"

    fun postWhisper(whisper: Whisper, onComplete: (Boolean) -> Unit = {}) {
        Log.d(TAG, "Attempting to post whisper: ${whisper.text}")
        Log.d(TAG, "User authenticated: ${FirebaseAuth.getInstance().currentUser != null}")
        Log.d(TAG, "Whisper details: userId=${whisper.userId}, lat=${whisper.latitude}, lon=${whisper.longitude}")

        val whisperMap = hashMapOf(
            "userId" to whisper.userId,
            "text" to whisper.text,
            "latitude" to whisper.latitude,
            "longitude" to whisper.longitude,
            "timestamp" to whisper.timestamp,
            "theme" to whisper.theme,
            "isPriority" to whisper.isPriority,
            "heartCount" to whisper.heartCount,
            "thumbCount" to whisper.thumbCount,
            "smileCount" to whisper.smileCount,
            "partyCount" to whisper.partyCount,
            "cryCount" to whisper.cryCount,
            "wowCount" to whisper.wowCount,
            "angryCount" to whisper.angryCount,
            "loveCount" to whisper.loveCount,
            "laughCount" to whisper.laughCount,
            "prayCount" to whisper.prayCount
        )

        Log.d(TAG, "Posting to Firestore collection: whispers")

        collection.add(whisperMap)
            .addOnSuccessListener { doc ->
                Log.d(TAG, "✅ Whisper successfully added with ID: ${doc.id}")
                Log.d(TAG, "Document path: ${doc.path}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error adding whisper to Firestore", e)
                Log.e(TAG, "Error code: ${e.localizedMessage}")
                Log.e(TAG, "Error details: ${e.message}")

                // Check for common Firestore errors
                when {
                    e.message?.contains("PERMISSION_DENIED") == true -> {
                        Log.e(TAG, "🔒 Permission denied - check Firestore security rules")
                    }
                    e.message?.contains("UNAVAILABLE") == true -> {
                        Log.e(TAG, "🌐 Network unavailable - check internet connection")
                    }
                    e.message?.contains("UNAUTHENTICATED") == true -> {
                        Log.e(TAG, "🔐 User not authenticated - check Firebase Auth")
                    }
                }
                onComplete(false)
            }
    }

    fun listenNearbyWhispers(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        onUpdate: (List<Whisper>) -> Unit
    ): ListenerRegistration {
        Log.d(TAG, "Setting up listener for nearby whispers")
        Log.d(TAG, "Location: ($lat, $lon), radius: ${radiusKm}km")

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "Current user ID: $currentUserId")

        val oneHourAgo = System.currentTimeMillis() - 3_600_000
        Log.d(TAG, "Filtering whispers newer than: $oneHourAgo (${System.currentTimeMillis() - oneHourAgo}ms ago)")

        return collection
            .whereGreaterThan("timestamp", oneHourAgo)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Firestore listener error", error)
                    Log.e(TAG, "Error details: ${error.message}")
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.w(TAG, "⚠️ Received null snapshots")
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                Log.d(TAG, "📥 Received ${snapshots.documents.size} documents from Firestore")

                val nearby = snapshots.documents.mapNotNull { doc ->
                    try {
                        Log.d(TAG, "Processing document: ${doc.id}")

                        val data = doc.data
                        Log.d(TAG, "Document data: $data")

                        val base = doc.toObject(Whisper::class.java)
                        if (base == null) {
                            Log.w(TAG, "⚠️ Failed to convert document ${doc.id} to Whisper object")
                            return@mapNotNull null
                        }

                        val whisper = base.copy(docId = doc.id)
                        Log.d(TAG, "Converted whisper: text='${whisper.text}', userId=${whisper.userId}")

                        val distance = haversine(lat, lon, whisper.latitude, whisper.longitude)
                        Log.d(TAG, "Distance to whisper '${whisper.text}': ${String.format("%.2f", distance)}km")

                        val shouldInclude = distance <= radiusKm || whisper.userId == currentUserId
                        Log.d(TAG, "Should include whisper: $shouldInclude (distance: ${String.format("%.2f", distance)}km, isOwn: ${whisper.userId == currentUserId})")

                        if (shouldInclude) whisper else null
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing document ${doc.id}", e)
                        null
                    }
                }

                Log.d(TAG, "📍 Filtered to ${nearby.size} nearby whispers")

                val sortedWhispers = nearby.sortedWith(
                    compareByDescending<Whisper> { it.isPriority }
                        .thenByDescending { it.timestamp }
                )

                Log.d(TAG, "📤 Sending ${sortedWhispers.size} whispers to UI")
                sortedWhispers.forEach { whisper ->
                    Log.d(TAG, "  - '${whisper.text}' (priority: ${whisper.isPriority}, time: ${whisper.timestamp})")
                }

                onUpdate(sortedWhispers)
            }
    }

    fun addReaction(docId: String, field: String) {
        if (docId.isBlank()) {
            Log.e(TAG, "❌ Cannot add reaction: blank document ID")
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Log.e(TAG, "❌ Cannot add reaction: user not authenticated")
            return
        }

        Log.d(TAG, "➕ Adding reaction: incrementing '$field' on document '$docId'")

        collection.document(docId)
            .update(field, FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d(TAG, "✅ Successfully incremented $field on $docId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to increment $field on $docId", e)
                Log.e(TAG, "Error details: ${e.message}")
            }
    }

    fun getUserWhispers(userId: String, onComplete: (List<Whisper>) -> Unit) {
        Log.d(TAG, "Fetching whispers for user: $userId")

        collection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // Limit to last 50 whispers
            .get()
            .addOnSuccessListener { documents ->
                val whispers = documents.mapNotNull { doc ->
                    try {
                        val whisper = doc.toObject(Whisper::class.java).copy(docId = doc.id)
                        whisper
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user whisper ${doc.id}", e)
                        null
                    }
                }
                Log.d(TAG, "Found ${whispers.size} whispers for user $userId")
                onComplete(whispers)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch user whispers", e)
                onComplete(emptyList())
            }
    }

    fun deleteWhisper(docId: String, userId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != userId) {
            Log.e(TAG, "Cannot delete whisper: user mismatch")
            onComplete(false)
            return
        }

        Log.d(TAG, "Deleting whisper: $docId")

        collection.document(docId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Successfully deleted whisper $docId")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to delete whisper $docId", e)
                onComplete(false)
            }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        Log.d(TAG, "Testing Firestore connection...")

        db.collection("test")
            .limit(1)
            .get()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Firestore connection successful")
                onResult(true, "Connected to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore connection failed", e)
                onResult(false, "Connection failed: ${e.message}")
            }
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