package com.example.whispernet

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.whispernet.databinding.ItemWhisperBinding

class WhisperAdapter(
    private val whispers: List<Whisper>,
    private val currentUserId: String,
    private val onReactionClick: (Whisper, String) -> Unit,
    private val isPremium: Boolean
) : RecyclerView.Adapter<WhisperAdapter.WhisperViewHolder>() {

    class WhisperViewHolder(val binding: ItemWhisperBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(w: Whisper, uid: String, react: (Whisper, String) -> Unit, premium: Boolean) {
            binding.whisperText.text = w.text
            val ctx = binding.root.context
            val bg = when (w.theme) {
                "soft_blue" -> R.color.soft_blue
                "fiery_red" -> R.color.fiery_red
                else -> android.R.color.transparent
            }
            binding.whisperText.setBackgroundColor(ctx.getColor(bg))

            binding.heartButton.text = "❤️ ${w.heartCount}"
            binding.thumbButton.text = "👍 ${w.thumbCount}"
            binding.smileButton.text = "😊 ${w.smileCount}"

            val own = w.userId == uid
            val baseButtons = listOf(
                binding.heartButton to "heartCount",
                binding.thumbButton to "thumbCount",
                binding.smileButton to "smileCount"
            )
            baseButtons.forEach { (btn, field) ->
                btn.isEnabled = !own
                btn.setOnClickListener { if (!own) react(w, field) else toastOwn() }
            }

            if (premium) {
                val extra = listOf(
                    binding.partyButton to ("🎉" to "partyCount"),
                    binding.cryButton to ("😢" to "cryCount"),
                    binding.wowButton to ("😮" to "wowCount"),
                    binding.angryButton to ("😡" to "angryCount"),
                    binding.loveButton to ("💖" to "loveCount"),
                    binding.laughButton to ("😂" to "laughCount"),
                    binding.prayButton to ("🙏" to "prayCount")
                )
                extra.forEach { (btn, pair) ->
                    val (emoji, field) = pair
                    val count = w::class.java.getDeclaredField(field).getInt(w)
                    btn.text = "$emoji $count"
                    btn.visibility = View.VISIBLE
                    btn.isEnabled = !own
                    btn.setOnClickListener { if (!own) react(w, field) else toastOwn() }
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

        private fun toastOwn() {
            Toast.makeText(binding.root.context, "Can't react to own post", Toast.LENGTH_SHORT).show()
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