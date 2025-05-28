package com.example.stepbet.data.models

import com.google.firebase.Timestamp

data class Challenge(
    val id: String = "",
    val userId: String = "",
    val targetSteps: Int = 0,
    val amountStaked: Double = 0.0,
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp = Timestamp.now(),
    val currentSteps: Int = 0,
    val status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val rewardAmount: Double = 0.0
)

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    FAILED
}