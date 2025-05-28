package com.example.stepbet.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.stepbet.MainActivity
import com.example.stepbet.R
import com.example.stepbet.data.repositories.ChallengeRepository
import com.example.stepbet.data.repositories.StepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class StepCounterService : Service(), SensorEventListener {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val stepCount = AtomicInteger(0)
    private val initialStepCount = AtomicInteger(-1)

    private val stepRepository = StepRepository()
    private val challengeRepository = ChallengeRepository()

    private var userId: String? = null
    private var activeChallengeId: String? = null

    // For binding the service to activities
    private val binder = StepServiceBinder()

    inner class StepServiceBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }

    companion object {
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "step_counter_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize the step sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("userId")
        activeChallengeId = intent?.getStringExtra("challengeId")

        // Check if step sensor is available
        if (stepSensor == null) {
            android.util.Log.w("StepCounterService", "Step counter sensor not available")
            stopSelf()
            return START_NOT_STICKY
        }

        // Register the step counter sensor
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start as a foreground service with proper notification
        try {
            startForeground(NOTIFICATION_ID, createNotification(0))
        } catch (e: Exception) {
            android.util.Log.e("StepCounterService", "Failed to start foreground service: ${e.message}")
            // If foreground service fails, stop the service
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val steps = it.values[0].toInt()

                // Initialize counter if it's the first reading
                if (initialStepCount.get() == -1) {
                    initialStepCount.set(steps)

                    // Check if we have steps from earlier today
                    scope.launch {
                        userId?.let { uid ->
                            val todaySteps = stepRepository.getTodaySteps(uid)
                            stepCount.set(todaySteps)

                            // Update the notification
                            updateNotification(todaySteps)
                        }
                    }
                } else {
                    // Calculate steps taken since service started
                    val stepsSinceReboot = steps - initialStepCount.get()
                    val totalSteps = stepCount.get() + stepsSinceReboot

                    // Update the notification
                    updateNotification(totalSteps)

                    // Save steps periodically (every 100 steps to reduce database calls)
                    if (totalSteps % 100 == 0) {
                        scope.launch {
                            userId?.let { uid ->
                                stepRepository.saveSteps(uid, totalSteps)

                                // Update active challenge if any
                                activeChallengeId?.let { challengeId ->
                                    challengeRepository.updateChallengeSteps(uid, challengeId, totalSteps)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step counter
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your steps throughout the day"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(steps: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StepBet")
            .setContentText("Steps today: $steps")
            .setSmallIcon(android.R.drawable.ic_media_play) // Using system icon for now
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(steps: Int) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification(steps))
        } catch (e: Exception) {
            android.util.Log.e("StepCounterService", "Failed to update notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Save final step count before service stops
        scope.launch {
            userId?.let { uid ->
                val finalSteps = stepCount.get()
                stepRepository.saveSteps(uid, finalSteps)
            }
        }

        sensorManager.unregisterListener(this)
        job.cancel()
    }

    fun getCurrentSteps(): Int {
        return stepCount.get()
    }
}