package com.lisaan.app.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lisaan.app.MainActivity
import java.time.LocalDate

class LisaanDailyWordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val dailyWords = listOf(
            Triple("Opportunity", "فرصة سانحة", "أوبورتيونيتي • /ˌɒpərˈtjuːnɪti/"),
            Triple("Confidence", "ثقة بالنفس", "كونفيدنس • /ˈkɒnfɪdəns/"),
            Triple("Prioritize", "يرتب الأولويات", "برايوريتايز • /praɪˈɒrɪtaɪz/"),
            Triple("Boarding pass", "بطاقة صعود الطائرة", "بوردينغ باس • /ˈbɔːrdɪŋ pæs/")
        )

        val dayIndex = LocalDate.now().dayOfYear % dailyWords.size
        val (wordEn, wordAr, phonetic) = dailyWords[dayIndex]

        appWidgetIds.forEach { appWidgetId ->
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, "🧭 كلمة اليوم في لِسان: $wordEn ($wordAr)")
                setTextViewText(android.R.id.text2, "النطق: $phonetic — اضغط للمتابعة 🔥")
                setOnClickPendingIntent(android.R.id.text1, pendingIntent)
                setOnClickPendingIntent(android.R.id.text2, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
