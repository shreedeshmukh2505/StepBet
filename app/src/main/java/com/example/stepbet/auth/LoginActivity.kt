package com.example.stepbet.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stepbet.MainActivity
import com.example.stepbet.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null

    // Store phone number as a class variable so it's available throughout the activity
    private var enteredPhoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSendOtp.setOnClickListener {
            val phoneInput = binding.etPhoneNumber.text.toString().trim()
            if (validatePhoneNumber(phoneInput)) {
                // Store the phone number with country code
                enteredPhoneNumber = "+91$phoneInput"
                sendVerificationCode(enteredPhoneNumber)
            }
        }

        binding.btnVerifyOtp.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (validateOtp(otp)) {
                verifyCode(otp)
            }
        }
    }

    private fun validatePhoneNumber(phoneNumber: String): Boolean {
        return when {
            phoneNumber.isEmpty() -> {
                binding.etPhoneNumber.error = "Please enter phone number"
                binding.etPhoneNumber.requestFocus()
                false
            }
            phoneNumber.length != 10 -> {
                binding.etPhoneNumber.error = "Please enter 10-digit phone number"
                binding.etPhoneNumber.requestFocus()
                false
            }
            !phoneNumber.all { it.isDigit() } -> {
                binding.etPhoneNumber.error = "Please enter valid phone number"
                binding.etPhoneNumber.requestFocus()
                false
            }
            else -> {
                binding.etPhoneNumber.error = null
                true
            }
        }
    }

    private fun validateOtp(otp: String): Boolean {
        return when {
            otp.isEmpty() -> {
                binding.etOtp.error = "Please enter OTP"
                binding.etOtp.requestFocus()
                false
            }
            otp.length != 6 -> {
                binding.etOtp.error = "Please enter 6-digit OTP"
                binding.etOtp.requestFocus()
                false
            }
            verificationId == null -> {
                Toast.makeText(this, "Please send OTP first", Toast.LENGTH_SHORT).show()
                false
            }
            else -> {
                binding.etOtp.error = null
                true
            }
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        binding.btnSendOtp.isEnabled = false
        binding.etPhoneNumber.isEnabled = false
        binding.btnSendOtp.text = "Sending..."

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // This will be invoked if auto-verification happens (rare)
            signInWithCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // Reset button states
            binding.btnSendOtp.isEnabled = true
            binding.etPhoneNumber.isEnabled = true
            binding.btnSendOtp.text = "Send OTP"

            val errorMessage = when {
                e.message?.contains("This operation is not allowed") == true ->
                    "Phone authentication not enabled. Please check Firebase configuration."
                e.message?.contains("invalid phone number") == true ->
                    "Invalid phone number format."
                e.message?.contains("too many requests") == true ->
                    "Too many attempts. Please try again later."
                else -> "Verification failed: ${e.message}"
            }

            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            super.onCodeSent(verificationId, token)
            this@LoginActivity.verificationId = verificationId

            // Navigate to OTP verification activity with required data
            val intent = Intent(this@LoginActivity, OtpVerificationActivity::class.java)
            intent.putExtra("phone_number", enteredPhoneNumber)
            intent.putExtra("verification_id", verificationId)
            startActivity(intent)

            // Reset UI state
            binding.btnSendOtp.isEnabled = true
            binding.btnSendOtp.text = "Send OTP"
            binding.etPhoneNumber.isEnabled = true

            // Enable OTP fields in case user comes back to this screen
            binding.etOtp.isEnabled = true
            binding.btnVerifyOtp.isEnabled = true

            Toast.makeText(this@LoginActivity, "OTP sent successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyCode(code: String) {
        if (verificationId == null) return

        binding.btnVerifyOtp.isEnabled = false
        binding.btnVerifyOtp.text = "Verifying..."

        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                // Reset button state
                binding.btnVerifyOtp.isEnabled = true
                binding.btnVerifyOtp.text = "Verify OTP"

                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                    // Check if it's a new user
                    if (task.result?.additionalUserInfo?.isNewUser == true) {
                        // Redirect to profile setup
                        val intent = Intent(this, UserProfileSetupActivity::class.java)
                        intent.putExtra("phone_number", enteredPhoneNumber)
                        startActivity(intent)
                    } else {
                        // Redirect to main activity
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                    finish()
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("invalid verification code") == true ->
                            "Invalid OTP. Please check and try again."
                        task.exception?.message?.contains("expired") == true ->
                            "OTP expired. Please request a new one."
                        else -> "Authentication failed: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

                    // Clear OTP field on error
                    binding.etOtp.text?.clear()
                    binding.etOtp.requestFocus()
                }
            }
    }
}