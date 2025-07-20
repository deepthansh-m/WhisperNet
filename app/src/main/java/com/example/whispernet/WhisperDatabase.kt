package com.example.whispernet

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Whisper(
    val docId: String = "",
    val id: Long = 0L,
    val userId: String = "",
    val text: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    var heartCount: Int = 0,
    var thumbCount: Int = 0,
    var smileCount: Int = 0,
    var partyCount: Int = 0,
    var cryCount: Int = 0,
    var wowCount: Int = 0,
    var angryCount: Int = 0,
    var loveCount: Int = 0,
    var laughCount: Int = 0,
    var prayCount: Int = 0,
    val theme: String = "default",
    val isPriority: Boolean = false
)

class WhisperDatabase(context: Context) : SQLiteOpenHelper(context, "WhisperDB", null, 2) { // Bump version to 2
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE whispers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT,
                text TEXT,
                latitude REAL,
                longitude REAL,
                timestamp INTEGER,
                heart_count INTEGER,
                thumb_count INTEGER,
                smile_count INTEGER,
                party_count INTEGER,
                cry_count INTEGER,
                wow_count INTEGER,
                angry_count INTEGER,
                love_count INTEGER,
                laugh_count INTEGER,
                pray_count INTEGER,
                theme TEXT,
                is_priority INTEGER
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS whispers")
        onCreate(db)
    }

    fun addWhisper(userId: String, text: String, latitude: Double, longitude: Double, theme: String, isPriority: Boolean): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("user_id", userId)
            put("text", text)
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", System.currentTimeMillis())
            put("heart_count", 0)
            put("thumb_count", 0)
            put("smile_count", 0)
            put("party_count", 0)
            put("cry_count", 0)
            put("wow_count", 0)
            put("angry_count", 0)
            put("love_count", 0)
            put("laugh_count", 0)
            put("pray_count", 0)
            put("theme", theme)
            put("is_priority", if (isPriority) 1 else 0)
        }
        val id = db.insert("whispers", null, values)
        db.close()
        return id
    }

    fun getNearbyWhispers(lat: Double, lon: Double, radiusKm: Double): List<Whisper> {
        val db = readableDatabase
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val cursor = db.rawQuery("""
            SELECT * FROM whispers WHERE timestamp > ?
        """, arrayOf(oneHourAgo.toString()))

        val whispers = mutableListOf<Whisper>()
        while (cursor.moveToNext()) {
            val wLat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"))
            val wLon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"))
            val distance = calculateDistance(lat, lon, wLat, wLon)
            if (distance <= radiusKm) {
                whispers.add(
                    Whisper(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")),
                        text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                        latitude = wLat,
                        longitude = wLon,
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        heartCount = cursor.getInt(cursor.getColumnIndexOrThrow("heart_count")),
                        thumbCount = cursor.getInt(cursor.getColumnIndexOrThrow("thumb_count")),
                        smileCount = cursor.getInt(cursor.getColumnIndexOrThrow("smile_count")),
                        partyCount = cursor.getInt(cursor.getColumnIndexOrThrow("party_count")),
                        cryCount = cursor.getInt(cursor.getColumnIndexOrThrow("cry_count")),
                        wowCount = cursor.getInt(cursor.getColumnIndexOrThrow("wow_count")),
                        angryCount = cursor.getInt(cursor.getColumnIndexOrThrow("angry_count")),
                        loveCount = cursor.getInt(cursor.getColumnIndexOrThrow("love_count")),
                        laughCount = cursor.getInt(cursor.getColumnIndexOrThrow("laugh_count")),
                        prayCount = cursor.getInt(cursor.getColumnIndexOrThrow("pray_count")),
                        theme = cursor.getString(cursor.getColumnIndexOrThrow("theme")),
                        isPriority = cursor.getInt(cursor.getColumnIndexOrThrow("is_priority")) == 1
                    )
                )
            }
        }
        cursor.close()
        db.close()
        return whispers.sortedWith(compareByDescending<Whisper> { it.isPriority }.thenByDescending { it.timestamp })
    }

    fun getUserWhispers(userId: String): List<Whisper> {
        val db = readableDatabase
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        val cursor = db.rawQuery("""
            SELECT * FROM whispers WHERE user_id = ? AND timestamp > ?
        """, arrayOf(userId, oneHourAgo.toString()))

        val whispers = mutableListOf<Whisper>()
        while (cursor.moveToNext()) {
            whispers.add(
                Whisper(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")),
                    text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    heartCount = cursor.getInt(cursor.getColumnIndexOrThrow("heart_count")),
                    thumbCount = cursor.getInt(cursor.getColumnIndexOrThrow("thumb_count")),
                    smileCount = cursor.getInt(cursor.getColumnIndexOrThrow("smile_count")),
                    partyCount = cursor.getInt(cursor.getColumnIndexOrThrow("party_count")),
                    cryCount = cursor.getInt(cursor.getColumnIndexOrThrow("cry_count")),
                    wowCount = cursor.getInt(cursor.getColumnIndexOrThrow("wow_count")),
                    angryCount = cursor.getInt(cursor.getColumnIndexOrThrow("angry_count")),
                    loveCount = cursor.getInt(cursor.getColumnIndexOrThrow("love_count")),
                    laughCount = cursor.getInt(cursor.getColumnIndexOrThrow("laugh_count")),
                    prayCount = cursor.getInt(cursor.getColumnIndexOrThrow("pray_count")),
                    theme = cursor.getString(cursor.getColumnIndexOrThrow("theme")),
                    isPriority = cursor.getInt(cursor.getColumnIndexOrThrow("is_priority")) == 1,
                )
            )
        }
        cursor.close()
        db.close()
        return whispers
    }

    fun addReaction(whisperId: Long, reactionType: String) {
        val db = writableDatabase
        val column = when (reactionType) {
            "heart" -> "heart_count"
            "thumb" -> "thumb_count"
            "smile" -> "smile_count"
            "party" -> "party_count"
            "cry" -> "cry_count"
            "wow" -> "wow_count"
            "angry" -> "angry_count"
            "love" -> "love_count"
            "laugh" -> "laugh_count"
            "pray" -> "pray_count"
            else -> return
        }
        db.execSQL("UPDATE whispers SET $column = $column + 1 WHERE id = ?", arrayOf(whisperId))
        db.close()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}