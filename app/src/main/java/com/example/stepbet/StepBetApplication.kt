package com.example.stepbet

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.stepbet.services.ChallengeEvaluationWorker
import com.google.firebase.FirebaseApp
import com.razorpay.Checkout

class StepBetApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase first
        FirebaseApp.initializeApp(this)

        // Initialize WorkManager manually
        WorkManager.initialize(this, workManagerConfiguration)

        // Initialize RazorPay
        Checkout.preload(applicationContext)

        // Schedule challenge evaluation worker AFTER WorkManager is initialized
        ChallengeEvaluationWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}