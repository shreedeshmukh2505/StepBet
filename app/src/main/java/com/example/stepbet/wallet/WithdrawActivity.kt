package com.example.stepbet.wallet

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.TransactionStatus
import com.example.stepbet.data.models.TransactionType
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.ActivityWithdrawBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID

class WithdrawActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWithdrawBinding
    private val userRepository = UserRepository()
    private val transactionRepository = TransactionRepository()

    private var currentWalletBalance = 0.0
    private val minimumWithdrawalAmount = 100.0

    companion object {
        private const val TAG = "WithdrawActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityWithdrawBinding.inflate(layoutInflater)
            setContentView(binding.root)

            android.util.Log.d(TAG, "WithdrawActivity onCreate started")

            // Set up toolbar with null check
            setupToolbar()

            // Load wallet balance
            loadWalletBalance()

            // Set up withdraw button
            binding.btnWithdraw.setOnClickListener {
                android.util.Log.d(TAG, "Withdraw button clicked")
                validateAndWithdraw()
            }

            android.util.Log.d(TAG, "WithdrawActivity onCreate completed successfully")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error initializing withdrawal: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                title = "Withdraw Money"
            }
            binding.toolbar.setNavigationOnClickListener {
                android.util.Log.d(TAG, "Back button pressed")
                finish()
            }
            android.util.Log.d(TAG, "Toolbar setup completed")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error setting up toolbar: ${e.message}", e)
            // Don't crash, just continue without custom toolbar
        }
    }

    private fun loadWalletBalance() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            android.util.Log.e(TAG, "User ID is null")
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        android.util.Log.d(TAG, "Loading wallet balance for user: $userId")

        lifecycleScope.launch {
            try {
                val user = userRepository.getUserById(userId)

                runOnUiThread {
                    if (user != null) {
                        currentWalletBalance = user.walletBalance
                        android.util.Log.d(TAG, "Wallet balance loaded: ₹$currentWalletBalance")

                        // Update UI with null checks
                        updateBalanceUI()

                    } else {
                        android.util.Log.w(TAG, "User not found")
                        currentWalletBalance = 0.0
                        updateBalanceUI()
                        Toast.makeText(this@WithdrawActivity, "User data not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading wallet balance: ${e.message}", e)
                runOnUiThread {
                    currentWalletBalance = 0.0
                    updateBalanceUI()
                    Toast.makeText(this@WithdrawActivity, "Error loading balance: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateBalanceUI() {
        try {
            binding.tvCurrentBalance.text = "Current Balance: ₹${String.format("%.2f", currentWalletBalance)}"
            binding.tvWithdrawableAmount.text = "Withdrawable Amount: ₹${String.format("%.2f", currentWalletBalance)}"

            // Disable withdraw button if balance is insufficient
            binding.btnWithdraw.isEnabled = currentWalletBalance >= minimumWithdrawalAmount

            if (currentWalletBalance < minimumWithdrawalAmount) {
                binding.btnWithdraw.text = "Insufficient Balance (Min ₹$minimumWithdrawalAmount)"
                android.util.Log.d(TAG, "Insufficient balance: ₹$currentWalletBalance < ₹$minimumWithdrawalAmount")
            } else {
                binding.btnWithdraw.text = "Withdraw"
                android.util.Log.d(TAG, "Sufficient balance for withdrawal")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating balance UI: ${e.message}", e)
        }
    }

    private fun validateAndWithdraw() {
        try {
            android.util.Log.d(TAG, "Starting withdrawal validation")

            // Get withdraw amount with null checks
            val withdrawAmountStr = binding.etWithdrawAmount.text?.toString()?.trim()
            if (withdrawAmountStr.isNullOrEmpty()) {
                binding.tilWithdrawAmount.error = "Please enter an amount"
                android.util.Log.w(TAG, "Empty withdrawal amount")
                return
            }

            val withdrawAmount = try {
                withdrawAmountStr.toDouble()
            } catch (e: NumberFormatException) {
                binding.tilWithdrawAmount.error = "Please enter a valid amount"
                android.util.Log.w(TAG, "Invalid withdrawal amount: $withdrawAmountStr")
                return
            }

            if (withdrawAmount <= 0) {
                binding.tilWithdrawAmount.error = "Please enter a valid amount"
                android.util.Log.w(TAG, "Invalid withdrawal amount: $withdrawAmount")
                return
            }

            if (withdrawAmount < minimumWithdrawalAmount) {
                binding.tilWithdrawAmount.error = "Minimum withdrawal amount is ₹$minimumWithdrawalAmount"
                android.util.Log.w(TAG, "Below minimum withdrawal: $withdrawAmount < $minimumWithdrawalAmount")
                return
            }

            if (withdrawAmount > currentWalletBalance) {
                binding.tilWithdrawAmount.error = "Insufficient balance"
                android.util.Log.w(TAG, "Insufficient balance: $withdrawAmount > $currentWalletBalance")
                return
            }

            // Validate bank details with null checks
            val accountNumber = binding.etAccountNumber.text?.toString()?.trim()
            if (accountNumber.isNullOrEmpty()) {
                binding.tilAccountNumber.error = "Please enter account number"
                android.util.Log.w(TAG, "Empty account number")
                return
            }

            val ifscCode = binding.etIfscCode.text?.toString()?.trim()?.uppercase()
            if (ifscCode.isNullOrEmpty()) {
                binding.tilIfscCode.error = "Please enter IFSC code"
                android.util.Log.w(TAG, "Empty IFSC code")
                return
            }

            // Basic IFSC validation
            if (ifscCode.length != 11) {
                binding.tilIfscCode.error = "IFSC code must be 11 characters"
                android.util.Log.w(TAG, "Invalid IFSC length: ${ifscCode.length}")
                return
            }

            val accountHolderName = binding.etAccountHolderName.text?.toString()?.trim()
            if (accountHolderName.isNullOrEmpty()) {
                binding.tilAccountHolderName.error = "Please enter account holder name"
                android.util.Log.w(TAG, "Empty account holder name")
                return
            }

            // Clear any previous errors
            binding.tilWithdrawAmount.error = null
            binding.tilAccountNumber.error = null
            binding.tilIfscCode.error = null
            binding.tilAccountHolderName.error = null

            android.util.Log.d(TAG, "Validation passed, processing withdrawal of ₹$withdrawAmount")

            // Process withdrawal
            processWithdrawal(withdrawAmount, accountNumber, ifscCode, accountHolderName)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in validateAndWithdraw: ${e.message}", e)
            Toast.makeText(this, "Validation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processWithdrawal(
        amount: Double,
        accountNumber: String,
        ifscCode: String,
        accountHolderName: String
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            android.util.Log.e(TAG, "User ID is null during withdrawal processing")
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            // Disable withdraw button and show processing state
            binding.btnWithdraw.isEnabled = false
            binding.btnWithdraw.text = "Processing..."

            android.util.Log.d(TAG, "Processing withdrawal - Amount: ₹$amount, User: $userId")

            lifecycleScope.launch {
                try {
                    // 1. Create a pending withdrawal transaction
                    val transactionId = UUID.randomUUID().toString()
                    val transaction = Transaction(
                        id = transactionId,
                        userId = userId,
                        type = TransactionType.WITHDRAWAL,
                        amount = amount,
                        timestamp = Timestamp.now(),
                        status = TransactionStatus.PENDING
                    )

                    android.util.Log.d(TAG, "Created transaction: $transactionId")

                    // 2. Calculate new balance
                    val newBalance = currentWalletBalance - amount
                    android.util.Log.d(TAG, "New balance will be: ₹$newBalance")

                    // 3. Try atomic transaction first
                    var success = try {
                        userRepository.updateUserBalanceAndCreateTransaction(userId, transaction, newBalance)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Atomic transaction failed: ${e.message}")
                        false
                    }

                    // 4. If atomic transaction fails, try individual operations
                    if (!success) {
                        android.util.Log.w(TAG, "Trying individual operations as fallback")
                        try {
                            val balanceUpdated = userRepository.updateWalletBalance(userId, newBalance)
                            if (balanceUpdated) {
                                val transactionCreated = transactionRepository.createTransaction(transaction)
                                success = transactionCreated != null
                                android.util.Log.d(TAG, "Fallback method - Balance: $balanceUpdated, Transaction: ${transactionCreated != null}")
                            }
                        } catch (fallbackException: Exception) {
                            android.util.Log.e(TAG, "Fallback operations failed: ${fallbackException.message}")
                            success = false
                        }
                    }

                    // 5. Update UI on main thread
                    runOnUiThread {
                        if (success) {
                            android.util.Log.d(TAG, "Withdrawal request submitted successfully")

                            Toast.makeText(
                                this@WithdrawActivity,
                                "Withdrawal request submitted successfully!\nAmount: ₹${String.format("%.2f", amount)}\nProcessing time: 24-48 hours",
                                Toast.LENGTH_LONG
                            ).show()

                            setResult(RESULT_OK)
                            finish()
                        } else {
                            android.util.Log.e(TAG, "Failed to process withdrawal")

                            Toast.makeText(
                                this@WithdrawActivity,
                                "Failed to process withdrawal request. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()

                            // Reset button state
                            binding.btnWithdraw.isEnabled = true
                            binding.btnWithdraw.text = "Withdraw"
                        }
                    }

                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error in withdrawal processing: ${e.message}", e)

                    runOnUiThread {
                        Toast.makeText(
                            this@WithdrawActivity,
                            "Error processing withdrawal: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        // Reset button state
                        binding.btnWithdraw.isEnabled = true
                        binding.btnWithdraw.text = "Withdraw"
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in processWithdrawal: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()

            // Reset button state
            binding.btnWithdraw.isEnabled = true
            binding.btnWithdraw.text = "Withdraw"
        }
    }

    override fun onBackPressed() {
        if (binding.btnWithdraw.text.toString().contains("Processing")) {
            Toast.makeText(this, "Withdrawal is being processed. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }
}