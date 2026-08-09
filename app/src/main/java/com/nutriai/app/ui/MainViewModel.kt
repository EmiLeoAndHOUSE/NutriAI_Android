package com.nutriai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.app.data.local.UserPreferencesRepository
import com.nutriai.app.data.model.DailyMealPlan
import com.nutriai.app.data.model.MacroTarget
import com.nutriai.app.data.model.MealType
import com.nutriai.app.data.model.UserProfile
import com.nutriai.app.data.remote.GeminiApiService
import com.nutriai.app.domain.calculator.NutritionalCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefsRepository = UserPreferencesRepository(application)
    private val geminiApiService = GeminiApiService()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _macroTarget = MutableStateFlow(NutritionalCalculator.calculateMacroTarget(UserProfile()))
    val macroTarget: StateFlow<MacroTarget> = _macroTarget.asStateFlow()

    private val _dailyPlan = MutableStateFlow<DailyMealPlan?>(null)
    val dailyPlan: StateFlow<DailyMealPlan?> = _dailyPlan.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefsRepository.userProfileFlow.collectLatest { profile ->
                _userProfile.value = profile
                _macroTarget.value = NutritionalCalculator.calculateMacroTarget(profile)
            }
        }
        viewModelScope.launch {
            userPrefsRepository.apiKeyFlow.collectLatest { key ->
                _apiKey.value = key
            }
        }
        viewModelScope.launch {
            userPrefsRepository.isOnboardingCompletedFlow.collectLatest { completed ->
                _isOnboardingCompleted.value = completed
                if (completed && _dailyPlan.value == null) {
                    generateDailyPlan()
                }
            }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _userProfile.value = profile
            _macroTarget.value = NutritionalCalculator.calculateMacroTarget(profile)
            userPrefsRepository.saveUserProfile(profile)
            generateDailyPlan()
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            _apiKey.value = key
            userPrefsRepository.saveApiKey(key)
        }
    }

    fun generateDailyPlan() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val currentProfile = _userProfile.value
            val target = _macroTarget.value

            val result = geminiApiService.generateDailyPlan(
                profile = currentProfile,
                target = target,
                apiKey = _apiKey.value
            )

            result.onSuccess { plan ->
                _dailyPlan.value = plan
            }.onFailure { err ->
                _errorMessage.value = "Impossibile generare la giornata alimentare: ${err.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun selectMealOption(mealType: MealType, optionIndex: Int) {
        val currentPlan = _dailyPlan.value ?: return
        val updatedSlots = currentPlan.slots.map { slot ->
            if (slot.mealType == mealType) {
                slot.copy(selectedOptionIndex = optionIndex)
            } else {
                slot
            }
        }
        _dailyPlan.value = currentPlan.copy(slots = updatedSlots)
    }

    fun regenerateSlot(mealType: MealType) {
        viewModelScope.launch {
            val currentPlan = _dailyPlan.value ?: return@launch
            val slotToUpdate = currentPlan.slots.find { it.mealType == mealType } ?: return@launch

            _isLoading.value = true
            val currentProfile = _userProfile.value
            val numSlots = currentPlan.slots.size.coerceAtLeast(1)
            val slotTarget = MacroTarget(
                calories = currentPlan.target.calories / numSlots,
                proteinGrams = currentPlan.target.proteinGrams / numSlots,
                carbsGrams = currentPlan.target.carbsGrams / numSlots,
                fatGrams = currentPlan.target.fatGrams / numSlots
            )

            val result = geminiApiService.regenerateMealSlot(
                profile = currentProfile,
                targetSlotMacro = slotTarget,
                mealType = mealType,
                apiKey = _apiKey.value
            )

            result.onSuccess { newOptions ->
                if (newOptions.isNotEmpty()) {
                    val updatedSlots = currentPlan.slots.map { slot ->
                        if (slot.mealType == mealType) {
                            slot.copy(options = newOptions, selectedOptionIndex = 0)
                        } else {
                            slot
                        }
                    }
                    _dailyPlan.value = currentPlan.copy(slots = updatedSlots)
                }
            }.onFailure { err ->
                _errorMessage.value = "Impossibile rigenerare il pasto: ${err.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            userPrefsRepository.resetOnboarding()
            _dailyPlan.value = null
        }
    }
}
