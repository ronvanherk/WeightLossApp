package com.quantumvikings.weightloss.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.DaySummaryDto
import com.quantumvikings.weightloss.data.FastDto
import com.quantumvikings.weightloss.data.MeResponse
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.WeekResponse
import com.quantumvikings.weightloss.data.WeightDto
import com.quantumvikings.weightloss.ui.theme.Green
import com.quantumvikings.weightloss.ui.theme.HeaderGradient
import com.quantumvikings.weightloss.ui.theme.Orange
import com.quantumvikings.weightloss.ui.theme.WaterColor
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val NL = Locale.forLanguageTag("nl")
private val SHORT = DateTimeFormatter.ofPattern("d MMM", NL)
private val RANGES = listOf("1 mnd" to 1L, "3 mnd" to 3L, "6 mnd" to 6L, "1 jaar" to 12L)

@Composable
fun ProgressScreen(session: Session, onWeek: () -> Unit) {
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var weights by remember { mutableStateOf<List<WeightDto>>(emptyList()) }
    var week by remember { mutableStateOf<List<DaySummaryDto>>(emptyList()) }
    var fasts by remember { mutableStateOf<List<FastDto>>(emptyList()) }
    var thisWeek by remember { mutableStateOf<WeekResponse?>(null) }
    var months by remember { mutableStateOf(3L) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    LaunchedEffect(reload) {
        runCatching { session.api.me() }.onSuccess { me = it }.onFailure { error = session.describe(it) }
        runCatching { session.api.summary(today.minusDays(6).toString(), today.toString()) }.onSuccess { week = it.days }
        runCatching { session.api.fasting() }.onSuccess { fasts = it.recent }
        runCatching { session.api.week(today.toString()) }.onSuccess { thisWeek = it }
    }
    LaunchedEffect(months, reload) {
        runCatching { session.api.weights(today.minusMonths(months).toString()) }
            .onSuccess { weights = it.weights }
            .onFailure { error = session.describe(it) }
    }

    val current = weights.lastOrNull()?.weightKg
    val start = weights.firstOrNull()?.weightKg
    val goal = me?.profile?.goalWeightKg
    val heightM = me?.profile?.heightCm?.div(100)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(HeaderGradient),
        ) {
            Column(modifier = Modifier.statusBarsPadding().padding(20.dp)) {
                Text("Voortgang", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(current?.let { fmt(it) } ?: "–", style = MaterialTheme.typography.displayMedium, color = Color.White)
                    Text(" kg", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { adding = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Green),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text(" Gewicht")
                    }
                }
                if (current != null && goal != null) {
                    val toGo = current - goal
                    Text(
                        if (toGo > 0.05) "Nog ${fmt(Math.round(toGo * 10) / 10.0)} kg tot je doel van ${fmt(goal)} kg" else "Doel van ${fmt(goal)} kg bereikt! 🎉",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (start != null && start > goal) {
                        val progress = ((start - current) / (start - goal)).toFloat().coerceIn(0f, 1f)
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(RoundedCornerShape(50)).background(Color.White))
                        }
                        Text(
                            "${(progress * 100).toInt()}% van je doel sinds ${LocalDate.parse(weights.first().date).format(SHORT)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else if (current == null) {
                    Text("Voer je gewicht in om je voortgang te volgen.", color = Color.White.copy(alpha = 0.9f))
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // This week so far; the whole review on its own screen
            SectionCard(modifier = Modifier.clickable(onClick = onWeek)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weekoverzicht", style = MaterialTheme.typography.titleMedium)
                        Text(
                            thisWeek?.let { w ->
                                if (w.daysLogged == 0) "Deze week nog niets bijgehouden"
                                else "Deze week ${w.daysOnTarget} van ${w.daysCounted} dagen binnen je doel"
                            } ?: "Gemiddelden, dagen binnen je doel en je trend",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Start", start?.let { "${fmt(it)} kg" }, Modifier.weight(1f))
                StatCard("Verschil", if (start != null && current != null) signed(current - start) + " kg" else null, Modifier.weight(1f))
                StatCard(
                    "BMI",
                    if (current != null && heightM != null && heightM > 0) fmt(Math.round(current / (heightM * heightM) * 10) / 10.0) else null,
                    Modifier.weight(1f),
                )
            }

            // Weight chart
            SectionCard {
                Text("Gewicht", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for ((label, m) in RANGES) {
                        FilterChip(
                            selected = months == m,
                            onClick = { months = m },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (weights.size < 2) {
                    Text(
                        "Na twee metingen zie je hier je lijn. Weeg je bij voorkeur op een vast moment, 's ochtends voor het ontbijt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    WeightChart(weights, goal)
                    Row {
                        Text(LocalDate.parse(weights.first().date).format(SHORT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(LocalDate.parse(weights.last().date).format(SHORT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Calories this week
            SectionCard {
                val logged = week.filter { it.kcal > 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Calorieën, 7 dagen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (logged.isNotEmpty()) Pill("gem. ${logged.sumOf { it.kcal } / logged.size} kcal", Orange)
                }
                Spacer(Modifier.height(12.dp))
                BarChart(week.map { it.kcal.toDouble() }, week.map { it.targetKcal?.toDouble() }, Orange, week.map { dayLetter(it.date) })
            }

            // Water this week
            SectionCard {
                val drank = week.filter { it.waterMl > 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Water, 7 dagen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (drank.isNotEmpty()) Pill("gem. ${fmt(Math.round(drank.sumOf { it.waterMl } / drank.size / 100.0) / 10.0)} l", WaterColor)
                }
                Spacer(Modifier.height(12.dp))
                BarChart(week.map { it.waterMl / 1000.0 }, week.map { 2.0 }, WaterColor, week.map { dayLetter(it.date) })
            }

            // Steps this week, once Health Connect has delivered any
            if (week.any { (it.steps ?: 0) > 0 }) {
                SectionCard {
                    val walked = week.mapNotNull { it.steps }.filter { it > 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stappen, 7 dagen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("gem. ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.GERMANY).format(walked.sum() / walked.size)}", StepsColor)
                    }
                    Spacer(Modifier.height(12.dp))
                    BarChart(week.map { (it.steps ?: 0).toDouble() }, week.map { 10_000.0 }, StepsColor, week.map { dayLetter(it.date) })
                }
            }

            // Fasting: the last finished fasts against their goal
            if (fasts.isNotEmpty()) {
                SectionCard {
                    val shown = fasts.take(7).reversed()
                    val hours = shown.map { f -> Duration.between(Instant.parse(f.startedAt), Instant.parse(f.endedAt!!)).toMinutes() / 60.0 }
                    val reached = fasts.count { f -> Duration.between(Instant.parse(f.startedAt), Instant.parse(f.endedAt!!)).toMinutes() >= f.goalHours * 60L }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vasten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("$reached van ${fasts.size} gehaald", FastingColor)
                    }
                    Spacer(Modifier.height(12.dp))
                    BarChart(
                        hours,
                        shown.map { it.goalHours.toDouble() },
                        FastingColor,
                        shown.map { dayLetter(Instant.parse(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Gemiddeld ${fmt(Math.round(hours.average() * 10) / 10.0)} uur over de laatste ${shown.size} keer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MeasurementsCard(session, me?.profile?.heightCm)
            PhotosCard(session)

            // History
            if (weights.isNotEmpty()) {
                SectionCard {
                    Text("Metingen", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val recent = weights.reversed().take(10)
                    recent.forEachIndexed { i, w ->
                        val prev = recent.getOrNull(i + 1)
                        Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(LocalDate.parse(w.date).format(DateTimeFormatter.ofPattern("EEE d MMMM", NL)), modifier = Modifier.weight(1f))
                            prev?.let {
                                val diff = w.weightKg - it.weightKg
                                Pill(signed(diff), if (diff <= 0) Green else Orange)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text("${fmt(w.weightKg)} kg", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        WeightDialog(
            initial = current,
            onDismiss = { adding = false },
            onSave = { kg ->
                scope.launch {
                    runCatching { session.api.saveWeight(WeightDto(today.toString(), kg)) }
                        .onSuccess { adding = false; reload++ }
                        .onFailure { error = session.describe(it); adding = false }
                }
            },
        )
    }
}

internal fun signed(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r > 0) "+${fmt(r)}" else fmt(r).replace("-", "−")
}

internal fun dayLetter(date: String) = LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, NL).take(2)

@Composable
private fun StatCard(label: String, value: String?, modifier: Modifier) {
    SectionCard(modifier = modifier, padding = 14.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "–", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WeightChart(weights: List<WeightDto>, goal: Double?) = TrendChart(weights.map { it.date }, weights.map { it.weightKg }, goal)

/** Line with a soft fill below it; the goal as a dashed line when it is in view. */
@Composable
internal fun TrendChart(dates: List<String>, values: List<Double>, goal: Double? = null, margin: Double = 0.5) {
    val line = MaterialTheme.colorScheme.primary
    val goalColor = Orange
    val grid = MaterialTheme.colorScheme.outlineVariant
    val firstDay = LocalDate.parse(dates.first()).toEpochDay()
    val lastDay = LocalDate.parse(dates.last()).toEpochDay()
    val all = values + listOfNotNull(goal)
    val min = all.min() - margin
    val max = all.max() + margin
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp)) {
        val span = (lastDay - firstDay).coerceAtLeast(1)
        fun x(date: String) = (LocalDate.parse(date).toEpochDay() - firstDay).toFloat() / span * size.width
        fun y(v: Double) = ((max - v) / (max - min)).toFloat() * size.height
        for (i in 0..3) {
            val gy = size.height * i / 3
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        if (goal != null) {
            drawLine(
                goalColor,
                Offset(0f, y(goal)),
                Offset(size.width, y(goal)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }
        val path = Path()
        dates.forEachIndexed { i, d -> if (i == 0) path.moveTo(x(d), y(values[i])) else path.lineTo(x(d), y(values[i])) }
        val fill = Path().apply {
            addPath(path)
            lineTo(x(dates.last()), size.height)
            lineTo(x(dates.first()), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        dates.forEachIndexed { i, d ->
            drawCircle(Color.White, 5.dp.toPx(), Offset(x(d), y(values[i])))
            drawCircle(line, 3.5.dp.toPx(), Offset(x(d), y(values[i])))
        }
    }
}

/** One bar per day; the target as a small dash above each bar. */
@Composable
internal fun BarChart(values: List<Double>, targets: List<Double?>, color: Color, labels: List<String>) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (values + targets.filterNotNull()).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val barW = slot * 0.5f
            values.forEachIndexed { i, v ->
                val left = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(left, 0f), androidx.compose.ui.geometry.Size(barW, size.height), androidx.compose.ui.geometry.CornerRadius(barW / 2))
                val h = (v / max * size.height).toFloat()
                if (h > 0) {
                    drawRoundRect(
                        if (targets[i] != null && v > targets[i]!! * 1.05 && color == Orange) Color(0xFFE5484D) else color,
                        Offset(left, size.height - h),
                        androidx.compose.ui.geometry.Size(barW, h),
                        androidx.compose.ui.geometry.CornerRadius(barW / 2),
                    )
                }
                targets[i]?.let { t ->
                    val ty = size.height - (t / max * size.height).toFloat()
                    drawLine(targetColor, Offset(left - 4, ty), Offset(left + barW + 4, ty), strokeWidth = 2.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (l in labels) {
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WeightDialog(initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var input by remember { mutableStateOf(initial?.let { fmt(it) } ?: "") }
    val kg = parseDecimal(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gewicht vandaag") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(enabled = kg != null && kg in 30.0..300.0, onClick = { onSave(kg!!) }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
