package com.example.stepbet.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class StepRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun saveSteps(userId: String, steps: Int): Boolean {
        return try {
            val today = dateFormat.format(Date())

            usersCollection.document(userId)
                .collection("stepData")
                .document("daily")
                .update(mapOf(today to steps))
                .await()

            true
        } catch (e: Exception) {
            try {
                // If update fails (document might not exist), try set
                val today = dateFormat.format(Date())

                usersCollection.document(userId)
                    .collection("stepData")
                    .document("daily")
                    .set(mapOf(today to steps))
                    .await()

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getTodaySteps(userId: String): Int {
        return try {
            val today = dateFormat.format(Date())

            val document = usersCollection.document(userId)
                .collection("stepData")
                .document("daily")
                .get()
                .await()

            val data = document.data
            if (data != null && data.containsKey(today)) {
                (data[today] as Long).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getStepsForDate(userId: String, date: Date): Int {
        return try {
            val dateString = dateFormat.format(date)

            val document = usersCollection.document(userId)
                .collection("stepData")
                .document("daily")
                .get()
                .await()

            val data = document.data
            if (data != null && data.containsKey(dateString)) {
                (data[dateString] as Long).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
}