package com.example.stepbet.data.repositories

import com.example.stepbet.data.models.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await

class TransactionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    companion object {
        private const val TAG = "TransactionRepository"
    }

    suspend fun createTransaction(transaction: Transaction): String? {
        return try {
            android.util.Log.d(TAG, "Creating transaction: ${transaction.id}")

            val userRef = usersCollection.document(transaction.userId)
            val newTransactionRef = userRef.collection("wallet")
                .document("transactions")
                .collection("items")
                .document(transaction.id) // Use provided ID

            newTransactionRef.set(transaction).await()

            android.util.Log.d(TAG, "Transaction created successfully: ${transaction.id}")
            transaction.id
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating transaction: ${e.message}", e)
            null
        }
    }

    suspend fun getUserTransactions(userId: String, limit: Long = 20): List<Transaction> {
        return try {
            android.util.Log.d(TAG, "Getting transactions for user: $userId, limit: $limit")

            val querySnapshot = usersCollection.document(userId)
                .collection("wallet")
                .document("transactions")
                .collection("items")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val transactions = querySnapshot.documents.mapNotNull {
                try {
                    it.toObject<Transaction>()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing transaction ${it.id}: ${e.message}")
                    null
                }
            }

            android.util.Log.d(TAG, "Retrieved ${transactions.size} transactions")
            transactions
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting user transactions: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createTransactionWithMetadata(transaction: Transaction, metadata: Map<String, Any>): String? {
        return try {
            android.util.Log.d(TAG, "Creating transaction with metadata: ${transaction.id}")

            val userRef = usersCollection.document(transaction.userId)
            val newTransactionRef = userRef.collection("wallet")
                .document("transactions")
                .collection("items")
                .document(transaction.id) // Use provided ID

            // Create a combined data map
            val combinedData = mutableMapOf<String, Any>().apply {
                put("id", transaction.id)
                put("userId", transaction.userId)
                put("type", transaction.type.toString())
                put("amount", transaction.amount)
                put("timestamp", transaction.timestamp)
                put("status", transaction.status.toString())
                transaction.razorpayTransactionId?.let { put("razorpayTransactionId", it) }
                put("metadata", metadata)
            }

            newTransactionRef.set(combinedData).await()

            android.util.Log.d(TAG, "Transaction with metadata created successfully: ${transaction.id}")
            transaction.id
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating transaction with metadata: ${e.message}", e)
            null
        }
    }

    suspend fun updateTransactionStatus(
        userId: String,
        transactionId: String,
        newStatus: com.example.stepbet.data.models.TransactionStatus
    ): Boolean {
        return try {
            android.util.Log.d(TAG, "Updating transaction status: $transactionId to $newStatus")

            usersCollection.document(userId)
                .collection("wallet")
                .document("transactions")
                .collection("items")
                .document(transactionId)
                .update("status", newStatus.toString()) // Convert enum to string
                .await()

            android.util.Log.d(TAG, "Transaction status updated successfully")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating transaction status: ${e.message}", e)
            false
        }
    }
}