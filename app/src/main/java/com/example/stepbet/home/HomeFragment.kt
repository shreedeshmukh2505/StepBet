package com.example.stepbet.home

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.stepbet.R
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.models.ChallengeStatus
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.TransactionStatus
import com.example.stepbet.data.models.TransactionType
import com.example.stepbet.data.models.User
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.data.repositories.StepRepository
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.FragmentHomeBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val userRepository = UserRepository()
    private val stepRepository = StepRepository()
    private val challengeRepository = ChallengeRepository()
    private val transactionRepository = TransactionRepository()

    private var currentUser: User? = null
    private var activeChallenge: Challenge? = null // Single challenge only
    private var currentStepCount = 0

    // Step counter sensor
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var initialStepCount = -1

    // SharedPreferences for step data persistence
    private lateinit var sharedPrefs: SharedPreferences

    // Auto-completion handler
    private val completionHandler = Handler(Looper.getMainLooper())
    private var completionRunnable: Runnable? = null
    private var dailyStepCount = 0   // Total daily steps (never resets during day)

    companion object {
        private const val TAG = "HomeFragment"
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_DAILY_STEPS = "daily_steps"
        private const val KEY_CHALLENGE_STEPS = "challenge_steps" // Add this
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_INITIAL_STEPS = "initial_steps"
        private const val COMPLETION_DELAY_MS = 3000L // 3 seconds
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        android.util.Log.d(TAG, "HomeFragment onViewCreated")

        // Initialize SharedPreferences
        sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize step sensor
        initializeStepSensor()

        // Setup click listeners
        setupClickListeners()

        // Load initial data
        loadTodaySteps()
        loadUserData()

        // Setup refresh listener
        binding.swipeRefresh.setOnRefreshListener {
            android.util.Log.d(TAG, "Pull to refresh triggered")
            loadUserData()
        }
    }

    private fun setupClickListeners() {
        // Setup create challenge button
        binding.btnCreateChallenge.setOnClickListener {
            if (activeChallenge != null) {
                android.widget.Toast.makeText(requireContext(), "Complete your current challenge first!", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                findNavController().navigate(R.id.action_homeFragment_to_createChallengeFragment)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Navigation error: ${e.message}")
                android.widget.Toast.makeText(requireContext(), "Navigation error", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Setup test step buttons
        binding.btnAdd100Steps.setOnClickListener { addTestSteps(100) }
        binding.btnAdd500Steps.setOnClickListener { addTestSteps(500) }
        binding.btnAdd1000Steps.setOnClickListener { addTestSteps(1000) }

        // Debug button
        binding.btnDebugChallenges.setOnClickListener {
            debugChallengeInfo()
        }
    }

    private fun initializeStepSensor() {
        try {
            sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            if (stepSensor == null) {
                android.util.Log.w(TAG, "Step counter sensor not available on this device")
                binding.layoutTestButtons.visibility = View.VISIBLE
            } else {
                android.util.Log.d(TAG, "Step counter sensor initialized")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error initializing step sensor: ${e.message}")
        }
    }

    private fun loadTodaySteps() {
        val today = getCurrentDateString()
        val lastDate = sharedPrefs.getString(KEY_LAST_DATE, "")

        android.util.Log.d(TAG, "Loading steps for today: $today, last date: $lastDate")

        if (today != lastDate) {
            // New day, reset both counters
            currentStepCount = 0
            dailyStepCount = 0
            sharedPrefs.edit()
                .putInt(KEY_DAILY_STEPS, 0)
                .putInt(KEY_CHALLENGE_STEPS, 0) // Add this key
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_INITIAL_STEPS, -1)
                .apply()
            android.util.Log.d(TAG, "New day detected, resetting both step counters")
        } else {
            // Same day, load existing counts
            dailyStepCount = sharedPrefs.getInt(KEY_DAILY_STEPS, 0)
            currentStepCount = sharedPrefs.getInt(KEY_CHALLENGE_STEPS, 0)
            initialStepCount = sharedPrefs.getInt(KEY_INITIAL_STEPS, -1)
            android.util.Log.d(TAG, "Loaded today's steps - Daily: $dailyStepCount, Challenge: $currentStepCount")
        }

        updateStepCountUI()
        loadStepsFromFirebase()
    }


    private fun loadStepsFromFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            lifecycleScope.launch {
                try {
                    val firebaseSteps = stepRepository.getTodaySteps(userId)
                    android.util.Log.d(TAG, "Steps from Firebase: $firebaseSteps, Local steps: $currentStepCount")

                    if (firebaseSteps > currentStepCount) {
                        currentStepCount = firebaseSteps
                        updateStepCountUI()
                        saveStepsLocally()
                        android.util.Log.d(TAG, "Updated local steps from Firebase: $currentStepCount")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error loading steps from Firebase: ${e.message}")
                }
            }
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun loadUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        android.util.Log.d(TAG, "Loading user data for userId: $userId")

        if (userId != null) {
            lifecycleScope.launch {
                binding.swipeRefresh.isRefreshing = true

                try {
                    // Load user data
                    currentUser = userRepository.getUserById(userId)
                    currentUser?.let { user ->
                        android.util.Log.d(TAG, "User loaded: ${user.displayName}, balance: ₹${user.walletBalance}")
                        binding.tvUserName.text = user.displayName.ifEmpty { "User" }
                        binding.tvWalletBalance.text = "₹${String.format("%.2f", user.walletBalance)}"
                    }

                    // Load SINGLE active challenge
                    val challenges = challengeRepository.getActiveUserChallenges(userId)
                    activeChallenge = challenges.firstOrNull() // Only take the first one

                    android.util.Log.d(TAG, "Found ${challenges.size} challenges, using: ${activeChallenge?.id}")

                    // Update UI
                    activity?.runOnUiThread {
                        updateChallengeUI()
                        updateStepProgress()

                        // Check if challenge should be completed
                        checkChallengeCompletion()
                    }

                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error loading user data: ${e.message}", e)
                    activity?.runOnUiThread {
                        binding.tvUserName.text = "User"
                        binding.tvWalletBalance.text = "₹0.00"
                        showNoChallenge()
                    }
                } finally {
                    activity?.runOnUiThread {
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            }
        }
    }

    private fun updateChallengeUI() {
        android.util.Log.d(TAG, "Updating challenge UI - Active challenge: ${activeChallenge?.id}")

        if (activeChallenge != null) {
            val challenge = activeChallenge!!

            // Show challenge card
            binding.layoutActiveChallenge.visibility = View.VISIBLE
            binding.layoutNoChallenge.visibility = View.GONE

            // Update challenge details
            binding.tvActiveChallengeLabel.text = "Active Challenge"
            binding.tvChallengeGoal.text = "${challenge.targetSteps} steps"
            binding.tvChallengeStake.text = "₹${String.format("%.2f", challenge.amountStaked)}"

            // Calculate potential reward
            val rewardPercentage = when {
                challenge.targetSteps < 5000 -> 0.10
                challenge.targetSteps < 8000 -> 0.15
                challenge.targetSteps < 10000 -> 0.20
                else -> 0.25
            }

            val potentialReward = challenge.amountStaked * (1 + rewardPercentage)
            binding.tvPotentialReward.text = "₹${String.format("%.2f", potentialReward)}"

            // Update progress (this will be handled in updateStepProgress())
            val progress = if (challenge.targetSteps > 0) {
                ((currentStepCount.toFloat() / challenge.targetSteps) * 100).toInt()
            } else 0

            // Show challenge status
            binding.tvChallengeStatus.visibility = View.VISIBLE
            binding.tvChallengeStatus.text = when {
                progress >= 100 -> "🎉 Challenge Completed! Well done!"
                progress >= 80 -> "Almost there! ${challenge.targetSteps - currentStepCount} steps to go"
                progress >= 50 -> "Halfway there! Keep going!"
                else -> "Keep walking to complete your challenge!"
            }

            // Update button text
            if (progress >= 100) {
                binding.btnCreateChallenge.text = "Challenge Completed! Create New One"
                binding.btnCreateChallenge.backgroundTintList = resources.getColorStateList(R.color.green_500, null)
            } else {
                binding.btnCreateChallenge.text = "Complete Current Challenge First"
                binding.btnCreateChallenge.backgroundTintList = resources.getColorStateList(R.color.gray_400, null)
            }

            android.util.Log.d(TAG, "Challenge UI updated - Progress: $progress%")

        } else {
            // No active challenge - show daily steps info
            showNoChallenge()
        }
    }
    private fun showNoChallenge() {
        android.util.Log.d(TAG, "Showing no challenge state")
        binding.layoutActiveChallenge.visibility = View.GONE
        binding.layoutNoChallenge.visibility = View.VISIBLE
        binding.btnCreateChallenge.text = "Create New Challenge"
        binding.btnCreateChallenge.backgroundTintList = resources.getColorStateList(R.color.primary_500, null)

        // When no challenge, still show daily step progress
        binding.tvProgressStatus.visibility = View.VISIBLE
        binding.tvProgressStatus.text = "Daily steps: $currentStepCount"
    }

    private fun updateStepProgress() {
        // Show current challenge steps in main counter
        binding.tvStepCount.text = currentStepCount.toString()

        // Always show total daily steps in progress text
        binding.tvStepProgress.text = "$dailyStepCount steps today"

        if (activeChallenge != null) {
            val challenge = activeChallenge!!

            // For challenge progress, use currentStepCount (challenge-specific)
            val challengeProgress = if (challenge.targetSteps > 0) {
                ((currentStepCount.toFloat() / challenge.targetSteps) * 100).toInt()
            } else 0

            // Update challenge progress bar and text
            binding.progressChallenge.progress = challengeProgress
            binding.tvChallengeProgress.text = "$currentStepCount/${challenge.targetSteps} ($challengeProgress%)"

            // Update main progress bar to show challenge progress
            binding.progressSteps.progress = challengeProgress

            // Show motivational message based on challenge progress
            val progressText = when {
                challengeProgress >= 100 -> "🎉 Goal Achieved! Congratulations!"
                challengeProgress >= 80 -> "Almost there! ${challenge.targetSteps - currentStepCount} steps to go"
                challengeProgress >= 50 -> "Halfway there! Keep going!"
                challengeProgress >= 25 -> "Good start! ${challenge.targetSteps - currentStepCount} steps remaining"
                else -> "Just getting started! ${challenge.targetSteps - currentStepCount} steps to goal"
            }

            binding.tvProgressStatus.visibility = View.VISIBLE
            binding.tvProgressStatus.text = progressText

        } else {
            // No active challenge - show 0 in main counter, daily total in progress
            binding.tvStepCount.text = "0"
            binding.progressSteps.progress = 0
            binding.progressChallenge.progress = 0
            binding.tvChallengeProgress.text = "No active challenge"
            binding.tvProgressStatus.visibility = View.VISIBLE
            binding.tvProgressStatus.text = "Create a challenge to start counting steps!"
        }

        // Update challenge step count in Firebase (only if there's an active challenge)
        activeChallenge?.let { challenge ->
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                lifecycleScope.launch {
                    try {
                        challengeRepository.updateChallengeSteps(userId, challenge.id, currentStepCount)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error updating challenge steps: ${e.message}")
                    }
                }
            }
        }
    }


    private fun checkChallengeCompletion() {
        activeChallenge?.let { challenge ->
            if (currentStepCount >= challenge.targetSteps && challenge.status == ChallengeStatus.ACTIVE) {
                android.util.Log.d(TAG, "Challenge completed! Starting completion process...")

                // Cancel any existing completion runnable
                completionRunnable?.let { completionHandler.removeCallbacks(it) }

                // Schedule challenge completion after delay
                completionRunnable = Runnable {
                    completeChallenge(challenge)
                }
                completionHandler.postDelayed(completionRunnable!!, COMPLETION_DELAY_MS)

                // Show immediate feedback
                android.widget.Toast.makeText(
                    requireContext(),
                    "🎉 Challenge Completed! Processing reward...",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun completeChallenge(challenge: Challenge) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        android.util.Log.d(TAG, "Completing challenge: ${challenge.id}")

        lifecycleScope.launch {
            try {
                // Calculate reward
                val rewardPercentage = when {
                    challenge.targetSteps < 5000 -> 0.10
                    challenge.targetSteps < 8000 -> 0.15
                    challenge.targetSteps < 10000 -> 0.20
                    else -> 0.25
                }

                val rewardAmount = challenge.amountStaked * (1 + rewardPercentage)

                // Complete challenge in repository with final step count
                val success = challengeRepository.completeChallenge(
                    userId,
                    challenge.id,
                    currentStepCount, // Use challenge-specific step count
                    ChallengeStatus.COMPLETED,
                    rewardAmount
                )

                if (success) {
                    // Create reward transaction
                    val transaction = Transaction(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        type = TransactionType.REWARD,
                        amount = rewardAmount,
                        timestamp = Timestamp.now(),
                        status = TransactionStatus.COMPLETED
                    )

                    // Update user balance
                    currentUser?.let { user ->
                        val newBalance = user.walletBalance + rewardAmount
                        val newEarnings = user.totalEarnings + (rewardAmount - challenge.amountStaked)

                        userRepository.updateUserBalanceAndCreateTransaction(userId, transaction, newBalance)

                        // Update local user data
                        currentUser = user.copy(
                            walletBalance = newBalance,
                            totalEarnings = newEarnings
                        )
                    }

                    activity?.runOnUiThread {
                        // RESET: Clear active challenge and reset challenge step count to 0
                        activeChallenge = null
                        currentStepCount = 0  // IMPORTANT: Reset challenge steps to 0

                        android.util.Log.d(TAG, "Challenge completed - Reset currentStepCount to 0, dailyStepCount remains: $dailyStepCount")

                        // Update UI immediately
                        updateStepCountUI()
                        updateChallengeUI()
                        updateStepProgress()

                        // Save the reset state
                        saveStepsLocally()

                        // Refresh user data to update wallet balance
                        loadUserData()

                        // Show success message
                        android.widget.Toast.makeText(
                            requireContext(),
                            "🎉 Challenge Completed! Earned ₹${String.format("%.2f", rewardAmount)}\nReady for next challenge!",
                            android.widget.Toast.LENGTH_LONG
                        ).show()

                        android.util.Log.d(TAG, "Challenge completion successful - Reward: ₹$rewardAmount")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error completing challenge: ${e.message}")
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error completing challenge: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveStepsLocally() {
        sharedPrefs.edit()
            .putInt(KEY_DAILY_STEPS, dailyStepCount)
            .putInt(KEY_CHALLENGE_STEPS, currentStepCount) // Save challenge steps separately
            .putString(KEY_LAST_DATE, getCurrentDateString())
            .apply()
    }

    private fun saveStepsToFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            lifecycleScope.launch {
                try {
                    stepRepository.saveSteps(userId, currentStepCount)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error saving steps to Firebase: ${e.message}")
                }
            }
        }
    }

    private fun addTestSteps(steps: Int) {
        // Always add to daily total
        dailyStepCount += steps

        // Only add to challenge steps if there's an active challenge
        if (activeChallenge != null) {
            currentStepCount += steps
        }

        android.util.Log.d(TAG, "Added $steps test steps - Challenge: $currentStepCount, Daily: $dailyStepCount")

        updateStepCountUI()
        updateStepProgress()
        saveStepsLocally()
        saveStepsToFirebase()

        // Check for completion
        checkChallengeCompletion()

        // Show progress feedback
        activeChallenge?.let { challenge ->
            val remaining = maxOf(0, challenge.targetSteps - currentStepCount)
            val message = if (remaining > 0) {
                "Added $steps steps! $remaining steps remaining"
            } else {
                "🎉 Goal achieved! Challenge will complete in ${COMPLETION_DELAY_MS/1000} seconds"
            }
            android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun debugChallengeInfo() {
        android.util.Log.d(TAG, "=== DEBUG CHALLENGE INFO ===")
        android.util.Log.d(TAG, "Active challenge: ${activeChallenge?.id}")
        android.util.Log.d(TAG, "Current steps: $currentStepCount")
        android.util.Log.d(TAG, "User: ${currentUser?.displayName}")
        activeChallenge?.let { challenge ->
            android.util.Log.d(TAG, "Challenge details:")
            android.util.Log.d(TAG, "  Target: ${challenge.targetSteps}")
            android.util.Log.d(TAG, "  Stake: ₹${challenge.amountStaked}")
            android.util.Log.d(TAG, "  Status: ${challenge.status}")
            android.util.Log.d(TAG, "  Progress: $currentStepCount/${challenge.targetSteps}")
        }
    }

    private fun updateStepCountUI() {
        if (activeChallenge != null) {
            binding.tvStepCount.text = currentStepCount.toString()
        } else {
            binding.tvStepCount.text = "0"
        }
        binding.tvStepProgress.text = "$dailyStepCount steps today"
        updateStepProgress()
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        loadUserData()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        saveStepsLocally()
        saveStepsToFirebase()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val totalSteps = it.values[0].toInt()

                if (initialStepCount == -1) {
                    initialStepCount = totalSteps - currentStepCount
                    sharedPrefs.edit().putInt(KEY_INITIAL_STEPS, initialStepCount).apply()
                } else {
                    val newStepCount = totalSteps - initialStepCount
                    if (newStepCount > currentStepCount) {
                        currentStepCount = newStepCount
                        updateStepCountUI()

                        if (currentStepCount % 10 == 0) {
                            saveStepsLocally()
                            saveStepsToFirebase()
                        }

                        // Check for completion on real steps too
                        checkChallengeCompletion()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroyView() {
        super.onDestroyView()

        // Cancel completion handler
        completionRunnable?.let { completionHandler.removeCallbacks(it) }

        saveStepsLocally()
        saveStepsToFirebase()
        _binding = null
    }
}