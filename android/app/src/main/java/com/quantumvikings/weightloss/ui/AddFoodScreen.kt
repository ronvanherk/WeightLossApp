package com.quantumvikings.weightloss.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.quantumvikings.weightloss.data.AddEntryRequest
import com.quantumvikings.weightloss.data.FoodDto
import com.quantumvikings.weightloss.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(session: Session, meal: String, date: String, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<FoodDto?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300) // wait until typing pauses
        runCatching { session.api.searchFoods(query) }
            .onSuccess { results = it.foods; error = null }
            .onFailure { error = session.describe(it) }
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
                        .onSuccess { picked = it.food }
                        .onFailure {
                            error = if (it is HttpException && it.code() == 404) {
                                "Barcode $code is onbekend. Zoek het product op naam."
                            } else {
                                session.describe(it)
                            }
                        }
                }
            }
            .addOnFailureListener { Toast.makeText(context, "Scannen mislukt", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${mealLabel(meal)} toevoegen") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") }
                },
                actions = { TextButton(onClick = { scan() }) { Text("Scan") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Zoek product (NL, DA, DE)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            LazyColumn {
                items(results, key = { it.id }) { food ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { picked = food }.padding(16.dp, 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(food.name)
                            food.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Text("${food.per100g.kcal.toInt()} kcal/100 g", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    picked?.let { food ->
        PortionDialog(
            food = food,
            onDismiss = { picked = null },
            onConfirm = { grams ->
                scope.launch {
                    runCatching { session.api.addEntry(AddEntryRequest(date, meal, food.id, grams)) }
                        .onSuccess { onDone() }
                        .onFailure { error = session.describe(it); picked = null }
                }
            },
        )
    }
}

@Composable
private fun PortionDialog(food: FoodDto, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var grams by remember { mutableStateOf(fmt(food.servingG ?: 100.0)) }
    val g = parseDecimal(grams)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Hoeveelheid (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (g != null && g > 0) {
                    val p = food.per100g
                    val f = g / 100
                    Text("${(p.kcal * f).toInt()} kcal")
                    Text(
                        "Eiwit ${fmt((p.protein ?: 0.0) * f)} g · Koolh. ${fmt((p.carbs ?: 0.0) * f)} g · Vet ${fmt((p.fat ?: 0.0) * f)} g",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = g != null && g > 0, onClick = { g?.let(onConfirm) }) { Text("Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
