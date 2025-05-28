package com.example.stepbet.challenges

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.models.ChallengeStatus
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.data.repositories.StepRepository
import com.example.stepbet.databinding.ActivityChallengeDetailsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ChallengeDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengeDetailsBinding
    private val challengeRepository = ChallengeRepository()
    private val stepRepository = StepRepository()

    private var challengeId: String? = null
    private var challenge: Challenge? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        // Get challenge ID from intent
        challengeId = intent.getStringExtra("challengeId")

        if (challengeId == null) {
            Toast.makeText(this, "Challenge not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadChallengeDetails()
        }

        // Load challenge details
        loadChallengeDetails()
    }

    private fun loadChallengeDetails() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null || challengeId == null) {
            binding.swipeRefresh.isRefreshing = false
            return
        }

        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                // Get challenge details
                val challengeData = challengeRepository.getActiveChallenge(userId, challengeId!!)

                // Get current step count
                val todaySteps = stepRepository.getTodaySteps(userId)

                runOnUiThread {
                    challengeData?.let {
                        challenge = it
                        updateUI(it, todaySteps)
                    } ?: run {
                        Toast.makeText(this@ChallengeDetailsActivity, "Challenge not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    binding.swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ChallengeDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun updateUI(challenge: Challenge, currentSteps: Int) {
        // Set challenge details
        binding.tvStepGoal.text = "${challenge.targetSteps} steps"
        binding.tvStakeAmount.text = "₹${challenge.amountStaked}"

        // Set dates
        val startDate = challenge.startTime.toDate()
        val endDate = challenge.endTime.toDate()
        binding.tvStartTime.text = dateFormat.format(startDate)
        binding.tvEndTime.text = dateFormat.format(endDate)

        // Set progress
        val progress = (currentSteps.toFloat() / challenge.targetSteps) * 100
        binding.progressSteps.progress = progress.toInt()
        binding.tvStepProgress.text = "$currentSteps/${challenge.targetSteps}"

        // Calculate potential reward based on step goal
        val rewardPercentage = when {
            challenge.targetSteps < 5000 -> 0.10
            challenge.targetSteps < 8000 -> 0.15
            challenge.targetSteps < 10000 -> 0.20
            else -> 0.25
        }
        val potentialReward = challenge.amountStaked * (1 + rewardPercentage)
        binding.tvPotentialReward.text = "₹${String.format("%.2f", potentialReward)}"

        // Set timer
        updateTimeRemaining(challenge.endTime.toDate())

        // Set challenge status
        when (challenge.status) {
            ChallengeStatus.ACTIVE -> {
                binding.tvChallengeStatus.text = "Active"
                binding.tvChallengeStatus.setBackgroundResource(com.example.stepbet.R.drawable.bg_status_pending)
                binding.layoutReward.visibility = View.GONE
            }
            ChallengeStatus.COMPLETED -> {
                binding.tvChallengeStatus.text = "Completed"
                binding.tvChallengeStatus.setBackgroundResource(com.example.stepbet.R.drawable.bg_status_completed)
                binding.layoutReward.visibility = View.VISIBLE
                binding.tvActualReward.text = "₹${challenge.rewardAmount}"
            }
            ChallengeStatus.FAILED -> {
                binding.tvChallengeStatus.text = "Failed"
                binding.tvChallengeStatus.setBackgroundResource(com.example.stepbet.R.drawable.bg_status_failed)
                binding.layoutReward.visibility = View.GONE
            }
        }
    }

    private fun updateTimeRemaining(endTime: Date) {
        val currentTime = Date()
        val timeRemainingMillis = endTime.time - currentTime.time

        if (timeRemainingMillis <= 0) {
            binding.tvTimeRemaining.text = "Challenge ended"
            return
        }

        // Calculate hours and minutes
        val hours = timeRemainingMillis / (60 * 60 * 1000)
        val minutes = (timeRemainingMillis % (60 * 60 * 1000)) / (60 * 1000)

        binding.tvTimeRemaining.text = "$hours hours, $minutes minutes remaining"
    }
}