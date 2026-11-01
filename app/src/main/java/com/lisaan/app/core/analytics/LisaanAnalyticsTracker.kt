package com.lisaan.app.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanAnalyticsTracker @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap()) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putString(key, value.toString())
                }
            }
        }
        firebaseAnalytics.logEvent(eventName, bundle)
    }

    // Onboarding Funnel Events
    fun logOnboardingStep(stepName: String, selectionValue: String = "") {
        logEvent(
            "onboarding_step_completed",
            mapOf("step_name" to stepName, "selection" to selectionValue)
        )
    }

    fun logPlacementTestCompleted(score: Int, total: Int, assignedLevel: String) {
        logEvent(
            "placement_test_completed",
            mapOf("score" to score, "total" to total, "assigned_level" to assignedLevel)
        )
    }

    // Auth Funnel Events
    fun logAuthSuccess(method: String, isNewUser: Boolean) {
        logEvent(
            "auth_success",
            mapOf("method" to method, "is_new_user" to isNewUser)
        )
    }

    fun logAccountDeleted(userId: String) {
        logEvent("account_deleted", mapOf("user_id" to userId))
    }
}
