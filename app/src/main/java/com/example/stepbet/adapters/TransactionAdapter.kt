package com.example.stepbet.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.stepbet.R
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.TransactionStatus
import com.example.stepbet.data.models.TransactionType
import com.example.stepbet.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<Transaction>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    fun setTransactions(newTransactions: List<Transaction>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            // Set transaction type and icon
            when (transaction.type) {
                TransactionType.DEPOSIT -> {
                    binding.tvTransactionTitle.text = "Added Money"
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_deposit)
                    binding.tvTransactionAmount.text = "+₹${transaction.amount}"
                    binding.tvTransactionAmount.setTextColor(binding.root.context.getColor(R.color.green_500))
                }
                TransactionType.WITHDRAWAL -> {
                    binding.tvTransactionTitle.text = "Withdrawal"
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_withdraw)
                    binding.tvTransactionAmount.text = "-₹${transaction.amount}"
                    binding.tvTransactionAmount.setTextColor(binding.root.context.getColor(R.color.red_500))
                }
                TransactionType.STAKE -> {
                    binding.tvTransactionTitle.text = "Challenge Stake"
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_stake)
                    binding.tvTransactionAmount.text = "-₹${transaction.amount}"
                    binding.tvTransactionAmount.setTextColor(binding.root.context.getColor(R.color.red_500))
                }
                TransactionType.REWARD -> {
                    binding.tvTransactionTitle.text = "Challenge Reward"
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_reward)
                    binding.tvTransactionAmount.text = "+₹${transaction.amount}"
                    binding.tvTransactionAmount.setTextColor(binding.root.context.getColor(R.color.green_500))
                }
            }

            // Set transaction date
            val date = transaction.timestamp.toDate()
            binding.tvTransactionDate.text = dateFormat.format(date)

            // Set status badge
            when (transaction.status) {
                TransactionStatus.PENDING -> {
                    binding.tvTransactionStatus.text = "Pending"
                    binding.tvTransactionStatus.setBackgroundResource(R.drawable.bg_status_pending)
                }
                TransactionStatus.COMPLETED -> {
                    binding.tvTransactionStatus.text = "Completed"
                    binding.tvTransactionStatus.setBackgroundResource(R.drawable.bg_status_completed)
                }
                TransactionStatus.FAILED -> {
                    binding.tvTransactionStatus.text = "Failed"
                    binding.tvTransactionStatus.setBackgroundResource(R.drawable.bg_status_failed)
                }
            }
        }
    }
}