package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.ProfileDto
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.TargetDto
import kotlinx.coroutines.launch
import java.time.LocalDate

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(session: Session, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("MALE") }
    var birthDate by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("LIGHT") }
    var goal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("0.5") }
    var locale by remember { mutableStateOf("nl") }
    var target by remember { mutableStateOf<TargetDto?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { session.api.me() }.onSuccess { me ->
            name = me.user.name
            target = me.target
            me.profile?.let { p ->
                sex = p.sex
                birthDate = p.birthDate
                height = fmt(p.heightCm)
                activity = p.activity
                goal = fmt(p.goalWeightKg)
                rate = RATES.map { it.first }.firstOrNull { it.toDouble() == p.weeklyLossKg } ?: "0.5"
                locale = p.locale
            }
        }.onFailure { message = session.describe(it) }
    }

    val birth = runCatching { LocalDate.parse(birthDate) }.getOrNull()
    val heightCm = parseDecimal(height)
    val goalKg = parseDecimal(goal)
    val valid = birth != null && heightCm != null && heightCm in 100.0..250.0 && goalKg != null && goalKg in 30.0..300.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (name.isEmpty()) "Profiel" else "Profiel van $name") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Choice("Geslacht", SEXES, sex) { sex = it }
            OutlinedTextField(
                value = birthDate,
                onValueChange = { birthDate = it },
                label = { Text("Geboortedatum (JJJJ-MM-DD)") },
                singleLine = true,
                isError = birthDate.isNotEmpty() && birth == null,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Lengte (cm)", height) { height = it }
            Choice("Activiteit", ACTIVITIES, activity) { activity = it }
            NumberField("Streefgewicht (kg)", goal) { goal = it }
            Choice("Tempo", RATES, rate) { rate = it }
            Choice("Taal van productnamen", LOCALES, locale) { locale = it }
            Button(enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
                val profile = ProfileDto(sex, birthDate, heightCm!!, activity, goalKg!!, rate.toDouble(), locale)
                scope.launch {
                    runCatching {
                        session.api.saveProfile(profile)
                        session.api.me()
                    }.onSuccess {
                        target = it.target
                        message = if (it.target == null) "Opgeslagen. Voer nog je gewicht in voor een dagdoel." else "Opgeslagen."
                    }.onFailure { message = session.describe(it) }
                }
            }) { Text("Opslaan") }
            message?.let { Text(it) }
            target?.let {
                Text(
                    "Dagdoel ${it.kcal} kcal (verbruik ${it.tdee} kcal, rust ${it.bmr} kcal)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = { session.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Uitloggen") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotEmpty() && parseDecimal(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
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
