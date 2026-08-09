package com.nutriai.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutriai.app.data.model.DayOfWeekPlan
import com.nutriai.app.data.model.MacroTarget
import com.nutriai.app.data.model.MealOption
import com.nutriai.app.data.model.MealSlotPlan
import com.nutriai.app.data.model.MealType
import com.nutriai.app.data.model.UserProfile
import com.nutriai.app.data.model.WeeklyMealPlan
import com.nutriai.app.ui.theme.AccentBlue
import com.nutriai.app.ui.theme.AccentOrange
import com.nutriai.app.ui.theme.AccentPurple
import com.nutriai.app.ui.theme.EmeraldGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userProfile: UserProfile,
    macroTarget: MacroTarget,
    weeklyPlan: WeeklyMealPlan?,
    isLoading: Boolean,
    onSelectDay: (Int) -> Unit,
    onRefreshPlan: () -> Unit,
    onSelectOption: (MealType, Int) -> Unit,
    onRegenerateSlot: (MealType) -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit
) {
    val activeDay = weeklyPlan?.currentDay

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NutriAI 🥑",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Piano Settimanale Personalizzato",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshPlan) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rigenera Settimana",
                            tint = EmeraldGreen
                        )
                    }
                    IconButton(onClick = onEditProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profilo",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading && weeklyPlan == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = EmeraldGreen, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Generazione piano settimanale in corso...",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Gemini AI sta creando 7 giorni di ricette italiane trasparenti e varie...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (weeklyPlan != null && activeDay != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Selettore dei Giorni della Settimana (Lunedì - Domenica)
                    ScrollableTabRow(
                        selectedTabIndex = weeklyPlan.selectedDayIndex,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = EmeraldGreen,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    ) {
                        weeklyPlan.days.forEachIndexed { index, dayPlan ->
                            Tab(
                                selected = weeklyPlan.selectedDayIndex == index,
                                onClick = { onSelectDay(index) },
                                text = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayPlan.dayName,
                                            fontWeight = if (weeklyPlan.selectedDayIndex == index) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = dayPlan.dateString,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (weeklyPlan.selectedDayIndex == index) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Card del Target e Avanzamento Macro del giorno selezionato
                    MacroHeaderCard(
                        target = macroTarget,
                        dayPlan = activeDay,
                        profile = userProfile
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Pasti di ${activeDay.dayName} 🍽️",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = activeDay.dateString, style = MaterialTheme.typography.bodySmall, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    activeDay.slots.forEach { slot ->
                        MealSlotCard(
                            slotPlan = slot,
                            onSelectOption = { idx -> onSelectOption(slot.mealType, idx) },
                            onRegenerateSlot = { onRegenerateSlot(slot.mealType) },
                            isLoading = isLoading
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun MacroHeaderCard(
    target: MacroTarget,
    dayPlan: DayOfWeekPlan,
    profile: UserProfile
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Target Calorico (${dayPlan.dayName})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${dayPlan.totalCalories} / ${target.calories} kcal",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = profile.goal.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val calProgress = (dayPlan.totalCalories.toFloat() / target.calories.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = calProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = EmeraldGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroPill(
                    label = "Proteine",
                    current = dayPlan.totalProtein,
                    target = target.proteinGrams,
                    unit = "g",
                    color = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
                MacroPill(
                    label = "Carboidrati",
                    current = dayPlan.totalCarbs,
                    target = target.carbsGrams,
                    unit = "g",
                    color = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                MacroPill(
                    label = "Grassi",
                    current = dayPlan.totalFat,
                    target = target.fatGrams,
                    unit = "g",
                    color = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MacroPill(
    label: String,
    current: Int,
    target: Int,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$current/$target$unit",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

@Composable
private fun MealSlotCard(
    slotPlan: MealSlotPlan,
    onSelectOption: (Int) -> Unit,
    onRegenerateSlot: () -> Unit,
    isLoading: Boolean
) {
    var expandedRecipe by remember { mutableStateOf(false) }
    val activeOption = slotPlan.selectedOption

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RestaurantMenu,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = slotPlan.mealType.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                activeOption?.let {
                    Text(
                        text = "${it.calories} kcal",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (slotPlan.options.size > 1) {
                TabRow(
                    selectedTabIndex = slotPlan.selectedOptionIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = EmeraldGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .padding(2.dp)
                ) {
                    slotPlan.options.forEachIndexed { index, option ->
                        Tab(
                            selected = slotPlan.selectedOptionIndex == index,
                            onClick = { onSelectOption(index) },
                            text = {
                                Text(
                                    text = "Opzione ${index + 1}",
                                    fontWeight = if (slotPlan.selectedOptionIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            activeOption?.let { option ->
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("P: ${option.proteinGrams}g", style = MaterialTheme.typography.labelMedium, color = AccentOrange)
                    Text("C: ${option.carbsGrams}g", style = MaterialTheme.typography.labelMedium, color = AccentBlue)
                    Text("G: ${option.fatGrams}g", style = MaterialTheme.typography.labelMedium, color = AccentPurple)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Ingredienti:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                option.ingredients.forEach { ing ->
                    Text(text = "• $ing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (option.recipeSteps.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedRecipe = !expandedRecipe }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (expandedRecipe) "Nascondi preparazione" else "Mostra preparazione ricetta",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expandedRecipe) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = EmeraldGreen
                        )
                    }

                    AnimatedVisibility(visible = expandedRecipe) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            option.recipeSteps.forEachIndexed { i, step ->
                                Text(
                                    text = "${i + 1}. $step",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRegenerateSlot,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                enabled = !isLoading
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rigenera questo pasto con AI")
                }
            }
        }
    }
}
