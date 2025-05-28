package com.example.stepbet.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ChallengeEvaluationService : Service() {

    companion object {
        private const val TAG = "ChallengeEvaluationService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Schedule periodic work to evaluate challenges
            scheduleChallengeEvaluation()

            Log.d(TAG, "Challenge evaluation scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling challenge evaluation: ${e.message}", e)
        }

        // This service doesn't need to run continuously, so stop it after scheduling the work
        stopSelf()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service is not meant to be bound
        return null
    }

    private fun scheduleChallengeEvaluation() {
        try {
            // Check if WorkManager is initialized
            val workManager = WorkManager.getInstance(applicationContext)

            // Schedule the worker to run every 1 hour
            val workRequest = PeriodicWorkRequestBuilder<ChallengeEvaluationWorker>(
                1, TimeUnit.HOURS
            ).build()

            // Enqueue the work request
            workManager.enqueueUniquePeriodicWork(
                "challenge_evaluation",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "WorkManager task scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager task: ${e.message}", e)
            throw e
        }
    }
}