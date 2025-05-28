package com.example.stepbet.utils

/**
 * Constants used throughout the app
 */
object Constants {

    // Preferences
    const val PREFS_NAME = "step_bet_prefs"
    const val KEY_USER_ID = "user_id"
    const val KEY_PHONE_NUMBER = "phone_number"
    const val KEY_FIRST_TIME = "first_time"
    const val KEY_LAST_STEP_COUNT = "last_step_count"
    const val KEY_INITIAL_STEP_COUNT = "initial_step_count"
    const val KEY_FCM_TOKEN = "fcm_token"

    // Challenge Constants
    const val MIN_STEP_GOAL = 5000
    const val MAX_STEP_GOAL = 15000
    const val DEFAULT_STEP_GOAL = 8000

    const val MIN_STAKE_AMOUNT = 20.0
    const val MAX_STAKE_AMOUNT = 200.0
    const val DEFAULT_STAKE_AMOUNT = 50.0

    // Step Calculation Constants
    const val STEPS_UPDATE_INTERVAL_MS = 10000L // 10 seconds

    // Wallet Constants
    const val MIN_WITHDRAWAL_AMOUNT = 100.0
    const val MIN_DEPOSIT_AMOUNT = 100.0

    // Payment Constants
    const val RAZORPAY_KEY_ID = "rzp_test_JAM1iybr6gVUvR" // Replace with your key

    // Firebase Collections
    object FirestoreCollection {
        const val USERS = "users"
        const val ACTIVE_CHALLENGES = "activeChallenges"
        const val CHALLENGE_HISTORY = "challengeHistory"
        const val WALLET = "wallet"
        const val TRANSACTIONS = "transactions"
        const val ITEMS = "items"
        const val STEP_DATA = "stepData"
        const val DAILY = "daily"
        const val SETTINGS = "settings"
    }

    // Notification Topics
    object FirebaseTopics {
        const val ALL_USERS = "all_users"
        const val CHALLENGES = "challenges"
        const val WALLET = "wallet"
    }

    // Intent Extras
    const val EXTRA_CHALLENGE_ID = "challengeId"
    const val EXTRA_IS_HISTORY = "isHistory"
    const val EXTRA_NOTIFICATION_TYPE = "notificationType"

    // Request Codes
    const val REQUEST_ACTIVITY_RECOGNITION = 101
    const val REQUEST_IMAGE_PICK = 102

    // Reward Percentages
    fun getRewardPercentage(stepGoal: Int): Double {
        return when {
            stepGoal < 5000 -> 0.10
            stepGoal < 8000 -> 0.15
            stepGoal < 10000 -> 0.20
            else -> 0.25
        }
    }
}