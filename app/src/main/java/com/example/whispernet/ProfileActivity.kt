package com.example.whispernet

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.example.whispernet.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var db: WhisperDatabase
    private lateinit var billingManager: BillingManager
    private lateinit var auth: FirebaseAuth

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = WhisperDatabase(this)
        billingManager = BillingManager(this) { updateProfile() }
        auth = FirebaseAuth.getInstance()
        binding.profileRecyclerView.layoutManager = LinearLayoutManager(this)
        updateProfile()

        if (billingManager.isPremium()) {
            binding.premiumButton.isEnabled = false
            binding.premiumButton.text = "Premium Active"
        } else {
            binding.premiumButton.setOnClickListener {
                billingManager.launchBillingFlow(this)
            }
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProfile() {
        val userId = auth.currentUser?.uid ?: return
        val whispers = db.getUserWhispers(userId)
        val activeCount = whispers.size
        val totalReactions = whispers.sumOf { it.heartCount + it.thumbCount + it.smileCount + it.partyCount + it.cryCount + it.wowCount + it.angryCount + it.loveCount + it.laughCount + it.prayCount }
        binding.activeWhispersText.text = "Active Whispers: $activeCount"
        binding.reactionCountText.text = "Reactions: $totalReactions"
        if (billingManager.isPremium()) {
            val heart = whispers.sumOf { it.heartCount }
            val thumb = whispers.sumOf { it.thumbCount }
            val smile = whispers.sumOf { it.smileCount }
            val party = whispers.sumOf { it.partyCount }
            val cry = whispers.sumOf { it.cryCount }
            val wow = whispers.sumOf { it.wowCount }
            val angry = whispers.sumOf { it.angryCount }
            val love = whispers.sumOf { it.loveCount }
            val laugh = whispers.sumOf { it.laughCount }
            val pray = whispers.sumOf { it.prayCount }
            binding.reactionBreakdownText.text = "Breakdown: ❤️ $heart, 👍 $thumb, 😊 $smile, 🎉 $party, 😢 $cry, 😮 $wow, 😡 $angry, 💖 $love, 😂 $laugh, 🙏 $pray"
        } else {
            val heart = whispers.sumOf { it.heartCount }
            val thumb = whispers.sumOf { it.thumbCount }
            val smile = whispers.sumOf { it.smileCount }
            binding.reactionBreakdownText.text = "Breakdown: ❤️ $heart, 👍 $thumb, 😊 $smile"
        }
        binding.profileRecyclerView.adapter = WhisperAdapter(whispers, userId, { _, _ -> }, billingManager.isPremium())
    }
}