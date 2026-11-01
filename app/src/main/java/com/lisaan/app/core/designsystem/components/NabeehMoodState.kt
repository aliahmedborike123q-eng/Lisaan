package com.lisaan.app.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LapisBlueLight
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight

enum class NabeehMoodState {
    IDLE,
    CELEBRATE,
    THINKING,
    ENCOURAGE,
    STREAK_FIRE
}

@Composable
fun NabeehMascotView(
    mood: NabeehMoodState = NabeehMoodState.IDLE,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nabeeh_anim")

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (mood == NabeehMoodState.CELEBRATE) -16f else -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == NabeehMoodState.CELEBRATE) 450 else 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val crestRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = if (mood == NabeehMoodState.THINKING) 12f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crest"
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "نبيه تميمة تطبيق لسان" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cy = h * 0.55f + bounceOffset

            // 1. هالة خلفية دافئة حسب الحالة
            val auraColor = when (mood) {
                NabeehMoodState.CELEBRATE, NabeehMoodState.STREAK_FIRE -> DesertAmberLight.copy(alpha = 0.22f)
                NabeehMoodState.THINKING -> LapisBlueLight.copy(alpha = 0.18f)
                NabeehMoodState.ENCOURAGE -> OasisEmeraldLight.copy(alpha = 0.20f)
                NabeehMoodState.IDLE -> LapisBlueLight.copy(alpha = 0.12f)
            }
            drawCircle(
                color = auraColor,
                radius = w * 0.46f,
                center = Offset(w * 0.5f, cy)
            )

            // 2. تاج الهدهد المميز (Crest Feathers)
            rotate(degrees = crestRotation, pivot = Offset(w * 0.5f, cy - h * 0.22f)) {
                val featherColors = listOf(DesertAmberLight, Color(0xFFEA580C), DesertAmberLight)
                val angles = listOf(-25f, 0f, 25f)
                angles.forEachIndexed { idx, angle ->
                    rotate(degrees = angle, pivot = Offset(w * 0.5f, cy - h * 0.20f)) {
                        drawRoundRect(
                            color = featherColors[idx],
                            topLeft = Offset(w * 0.46f, cy - h * 0.42f),
                            size = Size(w * 0.08f, h * 0.22f),
                            cornerRadius = CornerRadius(16f, 16f)
                        )
                        // طرف الريشة الداكن المميز للهدهد
                        drawCircle(
                            color = Color(0xFF18222F),
                            radius = w * 0.04f,
                            center = Offset(w * 0.50f, cy - h * 0.40f)
                        )
                    }
                }
            }

            // 3. جسم الهدهد (Warm Terracotta Amber)
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(w * 0.26f, cy - h * 0.24f),
                size = Size(w * 0.48f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
            )

            // 4. الصدر الأبيض الكريمي
            drawRoundRect(
                color = Color(0xFFFFFBEB),
                topLeft = Offset(w * 0.33f, cy - h * 0.06f),
                size = Size(w * 0.34f, h * 0.30f),
                cornerRadius = CornerRadius(w * 0.17f, w * 0.17f)
            )

            // 5. الوشاح اللازوردي الذكي (Lapis Scarf)
            drawRoundRect(
                color = LapisBlueLight,
                topLeft = Offset(w * 0.28f, cy - h * 0.04f),
                size = Size(w * 0.44f, h * 0.09f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            // بوصلة لسان الذهبية على الوشاح
            drawCircle(
                color = DesertAmberLight,
                radius = w * 0.045f,
                center = Offset(w * 0.5f, cy + h * 0.005f)
            )

            // 6. العينان الذكيتان
            val eyeY = cy - h * 0.13f
            drawCircle(color = Color.White, radius = w * 0.075f, center = Offset(w * 0.40f, eyeY))
            drawCircle(color = Color.White, radius = w * 0.075f, center = Offset(w * 0.60f, eyeY))
            drawCircle(color = Color(0xFF0F172A), radius = w * 0.04f, center = Offset(w * 0.40f, eyeY))
            drawCircle(color = Color(0xFF0F172A), radius = w * 0.04f, center = Offset(w * 0.60f, eyeY))

            // 7. منقار الهدهد الطويل الأنيق
            val beakPath = Path().apply {
                moveTo(w * 0.46f, cy - h * 0.10f)
                lineTo(w * 0.54f, cy - h * 0.10f)
                lineTo(w * 0.50f, cy - h * 0.01f)
                close()
            }
            drawPath(path = beakPath, color = Color(0xFF1E293B))
        }
    }
}
