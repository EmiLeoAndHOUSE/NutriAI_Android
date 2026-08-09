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
     * Genera un piatto personalizzato bilanciato in base alla richiesta specifica espressa dall'utente.
     */
    suspend fun generateCustomUserMealOption(
        profile: UserProfile,
        targetSlotMacro: MacroTarget,
        mealType: MealType,
        userDesire: String,
        apiKey: String
    ): Result<MealOption> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            return@withContext Result.success(generateMockCustomMealOption(mealType, targetSlotMacro, userDesire))
        }

        runCatching {
            val prompt = buildCustomDesirePrompt(profile, targetSlotMacro, mealType, userDesire)
            val jsonResponseString = callGeminiApi(prompt, apiKey)
            val options = parseMealOptionsFromJson(jsonResponseString)
            options.firstOrNull()?.copy(isCustom = true) ?: generateMockCustomMealOption(mealType, targetSlotMacro, userDesire)
        }
    }

    /**
     * Rigenera le opzioni per un singolo pasto specificato garantendo ricette rigorosamente adatte a quel momento della giornata.
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
        val likedStr = if (profile.likedFoods.isNotEmpty()) profile.likedFoods.joinToString(", ") else "Nessun cibo specifico (usa SOLO alimenti tradizionali della cucina italiana)"
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

            REGOLE IMPERATIVE PER IL MOMENTO DELLA GIORNATA:
            1. COLAZIONE (BREAKFAST): Solo ed unicamente cibi da colazione italiana (Porridge, Pancake, Toast dolci/salati con ricotta o uova, Yogurt greco con frutta/frutta secca, Fette biscottate). MAI PASTA, RISO SALATO, CARNE O PESCE A COLAZIONE!
            2. SPUNTINI (SNACK_MORNING / SNACK_AFTERNOON): Solo spuntini spezza-fame sani italiani (Yogurt greco, frutta fresca, tostino con affettato magro, parmigiano, frullato).
            3. PRANZO (LUNCH): Primi piatti bilanciati o piatti unici (Pasta, Riso, Farro, Gnocchi, Polenta con proteine magre e verdure).
            4. CENA (DINNER): Secondi piatti proteici (pesce, carne magra, uova) con contorno ed una quota di carboidrati moderata.
            5. OGNI GIORNO DEVE AVERE RICETTE COMPLETAMENTE DIVERSE.
            6. NON INSERIRE MAI ALIMENTI CARATTERIZZANTI (come Salmone, Gorgonzola, Avocado, Fegato) SE L'UTENTE NON LI HA ESPLICITAMENTE SELEZIONATI TRA I CIBI GRADITI!

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
        val mealSpecificInstruction = when (mealType) {
            MealType.BREAKFAST -> "QUESTO PASTO È LA COLAZIONE. Proponi unicamente alimenti tipici da COLAZIONE della tradizione italiana (es. Porridge, Pancake proteici, Toast dolci o salati con uova/ricotta, Yogurt greco con frutta fresca/secca, Fette biscottate con marmellata, Cappuccino/latte vegetale). NON PROPORRE MAI PIATTI DA PRANZO O CENA (Tassativamente vietati gnocchi, pasta, riso salato, carne rossa/bianca, pesce a colazione!)."
            MealType.SNACK_MORNING, MealType.SNACK_AFTERNOON -> "QUESTO PASTO È UNO SPUNTINO. Proponi unicamente spuntini spezza-fame italiani sani e veloci (es. Yogurt greco con mandorle/noci e frutto, tostino con affettato magro, parmigiano con frutto, frullato proteico). NON PROPORRE PIATTI DA PRANZO/CENA."
            MealType.LUNCH -> "QUESTO PASTO È IL PRANZO. Proponi un primo piatto bilanciato o piatto unico della cucina italiana (es. Riso, Pasta integrale, Farro, Gnocchi, Polenta conditi con fonti proteiche e verdure)."
            MealType.DINNER -> "QUESTO PASTO È LA CENA. Proponi un secondo piatto proteico della cucina italiana (pesce, carne magra, uova) accompagnato da abbondanti verdure ed una fonte moderata di carboidrati (pane integrale, patate al forno, riso basmati)."
        }

        return """
            Genera 2 NUOVE ED INEDITI alternative (Seed: $randomSeed) per il pasto: ${mealType.label} (${mealType.name}).
            $mealSpecificInstruction

            TARGET PER QUESTO PASTO:
            - Calorie: ~${slotTarget.calories} kcal
            - Proteine: ~${slotTarget.proteinGrams}g
            - Carboidrati: ~${slotTarget.carbsGrams}g
            - Grassi: ~${slotTarget.fatGrams}g
            - Allergie da evitare: ${profile.allergies.joinToString().ifEmpty { "Nessuna" }}
            - Cibi graditi preferiti: ${profile.likedFoods.joinToString().ifEmpty { "Alimenti tradizionali della cucina italiana" }}

            Rispondi ESCLUSIVAMENTE con un array JSON di 2 oggetti MealOption.
        """.trimIndent()
    }

    private fun buildCustomDesirePrompt(profile: UserProfile, slotTarget: MacroTarget, mealType: MealType, userDesire: String): String {
        return """
            L'utente ha espresso il desiderio specifico di mangiare: "$userDesire" per il pasto ${mealType.label} (${mealType.name}).
            Crea un piatto sano e bilanciato che soddisfi questa richiesta e rispetti al grammo i seguenti macro target per questo pasto:
            - Calorie: ~${slotTarget.calories} kcal
            - Proteine: ~${slotTarget.proteinGrams}g
            - Carboidrati: ~${slotTarget.carbsGrams}g
            - Grassi: ~${slotTarget.fatGrams}g
            - ALLERGIE DA EVITARE: ${profile.allergies.joinToString().ifEmpty { "Nessuna" }}

            Rispondi ESCLUSIVAMENTE con un array JSON contenente 1 oggetto MealOption:
            [
              {
                "title": "$userDesire (Versione Bilanciata)",
                "description": "Come questo piatto è stato bilanciato per te per soddisfare la tua voglia di $userDesire a ${mealType.label}.",
                "calories": ${slotTarget.calories},
                "proteinGrams": ${slotTarget.proteinGrams},
                "carbsGrams": ${slotTarget.carbsGrams},
                "fatGrams": ${slotTarget.fatGrams},
                "ingredients": ["Ingrediente 1 con dose esatta", "Ingrediente 2 con dose esatta"],
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

    // --- GENERATORE DI MOCK SETTIMANALE ED OFFERTA PASTI SPECIFICA PER IL MOMENTO DELLA GIORNATA ---

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

        return when (mealType) {
            MealType.BREAKFAST -> listOf(
                MealOption(
                    title = if (dayIndex % 2 == 0) "Porridge Avena e Frutti di Bosco" else "Pancake Proteici alla Banana",
                    description = "Colazione energetica bilanciata della tradizione italiana.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("60g Fiocchi d'avena", "150ml Latte scremato", "100g Frutti di bosco"),
                    recipeSteps = listOf("Scalda il latte con l'avena per 5 minuti e servire caldo con frutti di bosco.")
                ),
                MealOption(
                    title = "Toast Integrale con Ricotta Magra e Miele",
                    description = "Alternativa soffice e gustosa per colazione.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("60g Pane integrale tostato", "80g Ricotta magra", "10g Miele"),
                    recipeSteps = listOf("Tosta il pane integrale e spalma la ricotta con il miele a filo.")
                )
            )
            MealType.SNACK_MORNING, MealType.SNACK_AFTERNOON -> listOf(
                MealOption(
                    title = "Yogurt Greco 0% con Mandorle e Mela",
                    description = "Spuntino spezza-fame saziante e ricco di proteine.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("170g Yogurt Greco 0%", "15g Mandorle", "1 Mela"),
                    recipeSteps = listOf("Mescola lo yogurt con la mela a cubetti e le mandorle tritate.")
                ),
                MealOption(
                    title = "Tostino Integrale con Bresaola e Rucola",
                    description = "Merenda salata veloce ed ad alto tenore proteico.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("40g Pane integrale", "50g Bresaola", "Rucola", "1 cucchiaino Olio EVO"),
                    recipeSteps = listOf("Farcisci il pane con bresaola e rucola ed un filo d'olio EVO.")
                )
            )
            MealType.LUNCH -> {
                val carbsList = listOf("Pasta Integrale", "Riso Basmati", "Gnocchi di Patate", "Farro integrale", "Riso Venere")
                val proteinList = listOf("Petto di Pollo", "Fesa di Tacchino", "Filetto di Orata", "Uova strapazzate", "Bresaola")
                val dayCarb = carbsList.getOrElse(dayIndex % carbsList.size) { "Pasta Integrale" }
                val dayProtein = proteinList.getOrElse(dayIndex % proteinList.size) { "Petto di Pollo" }

                listOf(
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
                        description = "Alternativa fresca e ricca di nutrienti per il pranzo.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("70g Quinoa", "150g Fesa di Tacchino", "Pomodorini", "Olio EVO"),
                        recipeSteps = listOf("Cuoci la quinoa e condisci con tacchino e pomodorini freschi.")
                    )
                )
            }
            MealType.DINNER -> {
                val proteinList = listOf("Filetto di Spigola", "Petto di Tacchino", "Tagliata di Pollo", "Omelette alle Erbe")
                val dayProtein = if (likesSalmon && dayIndex == 4) "Salmone al Cartoccio" else proteinList.getOrElse(dayIndex % proteinList.size) { "Filetto di Spigola" }

                listOf(
                    MealOption(
                        title = "$dayProtein al Cartoccio con Insalata e Pane Integrale",
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
            }
        }
    }

    private fun generateRandomizedMockMealOptions(
        mealType: MealType,
        slotMacro: MacroTarget,
        profile: UserProfile
    ): List<MealOption> {
        val likesSalmon = profile.likedFoods.any { it.contains("Salmone", ignoreCase = true) }

        return when (mealType) {
            MealType.BREAKFAST -> {
                val breakfastPool = listOf(
                    MealOption(
                        title = "Porridge caldo d'Avena con Mirtilli e Mandorle",
                        description = "Nuova combinazione da colazione ricca di fibre e grassi sani.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("60g Fiocchi d'avena", "150ml Latte scremato", "80g Mirtilli freschi", "15g Mandorle tritate", "1 cucchiaino Miele"),
                        recipeSteps = listOf("Cuoci l'avena nel latte caldo per 5 minuti.", "Servi con mirtilli freschi e mandorle tritate.")
                    ),
                    MealOption(
                        title = "Pancake Proteici al Cacao con Banana a Fette",
                        description = "Colazione sfiziosa e proteica rigenerata per te.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("50g Farina d'avena", "100ml Albumi", "1 Banana", "10g Cacao amaro", "10g Noci"),
                        recipeSteps = listOf("Schiaccia la banana con albumi, farina e cacao.", "Cuoci in padella antiaderente 2 min per lato.")
                    ),
                    MealOption(
                        title = "Toast Integrale con Ricotta Magra e Miele d'Acacia",
                        description = "Colazione dolce tradizionale e bilanciata.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("60g Pane integrale tostato", "80g Ricotta magra", "15g Miele", "1 Spicchio d'arancia"),
                        recipeSteps = listOf("Tosta il pane integrale.", "Spalma la ricotta e guarnisci con miele d'acacia.")
                    ),
                    MealOption(
                        title = "Coppetta di Yogurt Greco 0% con Cereali Integrali e Noci",
                        description = "Colazione fresca e veloce ricca di probiotici.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("170g Yogurt Greco 0%", "40g Cereali integrali d'avena", "15g Noci aperte", "1 Mela a cubetti"),
                        recipeSteps = listOf("Unisci lo yogurt con i cereali integrali, le noci e i pezzetti di mela.")
                    )
                ).shuffled()
                breakfastPool.take(2)
            }
            MealType.SNACK_MORNING, MealType.SNACK_AFTERNOON -> {
                val snackPool = listOf(
                    MealOption(
                        title = "Yogurt Greco 0% con Noci e Mela Croccante",
                        description = "Spuntino spezza-fame ad alto potere saziante.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("170g Yogurt Greco 0%", "15g Noci", "1 Mela croccante"),
                        recipeSteps = listOf("Mescola lo yogurt con le noci tritate e la mela a cubetti.")
                    ),
                    MealOption(
                        title = "Tostino Integrale con Bresaola e Scaglie di Parmigiano",
                        description = "Spuntino salato rapido ricchissimo di proteine.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("40g Pane integrale", "50g Bresaola della Valtellina", "10g Scaglie di Parmigiano", "1 cucchiaino Olio EVO"),
                        recipeSteps = listOf("Farcisci il pane con bresaola e parmigiano ed un filo d'olio EVO.")
                    ),
                    MealOption(
                        title = "Frullato Proteico alla Banana e Latte d'Avena",
                        description = "Merenda rigenerante e saziante.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("200ml Latte d'avena", "1 Banana", "20g Proteine in polvere o albumi", "10g Mandorle"),
                        recipeSteps = listOf("Frulla tutti gli ingredienti fino ad ottenere una consistenza omogenea.")
                    )
                ).shuffled()
                snackPool.take(2)
            }
            MealType.LUNCH -> {
                val randomCarbs = listOf("Pasta Integrale", "Riso Basmati", "Gnocchi di Patate", "Farro", "Riso Venere").shuffled().first()
                val randomProtein = listOf("Petto di Pollo", "Fesa di Tacchino", "Filetto di Orata", "Merluzzo", "Uova strapazzate", "Ricotta magra").shuffled().first()
                val randomVeg = listOf("Zucchine", "Spinaci", "Pomodori", "Broccoli", "Asparagi").shuffled().first()

                listOf(
                    MealOption(
                        title = "$randomCarbs con $randomProtein e $randomVeg",
                        description = "Nuova combinazione di pranzo bilanciata rigenerata per le tue preferenze.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g $randomCarbs", "160g $randomProtein", "100g $randomVeg", "1 cucchiaio Olio EVO"),
                        recipeSteps = listOf("Cuoci $randomCarbs al dente.", "Spadella $randomProtein con $randomVeg in olio EVO ed unisci i componenti.")
                    ),
                    MealOption(
                        title = "Insalata calda di $randomCarbs, $randomProtein e $randomVeg",
                        description = "Alternativa fresca e gustosa rigenerata per il pranzo.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("75g $randomCarbs", "150g $randomProtein", "120g $randomVeg", "1 cucchiaio Olio EVO"),
                        recipeSteps = listOf("Griglia $randomProtein.", "Lessa $randomCarbs e mescola con $randomVeg ed olio a crudo.")
                    )
                )
            }
            MealType.DINNER -> {
                val mainProtein = if (likesSalmon) "Salmone al Cartoccio" else listOf("Filetto di Spigola", "Tagliata di Petto di Tacchino", "Omelette ai Funghi", "Merluzzo al Vapore").shuffled().first()
                val mainSide = listOf("Patate al forno ed insalata", "Verdure grigliate e pane integrale", "Spinaci al vapore e pane di segale").shuffled().first()

                listOf(
                    MealOption(
                        title = "$mainProtein con $mainSide",
                        description = "Cena leggera e ad alta digeribilità rigenerata per te.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g Fonte proteica ($mainProtein)", "Contorno ($mainSide)", "1 cucchiaio Olio EVO"),
                        recipeSteps = listOf("Cuoci $mainProtein al forno o alla piastra con erbe aromatiche.", "Accompagna con il contorno fresco ed un filo d'olio EVO.")
                    ),
                    MealOption(
                        title = "Omelette alle Erbe con Verdure Grigliate e Pane Integrale",
                        description = "Cena rapida, calda e proteica.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("2 Uova + 100ml Albumi", "Melanzane e Zucchine grigliate", "50g Pane integrale", "Olio EVO"),
                        recipeSteps = listOf("Cuoci l'omelette in padella e servire con verdure grigliate e pane tostato.")
                    )
                )
            }
        }
    }

    private fun generateMockCustomMealOption(mealType: MealType, slotMacro: MacroTarget, userDesire: String): MealOption {
        val formattedTitle = userDesire.trim().replaceFirstChar { it.uppercase() }
        return MealOption(
            title = "$formattedTitle (Versione Bilanciata NutriAI)",
            description = "Piatto creato su tua richiesta specifica, dosato per rientrare perfettamente nel tuo target nutrizionale di ${mealType.label}.",
            calories = slotMacro.calories,
            proteinGrams = slotMacro.proteinGrams,
            carbsGrams = slotMacro.carbsGrams,
            fatGrams = slotMacro.fatGrams,
            ingredients = listOf(
                "Ingrediente principale per $formattedTitle (dose calibrata)",
                "Fonte proteica/carboidrato di supporto per il bilanciamento",
                "1 cucchiaio Olio EVO o condimento a scelta"
            ),
            recipeSteps = listOf(
                "Prepara $formattedTitle utilizzando i dosaggi indicati per ${mealType.label}.",
                "Cuoci a fuoco medio per preservare i macronutrienti e servi caldo."
            ),
            isCustom = true
        )
    }
}
