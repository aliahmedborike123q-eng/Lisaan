package com.lisaan.app.domain.repository

import android.app.Activity
import com.lisaan.app.core.common.Resource
import com.lisaan.app.core.database.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

data class SubscriptionPlanOffer(
    val planId: String,
    val titleAr: String,
    val titleEn: String,
    val priceFormatted: String,
    val billingPeriodAr: String,
    val billingPeriodEn: String,
    val badgeTextAr: String? = null,
    val badgeTextEn: String? = null,
    val isLifetime: Boolean = false
)

interface BillingRepository {
    val activeSubscriptionFlow: Flow<SubscriptionEntity?>
    fun getAvailablePlans(): List<SubscriptionPlanOffer>
    suspend fun launchPurchaseFlow(activity: Activity?, planId: String): Resource<SubscriptionEntity>
    suspend fun restorePurchases(): Resource<SubscriptionEntity>
}
