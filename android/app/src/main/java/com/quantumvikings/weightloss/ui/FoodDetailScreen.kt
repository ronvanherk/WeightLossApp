package com.quantumvikings.weightloss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.AddEntryRequest
import com.quantumvikings.weightloss.data.FoodDetailResponse
import com.quantumvikings.weightloss.data.FoodUnitDto
import com.quantumvikings.weightloss.data.NewUnitRequest
import com.quantumvikings.weightloss.data.NutrientGroupDto
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.ui.theme.CarbsColor
import com.quantumvikings.weightloss.ui.theme.FatColor
import com.quantumvikings.weightloss.ui.theme.MEAL_STYLES
import com.quantumvikings.weightloss.ui.theme.ProteinColor
import kotlinx.coroutines.launch

@Composable
fun FoodDetailScreen(
    session: Session,
    foodId: Int,
    meal: String,
    date: String,
    initialGrams: Double?,
    onOpenFood: (Int) -> Unit,
    onEditRecipe: (Int) -> Unit,
    onAdded: () -> Unit,
    onBack: () -> Unit,
) {
    var detail by remember { mutableStateOf<FoodDetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var chosenMeal by remember { mutableStateOf(meal) }
    var favorite by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf<List<FoodUnitDto>>(emptyList()) }
    // Which measure the amount is in: BASE (g/ml), SERVING or a unit's key.
    var measureKey by remember { mutableStateOf(BASE) }
    var addingUnit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(foodId) {
        runCatching { session.api.food(foodId) }
            .onSuccess {
                detail = it
                favorite = it.favorite
                units = it.units
                val serving = it.food.servingG
                if (initialGrams == null && serving != null) {
                    measureKey = SERVING
                    amount = "1"
                } else {
                    amount = fmt(initialGrams ?: 100.0)
                }
            }
            .onFailure { error = session.describe(it) }
    }

    val style = MEAL_STYLES.getValue(chosenMeal)
    val d = detail
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            else CircularProgressIndicator()
        }
        return
    }
    val food = d.food
    val unit = unitLabel(food.unit)
    val measures = measuresOf(food.unit, food.servingG, food.servingLabel, units)
    val measure = measures.firstOrNull { it.key == measureKey } ?: measures.first()
    val grams = parseDecimal(amount)?.let { Math.round(it * measure.grams * 10) / 10.0 }?.takeIf { it > 0 && it <= 5000 }
    val f = (grams ?: 0.0) / 100
    val p = food.per100g

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.linearGradient(listOf(style.color, style.color.copy(alpha = 0.75f)))),
            ) {
                Column(modifier = Modifier.statusBarsPadding().padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White) }
                        Spacer(Modifier.weight(1f))
                        if (food.source == "RECIPE") {
                            IconButton(onClick = { onEditRecipe(food.id) }) { Icon(Icons.Filled.Edit, "Recept bewerken", tint = Color.White) }
                        }
                        IconButton(onClick = {
                            val now = !favorite
                            favorite = now
                            scope.launch {
                                runCatching { if (now) session.api.addFavorite(food.id) else session.api.removeFavorite(food.id) }
                                    .onFailure { favorite = !now; error = session.describe(it) }
                            }
                        }) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (favorite) "Uit favorieten" else "Aan favorieten toevoegen",
                                tint = Color.White,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(categoryEmoji(food.category), style = MaterialTheme.typography.displaySmall) }
                        Spacer(Modifier.height(10.dp))
                        Text(food.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        val sub = listOfNotNull(food.brand, food.category).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${p.kcal.toInt()} kcal per 100 $unit",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                // Portion: an amount in grams, portions or one of the units
                SectionCard {
                    Text("Hoeveelheid", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        suffix = { Text(if (measure.key == BASE) unit else "× ${measure.label}") },
                        singleLine = true,
                        isError = amount.isNotEmpty() && grams == null,
                        supportingText = if (measure.key != BASE && grams != null) {
                            { Text("= ${fmt(grams)} $unit") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        for (m in measures) {
                            FilterChip(
                                selected = m.key == measure.key,
                                onClick = {
                                    if (m.key == measure.key) return@FilterChip
                                    // Grams stay grams; a unit starts at one of it.
                                    amount = if (m.key == BASE) fmt(grams ?: 100.0) else "1"
                                    measureKey = m.key
                                },
                                label = { Text(if (m.key == BASE) unit else "${m.label} (${fmt(Math.round(m.grams * 10) / 10.0)} $unit)") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = style.color.copy(alpha = 0.2f)),
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = { addingUnit = true },
                            label = { Text("+ Eenheid") },
                        )
                    }
                    if (measure.key == BASE) {
                        val quick = buildList {
                            initialGrams?.let { add("Vorige keer" to it) }
                            add("50 $unit" to 50.0)
                            add("100 $unit" to 100.0)
                            add("200 $unit" to 200.0)
                        }.distinctBy { it.second }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            for ((label, g) in quick) {
                                FilterChip(
                                    selected = grams == g,
                                    onClick = { amount = fmt(g) },
                                    label = { Text(if (label.endsWith(unit)) label else "$label (${fmt(g)} $unit)") },
                                )
                            }
                        }
                    }
                    if (measure.unitId != null && measure.mine) {
                        TextButton(onClick = {
                            val id = measure.unitId
                            scope.launch {
                                runCatching { session.api.deleteUnit(food.id, id) }
                                    .onSuccess {
                                        units = units.filter { it.id != id }
                                        amount = fmt(grams ?: 100.0)
                                        measureKey = BASE
                                    }
                                    .onFailure { error = session.describe(it) }
                            }
                        }) { Text("Eenheid \"${measure.label}\" verwijderen", color = MaterialTheme.colorScheme.error) }
                    }
                }

                // Energy and macros for the portion
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MacroDonut(protein = (p.protein ?: 0.0), carbs = (p.carbs ?: 0.0), fat = (p.fat ?: 0.0), kcal = (p.kcal * f).toInt())
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            MacroRow("Koolhydraten", (p.carbs ?: 0.0) * f, CarbsColor)
                            MacroRow("Eiwit", (p.protein ?: 0.0) * f, ProteinColor)
                            MacroRow("Vet", (p.fat ?: 0.0) * f, FatColor)
                        }
                    }
                }

                // Meal
                SectionCard {
                    Text("Maaltijd", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        for ((m, label) in MEALS) {
                            val ms = MEAL_STYLES.getValue(m)
                            FilterChip(
                                selected = chosenMeal == m,
                                onClick = { chosenMeal = m },
                                label = { Text("${ms.emoji} $label") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ms.color.copy(alpha = 0.2f)),
                            )
                        }
                    }
                }

                // Nutrition table
                SectionCard {
                    Row {
                        Text("Voedingswaarde", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("100 $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                        Text(if (grams != null) "${fmt(grams)} $unit" else "portie", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    NutrientLine("Energie", p.kcal, "kcal", f, bold = true)
                    NutrientLine("Vet", p.fat, "g", f, bold = true)
                    NutrientLine("  waarvan verzadigd", p.saturatedFat, "g", f)
                    NutrientLine("Koolhydraten", p.carbs, "g", f, bold = true)
                    NutrientLine("  waarvan suikers", p.sugar, "g", f)
                    NutrientLine("Voedingsvezel", p.fiber, "g", f)
                    NutrientLine("Eiwit", p.protein, "g", f, bold = true)
                    NutrientLine("Zout", p.salt, "g", f)
                }

                for (group in d.nutrientGroups) NutrientSection(group, f)

                if (d.ingredients.isNotEmpty()) {
                    SectionCard(padding = 0.dp) {
                        Text("Ingrediënten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                        for (i in d.ingredients) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onOpenFood(i.foodId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(i.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Pill("${fmt(Math.round(i.share * 10) / 10.0)}%", style.color)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                val extras = buildList {
                    if (d.synonyms.isNotEmpty()) add("Ook bekend als" to d.synonyms.joinToString(", "))
                    d.remark?.let { add("Opmerking" to it) }
                    if (d.fortified.isNotEmpty()) add("Verrijkt met" to d.fortified.joinToString(", "))
                    if (d.traces.isNotEmpty()) add("Sporen van" to d.traces.joinToString(", "))
                }
                if (extras.isNotEmpty()) {
                    SectionCard {
                        for ((label, text) in extras) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }

                Text(
                    when (food.source) {
                        "NEVO" -> "Bron: NEVO-online versie 2025/9.0, RIVM, Bilthoven"
                        "OFF" -> "Bron: Open Food Facts (ODbL)" + (food.barcode?.let { " · barcode $it" } ?: "")
                        "CUSTOM" -> "Eigen product"
                        "RECIPE" -> "Eigen recept" + (food.servingG?.let { " · 1 portie = ${fmt(it)} g" } ?: "")
                        "FRIDA" -> "Bron: © The Danish Food Composition Database (fcdb.fooddata.dk), versie 6.1, mei 2026, DTU Fødevareinstituttet"
                        "BLS" -> "Bron: Max Rubner-Institut, Bundeslebensmittelschlüssel (BLS) 4.0, 2025"
                        else -> "Bron: ${food.source}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // Sticky add button
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp)) {
            Button(
                onClick = {
                    val g = grams ?: return@Button
                    busy = true
                    scope.launch {
                        runCatching { session.api.addEntry(AddEntryRequest(date, chosenMeal, food.id, g)) }
                            .onSuccess { onAdded() }
                            .onFailure { error = session.describe(it); busy = false }
                    }
                },
                enabled = grams != null && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = style.color),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    if (grams == null) "Vul een hoeveelheid in"
                    else "Toevoegen aan ${style.label.lowercase()} · ${(p.kcal * f).toInt()} kcal",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (addingUnit) {
        NewUnitDialog(
            unit = unit,
            suggestedGrams = grams,
            onDismiss = { addingUnit = false },
            onSave = { name, g ->
                scope.launch {
                    runCatching { session.api.addUnit(food.id, NewUnitRequest(name, g)) }
                        .onSuccess { u ->
                            units = (units + u).sortedBy { it.grams }
                            measureKey = "unit${u.id}"
                            amount = "1"
                            addingUnit = false
                        }
                        .onFailure { error = session.describe(it); addingUnit = false }
                }
            },
        )
    }
}

private const val BASE = "base"
private const val SERVING = "serving"

/** A way to measure a food: [grams] for one of it. [unitId] is set for a unit a user added. */
private data class Measure(val key: String, val label: String, val grams: Double, val unitId: Int? = null, val mine: Boolean = false)

/**
 * Grams (or ml), the pack's portion, the units users added, and rough kitchen measures. A spoon
 * weighs differently per food, so those are only a starting point; a unit of your own is exact.
 */
private fun measuresOf(unit: String, servingG: Double?, servingLabel: String?, units: List<FoodUnitDto>): List<Measure> = buildList {
    add(Measure(BASE, unitLabel(unit), 1.0))
    servingG?.let { add(Measure(SERVING, servingLabel ?: "portie", it)) }
    for (u in units) add(Measure("unit${u.id}", u.name, u.grams, u.id, u.mine))
    if (unit == "ml") {
        add(Measure("glass", "glas", 200.0))
        add(Measure("cup", "kopje", 150.0))
    } else {
        add(Measure("tbsp", "eetlepel", 15.0))
        add(Measure("tsp", "theelepel", 5.0))
    }
}

@Composable
private fun NewUnitDialog(unit: String, suggestedGrams: Double?, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(suggestedGrams?.let { fmt(it) } ?: "") }
    val grams = parseDecimal(amount)?.takeIf { it > 0 && it <= 5000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigen eenheid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Bijvoorbeeld \"boterham\" of \"handje\". Weeg het één keer; daarna kies je het met één tik. Ook de anderen zien deze eenheid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Naam") }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Weegt") },
                    suffix = { Text(unit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && grams != null, onClick = { onSave(name.trim(), grams!!) }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** Share of energy from each macro, with the portion's kcal in the middle. */
@Composable
private fun MacroDonut(protein: Double, carbs: Double, fat: Double, kcal: Int) {
    val parts = listOf(carbs * 4 to CarbsColor, protein * 4 to ProteinColor, fat * 9 to FatColor)
    val total = parts.sumOf { it.first }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(108.dp)) {
            val stroke = 12.dp.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val tl = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            if (total <= 0) {
                drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            } else {
                var start = -90f
                for ((v, color) in parts) {
                    val sweep = (v / total * 360).toFloat()
                    if (sweep > 0) drawArc(color, start, sweep - 2f, false, tl, arc, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$kcal", style = MaterialTheme.typography.headlineSmall)
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MacroRow(label: String, grams: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("${fmt(Math.round(grams * 10) / 10.0)} g", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NutrientLine(label: String, per100: Double?, unit: String, factor: Double, bold: Boolean = false) {
    if (per100 == null) return
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        Text("${round(per100)} $unit", modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        Text("${round(per100 * factor)} $unit", modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
    }
}

/** Two significant decimals for small values (vitamins in µg), one for the rest. */
private fun round(v: Double): String = when {
    v == 0.0 -> "0"
    v < 1 -> String.format(java.util.Locale.GERMANY, "%.2f", v).trimEnd('0').trimEnd(',')
    else -> fmt(Math.round(v * 10) / 10.0)
}

@Composable
private fun NutrientSection(group: NutrientGroupDto, factor: Double) {
    var open by remember { mutableStateOf(false) }
    SectionCard(padding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
        ) {
            Text(
                when (group.name) {
                    "Mineralen" -> "🧂  "
                    "Vitamines" -> "🍊  "
                    "Vetzuren" -> "🫒  "
                    else -> "⚡  "
                } + group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Pill("${group.nutrients.size}", MaterialTheme.colorScheme.primary)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Inklappen" else "Uitklappen")
        }
        AnimatedVisibility(open) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))
                for (n in group.nutrients) NutrientLine(n.name, n.value, n.unit, factor)
            }
        }
    }
}
