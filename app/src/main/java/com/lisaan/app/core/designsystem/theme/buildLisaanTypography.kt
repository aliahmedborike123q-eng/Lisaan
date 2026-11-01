package com.lisaan.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * في بيئة الإنتاج يتم وضع ملفات خطوط:
 * - res/font/ibm_plex_sans_arabic_regular.ttf / medium / semibold / bold
 * - res/font/plus_jakarta_sans_regular.ttf / medium / semibold / bold
 * ويتم التبديل بينهما تلقائياً أو دمجهما في FontFamily موحدة لضمان دعم التشكيل والـ IPA.
 */
val LisaanArabicLatinFontFamily = FontFamily.SansSerif

fun buildLisaanTypography(isArabic: Boolean): Typography {
    // زيادة طفيفة في ارتفاع السطر (lineHeight) للغة العربية لضمان عدم تداخل الحركات والتشكيل
    val lineHeightMultiplier = if (isArabic) 1.15f else 1.0f

    return Typography(
        displayLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = (44 * lineHeightMultiplier).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = (36 * lineHeightMultiplier).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = (32 * lineHeightMultiplier).sp
        ),
        titleLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = (28 * lineHeightMultiplier).sp
        ),
        titleMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = (24 * lineHeightMultiplier).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = (24 * lineHeightMultiplier).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = (20 * lineHeightMultiplier).sp
        ),
        labelLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = (20 * lineHeightMultiplier).sp
        ),
        labelMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = (16 * lineHeightMultiplier).sp
        )
    )
}
