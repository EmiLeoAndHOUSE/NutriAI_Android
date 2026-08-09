package com.nutriai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutriai.app.data.local.UserPreferencesRepository
import com.nutriai.app.data.model.MacroTarget
import com.nutriai.app.data.model.MealType
import com.nutriai.app.data.model.UserProfile
import com.nutriai.app.data.model.WeeklyMealPlan
import com.nutriai.app.data.remote.GeminiApiService
import com.nutriai.app.domain.calculator.NutritionalCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefsRepository = UserPreferencesRepository(application)
    private val geminiApiService = GeminiApiService()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _macroTarget = MutableStateFlow(NutritionalCalculator.calculateMacroTarget(UserProfile()))
    val macroTarget: StateFlow<MacroTarget> = _macroTarget.asStateFlow()

    private val _weeklyPlan = MutableStateFlow<WeeklyMealPlan?>(null)
    val weeklyPlan: StateFlow<WeeklyMealPlan?> = _weeklyPlan.asStateFlow()

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
                if (completed && _weeklyPlan.value == null) {
                    generateWeeklyPlan()
                }
            }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            _userProfile.value = profile
            _macroTarget.value = NutritionalCalculator.calculateMacroTarget(profile)
            userPrefsRepository.saveUserProfile(profile)
            generateWeeklyPlan()
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            _apiKey.value = key
            userPrefsRepository.saveApiKey(key)
        }
    }

    fun generateWeeklyPlan() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val currentProfile = _userProfile.value
            val target = _macroTarget.value

            val result = geminiApiService.generateWeeklyPlan(
                profile = currentProfile,
                target = target,
                apiKey = _apiKey.value
            )

            result.onSuccess { plan ->
                _weeklyPlan.value = plan
            }.onFailure { err ->
                _errorMessage.value = "Impossibile generare la settimana alimentare: ${err.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun selectDay(dayIndex: Int) {
        val current = _weeklyPlan.value ?: return
        if (dayIndex in current.days.indices) {
            _weeklyPlan.value = current.copy(selectedDayIndex = dayIndex)
        }
    }

    fun selectMealOption(mealType: MealType, optionIndex: Int) {
        val currentPlan = _weeklyPlan.value ?: return
        val currentDayIdx = currentPlan.selectedDayIndex
        val activeDay = currentPlan.days.getOrNull(currentDayIdx) ?: return

        val updatedSlots = activeDay.slots.map { slot ->
            if (slot.mealType == mealType) {
                slot.copy(selectedOptionIndex = optionIndex)
            } else {
                slot
            }
        }
        val updatedDays = currentPlan.days.toMutableList()
        updatedDays[currentDayIdx] = activeDay.copy(slots = updatedSlots)
        _weeklyPlan.value = currentPlan.copy(days = updatedDays)
    }

    fun regenerateSlot(mealType: MealType) {
        viewModelScope.launch {
            val currentPlan = _weeklyPlan.value ?: return@launch
            val currentDayIdx = currentPlan.selectedDayIndex
            val activeDay = currentPlan.days.getOrNull(currentDayIdx) ?: return@launch

            _isLoading.value = true
            val currentProfile = _userProfile.value
            val numSlots = activeDay.slots.size.coerceAtLeast(1)
            val slotTarget = MacroTarget(
                calories = activeDay.target.calories / numSlots,
                proteinGrams = activeDay.target.proteinGrams / numSlots,
                carbsGrams = activeDay.target.carbsGrams / numSlots,
                fatGrams = activeDay.target.fatGrams / numSlots
            )

            val result = geminiApiService.regenerateMealSlot(
                profile = currentProfile,
                targetSlotMacro = slotTarget,
                mealType = mealType,
                apiKey = _apiKey.value
            )

            result.onSuccess { newOptions ->
                if (newOptions.isNotEmpty()) {
                    val updatedSlots = activeDay.slots.map { slot ->
                        if (slot.mealType == mealType) {
                            slot.copy(options = newOptions, selectedOptionIndex = 0)
                        } else {
                            slot
                        }
                    }
                    val updatedDays = currentPlan.days.toMutableList()
                    updatedDays[currentDayIdx] = activeDay.copy(slots = updatedSlots)
                    _weeklyPlan.value = currentPlan.copy(days = updatedDays)
                }
            }.onFailure { err ->
                _errorMessage.value = "Impossibile rigenerare il pasto: ${err.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun generateCustomMeal(mealType: MealType, userDesire: String) {
        if (userDesire.isBlank()) return
        viewModelScope.launch {
            val currentPlan = _weeklyPlan.value ?: return@launch
            val currentDayIdx = currentPlan.selectedDayIndex
            val activeDay = currentPlan.days.getOrNull(currentDayIdx) ?: return@launch

            _isLoading.value = true
            val currentProfile = _userProfile.value
            val numSlots = activeDay.slots.size.coerceAtLeast(1)
            val slotTarget = MacroTarget(
                calories = activeDay.target.calories / numSlots,
                proteinGrams = activeDay.target.proteinGrams / numSlots,
                carbsGrams = activeDay.target.carbsGrams / numSlots,
                fatGrams = activeDay.target.fatGrams / numSlots
            )

            val result = geminiApiService.generateCustomUserMealOption(
                profile = currentProfile,
                targetSlotMacro = slotTarget,
                mealType = mealType,
                userDesire = userDesire,
                apiKey = _apiKey.value
            )

            result.onSuccess { customOption ->
                val updatedSlots = activeDay.slots.map { slot ->
                    if (slot.mealType == mealType) {
                        val newOptions = slot.options + customOption
                        slot.copy(options = newOptions, selectedOptionIndex = newOptions.lastIndex)
                    } else {
                        slot
                    }
                }
                val updatedDays = currentPlan.days.toMutableList()
                updatedDays[currentDayIdx] = activeDay.copy(slots = updatedSlots)
                _weeklyPlan.value = currentPlan.copy(days = updatedDays)
            }.onFailure { err ->
                _errorMessage.value = "Impossibile creare il piatto personalizzato: ${err.localizedMessage}"
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
            _weeklyPlan.value = null
        }
    }
}
