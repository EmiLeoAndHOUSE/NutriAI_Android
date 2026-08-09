package com.nutriai.app.data.remote

import com.nutriai.app.data.model.DayOfWeekPlan
import com.nutriai.app.data.model.ItalianFoodCatalog
import com.nutriai.app.data.model.MacroTarget
import com.nutriai.app.data.model.MealOption
import com.nutriai.app.data.model.MealSlotPlan
import com.nutriai.app.data.model.MealType
import com.nutriai.app.data.model.UserProfile
import com.nutriai.app.data.model.WeeklyMealPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class GeminiApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val dayNames = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")

    /**
     * Genera l'intero piano settimanale (7 giorni) tramite Gemini API o Mock dinamico offline.
     */
    suspend fun generateWeeklyPlan(
        profile: UserProfile,
        target: MacroTarget,
        apiKey: String
    ): Result<WeeklyMealPlan> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            return@withContext Result.success(generateMockWeeklyPlan(profile, target))
        }

        runCatching {
            val prompt = buildWeeklyPlanPrompt(profile, target)
            val jsonResponseString = callGeminiApi(prompt, apiKey)
            parseWeeklyPlanFromJson(jsonResponseString, target, profile)
        }
    }

    /**
     * Rigenera le opzioni per un singolo pasto specificato garantendo ricette fresche e diverse.
     */
    suspend fun regenerateMealSlot(
        profile: UserProfile,
        targetSlotMacro: MacroTarget,
        mealType: MealType,
        apiKey: String
    ): Result<List<MealOption>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            return@withContext Result.success(generateRandomizedMockMealOptions(mealType, targetSlotMacro, profile))
        }

        runCatching {
            val prompt = buildSingleSlotPrompt(profile, targetSlotMacro, mealType)
            val jsonResponseString = callGeminiApi(prompt, apiKey)
            val newOptions = parseMealOptionsFromJson(jsonResponseString)
            if (newOptions.isNotEmpty()) newOptions else generateRandomizedMockMealOptions(mealType, targetSlotMacro, profile)
        }
    }

    private fun buildWeeklyPlanPrompt(profile: UserProfile, target: MacroTarget): String {
        val likedStr = if (profile.likedFoods.isNotEmpty()) profile.likedFoods.joinToString(", ") else "Nessun cibo specifico (usa SOLO alimenti neutri: Pollo, Tacchino, Riso, Pasta, Uova, Zucchine)"
        val dislikedStr = if (profile.dislikedFoods.isNotEmpty()) profile.dislikedFoods.joinToString(", ") else "Nessuno"
        val allergiesStr = if (profile.allergies.isNotEmpty()) profile.allergies.joinToString(", ") else "Nessuna"

        return """
            Sei un nutrizionista esperto di cucina italiana. Genera un PIANO ALIMENTARE SETTIMANALE COMPLETO (da Lunedì a Domenica, 7 giorni) in formato JSON.
            PARAMETRI UTENTE:
            - Calorie Target Giornaliere: ${target.calories} kcal
            - Proteine Target: ${target.proteinGrams}g
            - Carboidrati Target: ${target.carbsGrams}g
            - Grassi Target: ${target.fatGrams}g
            - Stile Alimentare: ${profile.dietaryType.label}
            - ALLERGIE/INTOLLERANZE (RIGOROSAMENTE VIETATE): $allergiesStr
            - CIBI GRADITI (USA RIGOROSAMENTE QUESTI ALIMENTI): $likedStr
            - CIBI SGRADITI (DA ESCLUDERE TASSATIVAMENTE): $dislikedStr
            - Pasti richiesti per giorno: ${profile.activeMealTypes.joinToString { it.name }}

            REGOLE IMPERATIVE PER LA VARIETÀ SETTIMANALE:
            1. OGNI GIORNO (Lunedì, Martedì, Mercoledì, Giovedì, Venerdì, Sabato, Domenica) DEVE AVERE RICETTE COMPLETAMENTE DIVERSE.
            2. Varia le fonti di carboidrati tra i giorni (es. Lunedì Pasta, Martedì Riso, Mercoledì Gnocchi, Giovedì Farro, Venerdì Polenta/Riso, Sabato Piadina, Domenica Patate).
            3. NON INSERIRE MAI ALIMENTI SPECIFICI CARATTERIZZANTI (come Salmone, Gorgonzola, Avocado, Fegato, Pesce Spada, Crostacei) SE L'UTENTE NON LI HA ESPLICITAMENTE SELEZIONATI TRA I CIBI GRADITI!

            REQUISITI FORMATO RISPOSTA JSON:
            Restituisci ESCLUSIVAMENTE un oggetto JSON valido con la seguente struttura:
            {
              "days": [
                {
                  "dayName": "Lunedì",
                  "slots": [
                    {
                      "mealType": "BREAKFAST",
                      "options": [
                        {
                          "title": "Titolo Piatto",
                          "description": "Descrizione",
                          "calories": 400,
                          "proteinGrams": 25,
                          "carbsGrams": 45,
                          "fatGrams": 12,
                          "ingredients": ["Ingrediente 1 con peso"],
                          "recipeSteps": ["Step 1"]
                        },
                        {
                          "title": "Seconda Alternativa",
                          "description": "...",
                          "calories": 400,
                          "proteinGrams": 25,
                          "carbsGrams": 45,
                          "fatGrams": 12,
                          "ingredients": ["..."],
                          "recipeSteps": ["..."]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildSingleSlotPrompt(profile: UserProfile, slotTarget: MacroTarget, mealType: MealType): String {
        val randomSeed = Random.nextInt(1000, 9999)
        return """
            Genera 2 NUOVE ED INEDITI alternative gustose (Seed: $randomSeed) per il pasto: ${mealType.label} (${mealType.name}).
            TARGET PER QUESTO PASTO:
            - Calorie: ~${slotTarget.calories} kcal
            - Proteine: ~${slotTarget.proteinGrams}g
            - Carboidrati: ~${slotTarget.carbsGrams}g
            - Grassi: ~${slotTarget.fatGrams}g
            - Allergie da evitare: ${profile.allergies.joinToString().ifEmpty { "Nessuna" }}
            - Cibi graditi preferiti: ${profile.likedFoods.joinToString().ifEmpty { "Alimenti neutri italiani" }}

            Rispondi ESCLUSIVAMENTE con un array JSON di 2 oggetti MealOption:
            [
              {
                "title": "Nome piatto originale",
                "description": "Descrizione",
                "calories": ${slotTarget.calories},
                "proteinGrams": ${slotTarget.proteinGrams},
                "carbsGrams": ${slotTarget.carbsGrams},
                "fatGrams": ${slotTarget.fatGrams},
                "ingredients": ["Ingrediente 1", "Ingrediente 2"],
                "recipeSteps": ["Step 1", "Step 2"]
              }
            ]
        """.trimIndent()
    }

    private fun callGeminiApi(prompt: String, apiKey: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val requestBodyJson = """
            {
              "contents": [{
                "parts": [{"text": ${JSONObjectEscape(prompt)}}]
              }],
              "generationConfig": {
                "temperature": 0.85,
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("Errore API Gemini (${response.code}): $bodyString")
            }

            val rootObj = json.parseToJsonElement(bodyString).jsonObject
            val candidates = rootObj["candidates"]?.jsonArray
            val textContent = candidates?.getOrNull(0)?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.getOrNull(0)?.jsonObject
                ?.get("text")?.jsonPrimitive?.content ?: ""

            return textContent.trim()
        }
    }

    private fun JSONObjectEscape(string: String): String {
        val sb = StringBuilder("\"")
        for (c in string) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private fun parseWeeklyPlanFromJson(
        rawJson: String,
        target: MacroTarget,
        profile: UserProfile
    ): WeeklyMealPlan {
        val cleanJson = cleanJsonResponse(rawJson)
        val root = json.parseToJsonElement(cleanJson).jsonObject
        val daysArray = root["days"]?.jsonArray ?: JsonArray(emptyList())

        val dayPlans = mutableListOf<DayOfWeekPlan>()
        val cal = Calendar.getInstance()

        daysArray.forEachIndexed { idx, dayElem ->
            val dayObj = dayElem.jsonObject
            val dayName = dayObj["dayName"]?.jsonPrimitive?.content ?: dayNames.getOrElse(idx) { "Giorno ${idx + 1}" }
            val slotsArray = dayObj["slots"]?.jsonArray ?: JsonArray(emptyList())

            val slotPlans = mutableListOf<MealSlotPlan>()
            for (slotElem in slotsArray) {
                val slotObj = slotElem.jsonObject
                val mealTypeName = slotObj["mealType"]?.jsonPrimitive?.content ?: "LUNCH"
                val mealType = runCatching { MealType.valueOf(mealTypeName) }.getOrDefault(MealType.LUNCH)
                val optionsArray = slotObj["options"]?.jsonArray ?: JsonArray(emptyList())
                val options = parseMealOptionsFromArray(optionsArray)

                slotPlans.add(MealSlotPlan(mealType = mealType, options = options, selectedOptionIndex = 0))
            }

            val dateStr = SimpleDateFormat("dd MMMM", Locale.ITALIAN).format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)

            dayPlans.add(
                DayOfWeekPlan(
                    dayName = dayName,
                    dateString = dateStr,
                    target = target,
                    slots = if (slotPlans.isNotEmpty()) slotPlans else generateMockDailyPlanSlots(profile.activeMealTypes, target, profile, idx)
                )
            )
        }

        return WeeklyMealPlan(
            days = if (dayPlans.isNotEmpty()) dayPlans else generateMockWeeklyPlan(profile, target).days,
            selectedDayIndex = 0
        )
    }

    private fun parseMealOptionsFromJson(rawJson: String): List<MealOption> {
        val cleanJson = cleanJsonResponse(rawJson)
        val element = json.parseToJsonElement(cleanJson)
        return if (element is JsonArray) {
            parseMealOptionsFromArray(element)
        } else if (element is JsonObject && element.containsKey("options")) {
            parseMealOptionsFromArray(element["options"]!!.jsonArray)
        } else {
            emptyList()
        }
    }

    private fun parseMealOptionsFromArray(array: JsonArray): List<MealOption> {
        val list = mutableListOf<MealOption>()
        for (item in array) {
            val obj = item.jsonObject
            list.add(
                MealOption(
                    title = obj["title"]?.jsonPrimitive?.content ?: "Piatto Equilibrato",
                    description = obj["description"]?.jsonPrimitive?.content ?: "Ricetta sana e bilanciata.",
                    calories = obj["calories"]?.jsonPrimitive?.int ?: 400,
                    proteinGrams = obj["proteinGrams"]?.jsonPrimitive?.int ?: 25,
                    carbsGrams = obj["carbsGrams"]?.jsonPrimitive?.int ?: 45,
                    fatGrams = obj["fatGrams"]?.jsonPrimitive?.int ?: 12,
                    ingredients = obj["ingredients"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    recipeSteps = obj["recipeSteps"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                )
            )
        }
        return list
    }

    private fun cleanJsonResponse(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```json")) s = s.removePrefix("```json")
        else if (s.startsWith("```")) s = s.removePrefix("```")
        if (s.endsWith("```")) s = s.removeSuffix("```")
        return s.trim()
    }

    // --- GENERATORE DI MOCK SETTIMANALE DINAMICO E VARIABILE ---

    private fun generateMockWeeklyPlan(profile: UserProfile, target: MacroTarget): WeeklyMealPlan {
        val cal = Calendar.getInstance()
        val days = dayNames.mapIndexed { idx, dayName ->
            val dateStr = SimpleDateFormat("dd MMMM", Locale.ITALIAN).format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)

            DayOfWeekPlan(
                dayName = dayName,
                dateString = dateStr,
                target = target,
                slots = generateMockDailyPlanSlots(profile.activeMealTypes, target, profile, dayIndex = idx)
            )
        }
        return WeeklyMealPlan(days = days, selectedDayIndex = 0)
    }

    private fun generateMockDailyPlanSlots(
        activeTypes: List<MealType>,
        target: MacroTarget,
        profile: UserProfile,
        dayIndex: Int
    ): List<MealSlotPlan> {
        return activeTypes.map { mealType ->
            val slotMacro = MacroTarget(
                calories = (target.calories / activeTypes.size),
                proteinGrams = (target.proteinGrams / activeTypes.size),
                carbsGrams = (target.carbsGrams / activeTypes.size),
                fatGrams = (target.fatGrams / activeTypes.size)
            )
            MealSlotPlan(
                mealType = mealType,
                options = generateDaySpecificMockMealOptions(mealType, slotMacro, profile, dayIndex),
                selectedOptionIndex = 0
            )
        }
    }

    private fun generateDaySpecificMockMealOptions(
        mealType: MealType,
        slotMacro: MacroTarget,
        profile: UserProfile,
        dayIndex: Int
    ): List<MealOption> {
        val likesSalmon = profile.likedFoods.any { it.contains("Salmone", ignoreCase = true) }

        // Varietà di carboidrati e proteine per i 7 giorni
        val carbsList = listOf("Pasta Integrale", "Riso Basmati", "Gnocchi di Patate", "Farro con Verdure", "Riso Venere", "Piadina Integrale", "Patate al forno")
        val proteinList = listOf("Petto di Pollo", "Fesa di Tacchino", "Filetto di Orata", "Uova strapazzate", "Bresaola della Valtellina", "Mozzarella di Bufala", "Ricotta Magra")

        val dayCarb = carbsList.getOrElse(dayIndex % carbsList.size) { "Pasta Integrale" }
        val dayProtein = if (likesSalmon && dayIndex == 4) "Salmone Fresco" else proteinList.getOrElse(dayIndex % proteinList.size) { "Petto di Pollo" }

        return when (mealType) {
            MealType.BREAKFAST -> listOf(
                MealOption(
                    title = if (dayIndex % 2 == 0) "Porridge Avena e Frutti di Bosco" else "Pancake Proteici alla Banana",
                    description = "Colazione energetica bilanciata.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("60g Fiocchi d'avena", "150ml Latte scremato", "100g Frutti di bosco"),
                    recipeSteps = listOf("Scalda il latte con l'avena per 5 minuti e servire caldo.")
                ),
                MealOption(
                    title = "Toast Integrale con Ricotta e Miele",
                    description = "Alternativa soffice e gustosa.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("60g Pane integrale tostato", "80g Ricotta magra", "10g Miele"),
                    recipeSteps = listOf("Tosta il pane e spalma la ricotta con miele a filo.")
                )
            )
            MealType.LUNCH -> listOf(
                MealOption(
                    title = "$dayCarb con $dayProtein e Zucchine",
                    description = "Pranzo completo ed equilibrato secondo le tue preferenze.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("80g $dayCarb", "160g $dayProtein", "1 Zucchina", "1 cucchiaio Olio EVO"),
                    recipeSteps = listOf("Cuoci $dayCarb in acqua salata.", "Salta $dayProtein con zucchine ed olio EVO e servire caldo.")
                ),
                MealOption(
                    title = "Bowl di Quinoa con Tacchino e Pomodorini",
                    description = "Alternativa fresca e ricca di nutrienti.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("70g Quinoa", "150g Fesa di Tacchino", "Pomodorini", "Olio EVO"),
                    recipeSteps = listOf("Cuoci la quinoa e condisci con tacchino e pomodorini freschi.")
                )
            )
            MealType.DINNER -> listOf(
                MealOption(
                    title = "Filetto di $dayProtein al Cartoccio con Insalata",
                    description = "Cena leggera e ad alta digeribilità.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("180g $dayProtein", "Insalata mista", "60g Pane integrale", "1 cucchiaio Olio EVO"),
                    recipeSteps = listOf("Cuoci in forno o piastra $dayProtein con erbe aromatiche e servi con insalata.")
                ),
                MealOption(
                    title = "Omelette alle Erbe con Verdure Grigliate",
                    description = "Cena rapida e proteica.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("2 Uova + 100g Albumi", "Melanzane e Zucchine grigliate", "Olio EVO"),
                    recipeSteps = listOf("Cuoci l'omelette in padella e servire con verdure grigliate.")
                )
            )
            else -> listOf(
                MealOption(
                    title = "Yogurt Greco 0% con Mandorle e Mela",
                    description = "Spuntino spezza-fame saziante.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("170g Yogurt Greco 0%", "15g Mandorle", "1 Mela"),
                    recipeSteps = listOf("Unisci lo yogurt con frutta e mandorle tritate.")
                ),
                MealOption(
                    title = "Frullato Proteico Banana e Cacao",
                    description = "Merenda energetica rapida.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("200ml Latte vegetale", "1 Banana", "15g Cacao amaro", "20g Proteine"),
                    recipeSteps = listOf("Frulla tutto per 30 secondi e servire fresco.")
                )
            )
        }
    }

    private fun generateRandomizedMockMealOptions(
        mealType: MealType,
        slotMacro: MacroTarget,
        profile: UserProfile
    ): List<MealOption> {
        val randomCarbs = listOf("Pasta Integrale", "Riso Basmati", "Gnocchi", "Farro", "Riso Venere", "Cuscus", "Patate dolce").shuffled().first()
        val randomProtein = listOf("Petto di Pollo", "Fesa di Tacchino", "Filetto di Orata", "Merluzzo", "Uova strapazzate", "Ricotta magra").shuffled().first()
        val randomVeg = listOf("Zucchine", "Spinaci", "Pomodori", "Broccoli", "Asparagi", "Finocchi").shuffled().first()

        val newTitle1 = "$randomCarbs con $randomProtein e $randomVeg"
        val newTitle2 = "Insalata calda di $randomCarbs, $randomProtein e $randomVeg"

        return listOf(
            MealOption(
                title = newTitle1,
                description = "Nuova combinazione bilanciata rigenerata per le tue preferenze.",
                calories = slotMacro.calories,
                proteinGrams = slotMacro.proteinGrams,
                carbsGrams = slotMacro.carbsGrams,
                fatGrams = slotMacro.fatGrams,
                ingredients = listOf("80g $randomCarbs", "160g $randomProtein", "100g $randomVeg", "1 cucchiaio Olio EVO"),
                recipeSteps = listOf("Cuoci $randomCarbs al dente.", "Spadella $randomProtein con $randomVeg in olio EVO ed unisci i componenti.")
            ),
            MealOption(
                title = newTitle2,
                description = "Alternativa fresca e gustosa rigenerata dall'AI.",
                calories = slotMacro.calories,
                proteinGrams = slotMacro.proteinGrams,
                carbsGrams = slotMacro.carbsGrams,
                fatGrams = slotMacro.fatGrams,
                ingredients = listOf("75g $randomCarbs", "150g $randomProtein", "120g $randomVeg", "1 cucchiaio Olio EVO"),
                recipeSteps = listOf("Griglia $randomProtein.", "Lessa $randomCarbs e mescola con $randomVeg ed olio a crudo.")
            )
        )
    }
}
