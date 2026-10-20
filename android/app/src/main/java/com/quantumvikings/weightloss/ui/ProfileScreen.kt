package com.quantumvikings.weightloss.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.FastingAlarm
import com.quantumvikings.weightloss.data.ProfileDto
import com.quantumvikings.weightloss.data.Reminders
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.TargetDto
import com.quantumvikings.weightloss.ui.theme.CarbsColor
import com.quantumvikings.weightloss.ui.theme.FatColor
import com.quantumvikings.weightloss.ui.theme.HeaderGradient
import com.quantumvikings.weightloss.ui.theme.ProteinColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SEXES = listOf("MALE" to "Man", "FEMALE" to "Vrouw")
private val ACTIVITIES = listOf(
    "SEDENTARY" to "Zittend, weinig beweging",
    "LIGHT" to "Licht actief (1-3× sport per week)",
    "MODERATE" to "Matig actief (3-5×)",
    "ACTIVE" to "Actief (6-7×)",
    "VERY_ACTIVE" to "Zeer actief (zwaar werk + sport)",
)
private val RATES = listOf("0.0" to "Op gewicht blijven", "0.25" to "0,25 kg per week", "0.5" to "0,5 kg per week", "0.75" to "0,75 kg per week", "1.0" to "1 kg per week")
private val LOCALES = listOf("nl" to "Nederlands", "da" to "Dansk", "de" to "Deutsch")

