package com.lisaan.app.presentation.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.domain.repository.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    val plans = billingRepository.getAvailablePlans()
    val activeSubFlow = billingRepository.activeSubscriptionFlow

    private val _selectedPlanId = MutableStateFlow("lisaan_plus_yearly")
    val selectedPlanId: StateFlow<String> = _selectedPlanId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun selectPlan(planId: String) {
        _selectedPlanId.value = planId
    }

    fun subscribeNow(activity: Activity?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            billingRepository.launchPurchaseFlow(activity, _selectedPlanId.value)
            onSuccess()
        }
    }

    fun restoreUserPurchases(isAr: Boolean) {
        viewModelScope.launch {
            billingRepository.restorePurchases()
            _statusMessage.value = if (isAr) "تم فحص واستعادة حالة اشتراكك بنجاح" else "Purchases restored successfully"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    source: String = "general",
    onClose: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val selectedPlanId by viewModel.selectedPlanId.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    val activity = LocalContext.current as? Activity

    val benefits = listOf(
        if (isAr) "محادثات لا محدودة بالصوت والنص مع المدرب الذكي (AI Coach)" else "Unlimited Voice & Text AI Coach Sessions",
        if (isAr) "تحميل جميع الوحدات والدروس للتعلم بدون إنترنت (Offline Mode)" else "Download All Units for Full Offline Learning",
        if (isAr) "درع حماية الشعلة الأسبوعي التلقائي (Streak Freeze)" else "Automatic Weekly Streak Freeze Protection",
        if (isAr) "مراجعات ذكية غير محدودة (SM-2) وبدون أي إعلانات" else "Unlimited Smart Reviews (SM-2) & Zero Ads"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "لِسان بلس (Lisaan Plus)" else "Lisaan Plus") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.restoreUserPurchases(isAr) }) {
                        Text(if (isAr) "استعادة المشتريات" else "Restore Purchases")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NabeehMascotView(mood = NabeehMoodState.CELEBRATE, size = 100.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isAr) "انطلق نحو الطلاقة بلا حدود مع «لِسان بلس»" else "Unlock Full Fluency with Lisaan Plus",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        benefits.forEach { benefit ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OasisEmeraldLight)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = benefit, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                viewModel.plans.forEach { plan ->
                    val isSelected = selectedPlanId == plan.planId
                    val badgeText = if (isAr) plan.badgeTextAr else plan.badgeTextEn

                    OutlinedCard(
                        onClick = { viewModel.selectPlan(plan.planId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) DesertAmberLight else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (!badgeText.isNullOrBlank()) {
                                Badge(
                                    containerColor = DesertAmberLight,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ) {
                                    Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isAr) plan.titleAr else plan.titleEn,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isAr) plan.billingPeriodAr else plan.billingPeriodEn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = plan.priceFormatted,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                statusMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = msg, color = OasisEmeraldLight, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.subscribeNow(activity, onClose) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isAr) "اشترك الآن وابدأ التجربة" else "Subscribe & Unlock Now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
