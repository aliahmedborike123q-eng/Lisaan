package com.lisaan.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lisaan.app.core.database.dao.ProgressAndStreakDao
import com.lisaan.app.core.database.dao.Sm2ReviewDao
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.data.remote.firestore.FirestoreProgressDto
import com.lisaan.app.data.remote.firestore.FirestoreReviewItemDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class OfflineProgressSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val progressAndStreakDao: ProgressAndStreakDao,
    private val sm2ReviewDao: Sm2ReviewDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = preferencesDataStore.settingsFlow.first()
        val userId = settings.currentUserId
        if (userId.isNullOrBlank() || settings.isGuestUser) {
            return@withContext Result.success()
        }

        try {
            val userDocRef = firestore.collection("users").document(userId)

            // 1. مزامنة سجلات تقدم الدروس غير المتزامنة
            val unsyncedProgress = progressAndStreakDao.getUnsyncedProgressList()
            if (unsyncedProgress.isNotEmpty()) {
                val batch = firestore.batch()
                unsyncedProgress.forEach { entity ->
                    val docRef = userDocRef.collection("progress").document(entity.id)
                    batch.set(docRef, FirestoreProgressDto.fromEntity(entity), SetOptions.merge())
                }
                batch.commit().await()
                progressAndStreakDao.markProgressSynced(unsyncedProgress.map { it.id })
            }

            // 2. مزامنة بطاقات التكرار المتباعد SM-2 غير المتزامنة
            val unsyncedReviews = sm2ReviewDao.getUnsyncedReviewItems()
            if (unsyncedReviews.isNotEmpty()) {
                val batch = firestore.batch()
                unsyncedReviews.forEach { entity ->
                    val docRef = userDocRef.collection("sm2_reviews").document(entity.id)
                    batch.set(docRef, FirestoreReviewItemDto.fromEntity(entity), SetOptions.merge())
                }
                batch.commit().await()
                sm2ReviewDao.markReviewItemsSynced(unsyncedReviews.map { it.id })
            }

            // 3. مزامنة الـ Streak اليومي
            val unsyncedStreaks = progressAndStreakDao.getUnsyncedStreaks()
            unsyncedStreaks.forEach { streak ->
                userDocRef.collection("metadata").document("streak")
                    .set(streak, SetOptions.merge()).await()
                progressAndStreakDao.markStreakSynced(streak.userId)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_SYNC_WORK_NAME = "lisaan_offline_auto_sync"

        fun enqueueAutomaticNetworkSync(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<OfflineProgressSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
