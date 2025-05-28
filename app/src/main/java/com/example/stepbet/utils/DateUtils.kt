package com.example.stepbet.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {

    private val defaultDateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun formatTimestamp(timestamp: Timestamp, pattern: String? = null): String {
        val date = timestamp.toDate()
        return if (pattern != null) {
            val customFormat = SimpleDateFormat(pattern, Locale.getDefault())
            customFormat.format(date)
        } else {
            defaultDateFormat.format(date)
        }
    }

    fun formatShortDate(date: Date): String {
        return shortDateFormat.format(date)
    }

    fun formatTime(date: Date): String {
        return timeFormat.format(date)
    }

    fun getTimeRemaining(endTimestamp: Timestamp): String {
        val endTimeMillis = endTimestamp.toDate().time
        val currentTimeMillis = System.currentTimeMillis()

        if (currentTimeMillis >= endTimeMillis) {
            return "Time's up"
        }

        val diffMillis = endTimeMillis - currentTimeMillis
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60

        return when {
            hours > 0 -> "$hours hrs ${minutes} mins remaining"
            minutes > 0 -> "$minutes mins remaining"
            else -> "Less than a minute remaining"
        }
    }

    fun isTimestampExpired(timestamp: Timestamp): Boolean {
        return timestamp.toDate().time < System.currentTimeMillis()
    }

    fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun addDaysToDate(date: Date, days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return calendar.time
    }

    fun dateToTimestamp(date: Date): Timestamp {
        return Timestamp(date)
    }
}

