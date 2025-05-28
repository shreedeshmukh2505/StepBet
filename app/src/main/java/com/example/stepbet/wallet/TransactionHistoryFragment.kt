package com.example.stepbet.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stepbet.adapters.TransactionAdapter
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.databinding.FragmentTransactionHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class TransactionHistoryFragment : Fragment() {

    private var _binding: FragmentTransactionHistoryBinding? = null
    private val binding get() = _binding!!

    private val transactionRepository = TransactionRepository()
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        transactionAdapter = TransactionAdapter()
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }

        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadTransactions()
        }

        // Initial load
        loadTransactions()
    }

    private fun loadTransactions() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            binding.swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    // Get transactions from repository
                    val transactions = transactionRepository.getUserTransactions(userId, 50)

                    // Update UI on main thread
                    activity?.runOnUiThread {
                        if (transactions.isEmpty()) {
                            showEmptyState()
                        } else {
                            hideEmptyState()
                            transactionAdapter.setTransactions(transactions)
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

    private fun showEmptyState() {
        binding.rvTransactions.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.rvTransactions.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}