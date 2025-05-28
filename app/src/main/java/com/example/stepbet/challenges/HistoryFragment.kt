package com.example.stepbet.challenges

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stepbet.adapters.ChallengeAdapter
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var challengeAdapter: ChallengeAdapter
    private val firestore = FirebaseFirestore.getInstance()



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        challengeAdapter = ChallengeAdapter { challenge ->
            // Navigate to challenge details
            val intent = Intent(requireContext(), ChallengeDetailsActivity::class.java).apply {
                putExtra("challengeId", challenge.id)
                putExtra("isHistory", true)
            }
            startActivity(intent)
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = challengeAdapter
        }

        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadChallengeHistory()
        }

        // Load challenge history
        loadChallengeHistory()
    }

    private fun loadChallengeHistory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            binding.swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    // Get challenge history from Firestore
                    val querySnapshot = firestore.collection("users")
                        .document(userId)
                        .collection("challengeHistory")
                        .orderBy("endTime", Query.Direction.DESCENDING)
                        .get()
                        .await()

                    val challenges = querySnapshot.documents.mapNotNull { it.toObject<Challenge>() }

                    // Update UI on main thread
                    activity?.runOnUiThread {
                        if (challenges.isEmpty()) {
                            showEmptyState()
                        } else {
                            showChallenges(challenges)
                        }
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

    private fun showChallenges(challenges: List<Challenge>) {
        binding.layoutEmpty.visibility = View.GONE
        binding.rvHistory.visibility = View.VISIBLE
        challengeAdapter.setChallenges(challenges)

        // Update statistics
        val totalChallenges = challenges.size
        val completedChallenges = challenges.count { it.currentSteps >= it.targetSteps }
        val successRate = if (totalChallenges > 0) {
            (completedChallenges.toFloat() / totalChallenges) * 100
        } else {
            0f
        }

        binding.tvTotalChallenges.text = totalChallenges.toString()
        binding.tvCompletedChallenges.text = completedChallenges.toString()
        binding.tvSuccessRate.text = "${String.format("%.1f", successRate)}%"
    }

    private fun showEmptyState() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.rvHistory.visibility = View.GONE

        // Reset statistics
        binding.tvTotalChallenges.text = "0"
        binding.tvCompletedChallenges.text = "0"
        binding.tvSuccessRate.text = "0%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}