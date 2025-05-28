package com.example.stepbet.services

import android.content.Context
import androidx.work.*
import com.example.stepbet.data.models.ChallengeStatus
import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.TransactionStatus
import com.example.stepbet.data.models.TransactionType
import com.example.stepbet.data.models.User
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.data.repositories.StepRepository
import com.example.stepbet.data.repositories.TransactionRepository
import com.example.stepbet.data.repositories.UserRepository
import com.google.firebase.Timestamp
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.coroutineScope

class ChallengeEvaluationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val challengeRepository = ChallengeRepository()
    private val stepRepository = StepRepository()
    private val userRepository = UserRepository()
    private val transactionRepository = TransactionRepository()

    override suspend fun doWork(): Result = coroutineScope {
        try {
            // Get all users (this is simplified - in a real app, you'd use paging)
            val users = userRepository.getAllUsers(100)

            for (user in users) {
                // Get active challenges for user
                val challenges = challengeRepository.getActiveUserChallenges(user.id)

                for (challenge in challenges) {
                    // Check if challenge is expired (end time has passed)
                    if (Timestamp.now().seconds > challenge.endTime.seconds) {
                        // Get current steps
                        val steps = stepRepository.getTodaySteps(user.id)

                        // Determine if challenge is completed
                        val isCompleted = steps >= challenge.targetSteps

                        if (isCompleted) {
                            // Calculate reward
                            val rewardPercentage = when {
                                challenge.targetSteps < 5000 -> 0.10
                                challenge.targetSteps < 8000 -> 0.15
                                challenge.targetSteps < 10000 -> 0.20
                                else -> 0.25
                            }

                            val rewardAmount = challenge.amountStaked * (1 + rewardPercentage)

                            // Complete challenge with success
                            challengeRepository.completeChallenge(
                                user.id,
                                challenge.id,
                                steps,
                                ChallengeStatus.COMPLETED,
                                rewardAmount
                            )

                            // Create reward transaction
                            val transaction = Transaction(
                                id = UUID.randomUUID().toString(),
                                userId = user.id,
                                type = TransactionType.REWARD,
                                amount = rewardAmount,
                                timestamp = Timestamp.now(),
                                status = TransactionStatus.COMPLETED
                            )

                            transactionRepository.createTransaction(transaction)

                            // Update user wallet balance
                            val newBalance = user.walletBalance + rewardAmount
                            userRepository.updateWalletBalance(user.id, newBalance)

                            // Update user total earnings
                            val newTotalEarnings = user.totalEarnings + rewardAmount - challenge.amountStaked
                            userRepository.updateUser(user.copy(totalEarnings = newTotalEarnings))
                        } else {
                            // Complete challenge with failure
                            challengeRepository.completeChallenge(
                                user.id,
                                challenge.id,
                                steps,
                                ChallengeStatus.FAILED
                            )

                            // Update user total losses
                            val newTotalLosses = user.totalLosses + challenge.amountStaked
                            userRepository.updateUser(user.copy(totalLosses = newTotalLosses))
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ChallengeEvaluationWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "challenge_evaluation",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}