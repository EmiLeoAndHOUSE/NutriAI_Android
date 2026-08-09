package com.nutriai.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp
import com.nutriai.app.data.model.ActivityLevel
import com.nutriai.app.data.model.DietGoal
import com.nutriai.app.data.model.DietaryType
import com.nutriai.app.data.model.Gender
import com.nutriai.app.data.model.ItalianFoodCatalog
import com.nutriai.app.data.model.MealType
import com.nutriai.app.data.model.UserProfile
import com.nutriai.app.ui.theme.EmeraldGreen

@Composable
fun OnboardingScreen(
    initialProfile: UserProfile,
    onComplete: (UserProfile) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var profile by remember { mutableStateOf(initialProfile) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(onClick = { currentStep-- }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Indietro",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Passo $currentStep di 4",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = currentStep / 4f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = EmeraldGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )


            Spacer(modifier = Modifier.height(24.dp))

            // Step Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "StepTransition"
                ) { step ->
                    when (step) {
                        1 -> StepPhysicalData(profile = profile, onProfileChange = { profile = it })
                        2 -> StepDietaryTypeAndAllergies(profile = profile, onProfileChange = { profile = it })
                        3 -> StepFoodPreferences(profile = profile, onProfileChange = { profile = it })
                        4 -> StepMealSlots(profile = profile, onProfileChange = { profile = it })
                    }
                }
            }

            // Bottom Navigation Button
            Button(
                onClick = {
                    if (currentStep < 4) {
                        currentStep++
                    } else {
                        onComplete(profile)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (currentStep == 4) "Genera la mia Dieta con AI" else "Continua",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (currentStep == 4) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPhysicalData(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Dati Fisici & Obiettivo 🎯",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Inserisci i tuoi parametri per calcolare il fabbisogno calorico preciso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Sesso
        Text("Sesso", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Gender.entries.forEach { gender ->
                val selected = profile.gender == gender
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onProfileChange(profile.copy(gender = gender)) }
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = gender.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input numerici con selezione automatica del testo al tocco per sovrascrittura immediata
        var ageValue by remember {
            mutableStateOf(TextFieldValue(text = if (profile.age > 0) profile.age.toString() else ""))
        }
        var heightValue by remember {
            mutableStateOf(TextFieldValue(text = if (profile.heightCm > 0) profile.heightCm.toInt().toString() else ""))
        }
        var currentWeightValue by remember {
            mutableStateOf(TextFieldValue(text = if (profile.currentWeightKg > 0) profile.currentWeightKg.toString() else ""))
        }
        var targetWeightValue by remember {
            mutableStateOf(TextFieldValue(text = if (profile.targetWeightKg > 0) profile.targetWeightKg.toString() else ""))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = ageValue,
                onValueChange = { newValue ->
                    ageValue = newValue
                    val parsed = newValue.text.toIntOrNull()
                    if (parsed != null) {
                        onProfileChange(profile.copy(age = parsed))
                    }
                },
                label = { Text("Età") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && ageValue.text.isNotEmpty()) {
                            ageValue = ageValue.copy(selection = TextRange(0, ageValue.text.length))
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = heightValue,
                onValueChange = { newValue ->
                    heightValue = newValue
                    val parsed = newValue.text.toDoubleOrNull()
                    if (parsed != null) {
                        onProfileChange(profile.copy(heightCm = parsed))
                    }
                },
                label = { Text("Altezza (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && heightValue.text.isNotEmpty()) {
                            heightValue = heightValue.copy(selection = TextRange(0, heightValue.text.length))
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = currentWeightValue,
                onValueChange = { newValue ->
                    currentWeightValue = newValue
                    val parsed = newValue.text.toDoubleOrNull()
                    if (parsed != null) {
                        onProfileChange(profile.copy(currentWeightKg = parsed))
                    }
                },
                label = { Text("Peso Attuale (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && currentWeightValue.text.isNotEmpty()) {
                            currentWeightValue = currentWeightValue.copy(selection = TextRange(0, currentWeightValue.text.length))
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = targetWeightValue,
                onValueChange = { newValue ->
                    targetWeightValue = newValue
                    val parsed = newValue.text.toDoubleOrNull()
                    if (parsed != null) {
                        onProfileChange(profile.copy(targetWeightKg = parsed))
                    }
                },
                label = { Text("Peso Ideale (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && targetWeightValue.text.isNotEmpty()) {
                            targetWeightValue = targetWeightValue.copy(selection = TextRange(0, targetWeightValue.text.length))
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }



        Spacer(modifier = Modifier.height(20.dp))

        // Obiettivo
        Text("Il tuo Obiettivo", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        DietGoal.entries.forEach { goal ->
            val selected = profile.goal == goal
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onProfileChange(profile.copy(goal = goal)) }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = goal.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Attività
        Text("Stile di Vita & Attività", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        ActivityLevel.entries.forEach { act ->
            val selected = profile.activityLevel == act
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onProfileChange(profile.copy(activityLevel = act)) }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = act.label,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = act.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDietaryTypeAndAllergies(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit
) {
    val commonAllergies = remember { listOf("Lattosio", "Glutine", "Frutta a guscio", "Nichel", "Uova", "Crostacei", "Soia") }
    var customAllergy by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Regime & Intolleranze 🥗",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Seleziona eventuali restrizioni o allergie da escludere dai pasti.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Stile Alimentare", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        DietaryType.entries.forEach { diet ->
            val selected = profile.dietaryType == diet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onProfileChange(profile.copy(dietaryType = diet)) }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = diet.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Allergie o Intolleranze", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonAllergies.forEach { allergy ->
                val selected = profile.allergies.contains(allergy)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val newAllergies = if (selected) {
                            profile.allergies - allergy
                        } else {
                            profile.allergies + allergy
                        }
                        onProfileChange(profile.copy(allergies = newAllergies))
                    },
                    label = { Text(allergy) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customAllergy,
                onValueChange = { customAllergy = it },
                label = { Text("Aggiungi altra intolleranza...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (customAllergy.isNotBlank() && !profile.allergies.contains(customAllergy.trim())) {
                        onProfileChange(profile.copy(allergies = profile.allergies + customAllergy.trim()))
                        customAllergy = ""
                    }
                },
                modifier = Modifier
                    .background(EmeraldGreen, CircleShape)
                    .padding(4.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Aggiungi", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StepFoodPreferences(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val categories = ItalianFoodCatalog.categories

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Cucina Italiana & Preferenze 🇮🇹",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Seleziona gli alimenti che ti piacciono. L'AI userà rigorosamente solo i cibi da te approvati!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Campo di ricerca rapida
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Cerca alimento italiano (es. Pasta, Orata, Zucchine)...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isBlank()) {
            // Selettore per categorie
            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = EmeraldGreen,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = {
                            Text(
                                text = cat.categoryName,
                                fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentCategory = categories.getOrNull(selectedCategoryIndex) ?: categories.first()
            Text(
                text = currentCategory.categoryName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = EmeraldGreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentCategory.items.forEach { food ->
                    val isLiked = profile.likedFoods.contains(food)
                    val isDisliked = profile.dislikedFoods.contains(food)

                    FilterChip(
                        selected = isLiked,
                        onClick = {
                            val newLiked = if (isLiked) profile.likedFoods - food else profile.likedFoods + food
                            val newDisliked = profile.dislikedFoods - food
                            onProfileChange(profile.copy(likedFoods = newLiked, dislikedFoods = newDisliked))
                        },
                        label = { Text(food) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        } else {
            // Risultati della ricerca filtrati
            val filteredFoods = categories.flatMap { it.items }.filter { it.contains(searchQuery, ignoreCase = true) }
            Text(
                text = "Risultati ricerca per \"$searchQuery\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = EmeraldGreen
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredFoods.forEach { food ->
                    val isLiked = profile.likedFoods.contains(food)
                    FilterChip(
                        selected = isLiked,
                        onClick = {
                            val newLiked = if (isLiked) profile.likedFoods - food else profile.likedFoods + food
                            onProfileChange(profile.copy(likedFoods = newLiked))
                        },
                        label = { Text(food) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cibi Graditi Selezionati (Riepilogo)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "I Tuoi Alimenti Graditi Selezionati (${profile.likedFoods.size})",
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (profile.likedFoods.isEmpty()) {
                    Text(
                        text = "Nessun alimento selezionato. (Se non selezioni cibi specifici, l'AI userà solo ingredienti base neutri come Pollo, Riso, Uova e Zucchine).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.likedFoods.forEach { food ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldGreen.copy(alpha = 0.2f),
                                modifier = Modifier.clickable {
                                    onProfileChange(profile.copy(likedFoods = profile.likedFoods - food))
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = food, style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Rimuovi",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun StepMealSlots(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Struttura della Giornata 🕒",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Scegli quali pasti vuoi includere nel tuo programma giornaliero.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        MealType.entries.forEach { mealType ->
            val selected = profile.activeMealTypes.contains(mealType)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val newSlots = if (selected) {
                            if (profile.activeMealTypes.size > 1) profile.activeMealTypes - mealType else profile.activeMealTypes
                        } else {
                            profile.activeMealTypes + mealType
                        }
                        onProfileChange(profile.copy(activeMealTypes = newSlots))
                    }
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mealType.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen)
                    }
                }
            }
        }
    }
}