/** Macro split presets as "protein/fat/carbs" percentages. */
private const val CUSTOM_SPLIT = "custom"
private val SPLITS = listOf(
    "25/30/45" to "Standaard · 25% eiwit, 30% vet, 45% koolh.",
    "35/30/35" to "Eiwitrijk · 35% eiwit, 30% vet, 35% koolh.",
    "30/45/25" to "Koolhydraatarm · 30% eiwit, 45% vet, 25% koolh.",
    "25/65/10" to "Keto · 25% eiwit, 65% vet, 10% koolh.",
    CUSTOM_SPLIT to "Eigen verdeling",
)
private val WEEKENDS = listOf(0, 100, 200, 300, 400, 500).map { extra ->
    extra.toString() to if (extra == 0) "Elke dag hetzelfde" else "+$extra kcal op za en zo (−${Math.round(extra * 2 / 5.0)} doordeweeks)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(session: Session) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("MALE") }
    var birthDate by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("LIGHT") }
    var goal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("0.5") }
    var locale by remember { mutableStateOf("nl") }
    var split by remember { mutableStateOf("25/30/45") }
    var proteinPct by remember { mutableStateOf("25") }
    var fatPct by remember { mutableStateOf("30") }
    var carbsPct by remember { mutableStateOf("45") }
    var fixedOn by remember { mutableStateOf(false) }
    var fixedKcal by remember { mutableStateOf("") }
    var weekend by remember { mutableStateOf("0") }
    var target by remember { mutableStateOf<TargetDto?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { session.api.me() }.onSuccess { me ->
            name = me.user.name
            email = me.user.email
            target = me.target
            me.profile?.let { p ->
                sex = p.sex
                birthDate = p.birthDate
                height = fmt(p.heightCm)
                activity = p.activity
                goal = fmt(p.goalWeightKg)
                rate = RATES.map { it.first }.firstOrNull { it.toDouble() == p.weeklyLossKg } ?: "0.5"
                locale = p.locale
                proteinPct = p.proteinPct.toString()
                fatPct = p.fatPct.toString()
                carbsPct = p.carbsPct.toString()
                val key = "${p.proteinPct}/${p.fatPct}/${p.carbsPct}"
                split = if (SPLITS.any { it.first == key }) key else CUSTOM_SPLIT
                fixedOn = p.fixedKcal != null
                fixedKcal = p.fixedKcal?.toString() ?: ""
                weekend = WEEKENDS.map { it.first }.firstOrNull { it.toInt() == p.weekendExtraKcal } ?: "0"
            }
        }.onFailure { message = session.describe(it) }
    }

    val birth = runCatching { LocalDate.parse(birthDate) }.getOrNull()
    val heightCm = parseDecimal(height)
    val goalKg = parseDecimal(goal)
    val pcts = listOf(proteinPct, fatPct, carbsPct).map { it.trim().toIntOrNull() }
    val splitValid = pcts.all { it != null } && pcts.sumOf { it ?: 0 } == 100 &&
        pcts[0]!! in 10..70 && pcts[1]!! in 10..70 && pcts[2]!! in 5..70
    val fixed = fixedKcal.trim().toIntOrNull()
    val fixedValid = !fixedOn || (fixed != null && fixed in 1000..5000)
    val valid = birth != null && heightCm != null && heightCm in 100.0..250.0 && goalKg != null && goalKg in 30.0..300.0 &&
        splitValid && fixedValid

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(HeaderGradient),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(24.dp),
            ) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.take(1).uppercase().ifEmpty { "🙂" }, style = MaterialTheme.typography.displaySmall, color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(email, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            target?.let { t ->
                SectionCard {
                    Text("Jouw dagdoel", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${t.kcal}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                        Text(" kcal per dag", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        listOfNotNull(
                            if (t.fixed) "Vast doel" else null,
                            "Verbruik ${t.tdee} kcal · in rust ${t.bmr} kcal",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (t.weekendKcal != 0) {
                        Text(
                            if (t.weekendKcal > 0) "Vandaag ${t.weekendKcal} kcal extra: het is weekend"
                            else "Vandaag ${-t.weekendKcal} kcal minder, voor het weekend",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Pill("Koolh. ${t.carbsG} g", CarbsColor)
                        Pill("Eiwit ${t.proteinG} g", ProteinColor)
                        Pill("Vet ${t.fatG} g", FatColor)
                    }
                    if (t.fiberG > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Vezels minstens ${t.fiberG} g · suikers hooguit ${t.sugarMaxG} g · verzadigd vet hooguit ${t.saturatedFatMaxG} g · zout hooguit ${t.saltMaxG} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (t.clamped) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Begrensd op het minimum; kies een lager tempo voor een gezonder plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            SectionCard {
                Text("Over jou", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SEXES.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = sex == value,
                            onClick = { sex = value },
                            shape = SegmentedButtonDefaults.itemShape(i, SEXES.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = birth?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl"))) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Geboortedatum") },
                    trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Filled.CalendarMonth, "Kiezen") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                NumberField("Lengte", height, "cm") { height = it }
                Spacer(Modifier.height(10.dp))
                Choice("Activiteit", ACTIVITIES, activity) { activity = it }
            }

            SectionCard {
                Text("Je doel", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                NumberField("Streefgewicht", goal, "kg") { goal = it }
                Spacer(Modifier.height(10.dp))
                Choice("Tempo", RATES, rate) { rate = it }
                Spacer(Modifier.height(10.dp))
                Choice("Taal van productnamen", LOCALES, locale) { locale = it }
            }

            SectionCard {
                Text("Calorieën en macro's", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !fixedOn, onClick = { fixedOn = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Berekend") }
                    SegmentedButton(selected = fixedOn, onClick = { fixedOn = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Vast doel") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (fixedOn) "Bijvoorbeeld het getal van je diëtist. Stappen en het weekend tellen nog mee."
                    else "Uit je gewicht, lengte, leeftijd, activiteit en tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fixedOn) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = fixedKcal,
                        onValueChange = { fixedKcal = it.filter(Char::isDigit).take(4) },
                        label = { Text("Dagdoel") },
                        suffix = { Text("kcal") },
                        singleLine = true,
                        isError = !fixedValid,
                        supportingText = { if (!fixedValid) Text("Tussen 1000 en 5000 kcal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Choice("Weekend", WEEKENDS, weekend) { weekend = it }
                Spacer(Modifier.height(10.dp))
                Choice("Verdeling", SPLITS, split) { choice ->
                    split = choice
                    if (choice != CUSTOM_SPLIT) {
                        val (pr, fa, ca) = choice.split("/")
                        proteinPct = pr; fatPct = fa; carbsPct = ca
                    }
                }
                if (split == CUSTOM_SPLIT) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PercentField("Eiwit", proteinPct, Modifier.weight(1f)) { proteinPct = it }
                        PercentField("Vet", fatPct, Modifier.weight(1f)) { fatPct = it }
                        PercentField("Koolh.", carbsPct, Modifier.weight(1f)) { carbsPct = it }
                    }
                    Text(
                        if (splitValid) "Samen 100% ✓" else "Samen ${pcts.sumOf { it ?: 0 }}%; dat moet 100% zijn (eiwit en vet 10-70%, koolh. 5-70%).",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (splitValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            RemindersCard()

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                val profile = ProfileDto(
                    sex, birthDate, heightCm!!, activity, goalKg!!, rate.toDouble(), locale,
                    proteinPct = pcts[0]!!, fatPct = pcts[1]!!, carbsPct = pcts[2]!!,
                    fixedKcal = if (fixedOn) fixed else null,
                    weekendExtraKcal = weekend.toInt(),
                )
                scope.launch {
                    runCatching {
                        session.api.saveProfile(profile)
                        session.api.me()
                    }.onSuccess {
                        target = it.target
                        message = if (it.target == null) "Opgeslagen. Voer nog je gewicht in (Voortgang) voor een dagdoel." else "Opgeslagen ✓"
                    }.onFailure { message = session.describe(it) }
                }
            }) { Text("Opslaan") }
            OutlinedButton(
                onClick = { session.signOut() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Uitloggen") }
        }
    }

    if (picking) {
        val initial = (birth ?: LocalDate.now().minusYears(40)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial, initialDisplayMode = DisplayMode.Input)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text("Kiezen") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuleren") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun NumberField(label: String, value: String, unit: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        isError = value.isNotEmpty() && parseDecimal(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PercentField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        suffix = { Text("%") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, text) in options) {
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

private val REMINDER_HINTS = mapOf(
    Reminders.Kind.BREAKFAST to "als je ontbijt nog leeg is",
    Reminders.Kind.LUNCH to "als je lunch nog leeg is",
    Reminders.Kind.DINNER to "als je avondeten nog leeg is",
    Reminders.Kind.WEIGHT to "als je vandaag nog niet gewogen bent",
    Reminders.Kind.WATER to "als je achterloopt met water",
    Reminders.Kind.WEEK to "op zondag: hoe ging je week",
)

/** Each reminder: on/off and a time. Stored on the phone; the alarms are local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersCard() {
    val context = LocalContext.current
    // Bumped after every change, so the rows re-read the stored settings.
    var version by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf<Reminders.Kind?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SectionCard {
        Text("Herinneringen", style = MaterialTheme.typography.titleMedium)
        Text(
            "Alleen als er echt iets te doen is.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        for (kind in Reminders.Kind.entries) {
            key(version) {
                val on = Reminders.enabled(context, kind)
                val minute = Reminders.minute(context, kind)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            REMINDER_HINTS.getValue(kind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { picking = kind }) { Text("%02d:%02d".format(minute / 60, minute % 60)) }
                    Switch(checked = on, onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= 33 && !FastingAlarm.canNotify(context)) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        Reminders.set(context, kind, checked)
                        version++
                    })
                }
            }
        }
    }

    picking?.let { kind ->
        val minute = Reminders.minute(context, kind)
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("${kind.label}: hoe laat?") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    // Choosing a time switches the reminder on: that is why you chose it.
                    if (Build.VERSION.SDK_INT >= 33 && !FastingAlarm.canNotify(context)) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Reminders.set(context, kind, true, state.hour * 60 + state.minute)
                    picking = null
                    version++
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Annuleren") } },
        )
    }
}
