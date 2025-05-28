package com.example.stepbet.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.stepbet.MainActivity
import com.example.stepbet.R

object NotificationUtils {

    private const val CHANNEL_ID_DEFAULT = "stepbet_default_channel"
    private const val CHANNEL_ID_CHALLENGE = "stepbet_challenge_channel"
    private const val CHANNEL_ID_WALLET = "stepbet_wallet_channel"

    /**
     * Create all notification channels for the app
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Default channel
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }

            // Challenge channel
            val challengeChannel = NotificationChannel(
                CHANNEL_ID_CHALLENGE,
                "Challenge Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about your challenges"
            }

            // Wallet channel
            val walletChannel = NotificationChannel(
                CHANNEL_ID_WALLET,
                "Wallet Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about your wallet and transactions"
            }

            // Create all channels
            notificationManager.createNotificationChannels(
                listOf(defaultChannel, challengeChannel, walletChannel)
            )
        }
    }

    /**
     * Show a notification with the specified title and message
     */
    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationType: NotificationType = NotificationType.DEFAULT,
        data: Map<String, String> = emptyMap()
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // Add data to the intent if provided
            data.forEach { (key, value) ->
                putExtra(key, value)
            }

            // Set notification type
            putExtra("notification_type", notificationType.name)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = when (notificationType) {
            NotificationType.CHALLENGE -> CHANNEL_ID_CHALLENGE
            NotificationType.WALLET -> CHANNEL_ID_WALLET
            else -> CHANNEL_ID_DEFAULT
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure notification channels exist for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager.getNotificationChannel(channelId) == null) {
            createNotificationChannels(context)
        }

        // Use a unique ID for each notification
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Cancel all notifications for the app
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    enum class NotificationType {
        DEFAULT,
        CHALLENGE,
        WALLET
    }
}