package com.lisaan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.lisaan.app.core.datastore.AppUserSettings
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.theme.LisaanTheme
import com.lisaan.app.presentation.navigation.LisaanRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userSettings by preferencesDataStore.settingsFlow.collectAsState(
                initial = AppUserSettings()
            )

            LisaanTheme(
                themeMode = userSettings.themeMode,
                interfaceLangCode = userSettings.interfaceLanguage
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val rootNavController = rememberNavController()
                    val startDestination = when {
                        !userSettings.isOnboardingCompleted -> LisaanRoute.OnboardingGraph.route
                        userSettings.currentUserId == null -> LisaanRoute.AuthGraph.route
                        else -> LisaanRoute.MainScaffold.route
                    }

                    // يتم ربط الشاشات التفصيلية تباعاً في المراحل 3 إلى 7 داخل LisaanRootNavHost
                }
            }
        }
    }
}
