package com.example.whispernet

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.whispernet.databinding.ActivityFeedBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class FeedActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityFeedBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var billingManager: BillingManager
    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreManager: FirestorePostManager
    private var whispersListener: ListenerRegistration? = null
    private lateinit var googleMap: GoogleMap
    private var currentUserLocation: Location? = null
    private val markerWhisperMap = mutableMapOf<Marker, Whisper>()
    private var selectedMarkerInfoView: View? = null

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

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        MobileAds.initialize(this)
        if (!billingManager.isPremium()) binding.adView.loadAd(AdRequest.Builder().build()) else binding.adView.visibility = View.GONE

        setupThemeSpinner()
        setupRadiusSeekBar()

        binding.postButton.setOnClickListener {
            Log.d(TAG, "Post button clicked")

            val txt = binding.whisperEditText.text.toString().trim()
            Log.d(TAG, "Text entered: '$txt', length: ${txt.length}")

            if (txt.isEmpty()) {
                Toast.makeText(this, "Please enter a whisper", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Text is empty")
                return@setOnClickListener
            }

            if (txt.length > 140) {
                Toast.makeText(this, "Whisper must be 140 characters or less", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Text too long: ${txt.length} characters")
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            Log.d(TAG, "Current user: ${currentUser?.uid}")
            if (currentUser == null) {
                Toast.makeText(this, "Please sign in to post", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "User not authenticated")
                return@setOnClickListener
            }

            val priority = billingManager.isPremium() && binding.priorityCheckBox.isChecked
            Log.d(TAG, "Priority: $priority, isPremium: ${billingManager.isPremium()}")

            Log.d(TAG, "Getting current location...")
            getCurrentLocation { loc ->
                Log.d(TAG, "Location obtained: ${loc.latitude}, ${loc.longitude}")
                postWhisper(txt, priority, loc)
            }
        }

        binding.profileButton.setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d(TAG, "Map is ready")
        if (checkPerm()) {
            enableMyLocation()
        }
        googleMap.setOnMarkerClickListener { marker ->
            handleMarkerClick(marker)
            true
        }
        googleMap.setOnMapClickListener {
            hideMarkerInfo()
        }
        getCurrentLocation { location ->
            currentUserLocation = location
            val userLatLng = LatLng(location.latitude, location.longitude)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            refreshFeed()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (checkPerm()) {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun handleMarkerClick(marker: Marker) {
        val whisper = markerWhisperMap[marker] ?: return
        hideMarkerInfo()
        showMarkerInfo(whisper, marker)
    }
    private fun debugUserAuthentication(whisper: Whisper) {
        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid ?: ""

        Log.d(TAG, "=== REACTION DEBUG ===")
        Log.d(TAG, "Current user: ${currentUser?.email} (${currentUser?.uid})")
        Log.d(TAG, "Whisper author: ${whisper.userId}")
        Log.d(TAG, "Is same user: ${whisper.userId == currentUserId}")
        Log.d(TAG, "Is user authenticated: ${currentUser != null}")
        Log.d(TAG, "Current user ID empty: ${currentUserId.isEmpty()}")
        Log.d(TAG, "===================")
    }
    @SuppressLint("MissingInflatedId")
    private fun showMarkerInfo(whisper: Whisper, marker: Marker) {
        val infoView = LayoutInflater.from(this).inflate(R.layout.whisper_info_layout, null)

        val whisperText = infoView.findViewById<TextView>(R.id.whisperInfoText)
        val heartButton = infoView.findViewById<Button>(R.id.heartInfoButton)
        val thumbButton = infoView.findViewById<Button>(R.id.thumbInfoButton)
        val smileButton = infoView.findViewById<Button>(R.id.smileInfoButton)
        val premiumReactionsLayout = infoView.findViewById<LinearLayout>(R.id.premiumReactionsLayout)

        whisperText.text = whisper.text

        val bg = when (whisper.theme) {
            "soft_blue" -> R.color.soft_blue
            "fiery_red" -> R.color.fiery_red
            else -> android.R.color.transparent
        }
        whisperText.setBackgroundColor(getColor(bg))

        heartButton.text = "❤️ ${whisper.heartCount}"
        thumbButton.text = "👍 ${whisper.thumbCount}"
        smileButton.text = "😊 ${whisper.smileCount}"

        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid ?: ""
        val isOwnPost = whisper.userId == currentUserId

        debugUserAuthentication(whisper)
        val canReact = currentUser != null && !isOwnPost && currentUserId.isNotEmpty()

        heartButton.isEnabled = canReact
        thumbButton.isEnabled = canReact
        smileButton.isEnabled = canReact

        heartButton.setOnClickListener {
            if (!canReact) {
                Toast.makeText(this, "You can't react to this", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firestoreManager.addReaction(whisper.docId, "heartCount")
            whisper.heartCount++
            heartButton.text = "❤️ ${whisper.heartCount}"
        }

        thumbButton.setOnClickListener {
            if (!canReact) {
                Toast.makeText(this, "You can't react to this", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firestoreManager.addReaction(whisper.docId, "thumbCount")
            whisper.thumbCount++
            thumbButton.text = "👍 ${whisper.thumbCount}"
        }

        smileButton.setOnClickListener {
            if (!canReact) {
                Toast.makeText(this, "You can't react to this", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firestoreManager.addReaction(whisper.docId, "smileCount")
            whisper.smileCount++
            smileButton.text = "😊 ${whisper.smileCount}"
        }

        if (billingManager.isPremium()) {
            premiumReactionsLayout.visibility = View.VISIBLE

            val premiumButtons = mapOf(
                R.id.partyInfoButton to ("🎉" to "partyCount"),
                R.id.cryInfoButton to ("😢" to "cryCount"),
                R.id.wowInfoButton to ("😮" to "wowCount"),
                R.id.angryInfoButton to ("😡" to "angryCount"),
                R.id.loveInfoButton to ("💖" to "loveCount"),
                R.id.laughInfoButton to ("😂" to "laughCount"),
                R.id.prayInfoButton to ("🙏" to "prayCount")
            )

            premiumButtons.forEach { (buttonId, emojiField) ->
                val button = infoView.findViewById<Button>(buttonId)
                val (emoji, field) = emojiField
                val count = when (field) {
                    "partyCount" -> whisper.partyCount
                    "cryCount" -> whisper.cryCount
                    "wowCount" -> whisper.wowCount
                    "angryCount" -> whisper.angryCount
                    "loveCount" -> whisper.loveCount
                    "laughCount" -> whisper.laughCount
                    "prayCount" -> whisper.prayCount
                    else -> 0
                }
                button.text = "$emoji $count"
                button.isEnabled = canReact

                button.setOnClickListener {
                    if (!canReact) {
                        Toast.makeText(this, "You can't react to this", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    firestoreManager.addReaction(whisper.docId, field)
                    when (field) {
                        "partyCount" -> {
                            whisper.partyCount++
                            button.text = "$emoji ${whisper.partyCount}"
                        }
                        "cryCount" -> {
                            whisper.cryCount++
                            button.text = "$emoji ${whisper.cryCount}"
                        }
                        "wowCount" -> {
                            whisper.wowCount++
                            button.text = "$emoji ${whisper.wowCount}"
                        }
                        "angryCount" -> {
                            whisper.angryCount++
                            button.text = "$emoji ${whisper.angryCount}"
                        }
                        "loveCount" -> {
                            whisper.loveCount++
                            button.text = "$emoji ${whisper.loveCount}"
                        }
                        "laughCount" -> {
                            whisper.laughCount++
                            button.text = "$emoji ${whisper.laughCount}"
                        }
                        "prayCount" -> {
                            whisper.prayCount++
                            button.text = "$emoji ${whisper.prayCount}"
                        }
                    }
                }
            }
        } else {
            premiumReactionsLayout.visibility = View.GONE
        }

        val closeButton = infoView.findViewById<Button>(R.id.closeInfoButton)
        closeButton.setOnClickListener {
            hideMarkerInfo()
        }

        binding.mapContainer.addView(infoView)
        selectedMarkerInfoView = infoView
    }

    private fun hideMarkerInfo() {
        selectedMarkerInfoView?.let {
            binding.mapContainer.removeView(it)
            selectedMarkerInfoView = null
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
                Log.d(TAG, "Theme selected: $selectedTheme")
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
        Log.d(TAG, "getCurrentLocation called")

        if (!checkPerm()) {
            Log.e(TAG, "Location permission not granted")
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission check failed")
            return
        }

        Log.d(TAG, "Requesting current location...")
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(TAG, "Location success: ${loc.latitude}, ${loc.longitude}")
                    currentUserLocation = loc
                    callback(loc)
                } else {
                    Log.e(TAG, "Location is null")
                    Toast.makeText(this, "Location unavailable. Please enable GPS and try again.", Toast.LENGTH_LONG).show()
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            Log.d(TAG, "Using last known location: ${lastLoc.latitude}, ${lastLoc.longitude}")
                            currentUserLocation = lastLoc
                            callback(lastLoc)
                        } else {
                            Log.e(TAG, "No last known location available")
                            Toast.makeText(this, "Cannot determine location. Please check GPS settings.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Location error: ${e.message}", e)
                Toast.makeText(this, "Location error: ${e.message}", Toast.LENGTH_LONG).show()

                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        Log.d(TAG, "Using last known location after error: ${lastLoc.latitude}, ${lastLoc.longitude}")
                        currentUserLocation = lastLoc
                        callback(lastLoc)
                    }
                }
            }
    }

    private fun postWhisper(text: String, isPriority: Boolean, loc: Location) {
        Log.d(TAG, "postWhisper called with text: '$text', isPriority: $isPriority")

        val uid = auth.currentUser?.uid
        Log.d(TAG, "User ID: $uid")

        if (uid == null) {
            Log.e(TAG, "User ID is null")
            Toast.makeText(this, "Authentication error. Please sign in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val whisper = Whisper(
            userId = uid,
            text = text,
            latitude = loc.latitude,
            longitude = loc.longitude,
            timestamp = System.currentTimeMillis(),
            theme = selectedTheme,
            isPriority = isPriority
        )

        Log.d(TAG, "Created whisper object: userId=${whisper.userId}, text='${whisper.text}', lat=${whisper.latitude}, lon=${whisper.longitude}, theme=${whisper.theme}")

        binding.postButton.isEnabled = false
        binding.postButton.text = "Posting..."

        firestoreManager.postWhisper(whisper) { success ->
            Log.d(TAG, "postWhisper callback: success = $success")

            binding.postButton.isEnabled = true
            binding.postButton.text = "Post"

            if (success) {
                Log.d(TAG, "Post successful, clearing text and refreshing feed")
                binding.whisperEditText.setText("")
                binding.priorityCheckBox.isChecked = false
                refreshFeed()
                Toast.makeText(this, "Whisper posted successfully! 🎉", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Post failed")
                Toast.makeText(this, "Failed to post whisper. Please check your internet connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshFeed() {
        Log.d(TAG, "refreshFeed called")
        currentUserLocation?.let { loc ->
            Log.d(TAG, "Refreshing feed for location: ${loc.latitude}, ${loc.longitude}")
            attachListener(loc.latitude, loc.longitude)
        } ?: run {
            Log.w(TAG, "Cannot refresh feed - no current location")
            getCurrentLocation { location ->
                currentUserLocation = location
                attachListener(location.latitude, location.longitude)
            }
        }
    }

    private fun attachListener(lat: Double, lon: Double) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.e(TAG, "Cannot attach listener - user not authenticated")
            return
        }

        Log.d(TAG, "Attaching listener for location: ($lat, $lon) with radius: ${radiusKm}km")
        whispersListener?.remove()
        whispersListener = firestoreManager.listenNearbyWhispers(lat, lon, radiusKm) { list ->
            Log.d(TAG, "Received ${list.size} whispers from Firestore")
            updateMapWithWhispers(list, lat, lon, uid)
        }
    }

    private fun updateMapWithWhispers(whispers: List<Whisper>, userLat: Double, userLon: Double, currentUserId: String) {
        if (!::googleMap.isInitialized) {
            Log.w(TAG, "Google Map not initialized")
            return
        }

        Log.d(TAG, "Updating map with ${whispers.size} whispers")

        googleMap.clear()
        markerWhisperMap.clear()

        val visibleWhispers = whispers.filter { whisper ->
            val distance = calculateDistance(userLat, userLon, whisper.latitude, whisper.longitude)
            val isVisible = distance <= 2.0 || whisper.userId == currentUserId
            Log.d(TAG, "Whisper '${whisper.text}' at distance ${String.format("%.2f", distance)}km, visible: $isVisible")
            isVisible
        }

        Log.d(TAG, "Showing ${visibleWhispers.size} visible whispers on map")

        visibleWhispers.forEach { whisper ->
            val markerColor = if (whisper.isPriority) {
                BitmapDescriptorFactory.HUE_YELLOW // Changed from HUE_GOLD to HUE_YELLOW
            } else {
                when (whisper.theme) {
                    "soft_blue" -> BitmapDescriptorFactory.HUE_BLUE
                    "fiery_red" -> BitmapDescriptorFactory.HUE_RED
                    else -> BitmapDescriptorFactory.HUE_GREEN
                }
            }

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(whisper.latitude, whisper.longitude))
                    .title(whisper.text.take(30) + if (whisper.text.length > 30) "..." else "")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )

            marker?.let {
                markerWhisperMap[it] = whisper
                Log.d(TAG, "Added marker for whisper: '${whisper.text}'")
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    private fun checkPerm(): Boolean {
        val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.d(TAG, "Location permission not granted, requesting...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_REQ
            )
        } else {
            Log.d(TAG, "Location permission granted")
        }
        return granted
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        Log.d(TAG, "Permission result: request=$req, results=${res.contentToString()}")

        if (req == LOCATION_REQ && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission granted in callback")
            if (::googleMap.isInitialized) {
                enableMyLocation()
            }
            refreshFeed()
        } else {
            Log.w(TAG, "Location permission denied")
            Toast.makeText(this, "Location permission is required to use this app", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        if (::googleMap.isInitialized && currentUserLocation != null) {
            refreshFeed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        whispersListener?.remove()
    }
}