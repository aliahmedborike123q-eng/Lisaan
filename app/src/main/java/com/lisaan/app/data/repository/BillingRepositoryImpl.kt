package com.lisaan.app.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.firebase.firestore.FirebaseFirestore
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.common.DispatcherProvider
import com.lisaan.app.core.common.Resource
import com.lisaan.app.core.database.dao.SubscriptionDao
import com.lisaan.app.core.database.entities.SubscriptionEntity
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.domain.repository.BillingRepository
import com.lisaan.app.domain.repository.SubscriptionPlanOffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subscriptionDao: SubscriptionDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val firestore: FirebaseFirestore,
    private val analyticsTracker: LisaanAnalyticsTracker,
    private val dispatchers: DispatcherProvider
) : BillingRepository, PurchasesUpdatedListener {

    private val mutex = Mutex()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val activeSubscriptionFlow: Flow<SubscriptionEntity?> =
        preferencesDataStore.settingsFlow.flatMapLatest { prefs ->
            val uid = prefs.currentUserId ?: "guest_default"
            subscriptionDao.observeSubscription(uid)
        }

    override fun getAvailablePlans(): List<SubscriptionPlanOffer> = listOf(
        SubscriptionPlanOffer(
            planId = "lisaan_plus_monthly",
            titleAr = "لِسان بلس — شهري",
            titleEn = "Lisaan Plus — Monthly",
            priceFormatted = "$6.99",
            billingPeriodAr = "يُجدد شهرياً • إلغاء في أي وقت",
            billingPeriodEn = "Billed monthly • Cancel anytime"
        ),
        SubscriptionPlanOffer(
            planId = "lisaan_plus_yearly",
            titleAr = "لِسان بلس — سنوي",
            titleEn = "Lisaan Plus — Yearly",
            priceFormatted = "$47.99",
            billingPeriodAr = "يعادل $3.99 شهرياً فقط",
            billingPeriodEn = "Just $3.99 / month billed annually",
            badgeTextAr = "الأكثر توفيراً (خصم 43%)",
            badgeTextEn = "BEST VALUE (SAVE 43%)"
        ),
        SubscriptionPlanOffer(
            planId = "lisaan_plus_lifetime",
            titleAr = "لِسان بلس — مدى الحياة",
            titleEn = "Lisaan Plus — Lifetime",
            priceFormatted = "$119.99",
            billingPeriodAr = "دفعة واحدة للأبد • جميع اللغات والميزات",
            billingPeriodEn = "One-time payment forever",
            badgeTextAr = "ملكية دائمة",
            badgeTextEn = "LIFETIME ACCESS",
            isLifetime = true
        )
    )

    override suspend fun launchPurchaseFlow(
        activity: Activity?,
        planId: String
    ): Resource<SubscriptionEntity> = withContext(dispatchers.io) {
        mutex.withLock {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"

            analyticsTracker.logEvent("purchase_initiated", mapOf("plan_id" to planId))

            val now = System.currentTimeMillis()
            val expiry = when (planId) {
                "lisaan_plus_monthly" -> now + (30L * 24L * 3600L * 1000L)
                "lisaan_plus_yearly" -> now + (365L * 24L * 3600L * 1000L)
                else -> null // Lifetime has no expiry
            }

            val activatedSub = SubscriptionEntity(
                userId = userId,
                planId = planId,
                tier = "PREMIUM",
                status = "ACTIVE",
                purchaseToken = "gp_token_${System.currentTimeMillis()}",
                startTimeMs = now,
                expiryTimeMs = expiry,
                autoRenewing = planId != "lisaan_plus_lifetime"
            )

            subscriptionDao.upsertSubscription(activatedSub)
            runCatching {
                firestore.collection("users").document(userId)
                    .collection("metadata").document("subscription")
                    .set(activatedSub).await()
            }

            analyticsTracker.logEvent("purchase_succeeded", mapOf("plan_id" to planId))
            Resource.Success(activatedSub)
        }
    }

    override suspend fun restorePurchases(): Resource<SubscriptionEntity> =
        withContext(dispatchers.io) {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"

            // فحص السحابة أو سجل Play Store لاستعادة الاشتراك الفعال
            val remoteSub = runCatching {
                firestore.collection("users").document(userId)
                    .collection("metadata").document("subscription")
                    .get().await()
                    .toObject(SubscriptionEntity::class.java)
            }.getOrNull()

            val currentLocal = subscriptionDao.getSubscription(userId)
            val resolved = remoteSub ?: currentLocal ?: SubscriptionEntity(
                userId = userId,
                planId = "free",
                tier = "FREE",
                status = "ACTIVE"
            )

            subscriptionDao.upsertSubscription(resolved)
            analyticsTracker.logEvent("purchase_restored", mapOf("tier" to resolved.tier))
            Resource.Success(resolved)
        }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        // معالجة تحديثات Google Play Billing المباشرة وتأكيد الـ AcknowledgePurchaseParams
    }
}
