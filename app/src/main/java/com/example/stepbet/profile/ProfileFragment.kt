package com.example.stepbet.profile

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.stepbet.auth.LoginActivity
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.models.ChallengeStatus
import com.example.stepbet.data.models.User
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val userRepository = UserRepository()
    private val firestore = FirebaseFirestore.getInstance()
    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up SwipeRefreshLayout
        binding.swipeRefresh.setOnRefreshListener {
            loadUserData()
        }

        // Set up click listeners
        binding.btnChangePhoto.setOnClickListener {
            // In a full implementation, we'd launch an image picker here
            Toast.makeText(requireContext(), "This feature will be implemented soon!", Toast.LENGTH_SHORT).show()
        }

        binding.layoutEditProfile.setOnClickListener {
            // In a full implementation, we'd navigate to EditProfileActivity
            Toast.makeText(requireContext(), "This feature will be implemented soon!", Toast.LENGTH_SHORT).show()
        }

        binding.layoutChallengeHistory.setOnClickListener {
            // In a full implementation, we'd navigate to a dedicated history screen
            Toast.makeText(requireContext(), "Challenge history is available in the Challenges tab!", Toast.LENGTH_SHORT).show()
        }

        binding.layoutNotifications.setOnClickListener {
            // In a full implementation, we'd navigate to a notifications screen
            Toast.makeText(requireContext(), "This feature will be implemented soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }

        // Load user data
        loadUserData()
    }

    private fun loadUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            binding.swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    // Load user data
                    currentUser = userRepository.getUserById(userId)

                    // Load challenge statistics
                    val challengeStats = loadChallengeStatistics(userId)

                    // Update UI on main thread
                    activity?.runOnUiThread {
                        currentUser?.let { user ->
                            updateUserUI(user)
                        }
                        updateStatisticsUI(challengeStats)
                        binding.swipeRefresh.isRefreshing = false
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            }
        }
    }

    private fun updateUserUI(user: User) {
        // Set user name and phone
        binding.tvName.text = user.displayName
        binding.tvPhone.text = user.phoneNumber

        // Set profile image if available
        if (user.profileImageBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(user.profileImageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.ivProfileImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // If there's an error decoding the image, just leave the default image
            }
        }

        // Set earnings
        binding.tvTotalEarnings.text = "₹${String.format("%.2f", user.totalEarnings)}"
    }

    private suspend fun loadChallengeStatistics(userId: String): Map<String, Any> {
        // Get all challenges from history
        val querySnapshot = firestore.collection("users")
            .document(userId)
            .collection("challengeHistory")
            .get()
            .await()

        val challenges = querySnapshot.documents.mapNotNull { it.toObject<Challenge>() }

        // Calculate statistics
        val totalChallenges = challenges.size
        val completedChallenges = challenges.count { it.status == ChallengeStatus.COMPLETED }
        val successRate = if (totalChallenges > 0) {
            (completedChallenges.toFloat() / totalChallenges) * 100
        } else {
            0f
        }

        return mapOf(
            "totalChallenges" to totalChallenges,
            "completedChallenges" to completedChallenges,
            "successRate" to successRate
        )
    }

    private fun updateStatisticsUI(stats: Map<String, Any>) {
        binding.tvChallengesCount.text = stats["totalChallenges"].toString()
        binding.tvSuccessRate.text = "${String.format("%.1f", stats["successRate"] as Float)}%"
    }

    private fun logout() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Navigate to login screen
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}