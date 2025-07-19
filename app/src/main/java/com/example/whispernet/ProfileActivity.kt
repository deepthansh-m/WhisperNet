package com.example.whispernet

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whispernet.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var billingManager: BillingManager
    private lateinit var firestoreManager: FirestorePostManager
    private var listener: ListenerRegistration? = null
    private val TAG = "ProfileActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestoreManager = FirestorePostManager()
        billingManager = BillingManager(this) { /* premium callback */ }

        binding.profileRecyclerView.layoutManager = LinearLayoutManager(this)

        if (billingManager.isPremium()) {
            binding.premiumButton.isEnabled = false
            binding.premiumButton.text = "Premium Active"
        } else {
            binding.premiumButton.setOnClickListener { billingManager.launchBillingFlow(this) }
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        subscribeToMyWhispers()
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }

    private fun subscribeToMyWhispers() {
        val uid = auth.currentUser?.uid ?: return
        val oneHourAgo = System.currentTimeMillis() - 3_600_000

        listener = firestoreManager.listenNearbyWhispers(0.0, 0.0, Double.MAX_VALUE) { list ->
            val mine = list.filter { it.userId == uid && it.timestamp > oneHourAgo }
            updateProfileUI(mine)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProfileUI(whispers: List<Whisper>) {
        val activeCount = whispers.size
        val totalReactions = whispers.sumOf {
            it.heartCount + it.thumbCount + it.smileCount + it.partyCount +
                    it.cryCount + it.wowCount + it.angryCount +
                    it.loveCount + it.laughCount + it.prayCount
        }
        binding.activeWhispersText.text = "Active Whispers: $activeCount"
        binding.reactionCountText.text = "Reactions: $totalReactions"

        val breakdown = if (billingManager.isPremium()) {
            "❤️ ${whispers.sumOf { it.heartCount }}, " +
                    "👍 ${whispers.sumOf { it.thumbCount }}, " +
                    "😊 ${whispers.sumOf { it.smileCount }}, " +
                    "🎉 ${whispers.sumOf { it.partyCount }}, " +
                    "😢 ${whispers.sumOf { it.cryCount }}, " +
                    "😮 ${whispers.sumOf { it.wowCount }}, " +
                    "😡 ${whispers.sumOf { it.angryCount }}, " +
                    "💖 ${whispers.sumOf { it.loveCount }}, " +
                    "😂 ${whispers.sumOf { it.laughCount }}, " +
                    "🙏 ${whispers.sumOf { it.prayCount }}"
        } else {
            "❤️ ${whispers.sumOf { it.heartCount }}, " +
                    "👍 ${whispers.sumOf { it.thumbCount }}, " +
                    "😊 ${whispers.sumOf { it.smileCount }}"
        }
        binding.reactionBreakdownText.text = "Breakdown: $breakdown"

        binding.profileRecyclerView.adapter = WhisperAdapter(
            whispers,
            auth.currentUser?.uid ?: "",
            onReactionClick = { _, _ -> Toast.makeText(this, "Can't react here", Toast.LENGTH_SHORT).show() },
            isPremium = billingManager.isPremium()
        )

        Log.d(TAG, "Profile updated, whispers=${whispers.size}")
    }
}
