package com.nutriai.app.data.model

data class FoodCategory(
    val categoryName: String,
    val items: List<String>
)

object ItalianFoodCatalog {
    val categories = listOf(
        FoodCategory(
            categoryName = "🍝 Cereali, Pasta & Pane",
            items = listOf(
                "Pasta Integrale", "Pasta Grano Duro", "Riso Basmati", "Riso Venere / Nero",
                "Riso Arborio / Carnaroli", "Avena / Fiocchi d'avena", "Farro", "Orzo",
                "Polenta", "Gnocchi di patate", "Pane Integrale", "Pane di Segale", "Piadina Integrale"
            )
        ),
        FoodCategory(
            categoryName = "🥩 Carni & Affettati Magri",
            items = listOf(
                "Petto di Pollo", "Fesa di Tacchino", "Macinato Magro di Bovino",
                "Bresaola della Valtellina", "Prosciutto Crudo Dolce", "Fesa di Tacchino a fette"
            )
        ),
        FoodCategory(
            categoryName = "🐟 Pesce & Crostacei",
            items = listOf(
                "Filetto di Orata", "Spigola / Branzino", "Merluzzo / Nasello",
                "Tonno al naturale", "Salmone fresco", "Gamberi / Mazzancolle", "Sgombro"
            )
        ),
        FoodCategory(
            categoryName = "🥚 Uova & Legumi",
            items = listOf(
                "Uova Intere", "Albumi d'uovo", "Ceci", "Lenticchie",
                "Fagioli Borlotti", "Fagioli Cannellini", "Piselli", "Edamame"
            )
        ),
        FoodCategory(
            categoryName = "🧀 Latticini & Formaggi Italiani",
            items = listOf(
                "Yogurt Greco 0%", "Ricotta Magra / Vaccina", "Mozzarella di Bufala",
                "Mozzarella Light", "Parmigiano Reggiano / Grana", "Fiocchi di Latte", "Scamorza"
            )
        ),
        FoodCategory(
            categoryName = "🥦 Verdure & Ortaggi",
            items = listOf(
                "Zucchine", "Pomodori", "Broccoli", "Spinaci", "Melanzane",
                "Peperoni", "Finocchi", "Asparagi", "Carote", "Insalata Mista",
                "Rucola", "Radicchio", "Zucca"
            )
        ),
        FoodCategory(
            categoryName = "🍎 Frutta Fresca",
            items = listOf(
                "Mela", "Banana", "Fragole", "Mirtilli / Frutti di bosco",
                "Arancia", "Kiwi", "Pera", "Pesca", "Ananas"
            )
        ),
        FoodCategory(
            categoryName = "🥑 Frutta Secca & Grassi Buoni",
            items = listOf(
                "Olio Extravergine d'Oliva (EVO)", "Mandorle", "Noci",
                "Nocciole", "Burro d'Arachidi 100%", "Semi di Chia / Lino", "Avocado"
            )
        )
    )
}
