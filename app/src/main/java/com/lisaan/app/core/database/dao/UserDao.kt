package com.lisaan.app.core.database.dao

import androidx.room.*
import com.lisaan.app.core.database.entities.*
import com.lisaan.app.core.database.relations.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Transaction
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeCompleteProfile(userId: String): Flow<UserCompleteProfileRelation?>

    @Query("UPDATE users SET total_xp = total_xp + :addedXp, last_active_at = :timestamp WHERE id = :userId")
    suspend fun incrementUserXp(userId: String, addedXp: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE users SET interface_lang = :langCode WHERE id = :userId")
    suspend fun updateInterfaceLanguage(userId: String, langCode: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)
}

@Dao
interface CourseContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<LanguageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<UnitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    fun observeCourseHierarchy(courseId: String): Flow<CourseWithUnitsRelation?>

    @Transaction
    @Query("SELECT * FROM units WHERE course_id = :courseId ORDER BY order_index ASC")
    fun observeUnitsWithLessons(courseId: String): Flow<List<UnitWithLessonsRelation>>

    @Transaction
    @Query("SELECT * FROM lessons WHERE id = :lessonId LIMIT 1")
    fun observeLessonDetails(lessonId: String): Flow<LessonWithDetailsRelation?>

    @Transaction
    @Query("SELECT * FROM lessons WHERE id = :lessonId LIMIT 1")
    suspend fun getLessonDetailsSnapshot(lessonId: String): LessonWithDetailsRelation?

    @Query("SELECT * FROM exercises WHERE lesson_id = :lessonId ORDER BY order_index ASC")
    suspend fun getExercisesForLesson(lessonId: String): List<ExerciseEntity>

    @Query("UPDATE units SET is_downloaded_offline = :isDownloaded, last_synced_at = :timestamp WHERE id = :unitId")
    suspend fun markUnitOfflineStatus(unitId: String, isDownloaded: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun getLessonsCount(): Int
}

@Dao
interface VocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularies(items: List<VocabularyEntity>)

    @Query("SELECT * FROM vocabularies ORDER BY word_target ASC")
    fun observeAllVocabularies(): Flow<List<VocabularyEntity>>

    @Query("""
        SELECT * FROM vocabularies
        WHERE (:category IS NULL OR category_tag = :category)
        AND (:onlyBookmarked = 0 OR is_bookmarked = 1)
        AND (
            word_target LIKE '%' || :query || '%'
            OR translation_ar LIKE '%' || :query || '%'
            OR transliteration LIKE '%' || :query || '%'
        )
        ORDER BY word_target ASC
    """)
    fun searchVocabularies(
        query: String,
        category: String?,
        onlyBookmarked: Boolean
    ): Flow<List<VocabularyEntity>>

    @Query("UPDATE vocabularies SET is_bookmarked = :bookmarked WHERE id = :vocabId")
    suspend fun setBookmarkStatus(vocabId: String, bookmarked: Boolean)

    @Query("UPDATE vocabularies SET local_audio_path = :localAudio, local_image_path = :localImage WHERE id = :vocabId")
    suspend fun updateLocalMediaPaths(vocabId: String, localAudio: String?, localImage: String?)

    @Query("SELECT * FROM vocabularies WHERE unit_id = :unitId")
    suspend fun getVocabulariesByUnit(unitId: String): List<VocabularyEntity>
}

@Dao
interface ProgressAndStreakDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: UserProgressEntity)

    @Query("SELECT * FROM user_progress WHERE user_id = :userId")
    fun observeUserProgress(userId: String): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE user_id = :userId AND lesson_id = :lessonId LIMIT 1")
    suspend fun getLessonProgress(userId: String, lessonId: String): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE is_pending_sync = 1")
    suspend fun getUnsyncedProgressList(): List<UserProgressEntity>

    @Query("UPDATE user_progress SET is_pending_sync = 0 WHERE id IN (:ids)")
    suspend fun markProgressSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(streak: StreakEntity)

    @Query("SELECT * FROM streaks WHERE user_id = :userId LIMIT 1")
    fun observeStreak(userId: String): Flow<StreakEntity?>

    @Query("SELECT * FROM streaks WHERE user_id = :userId LIMIT 1")
    suspend fun getStreakSnapshot(userId: String): StreakEntity?

    @Query("SELECT * FROM streaks WHERE is_pending_sync = 1")
    suspend fun getUnsyncedStreaks(): List<StreakEntity>

    @Query("UPDATE streaks SET is_pending_sync = 0 WHERE user_id = :userId")
    suspend fun markStreakSynced(userId: String)
}

