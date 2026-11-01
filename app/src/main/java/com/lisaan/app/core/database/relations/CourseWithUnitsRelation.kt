package com.lisaan.app.core.database.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.lisaan.app.core.database.entities.*

// علاقة الكورس مع جميع وحداته
data class CourseWithUnitsRelation(
    @Embedded val course: CourseEntity,
    @Relation(
        entity = UnitEntity::class,
        parentColumn = "id",
        entityColumn = "course_id"
    )
    val unitsWithLessons: List<UnitWithLessonsRelation>
)

// علاقة الوحدة مع دروسها ومفرداتها
data class UnitWithLessonsRelation(
    @Embedded val unit: UnitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "unit_id"
    )
    val lessons: List<LessonEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "unit_id"
    )
    val vocabularies: List<VocabularyEntity>
)

// علاقة الدرس مع جميع تمارينه ومفرداته وتقدم المستخدم
data class LessonWithDetailsRelation(
    @Embedded val lesson: LessonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "lesson_id"
    )
    val vocabularies: List<VocabularyEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "lesson_id"
    )
    val exercises: List<ExerciseEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "lesson_id"
    )
    val userProgressList: List<UserProgressEntity>
)

// علاقة عنصر المراجعة الذكية SM-2 مع المفردة الخاصة به
data class ReviewItemWithVocabularyRelation(
    @Embedded val reviewItem: ReviewItemEntity,
    @Relation(
        parentColumn = "vocabulary_id",
        entityColumn = "id"
    )
    val vocabulary: VocabularyEntity
)

// علاقة منشور المجتمع مع جميع التصحيحات والتعليقات عليه
data class CommunityPostWithCorrectionsRelation(
    @Embedded val post: CommunityPostEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "post_id"
    )
    val corrections: List<CorrectionEntity>
)

// علاقة ملف المستخدم الكامل مع الـ Streak والاشتراك
data class UserCompleteProfileRelation(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    val streak: StreakEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    val subscription: SubscriptionEntity?
)
