package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.WeekResponse
import com.quantumvikings.weightloss.ui.theme.CarbsColor
import com.quantumvikings.weightloss.ui.theme.FatColor
import com.quantumvikings.weightloss.ui.theme.Green
import com.quantumvikings.weightloss.ui.theme.HeaderGradient
import com.quantumvikings.weightloss.ui.theme.Orange
import com.quantumvikings.weightloss.ui.theme.ProteinColor
import com.quantumvikings.weightloss.ui.theme.WaterColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

private val NL = Locale.forLanguageTag("nl")

/** One week, Monday to Sunday, against the week before; arrows go back and forth. */
@Composable
fun WeekScreen(session: Session, onBack: () -> Unit) {
    val today = LocalDate.now()
    var day by remember { mutableStateOf(today) }
    var week by remember { mutableStateOf<WeekResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(day) {
        week = null
        error = null
        runCatching { session.api.week(day.toString(), today.toString()) }
            .onSuccess { week = it }
            .onFailure { error = session.describe(it) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(HeaderGradient),
        ) {
            Column(Modifier.statusBarsPadding().padding(bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White) }
                    Text("Weekoverzicht", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    IconButton(onClick = { day = day.minusWeeks(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorige week", tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        val monday = day.with(java.time.DayOfWeek.MONDAY)
                        val short = DateTimeFormatter.ofPattern("d MMM", NL)
                        Text("Week ${monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "${monday.format(short)} – ${monday.plusDays(6).format(short)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    val isCurrent = !day.with(java.time.DayOfWeek.SUNDAY).isBefore(today)
                    IconButton(enabled = !isCurrent, onClick = { day = day.plusWeeks(1) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            "Volgende week",
                            tint = if (isCurrent) Color.White.copy(alpha = 0.3f) else Color.White,
                        )
                    }
                }
                week?.let { w ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("${w.daysOnTarget} van ${w.daysCounted}", style = MaterialTheme.typography.displaySmall, color = Color.White)
                        Text("dagen binnen je doel", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val w = week
            if (w == null) {
                if (error == null) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            SectionCard {
                Text("Calorieën", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Line("Gemiddeld per dag", w.average.kcal?.let { "$it kcal" })
                Line("Je doel gemiddeld", w.average.targetKcal?.let { "$it kcal" })
                Line("Vorige week", w.previous.kcal?.let { "$it kcal" })
                Spacer(Modifier.height(12.dp))
                BarChart(
                    w.days.map { it.kcal.toDouble() },
                    w.days.map { it.targetKcal?.toDouble() },
                    Orange,
                    w.days.map { dayLetter(it.date) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Binnen je doel: tussen 70% en 105% van wat je die dag mocht. " +
                        if (w.daysLogged < w.daysCounted) "${w.daysCounted - w.daysLogged} dag(en) niets bijgehouden." else "Elke dag bijgehouden 👏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (w.average.kcal != null) {
                SectionCard {
                    Text("Macro's, gemiddeld per dag", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Koolh. ${w.average.carbs?.let { fmt(it) } ?: "–"} g", CarbsColor)
                        Pill("Eiwit ${w.average.protein?.let { fmt(it) } ?: "–"} g", ProteinColor)
                        Pill("Vet ${w.average.fat?.let { fmt(it) } ?: "–"} g", FatColor)
                    }
                }
            }

            SectionCard {
                Text("Lichaam", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Line("Gewicht gemiddeld", w.weight.average?.let { "${fmt(it)} kg" })
                Line(
                    "Tegen vorige week",
                    w.weight.change?.let { "${signed(it)} kg" },
                    color = w.weight.change?.let { if (it <= 0) Green else Orange },
                )
                Line("Taille", w.waist.cm?.let { cm -> "${fmt(cm)} cm" + (w.waist.change?.let { " (${signed(it)})" } ?: "") })
                if (w.weight.average == null) {
                    Text(
                        "Weeg je deze week minstens één keer voor een trend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard {
                Text("Verder", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Line("Water gemiddeld", w.average.waterMl?.let { "${fmt(Math.round(it / 100.0) / 10.0)} l" }, color = WaterColor)
                Line("Stappen gemiddeld", w.average.steps?.let { java.text.NumberFormat.getIntegerInstance(Locale.GERMANY).format(it) }, color = StepsColor)
                if (w.fasting.count > 0) Line("Vasten", "${w.fasting.reached} van ${w.fasting.count} gehaald", color = FastingColor)
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String?, color: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value ?: "–", style = MaterialTheme.typography.labelLarge, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
