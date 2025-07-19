package com.example.whispernet

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
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
import com.example.whispernet.databinding.ActivityFeedBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class FeedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var billingManager: BillingManager
    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreManager: FirestorePostManager
    private var whispersListener: ListenerRegistration? = null

    private val LOCATION_REQ = 42
    private var selectedTheme = "default"
    private var radiusKm = 2.0
    private val TAG = "FeedActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        billingManager = BillingManager(this) { refreshFeed() }
        auth = FirebaseAuth.getInstance()
        firestoreManager = FirestorePostManager()

        MobileAds.initialize(this)
        if (!billingManager.isPremium()) binding.adView.loadAd(AdRequest.Builder().build()) else binding.adView.visibility = View.GONE

        binding.whisperRecyclerView.layoutManager = LinearLayoutManager(this)
        setupThemeSpinner()
        setupRadiusSeekBar()
        refreshFeed()

        binding.postButton.setOnClickListener {
            val txt = binding.whisperEditText.text.toString().trim()
            if (txt.isEmpty() || txt.length > 140) {
                Toast.makeText(this, "Enter a whisper (1–140 chars)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val priority = billingManager.isPremium() && binding.priorityCheckBox.isChecked
            getCurrentLocation { loc -> postWhisper(txt, priority, loc) }
        }

        binding.profileButton.setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupThemeSpinner() {
        val themes = if (billingManager.isPremium()) arrayOf("Default", "Soft Blue", "Fiery Red") else arrayOf("Default")
        binding.themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedTheme = when (themes[pos]) {
                    "Soft Blue" -> "soft_blue"
                    "Fiery Red" -> "fiery_red"
                    else -> "default"
                }
            }
            override fun onNothingSelected(p: AdapterView<*>) { selectedTheme = "default" }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupRadiusSeekBar() {
        if (!billingManager.isPremium()) {
            binding.radiusSeekBar.isEnabled = false
            binding.radiusSeekBar.progress = 3
            binding.radiusText.text = "Radius: 2 km"
        } else {
            binding.radiusSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                    radiusKm = 0.5 + p * 0.5
                    binding.radiusText.text = "Radius: $radiusKm km"
                    refreshFeed()
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })
            binding.radiusSeekBar.progress = 3
        }
    }

    private fun getCurrentLocation(callback: (Location) -> Unit) {
        if (!checkPerm()) return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) callback(loc)
                else Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun postWhisper(text: String, isPriority: Boolean, loc: Location) {
        val uid = auth.currentUser?.uid ?: return
        val whisper = Whisper(
            userId = uid,
            text = text,
            latitude = loc.latitude,
            longitude = loc.longitude,
            timestamp = System.currentTimeMillis(),
            theme = selectedTheme,
            isPriority = isPriority
        )
        firestoreManager.postWhisper(whisper) {
            if (it) {
                binding.whisperEditText.setText("")
                refreshFeed()
            }
        }
    }

    private fun refreshFeed() {
        getCurrentLocation { loc -> attachListener(loc.latitude, loc.longitude) }
    }

    private fun attachListener(lat: Double, lon: Double) {
        val uid = auth.currentUser?.uid ?: return
        whispersListener?.remove()
        whispersListener = firestoreManager.listenNearbyWhispers(lat, lon, radiusKm) { list ->
            binding.whisperRecyclerView.adapter = WhisperAdapter(
                list,
                uid,
                onReactionClick = { w, field ->
                    if (w.userId != uid) firestoreManager.addReaction(w.docId, field)
                    else Toast.makeText(this, "Can't react to own post", Toast.LENGTH_SHORT).show()
                },
                isPremium = billingManager.isPremium()
            )
            Log.d(TAG, "Feed update ${list.size} whispers @($lat,$lon)")
        }
    }

    private fun checkPerm(): Boolean {
        val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQ
            )
        }
        return granted
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        if (req == LOCATION_REQ && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            refreshFeed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        whispersListener?.remove()
    }
}