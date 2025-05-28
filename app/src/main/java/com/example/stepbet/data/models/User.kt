package com.example.stepbet.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId val id: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val totalEarnings: Double = 0.0,
    val totalLosses: Double = 0.0,
    val walletBalance: Double = 0.0,
    val profileImageBase64: String = ""
)
