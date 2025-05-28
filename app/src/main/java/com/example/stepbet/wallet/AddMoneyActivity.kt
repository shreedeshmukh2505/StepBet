package com.example.stepbet.wallet

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.TransactionStatus
import com.example.stepbet.data.models.TransactionType
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.ActivityAddMoneyBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class AddMoneyActivity : AppCompatActivity(), PaymentResultListener {

    private lateinit var binding: ActivityAddMoneyBinding
    private val userRepository = UserRepository()
    private val transactionRepository = TransactionRepository()

    private var selectedAmount = 100
    private var currentWalletBalance = 0.0

    companion object {
        private const val TAG = "AddMoneyActivity"
        private const val RAZORPAY_KEY_ID = "rzp_test_JAM1iybr6gVUvR"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMoneyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeRazorpay()
        loadWalletBalance()
        setUpAmountSelection()

        binding.btnAddMoney.setOnClickListener {
            validateAndStartPayment()
        }

        android.util.Log.d(TAG, "AddMoneyActivity initialized")
    }

    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                title = "Add Money"
            }
            binding.toolbar.setNavigationOnClickListener {
                finish()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Toolbar setup failed: ${e.message}")
        }
    }

    private fun initializeRazorpay() {
        try {
            Checkout.preload(applicationContext)
            android.util.Log.d(TAG, "RazorPay initialized with key: $RAZORPAY_KEY_ID")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "RazorPay initialization failed: ${e.message}")
            Toast.makeText(this, "Payment system initialization failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadWalletBalance() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val user = userRepository.getUserById(userId)
                user?.let {
                    currentWalletBalance = it.walletBalance
                    binding.tvCurrentBalance.text = "Current Balance: ₹${String.format("%.2f", currentWalletBalance)}"
                    android.util.Log.d(TAG, "Current wallet balance: ₹$currentWalletBalance")
                } ?: run {
                    currentWalletBalance = 0.0
                    binding.tvCurrentBalance.text = "Current Balance: ₹0.00"
                    android.util.Log.w(TAG, "User not found")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading wallet balance: ${e.message}")
                currentWalletBalance = 0.0
                binding.tvCurrentBalance.text = "Current Balance: ₹0.00"
            }
        }
    }

    private fun setUpAmountSelection() {
        binding.rb100.isChecked = true
        updateButtonText()

        binding.rgAmount.setOnCheckedChangeListener { _, checkedId ->
            selectedAmount = when (checkedId) {
                binding.rb100.id -> 100
                binding.rb200.id -> 200
                binding.rb500.id -> 500
                binding.rb1000.id -> 1000
                binding.rbCustom.id -> {
                    val customAmount = binding.etCustomAmount.text.toString().toIntOrNull()
                    if (customAmount != null && customAmount >= 10) {
                        customAmount
                    } else {
                        100 // Default fallback
                    }
                }
                else -> 100
            }

            // Enable/disable custom amount field
            binding.etCustomAmount.isEnabled = checkedId == binding.rbCustom.id

            // Clear custom amount if switching away from custom
            if (checkedId != binding.rbCustom.id) {
                binding.etCustomAmount.setText("")
            }

            updateButtonText()
            android.util.Log.d(TAG, "Amount selected: ₹$selectedAmount")
        }

        // Add TextWatcher for custom amount field
        binding.etCustomAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (binding.rbCustom.isChecked) {
                    val customAmount = s.toString().toIntOrNull()
                    selectedAmount = if (customAmount != null && customAmount >= 10) {
                        customAmount
                    } else {
                        10 // Minimum amount
                    }
                    updateButtonText()
                    android.util.Log.d(TAG, "Custom amount changed: ₹$selectedAmount")
                }
            }
        })
    }

    private fun updateButtonText() {
        binding.btnAddMoney.text = "Pay ₹$selectedAmount"
    }

    private fun validateAndStartPayment() {
        // Get the current selected amount (especially important for custom amount)
        if (binding.rbCustom.isChecked) {
            val customAmount = binding.etCustomAmount.text.toString().toIntOrNull()
            if (customAmount == null || customAmount < 10) {
                Toast.makeText(this, "Please enter a valid amount (minimum ₹10)", Toast.LENGTH_SHORT).show()
                return
            }
            selectedAmount = customAmount
        }

        if (selectedAmount < 10) {
            Toast.makeText(this, "Minimum amount is ₹10", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedAmount > 100000) {
            Toast.makeText(this, "Maximum amount is ₹1,00,000", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        android.util.Log.d(TAG, "Starting payment for ₹$selectedAmount")
        startRazorpayPayment()
    }

    private fun startRazorpayPayment() {
        val checkout = Checkout()
        checkout.setKeyID(RAZORPAY_KEY_ID)

        try {
            val options = JSONObject().apply {
                put("name", "StepBet")
                put("description", "Add Money to Wallet")
                put("currency", "INR")
                put("amount", selectedAmount * 100) // Convert to paisa

                // Add user details
                FirebaseAuth.getInstance().currentUser?.let { user ->
                    user.email?.let { put("prefill.email", it) }
                    user.phoneNumber?.let { put("prefill.contact", it) }
                }

                // Theme
                put("theme.color", "#673AB7")
                put("modal.backdropclose", false)
                put("modal.escape", true)
                put("modal.handleback", true)

                // Notes for tracking
                put("notes.user_id", FirebaseAuth.getInstance().currentUser?.uid)
                put("notes.purpose", "wallet_topup")
                put("notes.amount", selectedAmount)
            }

            android.util.Log.d(TAG, "RazorPay checkout options: $options")
            checkout.open(this, options)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "RazorPay checkout error: ${e.message}")
            Toast.makeText(this, "Payment initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        android.util.Log.d(TAG, "PAYMENT SUCCESS: $razorpayPaymentId for amount: ₹$selectedAmount")

        if (razorpayPaymentId == null) {
            android.util.Log.e(TAG, "Payment ID is null")
            Toast.makeText(this, "Payment ID not received", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            android.util.Log.e(TAG, "User ID is null")
            Toast.makeText(this, "User session expired", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        binding.btnAddMoney.isEnabled = false
        binding.btnAddMoney.text = "Processing..."

        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "Processing wallet update for userId: $userId, amount: $selectedAmount")

                // Create transaction record
                val transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = TransactionType.DEPOSIT,
                    amount = selectedAmount.toDouble(),
                    timestamp = Timestamp.now(),
                    status = TransactionStatus.COMPLETED,
                    razorpayTransactionId = razorpayPaymentId
                )

                // Calculate new balance
                val newBalance = currentWalletBalance + selectedAmount
                android.util.Log.d(TAG, "Updating balance from ₹$currentWalletBalance to ₹$newBalance")

                // Try to update using atomic transaction first
                var success = try {
                    userRepository.updateUserBalanceAndCreateTransaction(userId, transaction, newBalance)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Atomic transaction failed: ${e.message}")
                    false
                }

                // If atomic transaction fails, try individual operations
                if (!success) {
                    android.util.Log.w(TAG, "Trying individual operations as fallback")
                    try {
                        // Update wallet balance
                        val balanceUpdated = userRepository.updateWalletBalance(userId, newBalance)
                        if (balanceUpdated) {
                            // Create transaction record
                            val transactionId = transactionRepository.createTransaction(transaction)
                            success = transactionId != null
                            android.util.Log.d(TAG, "Fallback method - Balance updated: $balanceUpdated, Transaction created: ${transactionId != null}")
                        }
                    } catch (fallbackException: Exception) {
                        android.util.Log.e(TAG, "Fallback method also failed: ${fallbackException.message}")
                        success = false
                    }
                }

                // Update UI on main thread
                runOnUiThread {
                    if (success) {
                        android.util.Log.d(TAG, "Wallet updated successfully: +₹$selectedAmount")

                        Toast.makeText(
                            this@AddMoneyActivity,
                            "₹$selectedAmount added to your wallet successfully!",
                            Toast.LENGTH_LONG
                        ).show()

                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        android.util.Log.e(TAG, "Both atomic and fallback methods failed")
                        Toast.makeText(
                            this@AddMoneyActivity,
                            "Payment successful but wallet update failed. Please contact support with Payment ID: $razorpayPaymentId",
                            Toast.LENGTH_LONG
                        ).show()

                        // Reset button
                        binding.btnAddMoney.isEnabled = true
                        updateButtonText()
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in payment success handler: ${e.message}", e)

                runOnUiThread {
                    Toast.makeText(
                        this@AddMoneyActivity,
                        "Payment successful but wallet update failed. Error: ${e.message}. Contact support with Payment ID: $razorpayPaymentId",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnAddMoney.isEnabled = true
                    updateButtonText()
                }
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        android.util.Log.e(TAG, "PAYMENT FAILED: Code=$code, Response=$response")

        val errorMessage = when (code) {
            Checkout.NETWORK_ERROR -> "Network error. Please check your internet connection."
            Checkout.PAYMENT_CANCELED -> "Payment was cancelled."
            else -> "Payment failed. Please try again. Error code: $code"
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        android.util.Log.e(TAG, "Payment failure - Code: $code, Amount: ₹$selectedAmount")
    }

    override fun onBackPressed() {
        if (binding.btnAddMoney.text.toString().contains("Processing")) {
            Toast.makeText(this, "Payment is being processed. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }
}