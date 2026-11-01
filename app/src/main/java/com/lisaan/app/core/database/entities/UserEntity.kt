package com.lisaan.app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 1. User Entity
@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = false)]
)
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "email") val email: String?,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "is_guest") val isGuest: Boolean = false,
    @ColumnInfo(name = "interface_lang") val interfaceLang: String = "ar",
    @ColumnInfo(name = "current_course_id") val currentCourseId: String = "en_from_ar",
    @ColumnInfo(name = "learning_goal") val learningGoal: String = "TRAVEL", // TRAVEL, WORK, STUDY, HOBBY
    @ColumnInfo(name = "proficiency_level") val proficiencyLevel: String = "A1", // A1, A2, B1, B2
    @ColumnInfo(name = "daily_goal_minutes") val dailyGoalMinutes: Int = 10,
    @ColumnInfo(name = "total_xp") val totalXp: Int = 0,
    @ColumnInfo(name = "current_league") val currentLeague: String = "BRONZE", // BRONZE, SILVER, GOLD
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long = System.currentTimeMillis()
)

// 2. Language Entity
@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey
    @ColumnInfo(name = "code") val code: String, // "ar", "en"
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "flag_emoji") val flagEmoji: String,
    @ColumnInfo(name = "is_rtl") val isRtl: Boolean,
    @ColumnInfo(name = "is_available_for_learning") val isAvailableForLearning: Boolean
)

// 3. Course Entity
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["code"],
            childColumns = ["source_lang_code"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["code"],
            childColumns = ["target_lang_code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("source_lang_code"),
        Index("target_lang_code")
    ]
)
data class CourseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // "en_from_ar"
    @ColumnInfo(name = "source_lang_code") val sourceLangCode: String,
    @ColumnInfo(name = "target_lang_code") val targetLangCode: String,
    @ColumnInfo(name = "title_ar") val titleAr: String,
    @ColumnInfo(name = "title_en") val titleEn: String,
    @ColumnInfo(name = "description_ar") val descriptionAr: String,
    @ColumnInfo(name = "description_en") val descriptionEn: String,
    @ColumnInfo(name = "total_units") val totalUnits: Int,
    @ColumnInfo(name = "version") val version: Int = 1
)

// 4. Unit Entity
@Entity(
    tableName = "units",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("course_id"), Index("order_index")]
)
data class UnitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "title_ar") val titleAr: String,
    @ColumnInfo(name = "title_en") val titleEn: String,
    @ColumnInfo(name = "subtitle_ar") val subtitleAr: String,
    @ColumnInfo(name = "subtitle_en") val subtitleEn: String,
    @ColumnInfo(name = "theme_color_hex") val themeColorHex: String = "#0F4C81",
    @ColumnInfo(name = "icon_name") val iconName: String = "ic_unit_compass",
    @ColumnInfo(name = "is_downloaded_offline") val isDownloadedOffline: Boolean = false,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long = 0L
)

// 5. Lesson Entity
@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unit_id"), Index("order_index")]
)
data class LessonEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "unit_id") val unitId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "title_ar") val titleAr: String,
    @ColumnInfo(name = "title_en") val titleEn: String,
    @ColumnInfo(name = "intro_tip_ar") val introTipAr: String,
    @ColumnInfo(name = "intro_tip_en") val introTipEn: String,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int = 5,
    @ColumnInfo(name = "reward_xp") val rewardXp: Int = 20,
    @ColumnInfo(name = "dialogue_json") val dialogueJson: String = "[]"
)

