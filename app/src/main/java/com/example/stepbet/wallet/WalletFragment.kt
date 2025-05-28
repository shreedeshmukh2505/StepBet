package com.example.stepbet.wallet

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stepbet.adapters.TransactionAdapter
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.data.repositories.UserRepository
import com.example.stepbet.databinding.FragmentWalletBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!

    private val userRepository = UserRepository()
    private val transactionRepository = TransactionRepository()
    private lateinit var transactionAdapter: TransactionAdapter

    // Activity result launcher for AddMoneyActivity
    private val addMoneyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Reload wallet data after successful payment
            loadWalletData()
        }
    }

    // Activity result launcher for WithdrawActivity
    private val withdrawLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Reload wallet data after successful withdrawal
            loadWalletData()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        loadWalletData()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter()
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun setupClickListeners() {
        // Set up refresh listener
        binding.swipeRefresh.setOnRefreshListener {
            loadWalletData()
        }

        // Set up add money button
        binding.btnAddMoney.setOnClickListener {
            val intent = Intent(requireContext(), AddMoneyActivity::class.java)
            addMoneyLauncher.launch(intent)
        }

        // Set up withdraw button
        binding.btnWithdraw.setOnClickListener {
            val intent = Intent(requireContext(), WithdrawActivity::class.java)
            withdrawLauncher.launch(intent)
        }
    }

    private fun loadWalletData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            lifecycleScope.launch {
                try {
                    // Show loading state
                    binding.swipeRefresh.isRefreshing = true

                    // Load user data for wallet balance
                    val user = userRepository.getUserById(userId)

                    // Update UI with wallet balance
                    user?.let {
                        binding.tvWalletBalance.text = "₹${String.format("%.2f", it.walletBalance)}"
                        android.util.Log.d("WalletFragment", "Wallet balance loaded: ₹${it.walletBalance}")
                    } ?: run {
                        binding.tvWalletBalance.text = "₹0.00"
                        android.util.Log.w("WalletFragment", "User not found, setting balance to 0")
                    }

                    // Load transactions
                    val transactions = transactionRepository.getUserTransactions(userId, 20)
                    android.util.Log.d("WalletFragment", "Loaded ${transactions.size} transactions")

                    // Update RecyclerView with transactions
                    transactionAdapter.setTransactions(transactions)

                    // Update empty state visibility
                    updateEmptyState(transactions)

                } catch (e: Exception) {
                    android.util.Log.e("WalletFragment", "Error loading wallet data: ${e.message}")
                    binding.tvWalletBalance.text = "₹0.00"
                    updateEmptyState(emptyList())
                } finally {
                    // Hide loading state
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        } else {
            android.util.Log.e("WalletFragment", "User not logged in")
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun updateEmptyState(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvTransactions.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload data when returning to fragment
        loadWalletData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}