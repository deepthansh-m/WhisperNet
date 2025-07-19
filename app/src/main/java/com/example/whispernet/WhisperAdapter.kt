package com.example.whispernet

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whispernet.databinding.ItemWhisperBinding

class WhisperAdapter(
    private val whispers: List<Whisper>,
    private val currentUserId: String, // Add current user's UID
    private val onReactionClick: (Long, String) -> Unit,
    private val isPremium: Boolean
) : RecyclerView.Adapter<WhisperAdapter.WhisperViewHolder>() {

    class WhisperViewHolder(val binding: ItemWhisperBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(whisper: Whisper, currentUserId: String, onReactionClick: (Long, String) -> Unit, isPremium: Boolean) {
            binding.whisperText.text = whisper.text
            when (whisper.theme) {
                "soft_blue" -> binding.whisperText.setBackgroundColor(binding.root.context.getColor(R.color.soft_blue))
                "fiery_red" -> binding.whisperText.setBackgroundColor(binding.root.context.getColor(R.color.fiery_red))
                else -> binding.whisperText.setBackgroundColor(binding.root.context.getColor(android.R.color.transparent))
            }
            binding.heartButton.text = "❤️ ${whisper.heartCount}"
            binding.thumbButton.text = "👍 ${whisper.thumbCount}"
            binding.smileButton.text = "😊 ${whisper.smileCount}"

            val isOwnWhisper = whisper.userId == currentUserId
            binding.heartButton.isEnabled = !isOwnWhisper
            binding.thumbButton.isEnabled = !isOwnWhisper
            binding.smileButton.isEnabled = !isOwnWhisper
            if (!isOwnWhisper) {
                binding.heartButton.setOnClickListener { onReactionClick(whisper.id, "heart") }
                binding.thumbButton.setOnClickListener { onReactionClick(whisper.id, "thumb") }
                binding.smileButton.setOnClickListener { onReactionClick(whisper.id, "smile") }
            }

            if (isPremium) {
                binding.partyButton.visibility = View.VISIBLE
                binding.cryButton.visibility = View.VISIBLE
                binding.wowButton.visibility = View.VISIBLE
                binding.angryButton.visibility = View.VISIBLE
                binding.loveButton.visibility = View.VISIBLE
                binding.laughButton.visibility = View.VISIBLE
                binding.prayButton.visibility = View.VISIBLE
                binding.partyButton.text = "🎉 ${whisper.partyCount}"
                binding.cryButton.text = "😢 ${whisper.cryCount}"
                binding.wowButton.text = "😮 ${whisper.wowCount}"
                binding.angryButton.text = "😡 ${whisper.angryCount}"
                binding.loveButton.text = "💖 ${whisper.loveCount}"
                binding.laughButton.text = "😂 ${whisper.laughCount}"
                binding.prayButton.text = "🙏 ${whisper.prayCount}"
                binding.partyButton.isEnabled = !isOwnWhisper
                binding.cryButton.isEnabled = !isOwnWhisper
                binding.wowButton.isEnabled = !isOwnWhisper
                binding.angryButton.isEnabled = !isOwnWhisper
                binding.loveButton.isEnabled = !isOwnWhisper
                binding.laughButton.isEnabled = !isOwnWhisper
                binding.prayButton.isEnabled = !isOwnWhisper
                if (!isOwnWhisper) {
                    binding.partyButton.setOnClickListener { onReactionClick(whisper.id, "party") }
                    binding.cryButton.setOnClickListener { onReactionClick(whisper.id, "cry") }
                    binding.wowButton.setOnClickListener { onReactionClick(whisper.id, "wow") }
                    binding.angryButton.setOnClickListener { onReactionClick(whisper.id, "angry") }
                    binding.loveButton.setOnClickListener { onReactionClick(whisper.id, "love") }
                    binding.laughButton.setOnClickListener { onReactionClick(whisper.id, "laugh") }
                    binding.prayButton.setOnClickListener { onReactionClick(whisper.id, "pray") }
                }
            } else {
                binding.partyButton.visibility = View.GONE
                binding.cryButton.visibility = View.GONE
                binding.wowButton.visibility = View.GONE
                binding.angryButton.visibility = View.GONE
                binding.loveButton.visibility = View.GONE
                binding.laughButton.visibility = View.GONE
                binding.prayButton.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WhisperViewHolder {
        val binding = ItemWhisperBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WhisperViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WhisperViewHolder, position: Int) {
        holder.bind(whispers[position], currentUserId, onReactionClick, isPremium)
    }

    override fun getItemCount(): Int = whispers.size
}