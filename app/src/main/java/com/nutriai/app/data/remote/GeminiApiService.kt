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
     * Genera l'intero piano settimanale (7 giorni) tramite Gemini API o Engine tradizionali della cucina italiana.
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
            Sei un esperto chef di alta cucina tradizionale italiana ed un nutrizionista clinico. Genera un PIANO ALIMENTARE SETTIMANALE COMPLETO (da Lunedì a Domenica, 7 giorni) in formato JSON.
            PARAMETRI UTENTE:
            - Calorie Target Giornaliere: ${target.calories} kcal
            - Proteine Target: ${target.proteinGrams}g
            - Carboidrati Target: ${target.carbsGrams}g
            - Grassi Target: ${target.fatGrams}g
            - Stile Alimentare: ${profile.dietaryType.label}
            - ALLERGIE/INTOLLERANZE (RIGOROSAMENTE VIETATE): $allergiesStr
            - CIBI GRADITI SELEZIONATI DALL'UTENTE (USA RIGOROSAMENTE QUESTI INGREDIENTI): $likedStr
            - CIBI SGRADITI (DA ESCLUDERE TASSATIVAMENTE): $dislikedStr
            - Pasti richiesti per giorno: ${profile.activeMealTypes.joinToString { it.name }}

            REGOLE FONDAMENTALI SULLA TRADIZIONE ITALIANA AUTENTICA:
            1. COLAZIONE (BREAKFAST): Usa UNICAMENTE COLAZIONI TRADIZIONALI ITALIANE AUTENTICHE!
               - Cappuccino schiumato con Fette biscottate integrali, Burro di centrifuga e Marmellata/Miele
               - Caffellatte con Biscotti integrali da inzuppo e Spremuta d'arancia fresca
               - Ciambellone casereccio allo yogurt o Torta Margherita/Crostata casereccia alla frutta
               - Pane di grano duro tostato con Ricotta magra e Miele d'acacia
               - Yogurt bianco/greco con Muesli croccante integrale e Fragole o Mela
               - Toast salato con Prosciutto cotto di alta qualità e Mozzarella fiordilatte
               * DIVIETO ASSOLUTO: È TASSATIVAMENTE VIETATO PROPORRE PORRIDGE DI AVENA O PANCAKE ANGLOSASSONI A COLAZIONE!
            2. SPUNTINI (MORNING_SNACK / AFTERNOON_SNACK): Solo spuntini spezza-fame sani italiani (Yogurt greco, frutta fresca, tostino con affettato magro, parmigiano).
            3. PRANZO (LUNCH): Primi piatti tradizionali o piatti unici (Pasta al pomodoro e basilico, Riso Basmati/Venere con pollo/tacchino, Gnocchi di patate al pomodoro e mozzarella, Farro con verdure e tonno, Polenta morbida con ragù magro).
            4. CENA (DINNER): Secondi piatti della cucina italiana (Filetto di Spigola/Orata al cartoccio, Scaloppine al limone, Tagliata di pollo o tacchino, Omelette alle erbe) con abbondante contorno di verdure e pane integrale/patate al forno.
            5. OGNI SINGOLO INGREDIENTE NELLA LISTA "ingredients" DEVE AVERE IL PESO ESATTO IN GRAMMI (g) O MILLILITRI (ml) (es. "150g Petto di pollo", "80g Pasta integrale", "10g Olio extravergine d'oliva", "150ml Latte scremato"). MAI USARE TERMINI SENZA DOSI COME "q.b.", "dose calibrata", "1 cucchiaio".
            6. NESSUN CONDIMENTO INCOERENTE (MAI olio EVO nel caffè, latte o colazioni dolci!).

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
            MealType.BREAKFAST -> "QUESTO PASTO È LA COLAZIONE. Proponi alimenti da COLAZIONE TRADIZIONALE ITALIANA AUTENTICA (Cappuccino, Caffellatte, Fette biscottate con marmellata/miele, Biscotti integrali da inzuppo, Ciambellone casereccio allo yogurt, Crostata alla confettura, Toast prosciutto cotto e mozzarella). TASSATIVAMENTE VIETATI porridge anglosassone, pancake americani, gnocchi, pasta, riso salato, carne o pesce a colazione!"
            MealType.MORNING_SNACK, MealType.AFTERNOON_SNACK -> "QUESTO PASTO È UNO SPUNTINO. Proponi unicamente spuntini spezza-fame italiani sani e veloci (Yogurt greco con mandorle/noci e frutto, tostino con affettato magro, parmigiano con frutto, frullato proteico). NON PROPORRE PIATTI DA PRANZO/CENA."
            MealType.LUNCH -> "QUESTO PASTO È IL PRANZO. Proponi un primo piatto bilanciato o piatto unico della cucina italiana (Riso, Pasta integrale, Farro, Gnocchi, Polenta conditi con fonti proteiche magre e verdure)."
            MealType.DINNER -> "QUESTO PASTO È LA CENA. Proponi un secondo piatto proteico della cucina italiana (pesce, carne magra, uova) accompagnato da verdure ed una fonte moderata di carboidrati (pane integrale, patate al forno)."
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
            Crea un piatto sano e bilanciato che soddisfi questa richiesta e rispetti al grammo i seguenti macro target per questo pasto:
            - Calorie: ~${slotTarget.calories} kcal
            - Proteine: ~${slotTarget.proteinGrams}g
            - Carboidrati: ~${slotTarget.carbsGrams}g
            - Grassi: ~${slotTarget.fatGrams}g
            - ALLERGIE DA EVITARE: ${profile.allergies.joinToString().ifEmpty { "Nessuna" }}

            REGOLE IMPERATIVE:
            1. OGNI SINGOLO INGREDIENTE IN "ingredients" DEVE AVERE IL PESO ESATTO ESPRESSO IN GRAMMI (g) O MILLILITRI (ml) (es. "120g Farina d'avena", "150ml Albumi", "10g Cacao amaro").
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
                "ingredients": ["120g Pane di grano duro", "50g Prosciutto cotto", "150ml Spremuta d'arancia"],
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
                        description = "Dolce soffice preparato con ingredienti sani e genuini.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("60g Ciambellone casereccio allo yogurt", "200ml Latte parzialmente scremato", "50ml Caffè espresso", "100g Mela fresca"),
                        recipeSteps = listOf(
                            "Taglia una fetta da 60g di ciambellone casereccio allo yogurt.",
                            "Monta 200ml di latte con 50ml di caffè per un classico cappuccino italiano.",
                            "Servi con 100g di mela fresca a fettine."
                        )
                    ),
                    MealOption(
                        title = "Toast Salato con Prosciutto Cotto, Mozzarella e Spremuta d'Arancia",
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
                    title = "Yogurt Greco 0% con Mandorle e Mela",
                    description = "Spuntino spezza-fame fresco, saziante e ricco di proteine.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("170g Yogurt Greco 0%", "15g Mandorle sgusciate", "150g Mela a cubetti"),
                    recipeSteps = listOf(
                        "Versa 170g di yogurt greco 0% in una ciotola.",
                        "Taglia 150g di mela fresca a cubetti ed unisci allo yogurt.",
                        "Completa aggiungendo 15g di mandorle sgusciate tritate al momento."
                    )
                ),
                MealOption(
                    title = "Tostino Integrale con Bresaola e Rucola",
                    description = "Spuntino salato veloce ed ad alto tenore proteico.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("40g Pane integrale", "50g Bresaola della Valtellina", "20g Rucola fresca", "10g Olio extravergine d'oliva"),
                    recipeSteps = listOf(
                        "Tosta leggermente 40g di pane integrale.",
                        "Disponi 50g di bresaola e 20g di rucola fresca sul pane.",
                        "Condisci la rucola con 10g di olio extravergine d'oliva a crudo."
                    )
                )
            )
            MealType.LUNCH -> {
                val carbsList = listOf("Pasta Integrale", "Riso Basmati", "Gnocchi di Patate", "Farro integrale", "Riso Venere")
                val proteinList = listOf("Petto di Pollo", "Fesa di Tacchino", "Filetto di Orata", "Uova strapazzate", "Bresaola")
                val dayCarb = carbsList.getOrElse(dayIndex % carbsList.size) { "Pasta Integrale" }
                val dayProtein = proteinList.getOrElse(dayIndex % proteinList.size) { "Petto di Pollo" }

                listOf(
                    MealOption(
                        title = "$dayCarb al Pomodoro Fresco con $dayProtein e Zucchine",
                        description = "Pranzo completo ed equilibrato della cucina tradizionale italiana.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g $dayCarb", "160g $dayProtein", "150g Zucchine fresche", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Porta ad ebollizione una pentola d'acqua salata e cuoci 80g di $dayCarb per il tempo indicato sulla confezione.",
                            "Taglia 150g di zucchine a rondelle e 160g di $dayProtein a dadini.",
                            "Scalda 10g di olio extravergine d'oliva in padella e rosola il $dayProtein con le zucchine per 8-10 minuti.",
                            "Scola il $dayCarb al dente, uniscilo in padella con il condimento e salta per 1 minuto prima di servire."
                        )
                    ),
                    MealOption(
                        title = "Insalata Fredda di Farro con Fesa di Tacchino e Pomodorini",
                        description = "Alternativa fresca e ricca di nutrienti per il pranzo.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("70g Farro integrale", "150g Fesa di Tacchino", "120g Pomodorini ciliegino", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Cuoci 70g di farro in abbondante acqua salata per 15 minuti, poi scola e lascia intiepidire.",
                            "Griglia 150g di fesa di tacchino su una piastra per 3 minuti per lato e tagliala a listarelle.",
                            "In una ciotola unisci il farro, il tacchino ed 120g di pomodorini tagliati a metà.",
                            "Condisci con 10g di olio extravergine d'oliva a crudo ed origano."
                        )
                    )
                )
            }
            MealType.DINNER -> {
                val proteinList = listOf("Filetto di Spigola", "Petto di Tacchino", "Tagliata di Pollo", "Omelette alle Erbe")
                val dayProtein = if (likesSalmon && dayIndex == 4) "Salmone Fresco al Cartoccio" else proteinList.getOrElse(dayIndex % proteinList.size) { "Filetto di Spigola" }

                listOf(
                    MealOption(
                        title = "$dayProtein al Cartoccio con Insalata e Pane Integrale",
                        description = "Cena leggera e ad alta digeribilità.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g $dayProtein", "150g Insalata mista", "60g Pane integrale", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Disponi 180g di $dayProtein su un foglio di carta da forno con un rametto di rosmarino ed un pizzico di sale.",
                            "Chiudi il cartoccio ed inforna a 180°C per 15-18 minuti.",
                            "Lava 150g di insalata mista e condiscila con 10g di olio extravergine d'oliva.",
                            "Servi il $dayProtein caldo accompagnato dall'insalata e da 60g di pane integrale."
                        )
                    ),
                    MealOption(
                        title = "Omelette alle Erbe con Verdure Grigliate e Pane Integrale",
                        description = "Cena rapida e proteica della tradizione italiana.",
                        calories = slotMacro.calories,
                        proteinGrams = slotMacro.proteinGrams,
                        carbsGrams = slotMacro.carbsGrams,
                        fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("120g Uova (2 uova intere)", "100ml Albumi d'uovo", "150g Melanzane e Zucchine grigliate", "50g Pane integrale", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Sbatti 120g di uova con 100ml di albumi ed erbe aromatiche.",
                            "Scalda 5g di olio extravergine d'oliva in padella antiaderente e versa il composto cuocendo per 3 minuti per lato.",
                            "Griglia 150g di melanzane e zucchine e condisci con i restanti 5g di olio extravergine d'oliva.",
                            "Servi l'omelette ben calda con le verdure ed 50g di pane integrale tostato."
                        )
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
                        title = "Yogurt Greco 0% con Noci e Mela Croccante",
                        description = "Spuntino spezza-fame ad alto potere saziante.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("170g Yogurt Greco 0%", "15g Noci", "150g Mela fresca"),
                        recipeSteps = listOf(
                            "Versa 170g di yogurt greco 0% in una ciotola.",
                            "Aggiungi 150g di mela fresca a cubetti e 15g di noci tritate."
                        )
                    ),
                    MealOption(
                        title = "Tostino Integrale con Bresaola e Scaglie di Parmigiano",
                        description = "Spuntino salato rapido ricchissimo di proteine.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("40g Pane integrale", "50g Bresaola della Valtellina", "10g Scaglie di Parmigiano Reggiano", "5g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Tosta 40g di pane integrale.",
                            "Disponi 50g di bresaola e 10g di scaglie di parmigiano sul pane tostato.",
                            "Condisci con 5g di olio extravergine d'oliva a crudo."
                        )
                    ),
                    MealOption(
                        title = "Frullato Proteico alla Banana e Latte d'Avena",
                        description = "Merenda rigenerante e saziante.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("200ml Latte d'avena", "120g Banana fresca", "20g Proteine in polvere", "10g Mandorle"),
                        recipeSteps = listOf(
                            "Versa 200ml di latte d'avena nel mixer con 120g di banana, 20g di proteine ed 10g di mandorle.",
                            "Frulla per 40 secondi fino ad ottenere uno smoothie cremoso e servi fresco."
                        )
                    )
                ).shuffled()
                snackPool.take(2)
            }
            MealType.LUNCH -> {
                val randomCarbs = listOf("Pasta Integrale", "Riso Basmati", "Gnocchi di Patate", "Farro integrale", "Riso Venere").shuffled().first()
                val randomProtein = listOf("Petto di Pollo", "Fesa di Tacchino", "Filetto di Orata", "Merluzzo", "Uova strapazzate", "Bresaola").shuffled().first()
                val randomVeg = listOf("Zucchine", "Spinaci", "Pomodori ciliegino", "Broccoli", "Asparagi").shuffled().first()

                listOf(
                    MealOption(
                        title = "$randomCarbs con $randomProtein e $randomVeg",
                        description = "Nuova combinazione di pranzo bilanciata rigenerata per le tue preferenze.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("80g $randomCarbs", "160g $randomProtein", "150g $randomVeg", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Lessa 80g di $randomCarbs in acqua bollente salata.",
                            "Cuoci 160g di $randomProtein con 150g di $randomVeg in padella con 10g di olio extravergine d'oliva per 8 minuti.",
                            "Scola il $randomCarbs ed uniscilo alla spadellata di $randomProtein e $randomVeg prima di servire."
                        )
                    ),
                    MealOption(
                        title = "Insalata calda di $randomCarbs, $randomProtein e $randomVeg",
                        description = "Alternativa fresca e gustosa rigenerata per il pranzo.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("75g $randomCarbs", "150g $randomProtein", "120g $randomVeg", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Cuoci 75g di $randomCarbs al dente.",
                            "Griglia 150g di $randomProtein su piastra per 4 minuti per lato.",
                            "Mescola $randomCarbs, $randomProtein a pezzi e 120g di $randomVeg in una ciotola e condisci con 10g di olio extravergine d'oliva a crudo."
                        )
                    )
                )
            }
            MealType.DINNER -> {
                val mainProtein = if (likesSalmon) "Salmone Fresco al Cartoccio" else listOf("Filetto di Spigola", "Tagliata di Petto di Tacchino", "Omelette ai Funghi", "Merluzzo al Vapore").shuffled().first()
                val mainSide = listOf("Patate al forno ed insalata", "Verdure grigliate e pane integrale", "Spinaci al vapore e pane di segale").shuffled().first()

                listOf(
                    MealOption(
                        title = "$mainProtein con $mainSide",
                        description = "Cena leggera e ad alta digeribilità rigenerata per te.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("180g Fonte proteica ($mainProtein)", "150g Contorno ($mainSide)", "60g Pane integrale", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Cuoci 180g di $mainProtein al forno o alla piastra con salvia e rosmarino.",
                            "Prepara 150g di contorno ($mainSide) e condisci con 10g of olio extravergine d'oliva.",
                            "Servi il $mainProtein accompagnato dalle verdure e 60g di pane integrale."
                        )
                    ),
                    MealOption(
                        title = "Omelette alle Erbe con Verdure Grigliate e Pane Integrale",
                        description = "Cena rapida, calda e proteica.",
                        calories = slotMacro.calories, proteinGrams = slotMacro.proteinGrams, carbsGrams = slotMacro.carbsGrams, fatGrams = slotMacro.fatGrams,
                        ingredients = listOf("120g Uova intere", "100ml Albumi d'uovo", "150g Melanzane e Zucchine grigliate", "50g Pane integrale", "10g Olio extravergine d'oliva"),
                        recipeSteps = listOf(
                            "Sbatti 120g di uova con 100ml di albumi e cuoci in padella con 5g di olio extravergine d'oliva.",
                            "Griglia 150g di verdure e condisci con i restanti 5g di olio extravergine d'oliva.",
                            "Servi l'omelette con verdure grigliate e 50g di pane integrale tostato."
                        )
                    )
                )
            }
        }
    }

    private fun generateMockCustomMealOption(mealType: MealType, slotMacro: MacroTarget, userDesire: String): MealOption {
        val formattedTitle = userDesire.trim().replaceFirstChar { it.uppercase() }
        
        val isSweet = mealType == MealType.BREAKFAST || formattedTitle.contains("pancake", ignoreCase = true) || formattedTitle.contains("caffè", ignoreCase = true) || formattedTitle.contains("latte", ignoreCase = true) || formattedTitle.contains("cornetto", ignoreCase = true) || formattedTitle.contains("cioccolato", ignoreCase = true) || formattedTitle.contains("yogurt", ignoreCase = true)

        val customIngredients = if (isSweet) {
            listOf(
                "120g Ingrediente principale per $formattedTitle",
                "150ml Latte scremato o bevanda d'avena",
                "15g Miele d'acacia o confettura"
            )
        } else {
            listOf(
                "150g Ingrediente principale per $formattedTitle",
                "120g Contorno di ortaggi a scelta",
                "10g Olio extravergine d'oliva"
            )
        }

        val customSteps = if (isSweet) {
            listOf(
                "Prepara $formattedTitle utilizzando 120g di ingrediente principale e 150ml di latte/bevanda.",
                "Cuoci o lavora a freddo a seconda del piatto e guarnisci con 15g di miele d'acacia o confettura."
            )
        } else {
            listOf(
                "Prepara 150g di ingrediente principale per $formattedTitle ed impiatta con 120g di ortaggi.",
                "Condisci il tutto con 10g di olio extravergine d'oliva a crudo prima di servire."
            )
        }

        return MealOption(
            title = "$formattedTitle (Versione Bilanciata NutriAI)",
            description = "Piatto creato su tua richiesta specifica, dosato per rientrare perfettamente nel tuo target nutrizionale di ${mealType.label}.",
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
