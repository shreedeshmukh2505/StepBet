package com.example.stepbet.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stepbet.MainActivity
import com.example.stepbet.databinding.ActivityOtpVerificationBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var auth: FirebaseAuth

    private var phoneNumber: String = ""
    private var verificationId: String = ""
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countDownTimer: CountDownTimer? = null
    private var timeoutSeconds = 60L

    // List of all OTP EditTexts
    private val otpEditTexts by lazy {
        listOf(
            binding.etOtp1,
            binding.etOtp2,
            binding.etOtp3,
            binding.etOtp4,
            binding.etOtp5,
            binding.etOtp6
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Get data from intent
        phoneNumber = intent.getStringExtra("phone_number") ?: ""
        verificationId = intent.getStringExtra("verification_id") ?: ""

        if (phoneNumber.isEmpty() || verificationId.isEmpty()) {
            Toast.makeText(this, "Invalid verification data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set phone number in UI
        binding.tvPhoneNumber.text = phoneNumber

        // Set up OTP EditText behavior
        setupOtpInputs()

        // Setup verification button
        binding.btnVerifyOtp.setOnClickListener {
            val otp = otpEditTexts.joinToString("") { it.text.toString() }
            if (otp.length == 6) {
                verifyOtp(otp)
            } else {
                Toast.makeText(this, "Please enter complete OTP", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup resend code
        binding.tvResendCode.setOnClickListener {
            if (binding.tvResendCode.isEnabled) {
                resendVerificationCode()
            }
        }

        // Start countdown timer
        startCountdownTimer()
    }

    private fun setupOtpInputs() {
        for (i in otpEditTexts.indices) {
            otpEditTexts[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    // Auto-move to next EditText when current one is filled
                    if (s?.length == 1) {
                        if (i < otpEditTexts.size - 1) {
                            otpEditTexts[i + 1].requestFocus()
                        }
                    } else if (s?.length == 0) {
                        // Move to previous when current is emptied
                        if (i > 0) {
                            otpEditTexts[i - 1].requestFocus()
                        }
                    }

                    // Enable verify button if all fields are filled
                    val allFilled = otpEditTexts.all { it.text.isNotEmpty() }
                    binding.btnVerifyOtp.isEnabled = allFilled
                }
            })
        }
    }

    private fun verifyOtp(otp: String) {
        binding.btnVerifyOtp.isEnabled = false
        binding.btnVerifyOtp.text = "Verifying..."

        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.btnVerifyOtp.isEnabled = true
                binding.btnVerifyOtp.text = "Verify OTP"

                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification successful!", Toast.LENGTH_SHORT).show()

                    // Check if new user
                    if (task.result?.additionalUserInfo?.isNewUser == true) {
                        // Go to profile setup
                        val intent = Intent(this, UserProfileSetupActivity::class.java)
                        intent.putExtra("phone_number", phoneNumber)
                        startActivity(intent)
                    } else {
                        // Go to main activity
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                    finish()
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("invalid verification code") == true ->
                            "Invalid OTP. Please check and try again."
                        task.exception?.message?.contains("expired") == true ->
                            "OTP expired. Please request a new one."
                        else -> "Verification failed: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

                    // Clear OTP fields on error
                    otpEditTexts.forEach { it.text?.clear() }
                    otpEditTexts[0].requestFocus()
                }
            }
    }

    private fun resendVerificationCode() {
        binding.tvResendCode.isEnabled = false
        binding.tvResendCode.text = "Sending..."

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .apply {
                resendToken?.let { setForceResendingToken(it) }
            }
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
        startCountdownTimer()
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification completed (rare)
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            binding.tvResendCode.isEnabled = true
            binding.tvResendCode.text = "Resend Code"

            Toast.makeText(this@OtpVerificationActivity,
                "Failed to resend OTP: ${e.message}", Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(newVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            // Save new verification ID and resending token
            verificationId = newVerificationId
            resendToken = token

            binding.tvResendCode.text = "Resend Code"
            Toast.makeText(this@OtpVerificationActivity, "OTP sent again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCountdownTimer() {
        binding.tvResendCode.isEnabled = false

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeoutSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvCountdown.text = "Resend code in ${seconds / 60}:${String.format("%02d", seconds % 60)}"
            }

            override fun onFinish() {
                binding.tvResendCode.isEnabled = true
                binding.tvCountdown.text = "Didn't receive code?"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}