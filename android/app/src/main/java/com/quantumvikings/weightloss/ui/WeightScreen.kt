package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.WeightDto
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(session: Session, onBack: () -> Unit) {
    var weights by remember { mutableStateOf<List<WeightDto>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        runCatching { session.api.weights() }
            .onSuccess { weights = it.weights.reversed() }
            .onFailure { error = session.describe(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gewicht") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Gewicht vandaag (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    val kg = parseDecimal(input)
                    Button(enabled = kg != null && kg in 30.0..300.0, onClick = {
                        scope.launch {
                            runCatching { session.api.saveWeight(WeightDto(LocalDate.now().toString(), kg!!)) }
                                .onSuccess { input = ""; reload++ }
                                .onFailure { error = session.describe(it) }
                        }
                    }) { Text("Opslaan") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                if (weights.size >= 2) {
                    val change = weights.first().weightKg - weights.last().weightKg
                    Text(
                        "${if (change <= 0) "" else "+"}${fmt(Math.round(change * 10) / 10.0)} kg sinds ${weights.last().date}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(weights, key = { it.date }) { w ->
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
                    Text(w.date, modifier = Modifier.weight(1f))
                    Text("${fmt(w.weightKg)} kg")
                }
                HorizontalDivider()
            }
        }
    }
}
