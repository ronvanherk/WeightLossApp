package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quantumvikings.weightloss.data.FoodDto
import com.quantumvikings.weightloss.data.RecipeIngredientRequest
import com.quantumvikings.weightloss.data.RecipeRequest
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.ui.theme.Orange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One line of the recipe being edited; grams as typed. */
private class Line(val food: FoodDto, grams: String) {
    var grams by mutableStateOf(grams)
    val g get() = parseDecimal(grams)?.takeIf { it > 0 }
}

/**
 * Create or edit a recipe made of other foods. Shared with the other user, like own products.
 * [recipeId] null = new. [onSaved] gets the recipe's food id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    session: Session,
    recipeId: Int?,
    onSaved: (Int) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var cooked by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<Line>() }
    var loading by remember { mutableStateOf(recipeId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recipeId) {
        if (recipeId == null) return@LaunchedEffect
        runCatching { session.api.recipe(recipeId) }
            .onSuccess { r ->
                name = r.name
                servings = r.servings.toString()
                cooked = r.cookedG?.let(::fmt) ?: ""
                lines.clear()
                lines.addAll(r.ingredients.map { Line(it.food, fmt(it.grams)) })
            }
            .onFailure { error = session.describe(it) }
        loading = false
    }

    val portions = servings.toIntOrNull()?.takeIf { it in 1..50 }
    val cookedG = cooked.takeIf { it.isNotBlank() }?.let(::parseDecimal)
    val cookedValid = cooked.isBlank() || (cookedG != null && cookedG > 0)
    val rawG = lines.sumOf { it.g ?: 0.0 }
    fun total(per100: (FoodDto) -> Double?) = lines.sumOf { (per100(it.food) ?: 0.0) * (it.g ?: 0.0) / 100 }
    val kcal = total { it.per100g.kcal }
    val valid = name.isNotBlank() && portions != null && cookedValid && lines.isNotEmpty() && lines.all { it.g != null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == null) "Nieuw recept" else "Recept bewerken") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
                actions = {
                    if (recipeId != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Recept verwijderen") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Orange,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Naam, bijv. Lasagne van Cindy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = servings,
                            onValueChange = { servings = it.filter(Char::isDigit).take(2) },
                            label = { Text("Porties") },
                            singleLine = true,
                            isError = portions == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = cooked,
                            onValueChange = { cooked = it },
                            label = { Text("Gewicht klaar (g)") },
                            placeholder = { Text(fmt(Math.round(rawG).toDouble())) },
                            singleLine = true,
                            isError = !cookedValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Text(
                        "Weeg je de pan na het koken, vul dan het gewicht in: pasta en rijst worden zwaarder, saus lichter. " +
                            "Leeg = de som van de ingrediënten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                SectionCard(padding = 0.dp) {
                    Text("Ingrediënten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    if (lines.isEmpty()) {
                        Text(
                            "Nog niets toegevoegd.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    for (line in lines) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.food.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${((line.g ?: 0.0) * line.food.per100g.kcal / 100).toInt()} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = line.grams,
                                onValueChange = { line.grams = it },
                                suffix = { Text(unitLabel(line.food.unit)) },
                                singleLine = true,
                                isError = line.g == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(110.dp),
                            )
                            IconButton(onClick = { lines.remove(line) }) { Icon(Icons.Filled.Close, "Weghalen") }
                        }
                    }
                    TextButton(onClick = { adding = true }, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ingrediënt toevoegen")
                    }
                }

                if (lines.isNotEmpty() && portions != null) {
                    SectionCard {
                        Text("Per portie", style = MaterialTheme.typography.titleMedium)
                        val weight = cookedG ?: rawG
                        Text(
                            "${(kcal / portions).toInt()} kcal · ${fmt(Math.round(weight / portions).toDouble())} g",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        MacroLine(
                            total { it.per100g.protein } / portions,
                            total { it.per100g.carbs } / portions,
                            total { it.per100g.fat } / portions,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Hele recept ${kcal.toInt()} kcal" + if (weight > 0) " · ${(kcal / weight * 100).toInt()} kcal per 100 g" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Een recept is ook zichtbaar voor de andere gebruiker van de app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp)) {
                Button(
                    enabled = valid && !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        busy = true
                        val body = RecipeRequest(
                            name = name.trim(),
                            servings = portions!!,
                            cookedG = cookedG,
                            ingredients = lines.map { RecipeIngredientRequest(it.food.id, it.g!!) },
                        )
                        scope.launch {
                            runCatching {
                                if (recipeId == null) session.api.createRecipe(body) else session.api.updateRecipe(recipeId, body)
                            }
                                .onSuccess { onSaved(it.food.id) }
                                .onFailure { error = session.describe(it); busy = false }
                        }
                    },
                ) { Text("Recept opslaan") }
            }
        }
    }

    if (adding) {
        IngredientPicker(
            session = session,
            onPick = { food ->
                lines.add(Line(food, fmt(food.servingG ?: 100.0)))
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    if (confirmDelete && recipeId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Recept verwijderen?") },
            text = { Text("Ook voor de andere gebruiker. Een recept dat al in het dagboek staat, kan niet weg.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { session.api.deleteRecipe(recipeId) }
                            .onSuccess { onDeleted() }
                            .onFailure { error = session.describe(it) }
                    }
                }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}

/** Full-screen search for an ingredient; recent foods while the query is empty. */
@Composable
private fun IngredientPicker(session: Session, onPick: (FoodDto) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodDto>?>(null) }
    var recent by remember { mutableStateOf<List<FoodDto>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { session.api.recentFoods() }.onSuccess { recent = it.foods } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(300)
        runCatching { session.api.searchFoods(query) }.onSuccess { results = it.foods }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Orange).padding(8.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White) }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zoek een ingrediënt") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            val list = results ?: recent
            if (results == null && recent.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            }
            if (results?.isEmpty() == true) {
                Text("Niets gevonden.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.id }) { food ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(food) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(categoryEmoji(food.category), modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(food.brand, "${food.per100g.kcal.toInt()} kcal per 100 ${unitLabel(food.unit)}").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onPick(food) }) { Text("Kies") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
