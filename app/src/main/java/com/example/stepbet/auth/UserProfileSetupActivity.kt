package com.example.stepbet.auth

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stepbet.MainActivity
import com.example.stepbet.data.models.User
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.ActivityUserProfileSetupBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class UserProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileSetupBinding
    private val userRepository = UserRepository()

    private var selectedImageBitmap: Bitmap? = null
    private var phoneNumber: String = ""

    // Activity result launchers
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get phone number from intent
        phoneNumber = intent.getStringExtra("phone_number") ?: ""

        // Initialize activity result launchers
        initializeActivityLaunchers()

        // Set up click listeners
        setupClickListeners()
    }

    private fun initializeActivityLaunchers() {
        // Gallery launcher
        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleImageSelection(uri)
                }
            }
        }

        // Camera launcher
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                imageBitmap?.let {
                    selectedImageBitmap = it
                    binding.ivProfileImage.setImageBitmap(it)
                }
            }
        }

        // Permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                showImageSelectionDialog()
            } else {
                Toast.makeText(this, "Permissions required to select image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.ivProfileImage.setOnClickListener {
            checkPermissionsAndSelectImage()
        }

        binding.btnChangePhoto.setOnClickListener {
            checkPermissionsAndSelectImage()
        }

        binding.btnCreateProfile.setOnClickListener {
            validateAndCreateProfile()
        }
    }

    private fun checkPermissionsAndSelectImage() {
        val requiredPermissions = mutableListOf<String>()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }

        // Check storage permission based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            showImageSelectionDialog()
        }
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Camera", "Gallery")

        AlertDialog.Builder(this)
            .setTitle("Select Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            cameraLauncher.launch(cameraIntent)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryIntent.type = "image/*"
        galleryLauncher.launch(galleryIntent)
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            selectedImageBitmap = bitmap
            binding.ivProfileImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndCreateProfile() {
        val displayName = binding.etDisplayName.text.toString().trim()

        // Validation
        if (displayName.isEmpty()) {
            binding.etDisplayName.error = "Please enter your name"
            binding.etDisplayName.requestFocus()
            return
        }

        if (displayName.length < 2) {
            binding.etDisplayName.error = "Name must be at least 2 characters"
            binding.etDisplayName.requestFocus()
            return
        }

        // Clear error
        binding.etDisplayName.error = null

        // Create profile
        createUserProfile(displayName)
    }

    private fun createUserProfile(displayName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Authentication error. Please login again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Show loading
        binding.btnCreateProfile.isEnabled = false
        binding.btnCreateProfile.text = "Creating..."

        lifecycleScope.launch {
            try {
                // Convert image to base64 if selected
                val profileImageBase64 = selectedImageBitmap?.let { bitmap ->
                    convertBitmapToBase64(bitmap)
                } ?: ""

                // Create user object
                val user = User(
                    id = userId,
                    phoneNumber = phoneNumber,
                    displayName = displayName,
                    createdAt = Timestamp.now(),
                    totalEarnings = 0.0,
                    totalLosses = 0.0,
                    walletBalance = 0.0,
                    profileImageBase64 = profileImageBase64
                )

                // Save to Firestore
                val success = userRepository.createUser(user)

                if (success) {
                    Toast.makeText(this@UserProfileSetupActivity,
                        "Profile created successfully!", Toast.LENGTH_SHORT).show()

                    // Navigate to main activity
                    val intent = Intent(this@UserProfileSetupActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    throw Exception("Failed to create user profile")
                }

            } catch (e: Exception) {
                Toast.makeText(this@UserProfileSetupActivity,
                    "Error creating profile: ${e.message}", Toast.LENGTH_LONG).show()

                // Reset button
                binding.btnCreateProfile.isEnabled = true
                binding.btnCreateProfile.text = "Create Profile"
            }
        }
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        return try {
            // Resize bitmap to reasonable size to avoid large base64 strings
            val resizedBitmap = resizeBitmap(bitmap, 300, 300)

            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val aspectRatio = width.toFloat() / height.toFloat()

        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxWidth
            newHeight = (maxWidth / aspectRatio).toInt()
        } else {
            newHeight = maxHeight
            newWidth = (maxHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}