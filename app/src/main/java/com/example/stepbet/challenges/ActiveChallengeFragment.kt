package com.example.stepbet.challenges

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stepbet.R
import com.example.stepbet.adapters.ChallengeAdapter
import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.databinding.FragmentActiveChallengeBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ActiveChallengeFragment : Fragment() {

    private var _binding: FragmentActiveChallengeBinding? = null
    private val binding get() = _binding!!

    private val challengeRepository = ChallengeRepository()
    private lateinit var challengeAdapter: ChallengeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        challengeAdapter = ChallengeAdapter { challenge ->
            // Navigate to challenge details
            navigateToChallengeDetails(challenge)
        }

        binding.rvActiveChallenges.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = challengeAdapter
        }

        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadActiveChallenges()
        }

        // Set up create challenge button
        binding.btnCreateChallenge.setOnClickListener {
            findNavController().navigate(R.id.action_activeChallengeFragment_to_createChallengeFragment)
        }

        // Load active challenges
        loadActiveChallenges()
    }

    private fun loadActiveChallenges() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            binding.swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    val challenges = challengeRepository.getActiveUserChallenges(userId)

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
        binding.rvActiveChallenges.visibility = View.VISIBLE
        challengeAdapter.setChallenges(challenges)
    }

    private fun showEmptyState() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.rvActiveChallenges.visibility = View.GONE
    }

    private fun navigateToChallengeDetails(challenge: Challenge) {
        val intent = Intent(requireContext(), ChallengeDetailsActivity::class.java).apply {
            putExtra("challengeId", challenge.id)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Reload challenges when returning to fragment
        loadActiveChallenges()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}