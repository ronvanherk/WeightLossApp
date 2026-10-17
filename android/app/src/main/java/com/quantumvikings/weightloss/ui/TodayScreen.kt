package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.DiaryResponse
import com.quantumvikings.weightloss.data.EntryDto
import com.quantumvikings.weightloss.data.Session
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    session: Session,
    onAdd: (meal: String, date: String) -> Unit,
    onWeight: () -> Unit,
    onProfile: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var diary by remember { mutableStateOf<DiaryResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(date, reload) {
        runCatching { session.api.diary(date) }
            .onSuccess { diary = it; error = null }
            .onFailure { error = session.describe(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dagboek") },
                actions = {
                    TextButton(onClick = onWeight) { Text("Gewicht") }
                    IconButton(onClick = onProfile) { Icon(Icons.Default.Person, "Profiel") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { date = LocalDate.parse(date).minusDays(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorige dag")
                    }
                    val day = LocalDate.parse(date)
                    Text(
                        if (day == LocalDate.now()) "Vandaag" else day.format(DAY_FORMAT),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { date = LocalDate.parse(date).plusDays(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Volgende dag")
                    }
                }
            }
            error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            diary?.let { d ->
                item { Summary(d, onProfile) }
                for ((meal, label) in MEALS) {
                    val entries = d.entries.filter { it.meal == meal }
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${entries.sumOf { it.kcal }.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { onAdd(meal, date) }) { Icon(Icons.Default.Add, "Toevoegen aan $label") }
                        }
                        HorizontalDivider()
                    }
                    items(entries, key = { it.id }) { entry ->
                        EntryRow(entry, onDelete = {
                            scope.launch {
                                runCatching { session.api.deleteEntry(entry.id) }
                                    .onSuccess { reload++ }
                                    .onFailure { error = session.describe(it) }
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(d: DiaryResponse, onProfile: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val eaten = d.totals.kcal.toInt()
            val target = d.target
            if (target == null) {
                Text("$eaten kcal gegeten", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onProfile) { Text("Vul je profiel en gewicht in voor een dagdoel") }
            } else {
                val left = target.kcal - eaten
                Text(
                    if (left >= 0) "$left kcal over" else "${-left} kcal te veel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                LinearProgressIndicator(
                    progress = { (eaten.toFloat() / target.kcal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$eaten van ${target.kcal} kcal", style = MaterialTheme.typography.bodySmall)
                Row {
                    Macro("Eiwit", d.totals.protein, target.proteinG)
                    Spacer(Modifier.width(16.dp))
                    Macro("Koolh.", d.totals.carbs, target.carbsG)
                    Spacer(Modifier.width(16.dp))
                    Macro("Vet", d.totals.fat, target.fatG)
                }
                if (target.clamped) {
                    Text(
                        "Je doel is begrensd op het minimum; een lager tempo is gezonder.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double, target: Int) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text("${grams.toInt()} / $target g", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EntryRow(entry: EntryDto, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(entry.brand, "${fmt(entry.grams)} g").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("${entry.kcal.toInt()} kcal")
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Verwijderen") }
    }
}
