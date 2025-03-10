package com.example.whispernet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.example.whispernet.databinding.ActivityFeedBinding

class FeedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedBinding
    private lateinit var db: WhisperDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var billingManager: BillingManager
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var selectedTheme = "default"
    private var radiusKm = 2.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = WhisperDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        billingManager = BillingManager(this) { updateFeed() }

        MobileAds.initialize(this) {}
        if (!billingManager.isPremium()) {
            binding.adView.loadAd(AdRequest.Builder().build())
        } else {
            binding.adView.visibility = View.GONE
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
        }
    }

    private fun getLocationAndPost(text: String, theme: String, isPriority: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                db.addWhisper(text, location.latitude, location.longitude, theme, isPriority)
                binding.whisperEditText.text.clear()
                updateFeed()
            } else {
                Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFeed() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val whispers = db.getNearbyWhispers(location.latitude, location.longitude, radiusKm)
                binding.whisperRecyclerView.adapter = WhisperAdapter(whispers, { whisperId, reactionType ->
                    db.addReaction(whisperId, reactionType)
                    updateFeed()
                }, billingManager.isPremium())
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val text = binding.whisperEditText.text.toString().trim()
            if (text.isNotEmpty()) getLocationAndPost(text, selectedTheme, binding.priorityCheckBox.isChecked)
        }
    }
}