package com.example.stepbet.challenges

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.models.ChallengeStatus
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.TransactionStatus
import com.example.stepbet.data.models.TransactionType
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.FragmentCreateChallengeBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class CreateChallengeFragment : Fragment() {

    private var _binding: FragmentCreateChallengeBinding? = null
    private val binding get() = _binding!!

    private val userRepository = UserRepository()
    private val challengeRepository = ChallengeRepository()
    private val transactionRepository = TransactionRepository()

    private var currentWalletBalance = 0.0
    private var selectedStepGoal = 8000
    private var selectedStakeAmount = 50.0

    companion object {
        private const val TAG = "CreateChallengeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check for existing active challenges first
        checkExistingChallenges()

        // Load user's wallet balance
        loadUserData()

        // Set up step goal slider
        setupStepGoalSlider()

        // Set up stake amount selection
        setupStakeAmountSelection()

        // Set initial reward info
        updateRewardInfo()

        // Set up create challenge button
        binding.btnCreateChallenge.setOnClickListener {
            createChallenge()
        }
    }

    private fun checkExistingChallenges() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            lifecycleScope.launch {
                try {
                    val existingChallenges = challengeRepository.getActiveUserChallenges(userId)

                    if (existingChallenges.isNotEmpty()) {
                        android.util.Log.w(TAG, "User already has ${existingChallenges.size} active challenge(s)")

                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "You already have an active challenge! Complete it first.",
                                Toast.LENGTH_LONG
                            ).show()

                            // Navigate back to home
                            findNavController().popBackStack()
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error checking existing challenges: ${e.message}")
                }
            }
        }
    }

    private fun loadUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            lifecycleScope.launch {
                try {
                    val user = userRepository.getUserById(userId)
                    user?.let {
                        currentWalletBalance = it.walletBalance
                        binding.tvWalletBalance.text = "Your wallet balance: ₹${String.format("%.2f", currentWalletBalance)}"
                        android.util.Log.d(TAG, "Current wallet balance loaded: ₹$currentWalletBalance")

                        checkWalletBalance()
                    } ?: run {
                        android.util.Log.w(TAG, "User not found")
                        currentWalletBalance = 0.0
                        binding.tvWalletBalance.text = "Your wallet balance: ₹0.00"
                        checkWalletBalance()
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error loading user data: ${e.message}")
                    currentWalletBalance = 0.0
                    binding.tvWalletBalance.text = "Your wallet balance: ₹0.00"
                    checkWalletBalance()
                }
            }
        }
    }

    private fun setupStepGoalSlider() {
        binding.sliderStepGoal.apply {
            addOnChangeListener { _, value, _ ->
                selectedStepGoal = value.toInt()
                binding.tvSelectedStepGoal.text = "${selectedStepGoal} steps"
                updateRewardInfo()
                android.util.Log.d(TAG, "Step goal selected: $selectedStepGoal")
            }
            value = 8000f
            binding.tvSelectedStepGoal.text = "8,000 steps"
        }
    }

    private fun setupStakeAmountSelection() {
        binding.rbStake50.isChecked = true

        binding.rgStakeAmount.setOnCheckedChangeListener { _, checkedId ->
            selectedStakeAmount = when (checkedId) {
                binding.rbStake20.id -> 20.0
                binding.rbStake50.id -> 50.0
                binding.rbStake100.id -> 100.0
                binding.rbStake200.id -> 200.0
                binding.rbCustomStake.id -> {
                    val customAmount = binding.etCustomStakeAmount.text.toString().toDoubleOrNull()
                    if (customAmount != null && customAmount >= 20.0) {
                        customAmount
                    } else {
                        50.0
                    }
                }
                else -> 50.0
            }

            binding.etCustomStakeAmount.isEnabled = checkedId == binding.rbCustomStake.id

            if (checkedId != binding.rbCustomStake.id) {
                binding.etCustomStakeAmount.setText("")
            }

            updateRewardInfo()
            checkWalletBalance()
            android.util.Log.d(TAG, "Stake amount selected: ₹$selectedStakeAmount")
        }

        binding.etCustomStakeAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (binding.rbCustomStake.isChecked) {
                    val customAmount = s.toString().toDoubleOrNull()
                    selectedStakeAmount = if (customAmount != null && customAmount >= 20.0) {
                        customAmount
                    } else {
                        20.0
                    }
                    updateRewardInfo()
                    checkWalletBalance()
                    android.util.Log.d(TAG, "Custom stake amount changed: ₹$selectedStakeAmount")
                }
            }
        })
    }

    private fun updateRewardInfo() {
        val rewardPercentage = when {
            selectedStepGoal < 5000 -> 0.10
            selectedStepGoal < 8000 -> 0.15
            selectedStepGoal < 10000 -> 0.20
            else -> 0.25
        }

        val potentialReward = selectedStakeAmount * (1 + rewardPercentage)

        binding.tvRewardPercentageValue.text = "${(rewardPercentage * 100).toInt()}%"
        binding.tvPotentialRewardValue.text = "₹${String.format("%.2f", potentialReward)}"

        android.util.Log.d(TAG, "Reward info updated - Percentage: ${(rewardPercentage * 100).toInt()}%, Potential Reward: ₹${String.format("%.2f", potentialReward)}")
    }

    private fun checkWalletBalance() {
        val hasEnoughBalance = currentWalletBalance >= selectedStakeAmount

        binding.btnCreateChallenge.isEnabled = hasEnoughBalance

        if (!hasEnoughBalance) {
            binding.tvWalletBalance.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            binding.btnCreateChallenge.text = "Insufficient Balance - Add Money"
            android.util.Log.w(TAG, "Insufficient balance: ₹$currentWalletBalance < ₹$selectedStakeAmount")
        } else {
            binding.tvWalletBalance.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            binding.btnCreateChallenge.text = "Start Challenge"
            android.util.Log.d(TAG, "Sufficient balance: ₹$currentWalletBalance >= ₹$selectedStakeAmount")
        }
    }

    private fun createChallenge() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_SHORT).show()
            return
        }

        // Double-check for existing challenges
        lifecycleScope.launch {
            try {
                val existingChallenges = challengeRepository.getActiveUserChallenges(userId)
                if (existingChallenges.isNotEmpty()) {
                    android.util.Log.w(TAG, "Found existing challenge during creation, aborting")
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "You already have an active challenge!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    return@launch
                }

                // Proceed with challenge creation
                proceedWithChallengeCreation(userId)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error checking for existing challenges: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun proceedWithChallengeCreation(userId: String) {
        // Validate custom stake amount if selected
        if (binding.rbCustomStake.isChecked) {
            val customAmount = binding.etCustomStakeAmount.text.toString().toDoubleOrNull()
            if (customAmount == null || customAmount < 20.0) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Please enter a valid stake amount (minimum ₹20)", Toast.LENGTH_SHORT).show()
                }
                return
            }
            selectedStakeAmount = customAmount
        }

        // Final balance check
        if (currentWalletBalance < selectedStakeAmount) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Insufficient wallet balance. Please add money first.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Disable the button to prevent multiple clicks
        activity?.runOnUiThread {
            binding.btnCreateChallenge.isEnabled = false
            binding.btnCreateChallenge.text = "Creating Challenge..."
        }

        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "Starting challenge creation - UserId: $userId, StepGoal: $selectedStepGoal, StakeAmount: ₹$selectedStakeAmount")

                // Calculate challenge end time (24 hours from now)
                val startTime = Timestamp.now()
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val endTime = Timestamp(calendar.time)

                // Create the challenge
                val challengeId = UUID.randomUUID().toString()
                val challenge = Challenge(
                    id = challengeId,
                    userId = userId,
                    targetSteps = selectedStepGoal,
                    amountStaked = selectedStakeAmount,
                    startTime = startTime,
                    endTime = endTime,
                    currentSteps = 0,
                    status = ChallengeStatus.ACTIVE
                )

                android.util.Log.d(TAG, "Challenge object created: $challenge")

                // Create a stake transaction
                val transactionId = UUID.randomUUID().toString()
                val transaction = Transaction(
                    id = transactionId,
                    userId = userId,
                    type = TransactionType.STAKE,
                    amount = selectedStakeAmount,
                    timestamp = Timestamp.now(),
                    status = TransactionStatus.COMPLETED
                )

                // Calculate new balance
                val newBalance = currentWalletBalance - selectedStakeAmount

                // Try atomic transaction first
                var success = try {
                    android.util.Log.d(TAG, "Attempting atomic transaction...")
                    userRepository.updateUserBalanceAndCreateTransaction(userId, transaction, newBalance)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Atomic transaction failed: ${e.message}")
                    false
                }

                if (!success) {
                    android.util.Log.w(TAG, "Trying individual operations as fallback...")
                    try {
                        val balanceUpdated = userRepository.updateWalletBalance(userId, newBalance)
                        if (balanceUpdated) {
                            val transactionCreated = transactionRepository.createTransaction(transaction)
                            success = transactionCreated != null
                            android.util.Log.d(TAG, "Individual operations - Balance: $balanceUpdated, Transaction: ${transactionCreated != null}")
                        }
                    } catch (fallbackException: Exception) {
                        android.util.Log.e(TAG, "Fallback operations failed: ${fallbackException.message}")
                        success = false
                    }
                }

                if (success) {
                    // Create the challenge in Firestore
                    val challengeCreated = challengeRepository.createChallenge(challenge)

                    if (challengeCreated != null) {
                        android.util.Log.d(TAG, "Single challenge created successfully: $challengeCreated")

                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Challenge created successfully! Start walking!", Toast.LENGTH_LONG).show()
                            findNavController().popBackStack()
                        }
                    } else {
                        android.util.Log.e(TAG, "Failed to create challenge in repository")

                        // Rollback wallet balance
                        try {
                            userRepository.updateWalletBalance(userId, currentWalletBalance)
                            android.util.Log.d(TAG, "Wallet balance rolled back")
                        } catch (rollbackException: Exception) {
                            android.util.Log.e(TAG, "Failed to rollback wallet balance: ${rollbackException.message}")
                        }

                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Failed to create challenge. Please try again.", Toast.LENGTH_SHORT).show()
                            binding.btnCreateChallenge.isEnabled = true
                            binding.btnCreateChallenge.text = "Start Challenge"
                        }
                    }
                } else {
                    android.util.Log.e(TAG, "Failed to update wallet balance")
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to process payment. Please try again.", Toast.LENGTH_SHORT).show()
                        binding.btnCreateChallenge.isEnabled = true
                        binding.btnCreateChallenge.text = "Start Challenge"
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error creating challenge: ${e.message}", e)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnCreateChallenge.isEnabled = true
                    binding.btnCreateChallenge.text = "Start Challenge"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}