package com.example.whispernet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.example.whispernet.databinding.ActivityFeedBinding

class FeedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedBinding
    private lateinit var db: WhisperDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var billingManager: BillingManager
    private lateinit var auth: FirebaseAuth
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var selectedTheme = "default"
    private var radiusKm = 2.0
    private val TAG = "FeedActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            binding = ActivityFeedBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inflate layout: ${e.message}")
            Toast.makeText(this, "UI Error: Check Logcat", Toast.LENGTH_LONG).show()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = WhisperDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        billingManager = BillingManager(this) { updateFeed() }
        auth = FirebaseAuth.getInstance()

        try {
            MobileAds.initialize(this) {
                Log.d(TAG, "AdMob initialized")
                if (!billingManager.isPremium()) {
                    binding.adView.loadAd(AdRequest.Builder().build())
                    Log.d(TAG, "Ad loaded for free user")
                } else {
                    binding.adView.visibility = View.GONE
                    Log.d(TAG, "Ad hidden for premium user")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdMob initialization failed: ${e.message}")
        }

        binding.whisperRecyclerView.layoutManager = LinearLayoutManager(this)
        setupThemeSpinner()
        setupRadiusSeekBar()
        updateFeed()

        binding.postButton.setOnClickListener {
            val text = binding.whisperEditText.text.toString().trim()
            if (text.isNotEmpty() && text.length <= 140) {
                val isPriority = billingManager.isPremium() && binding.priorityCheckBox.isChecked
                getLocationAndPost(text, selectedTheme, isPriority)
            } else {
                Toast.makeText(this, "Enter a whisper (1-140 chars)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.profileButton.setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupThemeSpinner() {
        val themes = if (billingManager.isPremium()) {
            arrayOf("Default", "Soft Blue", "Fiery Red")
        } else {
            arrayOf("Default")
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = adapter
        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedTheme = when (themes[position]) {
                    "Soft Blue" -> "soft_blue"
                    "Fiery Red" -> "fiery_red"
                    else -> "default"
                }
                Log.d(TAG, "Theme selected: $selectedTheme")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedTheme = "default"
            }
        }
    }

    private fun setupRadiusSeekBar() {
        if (!billingManager.isPremium()) {
            binding.radiusSeekBar.isEnabled = false
            binding.radiusSeekBar.progress = 3 // 2 km
            binding.radiusText.text = "Radius: 2 km"
            radiusKm = 2.0
        } else {
            binding.radiusSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    radiusKm = 0.5 + (progress * 0.5)
                    binding.radiusText.text = "Radius: $radiusKm km"
                    updateFeed()
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
            binding.radiusSeekBar.progress = 3 // Default 2 km
            radiusKm = 2.0
        }
    }

    private fun getLocationAndPost(text: String, theme: String, isPriority: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            Log.d(TAG, "Requesting location permission")
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                db.addWhisper(userId, text, location.latitude, location.longitude, theme, isPriority)
                binding.whisperEditText.text.clear()
                updateFeed()
                Log.d(TAG, "Whisper posted by $userId: $text")
            } else {
                Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Location null")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Location fetch failed: ${e.message}")
            Toast.makeText(this, "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFeed() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted, skipping feed update")
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                val whispers = db.getNearbyWhispers(location.latitude, location.longitude, radiusKm)
                binding.whisperRecyclerView.adapter = WhisperAdapter(whispers, userId, { whisperId, reactionType ->
                    db.addReaction(whisperId, reactionType)
                    updateFeed()
                }, billingManager.isPremium())
                Log.d(TAG, "Feed updated with ${whispers.size} whispers")
            } else {
                Log.w(TAG, "Location null during feed update")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Feed update failed: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val text = binding.whisperEditText.text.toString().trim()
            if (text.isNotEmpty()) getLocationAndPost(text, selectedTheme, binding.priorityCheckBox.isChecked)
            updateFeed()
            Log.d(TAG, "Location permission granted")
        }
    }
}