package com.example.stepbet.data.repositories

import com.example.stepbet.data.models.Transaction
import com.example.stepbet.data.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    companion object {
        private const val TAG = "UserRepository"
    }

    suspend fun getUserById(userId: String): User? {
        return try {
            android.util.Log.d(TAG, "Getting user by ID: $userId")
            val document = usersCollection.document(userId).get().await()
            val user = document.toObject<User>()
            android.util.Log.d(TAG, "User retrieved: ${user?.displayName}, balance: ${user?.walletBalance}")
            user
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting user: ${e.message}", e)
            null
        }
    }

    suspend fun getUserByPhoneNumber(phoneNumber: String): User? {
        return try {
            val querySnapshot = usersCollection
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) return null
            querySnapshot.documents[0].toObject<User>()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting user by phone: ${e.message}")
            null
        }
    }

    suspend fun createUser(user: User): Boolean {
        return try {
            usersCollection.document(user.id).set(user).await()
            android.util.Log.d(TAG, "User created successfully: ${user.id}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating user: ${e.message}")
            false
        }
    }

    suspend fun updateUser(user: User): Boolean {
        return try {
            usersCollection.document(user.id).set(user).await()
            android.util.Log.d(TAG, "User updated successfully: ${user.id}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating user: ${e.message}")
            false
        }
    }

    suspend fun getAllUsers(limit: Long = 100): List<User> {
        return try {
            val querySnapshot = db.collection("users")
                .limit(limit)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { it.toObject<User>() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting all users: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateWalletBalance(userId: String, newBalance: Double): Boolean {
        return try {
            android.util.Log.d(TAG, "Updating wallet balance for $userId to ₹$newBalance")
            usersCollection.document(userId)
                .update("walletBalance", newBalance)
                .await()
            android.util.Log.d(TAG, "Wallet balance updated successfully: $userId -> ₹$newBalance")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating wallet balance: ${e.message}", e)
            false
        }
    }

    suspend fun updateUserBalanceAndCreateTransaction(
        userId: String,
        transaction: Transaction,
        newBalance: Double
    ): Boolean {
        return try {
            android.util.Log.d(TAG, "Starting atomic transaction: userId=$userId, newBalance=₹$newBalance, transactionAmount=₹${transaction.amount}")

            val result = db.runTransaction { firestoreTransaction ->
                // Get user document reference
                val userRef = usersCollection.document(userId)

                // Read current user data
                val userSnapshot = firestoreTransaction.get(userRef)

                if (!userSnapshot.exists()) {
                    android.util.Log.e(TAG, "User document does not exist: $userId")
                    throw Exception("User does not exist: $userId")
                }

                val currentUser = userSnapshot.toObject<User>()
                android.util.Log.d(TAG, "Current user balance: ₹${currentUser?.walletBalance}")

                // Update user's wallet balance
                firestoreTransaction.update(userRef, "walletBalance", newBalance)
                android.util.Log.d(TAG, "Balance update queued in transaction")

                // Create transaction document
                val transactionRef = userRef.collection("wallet")
                    .document("transactions")
                    .collection("items")
                    .document(transaction.id)

                val transactionWithId = transaction.copy(id = transactionRef.id)
                firestoreTransaction.set(transactionRef, transactionWithId)
                android.util.Log.d(TAG, "Transaction creation queued: ${transactionWithId.id}")

                // Return success indicator
                true
            }.await()

            android.util.Log.d(TAG, "Atomic transaction completed successfully")
            result

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in atomic transaction: ${e.message}", e)

            // Log more details about the error
            when {
                e.message?.contains("PERMISSION_DENIED") == true -> {
                    android.util.Log.e(TAG, "Permission denied - check Firestore rules")
                }
                e.message?.contains("UNAVAILABLE") == true -> {
                    android.util.Log.e(TAG, "Firestore service unavailable - network issue")
                }
                e.message?.contains("NOT_FOUND") == true -> {
                    android.util.Log.e(TAG, "Document not found - user may not exist")
                }
                else -> {
                    android.util.Log.e(TAG, "Unknown error in atomic transaction")
                }
            }

            false
        }
    }
}