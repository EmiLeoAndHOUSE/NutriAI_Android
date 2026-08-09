package com.nutriai.app.domain.calculator

import com.nutriai.app.data.model.Gender
import com.nutriai.app.data.model.MacroTarget
import com.nutriai.app.data.model.UserProfile
import kotlin.math.roundToInt

object NutritionalCalculator {

    /**
     * Calcola il BMR (Metabolismo Basale) usando la formula Mifflin-St Jeor.
     */
    fun calculateBMR(gender: Gender, weightKg: Double, heightCm: Double, age: Int): Double {
        val baseBMR = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age)
        return if (gender == Gender.MALE) {
            baseBMR + 5.0
        } else {
            baseBMR - 161.0
        }
    }

    /**
     * Calcola il TDEE (Total Daily Energy Expenditure) in base al livello di attività.
     */
    fun calculateTDEE(profile: UserProfile): Double {
        val bmr = calculateBMR(
            gender = profile.gender,
            weightKg = profile.currentWeightKg,
            heightCm = profile.heightCm,
            age = profile.age
        )
        return bmr * profile.activityLevel.multiplier
    }

    /**
     * Calcola il target calorico e la ripartizione dei macronutrienti per il profilo utente.
     */
    fun calculateMacroTarget(profile: UserProfile): MacroTarget {
        val tdee = calculateTDEE(profile)
        val adjustedCalories = (tdee + profile.goal.calorieAdjustment).roundToInt()

        // Soglie minime di sicurezza calorica
        val minSafetyCal = if (profile.gender == Gender.MALE) 1500 else 1200
        val finalCalories = maxOf(adjustedCalories, minSafetyCal)

        // Ripartizione Macro: 30% Proteine, 45% Carboidrati, 25% Grassi
        // 1g Proteine = 4 kcal, 1g Carboidrati = 4 kcal, 1g Grassi = 9 kcal
        val proteinGrams = ((finalCalories * 0.30) / 4.0).roundToInt()
        val fatGrams = ((finalCalories * 0.25) / 9.0).roundToInt()
        val carbsGrams = ((finalCalories * 0.45) / 4.0).roundToInt()

        return MacroTarget(
            calories = finalCalories,
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams
        )
    }
}
