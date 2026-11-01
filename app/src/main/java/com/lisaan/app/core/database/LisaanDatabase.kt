package com.lisaan.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lisaan.app.core.database.converters.RoomTypeConverters
import com.lisaan.app.core.database.dao.*
import com.lisaan.app.core.database.entities.*

@Database(
    entities = [
        UserEntity::class,
        LanguageEntity::class,
        CourseEntity::class,
        UnitEntity::class,
        LessonEntity::class,
        VocabularyEntity::class,
        ExerciseEntity::class,
        UserProgressEntity::class,
        ReviewItemEntity::class,
        StreakEntity::class,
        AchievementEntity::class,
        CommunityPostEntity::class,
        CorrectionEntity::class,
        SubscriptionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(RoomTypeConverters::class)
abstract class LisaanDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun courseContentDao(): CourseContentDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun progressAndStreakDao(): ProgressAndStreakDao
    abstract fun sm2ReviewDao(): Sm2ReviewDao
    abstract fun achievementDao(): AchievementDao
    abstract fun communityDao(): CommunityDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val DATABASE_NAME = "lisaan_app.db"
    }
}

object DatabaseMigrations {
    /**
     * Migration from Version 1 to Version 2:
     * Adds voice comments support to community corrections and today's XP counter to streaks.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE corrections ADD COLUMN voice_comment_url TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE streaks ADD COLUMN today_xp_earned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_review_items_next_review_epoch_ms ON review_items(next_review_epoch_ms)")
        }
    }
}
