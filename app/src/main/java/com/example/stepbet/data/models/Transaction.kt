package com.example.stepbet.data.models

import com.google.firebase.Timestamp

data class Transaction(
    val id: String = "",
    val userId: String = "",
    val type: TransactionType = TransactionType.DEPOSIT,
    val amount: Double = 0.0,
    val timestamp: Timestamp = Timestamp.now(),
    val status: TransactionStatus = TransactionStatus.PENDING,
    val razorpayTransactionId: String? = null
)

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    STAKE,
    REWARD
}

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}