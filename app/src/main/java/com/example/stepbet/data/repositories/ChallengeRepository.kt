package com.example.stepbet.data.repositories

import com.example.stepbet.data.models.Challenge
import com.example.stepbet.data.models.ChallengeStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await

class ChallengeRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    companion object {
        private const val TAG = "ChallengeRepository"
    }

    suspend fun createChallenge(challenge: Challenge): String? {
        return try {
            android.util.Log.d(TAG, "Creating challenge for user: ${challenge.userId}")

            val userRef = usersCollection.document(challenge.userId)
            val newChallengeRef = userRef.collection("activeChallenges").document(challenge.id)

            // Use the provided challenge ID instead of generating a new one
            android.util.Log.d(TAG, "Challenge document ID: ${challenge.id}")

            newChallengeRef.set(challenge).await()

            android.util.Log.d(TAG, "Challenge created successfully: ${challenge.id}")
            challenge.id

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating challenge: ${e.message}", e)

            // Log specific error details
            when {
                e.message?.contains("PERMISSION_DENIED") == true -> {
                    android.util.Log.e(TAG, "Permission denied - check Firestore rules for activeChallenges collection")
                }
                e.message?.contains("UNAVAILABLE") == true -> {
                    android.util.Log.e(TAG, "Firestore service unavailable - network issue")
                }
                e.message?.contains("NOT_FOUND") == true -> {
                    android.util.Log.e(TAG, "User document not found: ${challenge.userId}")
                }
                else -> {
                    android.util.Log.e(TAG, "Unknown error creating challenge")
                }
            }

            null
        }
    }

    suspend fun getActiveChallenge(userId: String, challengeId: String): Challenge? {
        return try {
            android.util.Log.d(TAG, "Getting active challenge: $challengeId for user: $userId")

            val document = usersCollection.document(userId)
                .collection("activeChallenges")
                .document(challengeId)
                .get()
                .await()

            val challenge = if (document.exists()) {
                document.toObject<Challenge>()
            } else {
                android.util.Log.w(TAG, "Challenge document not found: $challengeId")
                null
            }

            android.util.Log.d(TAG, "Challenge retrieved: ${challenge?.id}")
            challenge

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting active challenge: ${e.message}")
            null
        }
    }

    suspend fun getActiveUserChallenges(userId: String): List<Challenge> {
        return try {
            android.util.Log.d(TAG, "Getting active challenges for user: $userId")

            // First, try to get all challenges and filter manually
            val allChallengesQuery = usersCollection.document(userId)
                .collection("activeChallenges")
                .get()
                .await()

            android.util.Log.d(TAG, "Found ${allChallengesQuery.documents.size} total challenge documents")

            val challenges = allChallengesQuery.documents.mapNotNull { document ->
                try {
                    val challenge = document.toObject<Challenge>()
                    android.util.Log.d(TAG, "Challenge parsed: ${challenge?.id}, status: ${challenge?.status}, targetSteps: ${challenge?.targetSteps}")

                    // Filter for ACTIVE status manually since Firestore enum queries can be tricky
                    if (challenge?.status == ChallengeStatus.ACTIVE) {
                        challenge
                    } else {
                        android.util.Log.d(TAG, "Filtering out non-active challenge: ${challenge?.id} with status: ${challenge?.status}")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing challenge document ${document.id}: ${e.message}")
                    null
                }
            }

            // Sort by start time (newest first)
            val sortedChallenges = challenges.sortedByDescending { it.startTime.seconds }

            android.util.Log.d(TAG, "Retrieved ${sortedChallenges.size} ACTIVE challenges after filtering")
            sortedChallenges.forEachIndexed { index, challenge ->
                android.util.Log.d(TAG, "Active Challenge $index: ID=${challenge.id}, Steps=${challenge.targetSteps}, Stake=₹${challenge.amountStaked}")
            }

            sortedChallenges

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting active user challenges: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getAllUserChallenges(userId: String): List<Challenge> {
        return try {
            android.util.Log.d(TAG, "Getting ALL challenges for user: $userId")

            val querySnapshot = usersCollection.document(userId)
                .collection("activeChallenges")
                .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val challenges = querySnapshot.documents.mapNotNull { document ->
                try {
                    document.toObject<Challenge>()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing challenge document ${document.id}: ${e.message}")
                    null
                }
            }

            android.util.Log.d(TAG, "Retrieved ${challenges.size} total challenges")
            challenges

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting all user challenges: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateChallengeSteps(userId: String, challengeId: String, steps: Int): Boolean {
        return try {
            android.util.Log.d(TAG, "Updating challenge steps: $challengeId, steps: $steps")

            usersCollection.document(userId)
                .collection("activeChallenges")
                .document(challengeId)
                .update("currentSteps", steps)
                .await()

            android.util.Log.d(TAG, "Challenge steps updated successfully")
            true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating challenge steps: ${e.message}")
            false
        }
    }

    suspend fun completeChallenge(userId: String, challengeId: String,
                                  finalSteps: Int, status: ChallengeStatus,
                                  rewardAmount: Double = 0.0): Boolean {
        return try {
            android.util.Log.d(TAG, "Completing challenge: $challengeId, status: $status, reward: ₹$rewardAmount")

            val userRef = usersCollection.document(userId)
            val challengeRef = userRef.collection("activeChallenges").document(challengeId)

            // Get the challenge
            val challengeDoc = challengeRef.get().await()
            val challenge = challengeDoc.toObject<Challenge>()

            if (challenge == null) {
                android.util.Log.e(TAG, "Challenge not found: $challengeId")
                return false
            }

            // Update challenge status
            val updatedChallenge = challenge.copy(
                currentSteps = finalSteps,
                status = status,
                rewardAmount = rewardAmount
            )

            // Add to history
            userRef.collection("challengeHistory")
                .document(challengeId)
                .set(updatedChallenge)
                .await()

            // Remove from active challenges
            challengeRef.delete().await()

            android.util.Log.d(TAG, "Challenge completed and moved to history")
            true

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error completing challenge: ${e.message}")
            false
        }
    }
}