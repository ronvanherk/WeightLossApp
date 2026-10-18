package com.quantumvikings.weightloss.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.quantumvikings.weightloss.data.AddEntryRequest
import com.quantumvikings.weightloss.data.FoodDto
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.ui.theme.MEAL_STYLES
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val LIST_TABS = listOf("Recent", "Favorieten")

@Composable
fun AddFoodScreen(
    session: Session,
    meal: String,
    date: String,
    onOpenFood: (id: Int, grams: Double?) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val style = MEAL_STYLES.getValue(meal)
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<FoodDto>>(emptyList()) }
    var recent by remember { mutableStateOf<List<FoodDto>?>(null) }
    var favorites by remember { mutableStateOf<List<FoodDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Foods added from this screen, so their "+" turns into a check.
    var added by remember { mutableStateOf(setOf<Int>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        runCatching { session.api.recentFoods(meal) }.onSuccess { recent = it.foods }.onFailure { recent = emptyList() }
        runCatching { session.api.favoriteFoods() }.onSuccess { favorites = it.foods }.onFailure { favorites = emptyList() }
    }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300) // wait until typing pauses
        runCatching { session.api.searchFoods(query) }
            .onSuccess { results = it.foods; error = null }
            .onFailure { error = session.describe(it) }
        searching = false
    }

    fun quickAdd(food: FoodDto) {
        val grams = food.lastGrams ?: food.servingG ?: 100.0
        scope.launch {
            runCatching { session.api.addEntry(AddEntryRequest(date, meal, food.id, grams)) }
                .onSuccess {
                    added = added + food.id
                    snackbar.showSnackbar("${food.name} (${fmt(grams)} ${unitLabel(food.unit)}) toegevoegd aan ${style.label.lowercase()}")
                }
                .onFailure { error = session.describe(it) }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue ?: return@addOnSuccessListener
                scope.launch {
                    runCatching { session.api.foodByBarcode(code) }
                        .onSuccess { onOpenFood(it.food.id, null) }
                        .onFailure {
                            error = if (it is HttpException && it.code() == 404) {
                                "Barcode $code is onbekend. Zoek op naam, of maak het product zelf aan."
                            } else {
                                session.describe(it)
                            }
                        }
                }
            }
            .addOnFailureListener { Toast.makeText(context, "Scannen mislukt", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(style.color)
                    .statusBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White) }
                    Text(
                        "${style.emoji}  ${style.label}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (added.isNotEmpty()) {
                        Text("${added.size} toegevoegd", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(12.dp))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Zoek een product (NL, DA, DE)") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Wissen") }
                        },
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
                    Spacer(Modifier.width(10.dp))
                    FilledIconButton(
                        onClick = { scan() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = style.color,
                        ),
                        modifier = Modifier.padding(0.dp),
                    ) { Icon(Icons.Filled.QrCodeScanner, "Barcode scannen") }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            val searchMode = query.trim().length >= 2
            if (!searchMode) {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = style.color)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    LIST_TABS.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
            }
            val list = when {
                searchMode -> results
                tab == 0 -> recent
                else -> favorites
            }
            when {
                list == null || (searchMode && searching && results.isEmpty()) -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = style.color)
                }
                list.isEmpty() -> EmptyState(
                    when {
                        searchMode -> "Niets gevonden voor \"${query.trim()}\". Probeer een ander woord, scan de barcode of maak het product zelf aan."
                        tab == 0 -> "Nog niets gegeten met deze app. Zoek hierboven een product of scan een barcode."
                        else -> "Nog geen favorieten. Tik bij een product op het hartje."
                    },
                    onCreate,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { food ->
                        FoodRow(
                            food = food,
                            accent = style.color,
                            added = food.id in added,
                            onOpen = { onOpenFood(food.id, food.lastGrams) },
                            onQuickAdd = { quickAdd(food) },
                        )
                    }
                    item {
                        CreateLink(onCreate)
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: FoodDto, accent: Color, added: Boolean, onOpen: () -> Unit, onQuickAdd: () -> Unit) {
    SectionCard(padding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            EmojiBadge(categoryEmoji(food.category), accent, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val portion = food.lastGrams ?: food.servingG ?: 100.0
                Text(
                    listOfNotNull(
                        food.brand ?: food.category,
                        "${fmt(portion)} ${unitLabel(food.unit)} · ${(food.per100g.kcal * portion / 100).toInt()} kcal",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                MacroLine(food.per100g.protein, food.per100g.carbs, food.per100g.fat)
            }
            FilledIconButton(
                onClick = onQuickAdd,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (added) accent else accent.copy(alpha = 0.15f),
                    contentColor = if (added) Color.White else accent,
                ),
            ) { Icon(if (added) Icons.Filled.Check else Icons.Filled.Add, "Snel toevoegen") }
        }
    }
}

@Composable
private fun EmptyState(text: String, onCreate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Text("🥕", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        CreateLink(onCreate)
    }
}

@Composable
private fun CreateLink(onCreate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onCreate).padding(12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.EditNote, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Eigen product aanmaken", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}