@Dao
interface Sm2ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewItem(item: ReviewItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialReviewItems(items: List<ReviewItemEntity>)

    @Transaction
    @Query("""
        SELECT * FROM review_items
        WHERE user_id = :userId AND next_review_epoch_ms <= :currentTimeMs
        ORDER BY next_review_epoch_ms ASC, easiness_factor ASC
    """)
    fun observeDueReviewItems(userId: String, currentTimeMs: Long): Flow<List<ReviewItemWithVocabularyRelation>>

    @Transaction
    @Query("""
        SELECT * FROM review_items
        WHERE user_id = :userId AND next_review_epoch_ms <= :currentTimeMs
        ORDER BY next_review_epoch_ms ASC, easiness_factor ASC
        LIMIT :limit
    """)
    suspend fun getDueReviewSessionItems(userId: String, currentTimeMs: Long, limit: Int = 20): List<ReviewItemWithVocabularyRelation>

    @Query("SELECT COUNT(*) FROM review_items WHERE user_id = :userId AND next_review_epoch_ms <= :currentTimeMs")
    fun observeDueReviewCount(userId: String, currentTimeMs: Long): Flow<Int>

    @Query("SELECT * FROM review_items WHERE user_id = :userId AND vocabulary_id = :vocabId LIMIT 1")
    suspend fun getReviewItem(userId: String, vocabId: String): ReviewItemEntity?

    @Query("SELECT * FROM review_items WHERE is_pending_sync = 1")
    suspend fun getUnsyncedReviewItems(): List<ReviewItemEntity>

    @Query("UPDATE review_items SET is_pending_sync = 0 WHERE id IN (:ids)")
    suspend fun markReviewItemsSynced(ids: List<String>)
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(items: List<AchievementEntity>)

    @Query("SELECT * FROM achievements ORDER BY is_unlocked DESC, required_value ASC")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE code = :code LIMIT 1")
    suspend fun getAchievementByCode(code: String): AchievementEntity?

    @Query("UPDATE achievements SET current_progress = :progress, is_unlocked = :unlocked, unlocked_at = :unlockedAt WHERE id = :id")
    suspend fun updateAchievementProgress(id: String, progress: Int, unlocked: Boolean, unlockedAt: Long?)
}

@Dao
interface CommunityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(posts: List<CommunityPostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: CommunityPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCorrections(corrections: List<CorrectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCorrection(correction: CorrectionEntity)

    @Transaction
    @Query("SELECT * FROM community_posts WHERE is_reported = 0 ORDER BY created_at DESC")
    fun observeCommunityFeed(): Flow<List<CommunityPostWithCorrectionsRelation>>

    @Transaction
    @Query("SELECT * FROM community_posts WHERE id = :postId LIMIT 1")
    fun observePostWithCorrections(postId: String): Flow<CommunityPostWithCorrectionsRelation?>

    @Query("UPDATE community_posts SET is_reported = 1 WHERE id = :postId")
    suspend fun markPostReported(postId: String)

    @Query("UPDATE corrections SET thanks_count = thanks_count + 1, is_thanked_by_author = 1 WHERE id = :correctionId")
    suspend fun thankCorrection(correctionId: String)
}

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE user_id = :userId LIMIT 1")
    fun observeSubscription(userId: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE user_id = :userId LIMIT 1")
    suspend fun getSubscription(userId: String): SubscriptionEntity?
}
