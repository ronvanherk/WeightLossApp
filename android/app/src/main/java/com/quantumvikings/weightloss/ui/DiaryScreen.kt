package com.quantumvikings.weightloss.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.CopyRequest
import com.quantumvikings.weightloss.data.DaySummaryDto
import com.quantumvikings.weightloss.data.DiaryResponse
import com.quantumvikings.weightloss.data.EntryDto
import com.quantumvikings.weightloss.data.FastingResponse
import com.quantumvikings.weightloss.data.StartFastRequest
import com.quantumvikings.weightloss.data.UpdateFastRequest
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.UpdateEntryRequest
import com.quantumvikings.weightloss.data.WaterDto
import com.quantumvikings.weightloss.ui.theme.CarbsColor
import com.quantumvikings.weightloss.ui.theme.FatColor
import com.quantumvikings.weightloss.ui.theme.HeaderGradient
import com.quantumvikings.weightloss.ui.theme.MEAL_STYLES
import com.quantumvikings.weightloss.ui.theme.ProteinColor
import com.quantumvikings.weightloss.ui.theme.WaterColor
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val NL = Locale.forLanguageTag("nl")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", NL)
private const val GLASS_ML = 250
private const val WATER_GOAL_ML = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    session: Session,
    date: String,
    onDateChange: (String) -> Unit,
    onAdd: (meal: String) -> Unit,
    onProfile: () -> Unit,
) {
    val day = LocalDate.parse(date)
    var diary by remember { mutableStateOf<DiaryResponse?>(null) }
    var week by remember { mutableStateOf<List<DaySummaryDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<EntryDto?>(null) }
    var picking by remember { mutableStateOf(false) }
    var fasting by remember { mutableStateOf<FastingResponse?>(null) }
    val scope = rememberCoroutineScope()
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    LaunchedEffect(date, reload) {
        runCatching { session.api.diary(date, LocalDate.now().toString()) }
            .onSuccess { diary = it; error = null }
            .onFailure { error = session.describe(it) }
    }
    LaunchedEffect(reload) {
        runCatching { session.api.fasting() }.onSuccess { fasting = it }
    }
    LaunchedEffect(monday, reload) {
        runCatching { session.api.summary(monday.toString(), monday.plusDays(6).toString()) }
            .onSuccess { week = it.days }
    }

    /** Runs a fasting call, then reloads the card; errors go to the screen. */
    fun fast(call: suspend () -> Unit) {
        scope.launch {
            runCatching { call() }
                .onSuccess { fasting = session.api.fasting() }
                .onFailure { error = session.describe(it) }
        }
    }

    fun copyYesterday(meal: String) {
        scope.launch {
            runCatching { session.api.copyDiary(CopyRequest(from = day.minusDays(1).toString(), to = date, meal = meal)) }
                .onSuccess { res ->
                    if (res.copied == 0) error = "Gisteren stond er niets bij ${mealLabel(meal).lowercase()}."
                    else { error = null; reload++ }
                }
                .onFailure { error = session.describe(it) }
        }
    }

    fun setWater(ml: Int) {
        val d = diary ?: return
        diary = d.copy(waterMl = ml) // feels instant; the server call follows
        scope.launch {
            runCatching { session.api.saveWater(WaterDto(date, ml)) }
                .onFailure { error = session.describe(it); reload++ }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                day = day,
                diary = diary,
                week = week,
                onDay = { onDateChange(it.toString()) },
                onPick = { picking = true },
            )
        }
        error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        val d = diary
        if (d != null) {
            if (d.target == null) {
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Stel je doel in", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Vul je profiel en je gewicht in, dan berekenen we hoeveel je per dag kunt eten.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onProfile) { Text("Naar profiel") }
                    }
                }
            } else if (d.target.clamped) {
                item {
                    Text(
                        "Je doel staat op het minimum; een lager tempo is gezonder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            for ((meal, _) in MEALS) {
                item(key = meal) {
                    MealCard(
                        meal = meal,
                        entries = d.entries.filter { it.meal == meal },
                        targetKcal = d.target?.kcal,
                        onAdd = { onAdd(meal) },
                        onEntry = { editing = it },
                        onCopyYesterday = { copyYesterday(meal) },
                    )
                }
            }
            item(key = "water") { WaterCard(ml = d.waterMl, onChange = ::setWater) }
            val f = fasting
            if (f != null && day == LocalDate.now()) {
                item(key = "fasting") {
                    FastingCard(
                        current = f.current,
                        last = f.recent.firstOrNull(),
                        onStart = { goal, at ->
                            fast { session.api.startFast(StartFastRequest(goal, at?.truncatedTo(ChronoUnit.SECONDS)?.toString())) }
                        },
                        onAdjustStart = { at ->
                            f.current?.let { c -> fast { session.api.updateFast(c.id, UpdateFastRequest(startedAt = at.truncatedTo(ChronoUnit.SECONDS).toString())) } }
                        },
                        onStop = { f.current?.let { c -> fast { session.api.updateFast(c.id, UpdateFastRequest(end = true)) } } },
                        onCancel = { f.current?.let { c -> fast { session.api.deleteFast(c.id) } } },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        EditEntrySheet(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { grams, meal ->
                scope.launch {
                    runCatching { session.api.updateEntry(entry.id, UpdateEntryRequest(grams, meal)) }
                        .onSuccess { editing = null; reload++ }
                        .onFailure { error = session.describe(it) }
                }
            },
            onDelete = {
                scope.launch {
                    runCatching { session.api.deleteEntry(entry.id) }
                        .onSuccess { editing = null; reload++ }
                        .onFailure { error = session.describe(it) }
                }
            },
        )
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    picking = false
                }) { Text("Kiezen") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuleren") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Header(
    day: LocalDate,
    diary: DiaryResponse?,
    week: List<DaySummaryDto>,
    onDay: (LocalDate) -> Unit,
    onPick: () -> Unit,
) {
    val today = LocalDate.now()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(HeaderGradient),
    ) {
        Column(modifier = Modifier.statusBarsPadding().padding(bottom = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (day) {
                            today -> "Vandaag"
                            today.minusDays(1) -> "Gisteren"
                            today.plusDays(1) -> "Morgen"
                            else -> day.format(DAY_FORMAT).replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    if (day == today || day == today.minusDays(1) || day == today.plusDays(1)) {
                        Text(day.format(DAY_FORMAT), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                diary?.streak?.takeIf { it >= 2 }?.let { n ->
                    Text(
                        "🔥 $n",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics { contentDescription = "$n dagen op rij bijgehouden" },
                    )
                }
                IconButton(onClick = onPick) { Icon(Icons.Filled.CalendarMonth, "Datum kiezen", tint = Color.White) }
            }
            WeekStrip(day = day, week = week, onDay = onDay)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                HeaderStat("Gegeten", diary?.totals?.kcal?.toInt(), Modifier.weight(1f))
                CalorieRing(eaten = diary?.totals?.kcal?.toInt() ?: 0, target = diary?.target?.kcal)
                HeaderStat("Doel", diary?.target?.kcal, Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                val t = diary?.target
                val totals = diary?.totals
                val track = Color.White.copy(alpha = 0.25f)
                MacroBar("Koolhydraten", totals?.carbs ?: 0.0, t?.carbsG, CarbsColor, Modifier.weight(1f), Color.White, track)
                MacroBar("Eiwit", totals?.protein ?: 0.0, t?.proteinG, ProteinColor.lighter(), Modifier.weight(1f), Color.White, track)
                MacroBar("Vet", totals?.fat ?: 0.0, t?.fatG, FatColor.lighter(), Modifier.weight(1f), Color.White, track)
            }
        }
    }
}

private fun Color.lighter() = Color(red + (1 - red) * 0.25f, green + (1 - green) * 0.25f, blue + (1 - blue) * 0.25f, alpha)

@Composable
private fun HeaderStat(label: String, value: Int?, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.toString() ?: "–", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

/** Monday to Sunday of the shown week; a dot marks days with something logged. */
@Composable
private fun WeekStrip(day: LocalDate, week: List<DaySummaryDto>, onDay: (LocalDate) -> Unit) {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        IconButton(onClick = { onDay(day.minusWeeks(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorige week", tint = Color.White)
        }
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val selected = d == day
            val logged = week.firstOrNull { it.date == d.toString() }?.kcal?.let { it > 0 } == true
            val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "day")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable { onDay(d) }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, NL).take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                )
                Text(
                    "${d.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = if (d == LocalDate.now()) FontWeight.ExtraBold else FontWeight.SemiBold,
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !logged -> Color.Transparent
                                selected -> MaterialTheme.colorScheme.primary
                                else -> Color.White
                            },
                        ),
                )
            }
        }
        IconButton(onClick = { onDay(day.plusWeeks(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Volgende week", tint = Color.White)
        }
    }
}

@Composable
private fun MealCard(
    meal: String,
    entries: List<EntryDto>,
    targetKcal: Int?,
    onAdd: () -> Unit,
    onEntry: (EntryDto) -> Unit,
    onCopyYesterday: () -> Unit,
) {
    val style = MEAL_STYLES.getValue(meal)
    val kcal = entries.sumOf { it.kcal }.toInt()
    val advised = targetKcal?.let { (it * style.share).toInt() }
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp), padding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        ) {
            EmojiBadge(style.emoji, style.color)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(style.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (advised != null) "$kcal / $advised kcal" else "$kcal kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledIconButton(
                onClick = onAdd,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = style.color, contentColor = Color.White),
            ) { Icon(Icons.Filled.Add, "Toevoegen aan ${style.label}") }
        }
        if (entries.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            for (entry in entries) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntry(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(categoryEmoji(entry.category), modifier = Modifier.width(30.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(entry.brand, "${fmt(entry.grams)} ${unitLabel(entry.unit)}").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${entry.kcal.toInt()} kcal", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(6.dp))
        } else {
            TextButton(onClick = onCopyYesterday, modifier = Modifier.padding(start = 64.dp, bottom = 6.dp)) {
                Text("↺ Zelfde als gisteren", color = style.color)
            }
        }
    }
}

/** Eight glasses of 250 ml; tap a glass to fill up to it, tap the last full one to empty it. */
@Composable
private fun WaterCard(ml: Int, onChange: (Int) -> Unit) {
    val glasses = ml / GLASS_ML
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("💧", WaterColor)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${fmt(ml / 1000.0)} / ${fmt(WATER_GOAL_ML / 1000.0)} liter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ml >= WATER_GOAL_ML) Pill("Doel gehaald 🎉", WaterColor)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            for (i in 1..WATER_GOAL_ML / GLASS_ML) {
                val full = i <= glasses
                IconButton(onClick = { onChange(if (i == glasses) (i - 1) * GLASS_ML else i * GLASS_ML) }) {
                    Icon(
                        if (full) Icons.Filled.LocalDrink else Icons.Outlined.LocalDrink,
                        contentDescription = "Glas $i",
                        tint = if (full) WaterColor else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
        if (glasses > WATER_GOAL_ML / GLASS_ML) {
            Text("+ ${glasses - WATER_GOAL_ML / GLASS_ML} extra glazen", style = MaterialTheme.typography.bodySmall, color = WaterColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange((ml - GLASS_ML).coerceAtLeast(0)) }, enabled = ml > 0) { Text("− glas") }
            Button(
                onClick = { onChange(ml + GLASS_ML) },
                colors = ButtonDefaults.buttonColors(containerColor = WaterColor),
            ) { Text("+ glas (250 ml)") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntrySheet(
    entry: EntryDto,
    onDismiss: () -> Unit,
    onSave: (grams: Double, meal: String) -> Unit,
    onDelete: () -> Unit,
) {
    var grams by remember { mutableStateOf(fmt(entry.grams)) }
    var meal by remember { mutableStateOf(entry.meal) }
    val g = parseDecimal(grams)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(categoryEmoji(entry.category), MEAL_STYLES.getValue(meal).color)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    entry.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            OutlinedTextField(
                value = grams,
                onValueChange = { grams = it },
                label = { Text("Hoeveelheid (${unitLabel(entry.unit)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (g != null && g > 0 && entry.grams > 0) {
                val f = g / entry.grams
                Text("${(entry.kcal * f).toInt()} kcal", style = MaterialTheme.typography.headlineSmall)
                MacroLine(entry.protein * f, entry.carbs * f, entry.fat * f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for ((m, label) in MEALS) {
                    val style = MEAL_STYLES.getValue(m)
                    FilterChip(
                        selected = meal == m,
                        onClick = { meal = m },
                        label = { Text("${style.emoji} $label", maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = style.color.copy(alpha = 0.2f),
                        ),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text("Verwijderen") }
                Button(
                    onClick = { g?.let { onSave(it, meal) } },
                    enabled = g != null && g > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Opslaan") }
            }
        }
    }
}
