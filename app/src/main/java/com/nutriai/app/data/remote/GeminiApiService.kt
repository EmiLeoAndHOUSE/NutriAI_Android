package com.nutriai.app.data.remote

import com.nutriai.app.data.model.DailyMealPlan
import com.nutriai.app.data.model.MacroTarget
import com.nutriai.app.data.model.MealOption
import com.nutriai.app.data.model.MealSlotPlan
import com.nutriai.app.data.model.MealType
import com.nutriai.app.data.model.UserProfile
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
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Genera l'intera giornata alimentare tramite Gemini API (o Mock di ripiego se la chiave è vuota).
     */
    suspend fun generateDailyPlan(
        profile: UserProfile,
        target: MacroTarget,
        apiKey: String
    ): Result<DailyMealPlan> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            // Se non c'è una chiave valida, usiamo il generatore intelligente offline per permettere di testare l'app!
            return@withContext Result.success(generateMockDailyPlan(profile, target))
        }

        runCatching {
            val prompt = buildFullPlanPrompt(profile, target)
            val jsonResponseString = callGeminiApi(prompt, apiKey)
            parseDailyPlanFromJson(jsonResponseString, target, profile.activeMealTypes)
        }
    }

    /**
     * Rigenera le opzioni per un singolo pasto specificato.
     */
    suspend fun regenerateMealSlot(
        profile: UserProfile,
        targetSlotMacro: MacroTarget,
        mealType: MealType,
        apiKey: String
    ): Result<List<MealOption>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            return@withContext Result.success(generateMockMealOptions(mealType, targetSlotMacro))
        }

        runCatching {
            val prompt = buildSingleSlotPrompt(profile, targetSlotMacro, mealType)
            val jsonResponseString = callGeminiApi(prompt, apiKey)
            parseMealOptionsFromJson(jsonResponseString)
        }
    }

    private fun buildFullPlanPrompt(profile: UserProfile, target: MacroTarget): String {
        val likedStr = if (profile.likedFoods.isNotEmpty()) profile.likedFoods.joinToString(", ") else "Nessun cibo specifico (usa SOLO alimenti neutri: Pollo, Tacchino, Riso, Pasta, Uova, Zucchine)"
        val dislikedStr = if (profile.dislikedFoods.isNotEmpty()) profile.dislikedFoods.joinToString(", ") else "Nessuno"
        val allergiesStr = if (profile.allergies.isNotEmpty()) profile.allergies.joinToString(", ") else "Nessuna"

        return """
            Sei un nutrizionista esperto di cucina italiana. Genera una giornata alimentare completa e bilanciata in formato JSON.
            PARAMETRI UTENTE:
            - Calorie Target Giornaliere: ${target.calories} kcal
            - Proteine Target: ${target.proteinGrams}g
            - Carboidrati Target: ${target.carbsGrams}g
            - Grassi Target: ${target.fatGrams}g
            - Stile Alimentare: ${profile.dietaryType.label}
            - ALLERGIE/INTOLLERANZE (RIGOROSAMENTE VIETATE): $allergiesStr
            - CIBI GRADITI (USA RIGOROSAMENTE QUESTI ALIMENTI): $likedStr
            - CIBI SGRADITI (DA ESCLUDERE TASSATIVAMENTE): $dislikedStr
            - Pasti richiesti nella giornata: ${profile.activeMealTypes.joinToString { it.name }}

            REGOLE RIGIDE SULLE INGREDIENTI:
            1. Se l'utente ha selezionato cibi graditi (es. $likedStr), DEVI basare le ricette su tali alimenti.
            2. NON INSERIRE MAI ALIMENTI SPECIFICI CARATTERIZZANTI (come Salmone, Gorgonzola, Avocado, Fegato, Pesce Spada, Crostacei) SE L'UTENTE NON LI HA ESPLICITAMENTE SELEZIONATI TRA I CIBI GRADITI!
            3. Per le proteine, se non specificato diversamente tra i cibi graditi, usa solo fonti neutre come Petto di Pollo, Fesa di Tacchino, Uova o Orata/Merluzzo.

            REQUISITI FORMATO RISPOSTA:
            Restituisci ESCLUSIVAMENTE un oggetto JSON valido (senza markdown o testo esplicativo extra prima/dopo) con la seguente struttura:
            {
              "slots": [
                {
                  "mealType": "BREAKFAST",
                  "options": [
                    {
                      "title": "Titolo del piatto",
                      "description": "Breve descrizione stuzzicante",
                      "calories": 400,
                      "proteinGrams": 25,
                      "carbsGrams": 45,
                      "fatGrams": 12,
                      "ingredients": ["Ingrediente 1 con peso (es. 50g avena)", "Ingrediente 2 (es. 150g albumi)"],
                      "recipeSteps": ["Step 1 di preparazione", "Step 2 di preparazione"]
                    },
                    {
                      "title": "Seconda alternativa",
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
        """.trimIndent()
    }


    private fun buildSingleSlotPrompt(profile: UserProfile, slotTarget: MacroTarget, mealType: MealType): String {
        return """
            Genera 2 NUOVE alternative gustose per il pasto: ${mealType.label} (${mealType.name}).
            TARGET PER QUESTO PASTO:
            - Calorie: ~${slotTarget.calories} kcal
            - Proteine: ~${slotTarget.proteinGrams}g
            - Carboidrati: ~${slotTarget.carbsGrams}g
            - Grassi: ~${slotTarget.fatGrams}g
            - Allergie da evitare: ${profile.allergies.joinToString().ifEmpty { "Nessuna" }}
            - Cibi vietati: ${profile.dislikedFoods.joinToString().ifEmpty { "Nessuno" }}

            Rispondi ESCLUSIVAMENTE con un array JSON di oggetti MealOption:
            [
              {
                "title": "Nome piatto",
                "description": "Descrizione",
                "calories": 450,
                "proteinGrams": 30,
                "carbsGrams": 50,
                "fatGrams": 10,
                "ingredients": ["100g riso", "150g petto di pollo"],
                "recipeSteps": ["Cuoci il riso", "Griglia il pollo"]
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
                "temperature": 0.7,
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

            // Estraiamo il testo dalla struttura di risposta di Gemini API
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

    private fun parseDailyPlanFromJson(
        rawJson: String,
        target: MacroTarget,
        activeMealTypes: List<MealType>
    ): DailyMealPlan {
        val cleanJson = cleanJsonResponse(rawJson)
        val root = json.parseToJsonElement(cleanJson).jsonObject
        val slotsArray = root["slots"]?.jsonArray ?: JsonArray(emptyList())

        val slotPlans = mutableListOf<MealSlotPlan>()
        for (slotElem in slotsArray) {
            val slotObj = slotElem.jsonObject
            val mealTypeName = slotObj["mealType"]?.jsonPrimitive?.content ?: "LUNCH"
            val mealType = runCatching { MealType.valueOf(mealTypeName) }.getOrDefault(MealType.LUNCH)

            val optionsArray = slotObj["options"]?.jsonArray ?: JsonArray(emptyList())
            val options = parseMealOptionsFromArray(optionsArray)

            slotPlans.add(MealSlotPlan(mealType = mealType, options = options, selectedOptionIndex = 0))
        }

        val todayDate = SimpleDateFormat("dd MMMM yyyy", Locale.ITALIAN).format(Date())
        return DailyMealPlan(
            dateString = todayDate,
            target = target,
            slots = if (slotPlans.isNotEmpty()) slotPlans else generateMockDailyPlanSlots(activeMealTypes, target)
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
        if (s.startsWith("```json")) {
            s = s.removePrefix("```json")
        } else if (s.startsWith("```")) {
            s = s.removePrefix("```")
        }
        if (s.endsWith("```")) {
            s = s.removeSuffix("```")
        }
        return s.trim()
    }

    // --- GENERATORE DI MOCK OFFLINE DI TEST ---

    private fun generateMockDailyPlan(profile: UserProfile, target: MacroTarget): DailyMealPlan {
        val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.ITALIAN).format(Date())
        return DailyMealPlan(
            dateString = dateStr,
            target = target,
            slots = generateMockDailyPlanSlots(profile.activeMealTypes, target, profile)
        )
    }

    private fun generateMockDailyPlanSlots(activeTypes: List<MealType>, target: MacroTarget, profile: UserProfile): List<MealSlotPlan> {
        return activeTypes.map { mealType ->
            val slotMacro = MacroTarget(
                calories = (target.calories / activeTypes.size),
                proteinGrams = (target.proteinGrams / activeTypes.size),
                carbsGrams = (target.carbsGrams / activeTypes.size),
                fatGrams = (target.fatGrams / activeTypes.size)
            )
            MealSlotPlan(
                mealType = mealType,
                options = generateMockMealOptions(mealType, slotMacro, profile),
                selectedOptionIndex = 0
            )
        }
    }

    private fun generateMockMealOptions(mealType: MealType, slotMacro: MacroTarget, profile: UserProfile): List<MealOption> {
        val likesSalmon = profile.likedFoods.any { it.contains("Salmone", ignoreCase = true) }
        val fishOrMeatTitle = if (likesSalmon) "Riso Venere con Salmone e Zucchine" else "Pasta Integrale con Petto di Pollo e Zucchine"
        val fishOrMeatIngredients = if (likesSalmon) {
            listOf("80g Riso Venere", "150g Filetto di Salmone fresco", "1 Zucchina media", "1 cucchiaio Olio EVO")
        } else {
            listOf("80g Pasta Integrale", "160g Petto di Pollo a bocconcini", "1 Zucchina media", "1 cucchiaio Olio EVO")
        }
        val fishOrMeatSteps = if (likesSalmon) {
            listOf("Lessa il riso venere per 18 minuti.", "Spadella le zucchine tagliate a rondelle con olio EVO.", "Cuoci il salmone alla piastra e unisci gli ingredienti.")
        } else {
            listOf("Cuoci la pasta integrale al dente.", "Salta i bocconcini di pollo in padella con le zucchine ed olio EVO.", "Manteca la pasta con il condimento.")
        }

        return when (mealType) {
            MealType.BREAKFAST -> listOf(
                MealOption(
                    title = "Porridge Avena e Frutti di Bosco",
                    description = "Cremosa avena cotta in latte vegetale con frutti di bosco e proteine.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("60g Fiocchi d'avena", "150ml Latte scremato o vegetale", "100g Mirtilli freschi", "20g Proteine in polvere"),
                    recipeSteps = listOf("Scalda il latte in un pentolino con l'avena per 5 minuti.", "Mescola finché non diventa cremoso.", "Guarnisci con mirtilli e servire caldo.")
                ),
                MealOption(
                    title = "Pancake Proteici alla Banana",
                    description = "Pancake soffici e veloci preparati con banana e albumi.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("1 Banana matura", "150g Albumi", "40g Farina d'avena", "1 cucchiaino di miele"),
                    recipeSteps = listOf("Frulla banana, albumi e farina d'avena.", "Cuoci in padella antiaderente 2 minuti per lato.", "Aggiungi miele a filo.")
                )
            )
            MealType.LUNCH -> listOf(
                MealOption(
                    title = fishOrMeatTitle,
                    description = "Piatto sano e nutriente secondo le tue preferenze alimentari.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = fishOrMeatIngredients,
                    recipeSteps = fishOrMeatSteps
                ),
                MealOption(
                    title = "Bowl di Quinoa, Pollo e Avocado",
                    description = "Colorata e completa bowl proteica con grassi buoni.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("70g Quinoa", "160g Petto di pollo a straccetti", "40g Avocado", "Pomodorini e rucola"),
                    recipeSteps = listOf("Cuoci la quinoa in acqua salata.", "Griglia i straccetti di pollo con spezie a piacere.", "Disponi gli ingredienti nella bowl ed emulsiona con olio e limone.")
                )
            )
            MealType.DINNER -> listOf(
                MealOption(
                    title = "Filetto di Orata al Cartoccio con Patate",
                    description = "Cena leggera, digestiva ed elevata qualità proteica.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("200g Filetto di Orata fresco", "180g Patate a fette sottili", "Erbe aromatiche (rosmarino, timo)", "1 cucchiaio Olio EVO"),
                    recipeSteps = listOf("Disponi le fette di patata su carta forno.", "Adagia il filetto d'orata, sala, pepa e aggiungi rosmarino e olio.", "Chiudi il cartoccio e inforna a 200°C per 20 minuti.")
                ),
                MealOption(
                    title = "Omelette alle Erbe con Insalata Mista e Pane Integrale",
                    description = "Cena rapida e nutriente ricca di vitamine.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("2 Uova intere + 100g albumi", "60g Pane integrale di segale", "Insalata mista a foglia verde", "Olio EVO e limone"),
                    recipeSteps = listOf("Sbatte le uova con gli albumi, sale ed erbe.", "Cuoci in padella antiaderente per 4 minuti piegando a metà.", "Servi con pane tostato e insalata fresca.")
                )
            )
            else -> listOf(
                MealOption(
                    title = "Yogurt Greco 0% con Mandorle e Mela",
                    description = "Spuntino spezza-fame ad alto potere saziante.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("170g Yogurt Greco 0%", "15g Mandorle sgusciate", "1 Mela a cubetti"),
                    recipeSteps = listOf("Versa lo yogurt in una ciotola.", "Aggiungi la mela tagliata e le mandorle tritate.")
                ),
                MealOption(
                    title = "Frullato Proteico Banana e Cacao",
                    description = "Merenda energetica e gustosa pronta in 1 minuto.",
                    calories = slotMacro.calories,
                    proteinGrams = slotMacro.proteinGrams,
                    carbsGrams = slotMacro.carbsGrams,
                    fatGrams = slotMacro.fatGrams,
                    ingredients = listOf("200ml Latte vegetale", "1 Banana", "15g Cacao amaro in polvere", "20g Proteine"),
                    recipeSteps = listOf("Inserisci tutti gli ingredienti nel frullatore.", "Frulla per 30 secondi a massima velocità e servi fresco.")
                )
            )
        }
    }

}
