package com.nutriai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nutriai.app.ui.MainViewModel
import com.nutriai.app.ui.dashboard.DashboardScreen
import com.nutriai.app.ui.onboarding.OnboardingScreen
import com.nutriai.app.ui.settings.SettingsScreen
import com.nutriai.app.ui.theme.NutriAITheme

enum class AppScreen {
    ONBOARDING,
    DASHBOARD,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NutriAITheme {
                NutriAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NutriAppContent(viewModel: MainViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val macroTarget by viewModel.macroTarget.collectAsState()
    val weeklyPlan by viewModel.weeklyPlan.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.ONBOARDING) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isOnboardingCompleted) {
        currentScreen = if (isOnboardingCompleted) AppScreen.DASHBOARD else AppScreen.ONBOARDING
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                AppScreen.ONBOARDING -> {
                    OnboardingScreen(
                        initialProfile = userProfile,
                        onComplete = { updatedProfile ->
                            viewModel.saveProfile(updatedProfile)
                            currentScreen = AppScreen.DASHBOARD
                        }
                    )
                }
                AppScreen.DASHBOARD -> {
                    DashboardScreen(
                        userProfile = userProfile,
                        macroTarget = macroTarget,
                        weeklyPlan = weeklyPlan,
                        isLoading = isLoading,
                        onSelectDay = { dayIdx -> viewModel.selectDay(dayIdx) },
                        onRefreshPlan = { viewModel.generateWeeklyPlan() },
                        onSelectOption = { mealType, idx -> viewModel.selectMealOption(mealType, idx) },
                        onRegenerateSlot = { mealType -> viewModel.regenerateSlot(mealType) },
                        onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                        onEditProfile = { currentScreen = AppScreen.ONBOARDING }
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        currentApiKey = apiKey,
                        onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                        onResetProfile = {
                            viewModel.resetOnboarding()
                            currentScreen = AppScreen.ONBOARDING
                        },
                        onBack = { currentScreen = AppScreen.DASHBOARD }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
