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
     * Genera l'intero piano settimanale (7 giorni) tramite Gemini API o Engine 100% Tradizione Italiana.
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
     * Rigenera le opzioni per un singolo pasto specificato garantendo ricette 100% della tradizione italiana.
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
            Sei un grande Chef della tradizione culinaria italiana ed un nutrizionista esperto. Genera un PIANO ALIMENTARE SETTIMANALE COMPLETO (da Lunedì a Domenica, 7 giorni) in formato JSON.
            PARAMETRI UTENTE:
            - Calorie Target Giornaliere: ${target.calories} kcal
            - Proteine Target: ${target.proteinGrams}g
            - Carboidrati Target: ${target.carbsGrams}g
            - Grassi Target: ${target.fatGrams}g
            - Stile Alimentare: ${profile.dietaryType.label}
            - ALLERGIE/INTOLLERANZE (RIGOROSAMENTE VIETATE): $allergiesStr
            - CIBI GRADITI SELEZIONATI (USA RIGOROSAMENTE QUESTI INGREDIENTI ITALIANI): $likedStr
            - CIBI SGRADITI (DA ESCLUDERE TASSATIVAMENTE): $dislikedStr
            - Pasti richiesti per giorno: ${profile.activeMealTypes.joinToString { it.name }}

            REGOLE IMPERATIVE PER LA TRADIZIONE CULINARIA ITALIANA AUTENTICA IN TUTTI I PASTI:
            1. OGNI SINGOLO PASTO (COLAZIONE, SPUNTINO MATTINA, PRANZO, SPUNTINO POMERIGGIO, CENA) DEVE ESSERE RIGOROSAMENTE E 100% UNA RICETTA DELLA TRADIZIONE REGIONALE ITALIANA!
               - COLAZIONE (BREAKFAST): Cappuccino schiumato, Caffellatte con fette biscottate e marmellata/miele, Biscotti integrali da inzuppo, Ciambellone casereccio allo yogurt, Crostata alla confettura, Toast prosciutto cotto e mozzarella, Pane tostato con ricotta magra e miele. (TASSATIVAMENTE VIETATI PORRIDGE O PANCAKE ANGLOSASSONI!).
               - SPUNTINO (MORNING_SNACK / AFTERNOON_SNACK): Pane casereccio con Bresaola della Valtellina o Prosciutto Crudo di Parma, Parmigiano Reggiano/Grana Padano con noci o mela, Ricotta con miele e mandorle.
               - PRANZO (LUNCH): Primi piatti o piatti unici tradizionali italiani (Pasta al pomodoro e basilico fresco con parmigiano e petto di pollo, Risotto ai funghi o zucchine con fesa di tacchino, Gnocchi di patate alla sorrentina, Farro integrale con verdure e tonno, Polenta morbida con ragù magro di bovino).
               - CENA (DINNER): Secondi piatti della tradizione italiana (Filetto di Spigola/Orata al cartoccio con patate al forno ed insalata, Scaloppine al limone, Tagliata di pollo o tacchino al rosmarino, Merluzzo alla livornese, Omelette alla siciliana alle erbe).
            2. OGNI INGREDIENTE IN "ingredients" DEVE AVERE LA QUANTITÀ ESATTA ESPRESSA IN GRAMMI (g) O MILLILITRI (ml) (es. "150g Petto di pollo", "80g Pasta integrale", "10g Olio extravergine d'oliva", "150ml Latte scremato"). MAI USARE TERMINI GENERICI O SENZA DOSI.
            3. MAI METTERE CONDIMENTI INCOERENTI (es. MAI olio EVO nel caffè, nel latte o nelle colazioni dolci!).

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
                          "ingredients": ["40g Fette biscottate integrali", "200ml Latte scremato", "50ml Caffè espresso", "15g Marmellata di fragole", "10g Burro di centrifuga"],
                          "recipeSteps": ["Monta il latte con il caffè per il cappuccino.", "Spalmo il burro e la marmellata sulle fette biscottate."]
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
            MealType.BREAKFAST -> "QUESTO PASTO È LA COLAZIONE. Proponi UNICAMENTE ricette della COLAZIONE TRADIZIONALE ITALIANA AUTENTICA (Cappuccino, Caffellatte, Fette biscottate integrali con marmellata/miele, Biscotti integrali da inzuppo, Ciambellone casereccio allo yogurt, Crostata alla confettura, Toast prosciutto cotto e mozzarella). TASSATIVAMENTE VIETATI porridge anglosassone, pancake americani, gnocchi, pasta, riso salato, carne o pesce a colazione!"
            MealType.MORNING_SNACK, MealType.AFTERNOON_SNACK -> "QUESTO PASTO È UNO SPUNTINO. Proponi UNICAMENTE spuntini della tradizione italiana (Pane casereccio con Bresaola della Valtellina o Prosciutto Crudo di Parma, Parmigiano Reggiano con noci o mela, Ricotta con miele e mandorle, Yogurt naturale italiano). NON PROPORRE MAI PIATTI STRANIERI O DA PRANZO/CENA."
            MealType.LUNCH -> "QUESTO PASTO È IL PRANZO. Proponi UNICAMENTE primi piatti o piatti unici tradizionali della cucina italiana (Pasta al pomodoro fresco, Risotto ai funghi o zucchine, Gnocchi di patate alla sorrentina, Farro integrale con verdure e tonno, Polenta morbida con ragù magro)."
            MealType.DINNER -> "QUESTO PASTO È LA CENA. Proponi UNICAMENTE secondi piatti della tradizione italiana (Filetto di Spigola/Orata al cartoccio, Scaloppine al limone, Merluzzo alla livornese, Omelette alle erbe) con abbondante contorno di verdure e pane di grano duro o patate al forno."
        }

        return """
            Genera 2 NUOVE ED INEDITI alternative tradizionali della cucina italiana (Seed: $randomSeed) per il pasto: ${mealType.label} (${mealType.name}).
            $mealSpecificInstruction

            REGOLE TASSATIVE:
            1. OGNI INGREDIENTE DEVE AVERE LA QUANTITÀ ESATTA ESPRESSA IN GRAMMI (g) O MILLILITRI (ml) (es. "150g Petto di pollo", "10g Olio extravergine d'oliva", "150ml Latte scremato"). NESSUNA INDICAZIONE GENERICA SENZA PESO.
            2. COERENZA TOTALE TRA TITOLO, INGREDIENTI E PROCEDIMENTO (MAI Olio EVO in caffè, latte o colazioni dolci!).

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
            Crea un piatto sano e bilanciato della tradizione italiana che soddisfi questa richiesta e rispetti al grammo i seguenti macro target per questo pasto:
            - Calorie: ~${slotTarget.calories} kcal
            - Proteine: ~${slotTarget.proteinGrams}g
            - Carboidrati: ~${slotTarget.carbsGrams}g
            - Grassi: ~${slotTarget.fatGrams}g
            - ALLERGIE DA EVITARE: ${profile.allergies.joinToString().ifEmpty { "Nessuna" }}

            REGOLE IMPERATIVE:
            1. OGNI SINGOLO INGREDIENTE IN "ingredients" DEVE AVERE IL PESO ESATTO ESPRESSO IN GRAMMI (g) O MILLILITRI (ml) (es. "120g Pane di grano duro", "50g Prosciutto cotto", "150ml Spremuta d'arancia").
            2. LA PREPARAZIONE "recipeSteps" DEVE ESSERE PERFETTAMENTE INERENTE A "$userDesire" E AGLI INGREDIENTI INDICATI. MAI INGREDIENTI O CONDIMENTI STRANI O INCOERENTI (es. MAI olio EVO nel caffè/latte).

            Rispondi ESCLUSIVAMENTE con un array JSON contenente 1 oggetto MealOption:
            [
              {
                "title": "$userDesire (Versione Bilanciata NutriAI)",
                "description": "Come questo piatto è stato bilanciato per te per soddisfare la tua voglia di $userDesire a ${mealType.label}.",
                "calories": ${slotTarget.calories},
                "proteinGrams": ${slotTarget.proteinGrams},
                "carbsGrams": ${slotTarget.carbsGrams},
                "fatGrams": ${slotTarget.fatGrams},
                "ingredients": ["120g Pane di grano duro", "50g Prosciutto cotto di alta qualità", "150ml Spremuta d'arancia"],
                "recipeSteps": ["Step 1 di preparazione coerente", "Step 2 di cottura coerente"]
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

    // --- GENERATORE DI MOCK SETTIMANALE ED OFFERTA PASTI 100% CUCINA TRADIZIONALE ITALIANA ---

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
            MealType.BREAKFAST -> {
                val authenticItalianBreakfasts = listOf(
                    MealOption(
                        title = "Cappuccino Schiumato con Fette Biscottate Integrali, Burro e Marmellata",
                        description = "La classica ed inimitabile colazione della tradizione italiana.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("200ml Latte scremato o d'avena", "50ml Caffè espresso", "40g Fette biscottate integrali", "10g Burro di centrifuga", "15g Confettura di albicocche"),
                        recipeSteps = listOf(
                            "Prepara 50ml di caffè espresso e monta 200ml di latte fino ad ottenere una schiuma soffice per il cappuccino.",
                            "Spalmo 10g di burro di centrifuga e 15g di confettura sulle 40g di fette biscottate integrali.",
                            "Servi il cappuccino ben caldo insieme alle fette biscottate."
                        )
                    ),
                    MealOption(
                        title = "Caffellatte con Biscotti Integrali da Inzuppo e Spremuta",
                        description = "Colazione casereccia italiana ricca di energia e vitamina C.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("200ml Latte scremato", "50ml Caffè espresso", "45g Biscotti integrali ai cereali", "150ml Spremuta d'arancia fresca"),
                        recipeSteps = listOf(
                            "Scalda 200ml di latte ed unisci 50ml di caffè espresso in una tazza capiente.",
                            "Premi 150ml di spremuta d'arancia fresca.",
                            "Inzuppa 45g di biscotti integrali nel caffellatte caldo ed accompagna con la spremuta fresca."
                        )
                    ),
                    MealOption(
                        title = "Ciambellone Casereccio allo Yogurt e Cappuccino",
                        description = "Dolce soffice preparato con ingredienti sani e genuini della nostra tradizione.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("60g Ciambellone casereccio allo yogurt", "200ml Latte parzialmente scremato", "50ml Caffè espresso", "100g Mela fresca"),
                        recipeSteps = listOf(
                            "Taglia una fetta da 60g di ciambellone casereccio allo yogurt.",
                            "Monta 200ml di latte con 50ml di caffè per un classico cappuccino italiano.",
                            "Servi con 100g di mela fresca a fettine."
                        )
                    ),
                    MealOption(
                        title = "Toast Salato con Prosciutto Cotto di Alta Qualità, Mozzarella e Spremuta d'Arancia",
                        description = "Colazione salata della tradizione italiana.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("50g Pane di grano duro tostato", "40g Prosciutto cotto di alta qualità", "30g Mozzarella fiordilatte", "150ml Spremuta d'arancia fresca"),
                        recipeSteps = listOf(
                            "Tosta 50g di pane ed imbottisci con 40g di prosciutto cotto e 30g di mozzarella.",
                            "Scalda nel tostapane per 2 minuti finché la mozzarella fonda.",
                            "Accompagna con 150ml di spremuta fresca d'arancia."
                        )
                    )
                )
                authenticItalianBreakfasts.subList(dayIndex % 2, (dayIndex % 2) + 2)
            }
            MealType.MORNING_SNACK, MealType.AFTERNOON_SNACK -> listOf(
                MealOption(
                    title = "Pane Casereccio con Bresaola della Valtellina e Parmigiano Reggiano",
                    description = "Spuntino spezza-fame salato e proteico della tradizione italiana.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("40g Pane casereccio di grano duro", "50g Bresaola della Valtellina", "15g Scaglie di Parmigiano Reggiano", "5g Olio extravergine d'oliva"),
                    recipeSteps = listOf(
                        "Tosta leggermente 40g di pane casereccio di grano duro.",
                        "Disponi 50g di bresaola della Valtellina e 15g di scaglie di parmigiano reggiano sul pane.",
                        "Condisci con 5g di olio extravergine d'oliva a crudo."
                    )
                ),
                MealOption(
                    title = "Parmigiano Reggiano con Noci Italiane e Mela Fresca",
                    description = "Spuntino spezza-fame fresco, genuino e saziante.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("30g Parmigiano Reggiano stagionato", "15g Noci italiane sgusciate", "150g Mela fresca a fette"),
                    recipeSteps = listOf(
                        "Taglia 30g di Parmigiano Reggiano in scaglie.",
                        "Lava e taglia 150g di mela fresca a fette.",
                        "Gusta il parmigiano ed i pezzetti di mela con 15g di noci italiane sgusciate."
                    )
                )
            )
            MealType.LUNCH -> {
                val pastaOptions = listOf(
                    MealOption(
                        title = "Pasta Integrale al Pomodoro Fresco e Basilico con Petto di Pollo",
                        description = "Il primo piatto simbolo della tradizione italiana bilanciato con proteine magre.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g Pasta integrale", "160g Petto di pollo a straccetti", "150g Pomodori pelati italiani", "10g Olio extravergine d'oliva", "10g Parmigiano Reggiano"),
                        recipeSteps = listOf(
                            "Porta ad ebollizione una pentola d'acqua salata e cuoci 80g di pasta integrale per il tempo indicato.",
                            "Cuoci 160g di petto di pollo a straccetti in padella con 10g di olio EVO e 150g di pomodoro fresco per 8 minuti.",
                            "Scola la pasta al dente, salta in padella con il condimento e servire con una spolverata di 10g di Parmigiano Reggiano e basilico."
                        )
                    ),
                    MealOption(
                        title = "Risotto ai Funghi Porcini e Zucchine con Fesa di Tacchino",
                        description = "Primo piatto ricco e cremoso della tradizione del nord Italia.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g Riso Carnaroli/Basmati", "150g Fesa di Tacchino a dadini", "100g Funghi porcini/zucchine", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Tosta 80g di riso in padella con 10g di olio extravergine d'oliva e 100g di funghi/zucchine.",
                            "Aggiungi gradualmente brodo vegetale caldo mescolando per 15-18 minuti.",
                            "Unisci 150g di fesa di tacchino negli ultimi 5 minuti di cottura e servire ben caldo."
                        )
                    ),
                    MealOption(
                        title = "Gnocchi di Patate alla Sorrentina con Pomodoro, Basilico e Fiordilatte",
                        description = "Piatto tradizionale campano dal sapore inimitabile.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("150g Gnocchi di patate freschi", "120g Macinato magro di bovino", "150g Passata di pomodoro italiano", "30g Mozzarella fiordilatte", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Prepara un sugo leggero rosolando 120g di macinato magro con 150g di passata ed 10g di olio EVO.",
                            "Lessa 150g di gnocchi di patate in acqua salata e scolali appena salgono a galla.",
                            "Condisci gli gnocchi con il sugo e 30g di fiordilatte a cubetti e passa in forno per 5 minuti a far fondere la mozzarella."
                        )
                    )
                )
                pastaOptions.subList(dayIndex % 2, (dayIndex % 2) + 2)
            }
            MealType.DINNER -> {
                val dinnerOptions = listOf(
                    MealOption(
                        title = "Filetto di Orata al Cartoccio con Patate al Forno ed Insalata Mista",
                        description = "Secondo piatto di pesce raffinato ed altamente digeribile.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g Filetto di Orata fresca", "120g Patate novelle", "150g Insalata mista", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Disponi 180g di filetto di orata su carta da forno con un rametto di rosmarino ed 100g di patate a fettine sottili.",
                            "Chiudi il cartoccio ed inforna a 180°C per 18 minuti.",
                            "Condisci 150g di insalata mista fresca con 10g di olio extravergine d'oliva a crudo e servi con il pesce caldo."
                        )
                    ),
                    MealOption(
                        title = "Scaloppine di Petto di Tacchino al Limone con Verdure Grigliate e Pane Integrale",
                        description = "Classico secondo piatto italiano profumato agli agrumi.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("170g Petto di Tacchino", "Succo di 1 limone fresco", "150g Zucchine e Melanzane grigliate", "60g Pane integrale", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Infarina leggermente 170g di petto di tacchino e cuoci in padella con 5g di olio EVO per 3 minuti per lato.",
                            "Sfuma con il succo di 1 limone fresco creando una cremina morbida.",
                            "Griglia 150g di verdure ed ungi con i restanti 5g di olio EVO, servendo con 60g di pane integrale."
                        )
                    ),
                    MealOption(
                        title = "Merluzzo alla Livornese con Pomodorini, Capperi, Olive e Pane di Grano Duro",
                        description = "Ricetta tradizionale toscana ricca di sapore e genuinità.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g Cuore di Merluzzo", "150g Pomodorini ciliegino", "15g Olive nere", "5g Capperi", "60g Pane di grano duro", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "In una padella cuoci 150g di pomodorini tagliati con 15g di olive, 5g di capperi ed 10g di olio EVO per 5 minuti.",
                            "Adagia 180g di filetto di merluzzo nel sugo e copri con coperchio lasciando cuocere a fuoco lento per 10 minuti.",
                            "Servi il merluzzo ricoperto dal suo intingolo saporito accompagnato da 60g di pane tostato."
                        )
                    )
                )
                dinnerOptions.subList(dayIndex % 2, (dayIndex % 2) + 2)
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
                val authenticItalianBreakfastPool = listOf(
                    MealOption(
                        title = "Cappuccino Schiumato con Fette Biscottate Integrali e Confettura",
                        description = "Colazione tradizionale italiana sana e bilanciata.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("200ml Latte scremato", "50ml Caffè espresso", "40g Fette biscottate integrali", "10g Burro di centrifuga", "15g Confettura di ciliegie"),
                        recipeSteps = listOf(
                            "Prepara 50ml di caffè espresso e monta 200ml di latte fino a formare la schiuma del cappuccino.",
                            "Spalmo 10g di burro di centrifuga e 15g di confettura sulle fette biscottate integrali.",
                            "Servi il cappuccino ben caldo insieme alle fette biscottate."
                        )
                    ),
                    MealOption(
                        title = "Caffellatte all'Italiana con Biscotti Integrali da Inzuppo",
                        description = "La tipica colazione casereccia italiana.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("200ml Latte scremato", "50ml Caffè espresso", "45g Biscotti integrali ai cereali", "150ml Spremuta d'arancia"),
                        recipeSteps = listOf(
                            "Scalda 200ml di latte ed unisci 50ml di caffè espresso in una tazza.",
                            "Premi 150ml di spremuta fresca d'arancia.",
                            "Inzuppa 45g di biscotti integrali nel caffellatte."
                        )
                    ),
                    MealOption(
                        title = "Ciambellone Casereccio allo Yogurt e Cappuccino",
                        description = "Colazione dolce della tradizione fatta in casa.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("60g Ciambellone casereccio allo yogurt", "200ml Latte scremato", "50ml Caffè espresso", "100g Mela fresca"),
                        recipeSteps = listOf(
                            "Taglia una fetta da 60g di ciambellone allo yogurt.",
                            "Prepara un cappuccino schiumato con 200ml di latte e 50ml di caffè.",
                            "Servi il tutto accompagnando con 100g di mela fresca a fette."
                        )
                    ),
                    MealOption(
                        title = "Toast Salato con Prosciutto Cotto e Mozzarella Fiordilatte",
                        description = "Colazione salata tradizionale con spremuta fresca.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("50g Pane di grano duro tostato", "40g Prosciutto cotto di alta qualità", "30g Mozzarella fiordilatte", "150ml Spremuta d'arancia"),
                        recipeSteps = listOf(
                            "Tosta 50g di pane di grano duro ed imbottisci con 40g di prosciutto cotto e 30g di mozzarella.",
                            "Scalda nel tostapane per 2 minuti finché la mozzarella fonde.",
                            "Accompagna con 150ml di spremuta fresca d'arancia."
                        )
                    )
                ).shuffled()
                authenticItalianBreakfastPool.take(2)
            }
            MealType.MORNING_SNACK, MealType.AFTERNOON_SNACK -> {
                val snackPool = listOf(
                    MealOption(
                        title = "Pane Casereccio con Bresaola della Valtellina e Parmigiano Reggiano",
                        description = "Spuntino spezza-fame salato e proteico della tradizione italiana.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("40g Pane casereccio di grano duro", "50g Bresaola della Valtellina", "15g Scaglie di Parmigiano Reggiano", "5g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Tosta 40g di pane casereccio.",
                            "Disponi 50g di bresaola della Valtellina e 15g di scaglie di Parmigiano sul pane.",
                            "Condisci con 5g di olio extravergine d'oliva a crudo."
                        )
                    ),
                    MealOption(
                        title = "Parmigiano Reggiano con Noci Italiane e Mela Fresca",
                        description = "Spuntino genuino e saziante della tradizione nostra.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("30g Parmigiano Reggiano", "15g Noci italiane", "150g Mela fresca"),
                        recipeSteps = listOf(
                            "Taglia 30g di Parmigiano Reggiano a scaglie.",
                            "Servi con 150g di mela a fette e 15g di noci sgusciate."
                        )
                    )
                ).shuffled()
                snackPool.take(2)
            }
            MealType.LUNCH -> {
                val ItalianLunchPool = listOf(
                    MealOption(
                        title = "Pasta Integrale al Pomodoro Fresco e Basilico con Petto di Pollo",
                        description = "Primo piatto iconico della tradizione italiana con proteine magre.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g Pasta integrale", "160g Petto di Pollo", "150g Pomodori pelati italiani", "10g Olio extravergine d'oliva", "10g Parmigiano Reggiano"),
                        recipeSteps = listOf(
                            "Lessa 80g di pasta integrale al dente in acqua salata.",
                            "Cuoci 160g di pollo a straccetti con 150g di pomodoro ed 10g di olio EVO per 8 minuti.",
                            "Salta la pasta in padella con il sugo e spolvera con 10g di Parmigiano Reggiano."
                        )
                    ),
                    MealOption(
                        title = "Risotto ai Funghi Porcini e Zucchine con Fesa di Tacchino",
                        description = "Risotto tradizionale cremoso e saporito.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g Riso Carnaroli", "150g Fesa di Tacchino", "100g Funghi porcini", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Tosta 80g di riso Carnaroli con 10g di olio EVO ed 100g di funghi porcini.",
                            "Porta a cottura con brodo vegetale caldo per 16 minuti.",
                            "Unisci 150g di fesa di tacchino a dadini prima di servire ben caldo."
                        )
                    ),
                    MealOption(
                        title = "Gnocchi di Patate alla Sorrentina con Pomodoro, Basilico e Fiordilatte",
                        description = "Tradizione campana pura e gustosa.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("150g Gnocchi di patate freschi", "120g Macinato magro di bovino", "150g Passata di pomodoro", "30g Mozzarella fiordilatte", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Cuoci 150g di gnocchi in acqua salata finché salgono a galla.",
                            "Condisci con 150g di passata al pomodoro, 120g di macinato magro e 10g di olio EVO.",
                            "Spolvera con 30g di fiordilatte a cubetti e passa in forno per 5 minuti."
                        )
                    )
                ).shuffled()
                ItalianLunchPool.take(2)
            }
            MealType.DINNER -> {
                val ItalianDinnerPool = listOf(
                    MealOption(
                        title = "Filetto di Orata al Cartoccio con Patate al Forno ed Insalata Mista",
                        description = "Cena di pesce leggera e profumata al rosmarino.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g Filetto di Orata", "120g Patate al forno", "150g Insalata mista", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Inforna 180g di orata al cartoccio con 120g di patate a fette per 18 minuti a 180°C.",
                            "Servi il pesce caldo accompagnando con 150g di insalata fresca condita con 10g di olio EVO."
                        )
                    ),
                    MealOption(
                        title = "Scaloppine di Petto di Tacchino al Limone con Verdure Grigliate",
                        description = "Secondo piatto classico e leggero della cucina italiana.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("170g Petto di Tacchino", "Succo di 1 Limone", "150g Verdure grigliate", "60g Pane integrale", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Cuoci 170g di tacchino in padella con 5g di olio EVO ed il succo di 1 limone fresco.",
                            "Accompagna con 150g di verdure grigliate condite con i restanti 5g di olio EVO e 60g di pane integrale."
                        )
                    ),
                    MealOption(
                        title = "Merluzzo alla Livornese con Pomodorini, Capperi, Olive e Pane Tostato",
                        description = "Piatto tradizionale toscano saporito e salutare.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g Filetto di Merluzzo", "150g Pomodorini", "15g Olive nere", "5g Capperi", "60g Pane di grano duro", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Cuoci 180g di merluzzo in padella con 150g di pomodorini, 15g di olive, 5g di capperi e 10g di olio EVO per 12 minuti.",
                            "Servi caldo con il suo intingolo e 60g di pane tostato."
                        )
                    )
                ).shuffled()
                ItalianDinnerPool.take(2)
            }
        }
    }

    private fun generateMockCustomMealOption(mealType: MealType, slotMacro: MacroTarget, userDesire: String): MealOption {
        val formattedTitle = userDesire.trim().replaceFirstChar { it.uppercase() }
        
        val isSweet = mealType == MealType.BREAKFAST || formattedTitle.contains("caffè", ignoreCase = true) || formattedTitle.contains("latte", ignoreCase = true) || formattedTitle.contains("ciambellone", ignoreCase = true) || formattedTitle.contains("crostata", ignoreCase = true) || formattedTitle.contains("yogurt", ignoreCase = true) || formattedTitle.contains("biscotti", ignoreCase = true)

        val customIngredients = if (isSweet) {
            listOf(
                "120g Ingrediente principale per $formattedTitle",
                "150ml Latte scremato o spremuta d'arancia",
                "15g Miele d'acacia o confettura"
            )
        } else {
            listOf(
                "150g Ingrediente principale per $formattedTitle",
                "120g Contorno di ortaggi della tradizione italiana",
                "10g Olio extravergine d'oliva"
            )
        }

        val customSteps = if (isSweet) {
            listOf(
                "Prepara $formattedTitle utilizzando 120g di ingrediente principale della tradizione ed 150ml di latte/spremuta.",
                "Servi accompagnando con 15g di miele d'acacia o confettura alla frutta."
            )
        } else {
            listOf(
                "Prepara 150g di ingrediente principale per $formattedTitle ed impiatta con 120g di ortaggi della cucina italiana.",
                "Condisci il tutto con 10g di olio extravergine d'oliva a crudo prima di servire."
            )
        }

        return MealOption(
            title = "$formattedTitle (Versione Bilanciata Tradizione Italiana)",
            description = "Piatto della tradizione creato su tua richiesta specifica, dosato per rientrare perfettamente nel tuo target nutrizionale di ${mealType.label}.",
            calories = slotMacro.calories,
            proteinGrams = slotMacro.proteinGrams,
            carbsGrams = slotMacro.carbsGrams,
            fatGrams = slotMacro.fatGrams,
            ingredients = customIngredients,
            recipeSteps = customSteps,
            isCustom = true
        )
    }
}
