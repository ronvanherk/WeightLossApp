package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.CreateFoodRequest
import com.quantumvikings.weightloss.data.Session
import kotlinx.coroutines.launch

/** A product typed over from the label; shared with the other user of the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFoodScreen(session: Session, onCreated: (Int) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var serving by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val k = parseDecimal(kcal)
    val valid = name.isNotBlank() && k != null && k in 0.0..900.0 &&
        listOf(protein, carbs, fat).all { it.isBlank() || parseDecimal(it)?.let { v -> v in 0.0..100.0 } == true } &&
        (serving.isBlank() || (parseDecimal(serving) ?: 0.0) > 0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Eigen product") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                Text("Product", style = MaterialTheme.typography.titleMedium)
                Field("Naam", name, KeyboardType.Text) { name = it }
                Field("Merk (optioneel)", brand, KeyboardType.Text) { brand = it }
                Field("Portie in gram (optioneel)", serving, KeyboardType.Decimal) { serving = it }
            }
            SectionCard {
                Text("Voedingswaarde per 100 g", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Zoals op het etiket. Alleen energie is verplicht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Field("Energie (kcal)", kcal, KeyboardType.Decimal) { kcal = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Koolh. (g)", carbs, KeyboardType.Decimal, Modifier.weight(1f)) { carbs = it }
                    Field("Eiwit (g)", protein, KeyboardType.Decimal, Modifier.weight(1f)) { protein = it }
                    Field("Vet (g)", fat, KeyboardType.Decimal, Modifier.weight(1f)) { fat = it }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Een eigen product is ook zichtbaar voor de andere gebruiker van de app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            session.api.createFood(
                                CreateFoodRequest(
                                    name = name.trim(),
                                    brand = brand.trim().ifEmpty { null },
                                    kcal = k!!,
                                    protein = parseDecimal(protein),
                                    carbs = parseDecimal(carbs),
                                    fat = parseDecimal(fat),
                                    servingG = parseDecimal(serving),
                                ),
                            )
                        }.onSuccess { onCreated(it.food.id) }
                            .onFailure { error = session.describe(it); busy = false }
                    }
                },
            ) { Text("Opslaan") }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = keyboard == KeyboardType.Decimal && value.isNotBlank() && parseDecimal(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.padding(top = 8.dp),
    )
}
