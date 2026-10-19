package com.quantumvikings.weightloss.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.quantumvikings.weightloss.data.FastingAlarm
import com.quantumvikings.weightloss.data.FastingResponse
import com.quantumvikings.weightloss.data.HealthSteps
import com.quantumvikings.weightloss.data.MemberDto
import com.quantumvikings.weightloss.data.SaveMealRequest
import com.quantumvikings.weightloss.data.ShareRequest
import com.quantumvikings.weightloss.data.TargetDto
import com.quantumvikings.weightloss.data.TotalsDto
import com.quantumvikings.weightloss.data.StepsDto
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
import com.quantumvikings.weightloss.widget.DayWidget
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
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
private val SHARE_FACTORS = listOf(0.5, 0.75, 1.0, 1.25, 1.5)

/** A meal (or some of its entries) about to go to a household member's diary. */
private data class Share(val meal: String, val member: MemberDto, val entryIds: List<Int>? = null)

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
    var household by remember { mutableStateOf<List<MemberDto>>(emptyList()) }
    var sharing by remember { mutableStateOf<Share?>(null) }
    var savingMeal by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val health = remember { HealthSteps(context.applicationContext) }
    var stepsConnected by remember { mutableStateOf<Boolean?>(null) }
    var steps by remember { mutableStateOf<Long?>(null) }
    val connectSteps = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        stepsConnected = granted.containsAll(health.permissions)
        reload++
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Back from Samsung Health or a walk: fresh steps and target. The first resume is the first load.
    var resumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumed) reload++ else resumed = true
    }
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    LaunchedEffect(date, reload) {
        // Steps first, so the target that comes back already includes them.
        stepsConnected = runCatching { health.granted() }.getOrDefault(false)
        steps = null
        if (stepsConnected == true && !day.isAfter(LocalDate.now())) {
            steps = runCatching { health.steps(day) }.getOrNull()
            steps?.let { n -> runCatching { session.api.saveSteps(StepsDto(date, n.toInt())) } }
        }
        runCatching { session.api.diary(date, LocalDate.now().toString()) }
            .onSuccess { diary = it; error = null }
            .onFailure { error = session.describe(it) }
        if (day == LocalDate.now()) DayWidget.refresh(context)
    }
    LaunchedEffect(Unit) {
        runCatching { session.api.household() }.onSuccess { household = it.members }
    }
    LaunchedEffect(reload) {
        runCatching { session.api.fasting() }.onSuccess { fasting = it }
    }
    // The phone reminds at the end of the running fast, wherever it was started or changed.
    LaunchedEffect(fasting) {
        val f = fasting ?: return@LaunchedEffect
        val c = f.current
        if (c == null) FastingAlarm.cancel(context)
        else FastingAlarm.schedule(context, Instant.parse(c.startedAt).plus(Duration.ofHours(c.goalHours.toLong())), c.goalHours)
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
                .onSuccess { if (day == LocalDate.now()) DayWidget.refresh(context) }
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
        notice?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp)) }
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
                        household = household,
                        onShare = { member -> sharing = Share(meal, member) },
                        onSaveMeal = { savingMeal = meal },
                    )
                }
            }
            if (d.target != null && d.entries.isNotEmpty()) {
                item(key = "nutrients") { NutrientsCard(d.totals, d.target) }
            }
            item(key = "water") { WaterCard(ml = d.waterMl, onChange = ::setWater) }
            if ((health.available || health.installable) && !day.isAfter(LocalDate.now())) {
                item(key = "steps") {
                    StepsCard(
                        connected = stepsConnected,
                        installable = health.installable,
                        steps = steps,
                        stepsKcal = d.target?.stepsKcal ?: 0,
                        onConnect = { connectSteps.launch(health.permissions) },
                        onInstall = { runCatching { context.startActivity(health.installIntent()) } },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            val f = fasting
            if (f != null && day == LocalDate.now()) {
                item(key = "fasting") {
                    FastingCard(
                        current = f.current,
                        last = f.recent.firstOrNull(),
                        onStart = { goal, at ->
                            if (Build.VERSION.SDK_INT >= 33 && !FastingAlarm.canNotify(context)) {
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
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
            household = household,
            onDismiss = { editing = null },
            onShare = { member -> editing = null; sharing = Share(entry.meal, member, listOf(entry.id)) },
            onSave = { update ->
                scope.launch {
                    runCatching { session.api.updateEntry(entry.id, update) }
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

    sharing?.let { share ->
        ShareDialog(
            share = share,
            single = share.entryIds != null,
            onDismiss = { sharing = null },
            onConfirm = { factor ->
                sharing = null
                scope.launch {
                    runCatching { session.api.shareMeal(ShareRequest(date, share.meal, share.member.id, factor, share.entryIds)) }
                        .onSuccess { notice = "Bij ${share.member.name} gezet: ${mealLabel(share.meal).lowercase()} (${(factor * 100).toInt()}%)."; error = null }
                        .onFailure { error = session.describe(it) }
                }
            },
        )
    }

    savingMeal?.let { meal ->
        SaveMealDialog(
            meal = meal,
            onDismiss = { savingMeal = null },
            onSave = { name ->
                savingMeal = null
                scope.launch {
                    runCatching { session.api.saveMeal(SaveMealRequest(name, date, meal)) }
                        .onSuccess { notice = "\"$name\" opgeslagen. Je vindt hem bij toevoegen onder Maaltijden."; error = null }
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
    household: List<MemberDto>,
    onShare: (MemberDto) -> Unit,
    onSaveMeal: () -> Unit,
) {
    val style = MEAL_STYLES.getValue(meal)
    var menu by remember { mutableStateOf(false) }
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
            if (entries.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Meer voor ${style.label}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Opslaan als maaltijd") }, onClick = { menu = false; onSaveMeal() })
                        for (member in household) {
                            DropdownMenuItem(text = { Text("Ook bij ${member.name} zetten") }, onClick = { menu = false; onShare(member) })
                        }
                    }
                }
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
                    Text(if (entry.quick) "✏️" else categoryEmoji(entry.category), modifier = Modifier.width(30.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (entry.quick) "Snel toegevoegd"
                            else listOfNotNull(entry.brand, "${fmt(entry.grams)} ${unitLabel(entry.unit)}").joinToString(" · "),
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
    household: List<MemberDto>,
    onDismiss: () -> Unit,
    onShare: (MemberDto) -> Unit,
    onSave: (UpdateEntryRequest) -> Unit,
    onDelete: () -> Unit,
) {
    // A food entry edits its amount; a quick entry its name and kcal.
    var grams by remember { mutableStateOf(if (entry.quick) fmt(entry.kcal) else fmt(entry.grams)) }
    var name by remember { mutableStateOf(entry.name) }
    var meal by remember { mutableStateOf(entry.meal) }
    val g = parseDecimal(grams)
    val valid = g != null && (if (entry.quick) g >= 0 && name.isNotBlank() else g > 0)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(if (entry.quick) "✏️" else categoryEmoji(entry.category), MEAL_STYLES.getValue(meal).color)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    entry.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (entry.quick) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Omschrijving") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = grams,
                onValueChange = { grams = it },
                label = { Text(if (entry.quick) "Calorieën (kcal)" else "Hoeveelheid (${unitLabel(entry.unit)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!entry.quick && g != null && g > 0 && entry.grams > 0) {
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
            for (member in household) {
                TextButton(onClick = { onShare(member) }) { Text("Ook bij ${member.name} zetten") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text("Verwijderen") }
                Button(
                    onClick = {
                        g?.let {
                            onSave(
                                if (entry.quick) UpdateEntryRequest(meal = meal, name = name.trim(), kcal = it)
                                else UpdateEntryRequest(grams = it, meal = meal),
                            )
                        }
                    },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                ) { Text("Opslaan") }
            }
        }
    }
}

/** Their portion as a share of mine: Cindy often eats less of the same pan. */
@Composable
private fun ShareDialog(share: Share, single: Boolean, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var factor by remember { mutableStateOf(1.0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ook bij ${share.member.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (single) "Dit product gaat naar het dagboek van ${share.member.name}, dezelfde dag en maaltijd. Hoeveel at ${share.member.name} ervan?"
                    else "De hele ${mealLabel(share.meal).lowercase()} gaat naar het dagboek van ${share.member.name}. Hoeveel at ${share.member.name} vergeleken met jou?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (f in SHARE_FACTORS) {
                        FilterChip(selected = factor == f, onClick = { factor = f }, label = { Text("${(f * 100).toInt()}%") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(factor) }) { Text("Zetten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun SaveMealDialog(meal: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("Mijn ${mealLabel(meal).lowercase()}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opslaan als maaltijd") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Alles van deze ${mealLabel(meal).lowercase()} wordt één maaltijd die je later met één tik toevoegt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Naam") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** Fiber (at least) and sugar, saturated fat and salt (at most) against the day's target. */
@Composable
private fun NutrientsCard(totals: TotalsDto, target: TargetDto) {
    if (target.fiberG == 0) return // an older server without these limits
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("🥦", NutrientGood)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Voedingsstoffen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vezels minstens, de rest liefst niet meer dan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        NutrientRow("Vezels", totals.fiber, target.fiberG.toDouble(), minimum = true)
        NutrientRow("Suikers", totals.sugar, target.sugarMaxG.toDouble(), minimum = false)
        NutrientRow("Verzadigd vet", totals.saturatedFat, target.saturatedFatMaxG.toDouble(), minimum = false)
        NutrientRow("Zout", totals.salt, target.saltMaxG.toDouble(), minimum = false)
    }
}

private val NutrientGood = Color(0xFF2BB673)
private val NutrientWarn = Color(0xFFF2545B)

@Composable
private fun NutrientRow(label: String, value: Double, goal: Double, minimum: Boolean) {
    val progress = if (goal <= 0) 0f else (value / goal).toFloat()
    // A minimum turns green when reached; a maximum turns red when passed.
    val color = when {
        minimum && progress >= 1f -> NutrientGood
        minimum -> CarbsColor
        progress > 1f -> NutrientWarn
        else -> NutrientGood
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${fmt(Math.round(value * 10) / 10.0)} / ${if (minimum) "≥" else "≤"} ${fmt(goal)} g",
                style = MaterialTheme.typography.labelMedium,
                color = if (!minimum && progress > 1f) NutrientWarn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
        }
    }
}