// 6. Vocabulary Entity
@Entity(
    tableName = "vocabularies",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lesson_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("unit_id"),
        Index("lesson_id"),
        Index("word_target"),
        Index("category_tag"),
        Index("is_bookmarked")
    ]
)
data class VocabularyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "unit_id") val unitId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "word_target") val wordTarget: String,
    @ColumnInfo(name = "translation_ar") val translationAr: String,
    @ColumnInfo(name = "transliteration") val transliteration: String,
    @ColumnInfo(name = "phonetic_ipa") val phoneticIpa: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String, // NOUN, VERB, ADJ, PHRASE
    @ColumnInfo(name = "category_tag") val categoryTag: String, // GREETINGS, TRAVEL, WORK, FOOD, DAILY
    @ColumnInfo(name = "example_target") val exampleTarget: String,
    @ColumnInfo(name = "example_ar") val exampleAr: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String?,
    @ColumnInfo(name = "local_audio_path") val localAudioPath: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "local_image_path") val localImagePath: String? = null,
    @ColumnInfo(name = "is_bookmarked") val isBookmarked: Boolean = false
)

// 7. Exercise Entity
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lesson_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lesson_id"), Index("order_index")]
)
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    /**
     * Supported Exercise Types:
     * MULTIPLE_CHOICE, WRITTEN_TRANSLATION, WORD_ORDER, MATCHING_PAIRS,
     * FILL_BLANK, LISTENING_DICTATION, SPEAKING_PRONUNCIATION, TRUE_FALSE
     */
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "prompt_ar") val promptAr: String,
    @ColumnInfo(name = "prompt_en") val promptEn: String,
    @ColumnInfo(name = "target_text") val targetText: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "options_json") val optionsJson: String = "[]",
    @ColumnInfo(name = "correct_answer_json") val correctAnswerJson: String,
    @ColumnInfo(name = "explanation_ar") val explanationAr: String,
    @ColumnInfo(name = "explanation_en") val explanationEn: String,
    @ColumnInfo(name = "vocabulary_id") val vocabularyId: String? = null
)

// 8. UserProgress Entity
@Entity(
    tableName = "user_progress",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lesson_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "lesson_id"], unique = true),
        Index("lesson_id"),
        Index("unit_id"),
        Index("is_pending_sync")
    ]
)
data class UserProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // "${userId}_${lessonId}"
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "unit_id") val unitId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "best_score_percent") val bestScorePercent: Int = 0,
    @ColumnInfo(name = "stars_earned") val starsEarned: Int = 0, // 1..3
    @ColumnInfo(name = "attempts_count") val attemptsCount: Int = 0,
    @ColumnInfo(name = "earned_xp") val earnedXp: Int = 0,
    @ColumnInfo(name = "time_spent_seconds") val timeSpentSeconds: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_pending_sync") val isPendingSync: Boolean = true
)

// 9. ReviewItem (SM-2 Spaced Repetition) Entity
@Entity(
    tableName = "review_items",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocabulary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "vocabulary_id"], unique = true),
        Index("vocabulary_id"),
        Index("next_review_epoch_ms"),
        Index("is_pending_sync")
    ]
)
data class ReviewItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String, // "${userId}_${vocabularyId}"
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "vocabulary_id") val vocabularyId: String,
    @ColumnInfo(name = "repetitions") val repetitions: Int = 0,
    @ColumnInfo(name = "easiness_factor") val easinessFactor: Float = 2.5f,
    @ColumnInfo(name = "interval_days") val intervalDays: Int = 1,
    @ColumnInfo(name = "next_review_epoch_ms") val nextReviewEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_reviewed_epoch_ms") val lastReviewedEpochMs: Long = 0L,
    @ColumnInfo(name = "last_quality_grade") val lastQualityGrade: Int = 0, // 0..5 SM-2 grade
    @ColumnInfo(name = "lapse_count") val lapseCount: Int = 0,
    @ColumnInfo(name = "is_pending_sync") val isPendingSync: Boolean = true
)

// 10. Streak Entity
@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "current_streak_days") val currentStreakDays: Int = 0,
    @ColumnInfo(name = "longest_streak_days") val longestStreakDays: Int = 0,
    @ColumnInfo(name = "last_completed_date_iso") val lastCompletedDateIso: String? = null, // "YYYY-MM-DD"
    @ColumnInfo(name = "today_minutes_learned") val todayMinutesLearned: Int = 0,
    @ColumnInfo(name = "today_xp_earned") val todayXpEarned: Int = 0,
    @ColumnInfo(name = "freeze_count_available") val freezeCountAvailable: Int = 1,
    @ColumnInfo(name = "is_freeze_used_today") val isFreezeUsedToday: Boolean = false,
    @ColumnInfo(name = "weekly_activity_mask_json") val weeklyActivityMaskJson: String = "[false,false,false,false,false,false,false]",
    @ColumnInfo(name = "is_pending_sync") val isPendingSync: Boolean = true
)

