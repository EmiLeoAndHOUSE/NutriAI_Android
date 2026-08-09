package com.nutriai.app

import com.nutriai.app.data.model.ActivityLevel
import com.nutriai.app.data.model.DietGoal
import com.nutriai.app.data.model.Gender
import com.nutriai.app.data.model.UserProfile
import com.nutriai.app.domain.calculator.NutritionalCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionalCalculatorTest {

    @Test
    fun testBmrCalculationForMale() {
        // Uomo, 80kg, 180cm, 30 anni
        // Mifflin-St Jeor: (10 * 80) + (6.25 * 180) - (5 * 30) + 5 = 800 + 1125 - 150 + 5 = 1780
        val bmr = NutritionalCalculator.calculateBMR(
            gender = Gender.MALE,
            weightKg = 80.0,
            heightCm = 180.0,
            age = 30
        )
        assertEquals(1780.0, bmr, 0.1)
    }

    @Test
    fun testBmrCalculationForFemale() {
        // Donna, 60kg, 165cm, 25 anni
        // Mifflin-St Jeor: (10 * 60) + (6.25 * 165) - (5 * 25) - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        val bmr = NutritionalCalculator.calculateBMR(
            gender = Gender.FEMALE,
            weightKg = 60.0,
            heightCm = 165.0,
            age = 25
        )
        assertEquals(1345.25, bmr, 0.1)
    }

    @Test
    fun testMacroTargetForWeightLoss() {
        val user = UserProfile(
            age = 30,
            heightCm = 180.0,
            currentWeightKg = 80.0,
            targetWeightKg = 75.0,
            gender = Gender.MALE,
            activityLevel = ActivityLevel.MODERATE, // multiplier = 1.55
            goal = DietGoal.LOSE_WEIGHT // -400 kcal
        )

        // BMR = 1780
        // TDEE = 1780 * 1.55 = 2759.0
        // Target = 2759 - 400 = 2359 kcal
        val target = NutritionalCalculator.calculateMacroTarget(user)
        assertEquals(2359, target.calories)

        // Verifichiamo che i macro abbiano senso ed entrino nel bilancio energetico approssimativo
        assertTrue(target.proteinGrams > 0)
        assertTrue(target.carbsGrams > 0)
        assertTrue(target.fatGrams > 0)
    }
}
