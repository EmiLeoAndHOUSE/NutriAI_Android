package com.nutriai.app.data.model

import kotlinx.serialization.Serializable

enum class Gender(val label: String) {
    MALE("Uomo"),
    FEMALE("Donna")
}

enum class ActivityLevel(val label: String, val multiplier: Double, val description: String) {
    SEDENTARY("Sedentario", 1.2, "Poco o nessun esercizio, lavoro d'ufficio"),
    LIGHT("Leggermente Attivo", 1.375, "Esercizio leggero 1-3 giorni/settimana"),
    MODERATE("Moderatamente Attivo", 1.55, "Esercizio moderato 3-5 giorni/settimana"),
    ACTIVE("Molto Attivo", 1.725, "Esercizio intenso 6-7 giorni/settimana"),
    VERY_ACTIVE("Estremamente Attivo", 1.9, "Attività fisica molto pesante o lavoro atletico")
}

enum class DietGoal(val label: String, val calorieAdjustment: Int) {
    LOSE_WEIGHT("Perdere Peso (Deficit)", -400),
    MAINTAIN("Mantenere il Peso", 0),
    GAIN_WEIGHT("Aumentare Massa (Surplus)", 350)
}

enum class DietaryType(val label: String) {
    EVERYTHING("Onnivoro (Tutto)"),
    VEGETARIAN("Vegetariano"),
    VEGAN("Vegano"),
    KETO("Chetogenico"),
    PESCATARIAN("Pescetariano")
}

enum class MealType(val label: String) {
    BREAKFAST("Colazione"),
    MORNING_SNACK("Spuntino Mattina"),
    LUNCH("Pranzo"),
    AFTERNOON_SNACK("Merenda"),
    DINNER("Cena")
}

@Serializable
data class UserProfile(
    val age: Int = 28,
    val heightCm: Double = 175.0,
    val currentWeightKg: Double = 75.0,
    val targetWeightKg: Double = 70.0,
    val gender: Gender = Gender.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: DietGoal = DietGoal.LOSE_WEIGHT,
    val dietaryType: DietaryType = DietaryType.EVERYTHING,
    val allergies: List<String> = emptyList(),
    val likedFoods: List<String> = emptyList(),
    val dislikedFoods: List<String> = emptyList(),
    val activeMealTypes: List<MealType> = listOf(
        MealType.BREAKFAST,
        MealType.MORNING_SNACK,
        MealType.LUNCH,
        MealType.AFTERNOON_SNACK,
        MealType.DINNER
    )
)

@Serializable
data class MacroTarget(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int
)

@Serializable
data class MealOption(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val ingredients: List<String>,
    val recipeSteps: List<String>
)

@Serializable
data class MealSlotPlan(
    val mealType: MealType,
    val options: List<MealOption>,
    val selectedOptionIndex: Int = 0
) {
    val selectedOption: MealOption?
        get() = options.getOrNull(selectedOptionIndex)
}

@Serializable
data class DailyMealPlan(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dateString: String,
    val target: MacroTarget,
    val slots: List<MealSlotPlan>
) {
    val totalCalories: Int
        get() = slots.mapNotNull { it.selectedOption?.calories }.sum()

    val totalProtein: Int
        get() = slots.mapNotNull { it.selectedOption?.proteinGrams }.sum()

    val totalCarbs: Int
        get() = slots.mapNotNull { it.selectedOption?.carbsGrams }.sum()

    val totalFat: Int
        get() = slots.mapNotNull { it.selectedOption?.fatGrams }.sum()
}
