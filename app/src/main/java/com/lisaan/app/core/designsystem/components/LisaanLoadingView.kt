package com.lisaan.app.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage

@Composable
fun LisaanLoadingView(
    messageAr: String = "جاري التحميل...",
    messageEn: String = "Loading...",
    modifier: Modifier = Modifier
) {
    val isAr = LocalAppLanguage.current == "ar"
    val msg = if (isAr) messageAr else messageEn

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = msg
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NabeehMascotView(mood = NabeehMoodState.THINKING, size = 100.dp)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = msg,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LisaanEmptyStateView(
    titleAr: String,
    titleEn: String,
    subtitleAr: String,
    subtitleEn: String,
    actionButtonLabelAr: String? = null,
    actionButtonLabelEn: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isAr = LocalAppLanguage.current == "ar"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) titleAr else titleEn,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isAr) subtitleAr else subtitleEn,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onActionClick != null && actionButtonLabelAr != null && actionButtonLabelEn != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.heightIn(min = 48.dp) // WCAG 48dp minimum touch target
            ) {
                Text(if (isAr) actionButtonLabelAr else actionButtonLabelEn)
            }
        }
    }
}

@Composable
fun LisaanErrorStateView(
    errorAr: String,
    errorEn: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAr = LocalAppLanguage.current == "ar"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) errorAr else errorEn,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isAr) "إعادة المحاولة" else "Retry")
        }
    }
}