// 11. Achievement Entity
@Entity(
    tableName = "achievements",
    indices = [Index(value = ["code"], unique = true)]
)
data class AchievementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "code") val code: String, // e.g., "FIRST_LESSON", "STREAK_7", "VOCAB_50", "COMMUNITY_HELPER"
    @ColumnInfo(name = "title_ar") val titleAr: String,
    @ColumnInfo(name = "title_en") val titleEn: String,
    @ColumnInfo(name = "description_ar") val descriptionAr: String,
    @ColumnInfo(name = "description_en") val descriptionEn: String,
    @ColumnInfo(name = "icon_res_name") val iconResName: String,
    @ColumnInfo(name = "required_value") val requiredValue: Int,
    @ColumnInfo(name = "current_progress") val currentProgress: Int = 0,
    @ColumnInfo(name = "reward_xp") val rewardXp: Int = 50,
    @ColumnInfo(name = "is_unlocked") val isUnlocked: Boolean = false,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long? = null
)

// 12. CommunityPost Entity
@Entity(
    tableName = "community_posts",
    indices = [Index("author_id"), Index("created_at"), Index("target_lang_code")]
)
data class CommunityPostEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    @ColumnInfo(name = "author_avatar_url") val authorAvatarUrl: String?,
    @ColumnInfo(name = "target_lang_code") val targetLangCode: String = "en",
    @ColumnInfo(name = "native_lang_code") val nativeLangCode: String = "ar",
    @ColumnInfo(name = "text_content") val textContent: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String? = null,
    @ColumnInfo(name = "audio_duration_sec") val audioDurationSec: Int = 0,
    @ColumnInfo(name = "prompt_topic") val promptTopic: String,
    @ColumnInfo(name = "corrections_count") val correctionsCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_resolved") val isResolved: Boolean = false,
    @ColumnInfo(name = "is_reported") val isReported: Boolean = false
)

// 13. Correction Entity
@Entity(
    tableName = "corrections",
    foreignKeys = [
        ForeignKey(
            entity = CommunityPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("post_id"), Index("corrector_id")]
)
data class CorrectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "corrector_id") val correctorId: String,
    @ColumnInfo(name = "corrector_name") val correctorName: String,
    @ColumnInfo(name = "corrector_avatar_url") val correctorAvatarUrl: String?,
    @ColumnInfo(name = "original_text_snapshot") val originalTextSnapshot: String,
    @ColumnInfo(name = "corrected_text") val correctedText: String,
    @ColumnInfo(name = "explanation_note") val explanationNote: String,
    @ColumnInfo(name = "voice_comment_url") val voiceCommentUrl: String? = null,
    @ColumnInfo(name = "thanks_count") val thanksCount: Int = 0,
    @ColumnInfo(name = "is_thanked_by_author") val isThankedByAuthor: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

// 14. Subscription Entity
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "plan_id") val planId: String, // "free", "lisaan_plus_monthly", "lisaan_plus_yearly", "lisaan_plus_lifetime"
    @ColumnInfo(name = "tier") val tier: String = "FREE", // FREE, PREMIUM
    @ColumnInfo(name = "status") val status: String = "ACTIVE", // ACTIVE, EXPIRED, GRACE_PERIOD, CANCELED
    @ColumnInfo(name = "purchase_token") val purchaseToken: String? = null,
    @ColumnInfo(name = "start_time_ms") val startTimeMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expiry_time_ms") val expiryTimeMs: Long? = null,
    @ColumnInfo(name = "auto_renewing") val autoRenewing: Boolean = false
)
