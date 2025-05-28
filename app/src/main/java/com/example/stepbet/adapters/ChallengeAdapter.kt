package com.example.stepbet.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.models.ChallengeStatus
import com.example.stepbet.databinding.ItemChallengeBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ChallengeAdapter(private val onChallengeClicked: (Challenge) -> Unit) :
    RecyclerView.Adapter<ChallengeAdapter.ChallengeViewHolder>() {

    private val challenges = mutableListOf<Challenge>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    fun setChallenges(newChallenges: List<Challenge>) {
        challenges.clear()
        challenges.addAll(newChallenges)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
        val binding = ItemChallengeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChallengeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
        holder.bind(challenges[position])
    }

    override fun getItemCount(): Int = challenges.size

    inner class ChallengeViewHolder(private val binding: ItemChallengeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onChallengeClicked(challenges[position])
                }
            }
        }

        fun bind(challenge: Challenge) {
            // Set challenge details
            binding.tvStepGoal.text = "${challenge.targetSteps} steps"
            binding.tvStakeAmount.text = "₹${challenge.amountStaked}"

            // Set progress
            val progress = (challenge.currentSteps.toFloat() / challenge.targetSteps) * 100
            binding.progressSteps.progress = progress.toInt()
            binding.tvStepProgress.text = "${challenge.currentSteps}/${challenge.targetSteps}"

            // Set dates
            val startDate = challenge.startTime.toDate()
            val endDate = challenge.endTime.toDate()
            binding.tvChallengeTime.text = "From ${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}"

            // Set status badge
            when (challenge.status) {
                ChallengeStatus.ACTIVE -> {
                    binding.tvChallengeStatus.text = "Active"
                    binding.tvChallengeStatus.setBackgroundResource(com.example.stepbet.R.drawable.bg_status_pending)
                }
                ChallengeStatus.COMPLETED -> {
                    binding.tvChallengeStatus.text = "Completed"
                    binding.tvChallengeStatus.setBackgroundResource(com.example.stepbet.R.drawable.bg_status_completed)
                    binding.tvRewardAmount.visibility = ViewGroup.VISIBLE
                    binding.tvRewardAmount.text = "Reward: ₹${challenge.rewardAmount}"
                }
                ChallengeStatus.FAILED -> {
                    binding.tvChallengeStatus.text = "Failed"
                    binding.tvChallengeStatus.setBackgroundResource(com.example.stepbet.R.drawable.bg_status_failed)
                }
            }
        }
    }
}