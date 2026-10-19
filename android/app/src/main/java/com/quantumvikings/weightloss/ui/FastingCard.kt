package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.FastDto
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val FastingColor = Color(0xFF8E6CEF)
private val GOALS = listOf(12, 14, 16, 18, 20)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** "15 u 07 m" */
private fun hoursMinutes(d: Duration): String = "${d.toHours()} u ${"%02d".format(d.toMinutes() % 60)} m"

private fun local(instant: Instant) = instant.atZone(ZoneId.systemDefault())

/**
 * Intermittent fasting: pick a goal and start, or follow the running fast until the goal.
 * [onStart] and [onAdjustStart] take the start time; null means now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingCard(
    current: FastDto?,
    last: FastDto?,
    onStart: (goalHours: Int, startedAt: Instant?) -> Unit,
    onAdjustStart: (Instant) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(current?.id) {
        while (current != null) {
            now = Instant.now()
            delay(30_000)
        }
    }
    var goal by remember(last?.goalHours) { mutableIntStateOf(last?.goalHours ?: 16) }
    // Picking a start time: for a new fast, or to correct the running one.
    var picking by remember { mutableStateOf(false) }

    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("⏳", FastingColor)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Vasten", style = MaterialTheme.typography.titleMedium)
                val sub = when {
                    current != null -> "Sinds ${local(Instant.parse(current.startedAt)).format(TIME)} · doel ${current.goalHours} uur"
                    last?.endedAt != null -> "Vorige keer: ${hoursMinutes(Duration.between(Instant.parse(last.startedAt), Instant.parse(last.endedAt)))}"
                    else -> "Kies hoe lang, en start na je laatste maaltijd"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current != null) Pill("${current.goalHours}:${24 - current.goalHours.coerceAtMost(24)}", FastingColor)
        }
        Spacer(Modifier.height(12.dp))

        if (current != null) {
            val start = Instant.parse(current.startedAt)
            val elapsed = Duration.between(start, now).coerceAtLeast(Duration.ZERO)
            val goalDuration = Duration.ofHours(current.goalHours.toLong())
            val done = elapsed >= goalDuration
            Text(hoursMinutes(elapsed), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (elapsed.toMinutes().toFloat() / goalDuration.toMinutes()).coerceIn(0f, 1f) },
                color = FastingColor,
                trackColor = FastingColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            val end = local(start.plus(goalDuration))
            Text(
                if (done) "Doel gehaald 🎉 Je mag weer eten."
                else "Nog ${hoursMinutes(goalDuration.minus(elapsed))} · eten om ${end.format(TIME)}" +
                    if (end.toLocalDate() != LocalDate.now()) " (morgen)" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) FastingColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = FastingColor)) {
                    Text(if (done) "Afronden" else "Stoppen")
                }
                OutlinedButton(onClick = { picking = true }) { Text("Begintijd") }
                TextButton(onClick = onCancel) { Text("Wissen") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (h in GOALS) {
                    FilterChip(
                        selected = goal == h,
                        onClick = { goal = h },
                        label = { Text("$h uur") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FastingColor.copy(alpha = 0.2f)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStart(goal, null) }, colors = ButtonDefaults.buttonColors(containerColor = FastingColor)) {
                    Text("Start nu")
                }
                OutlinedButton(onClick = { picking = true }) { Text("Begon eerder…") }
            }
        }
    }

    if (picking) {
        val initial = current?.let { local(Instant.parse(it.startedAt)).toLocalTime() } ?: LocalTime.now()
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(if (current == null) "Wanneer at je voor het laatst?" else "Begintijd van het vasten") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    // A time later than now can only mean yesterday evening.
                    val zone = ZoneId.systemDefault()
                    var at = LocalDate.now().atTime(state.hour, state.minute).atZone(zone).toInstant()
                    if (at.isAfter(Instant.now())) at = at.minus(Duration.ofDays(1))
                    if (current == null) onStart(goal, at) else onAdjustStart(at)
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuleren") } },
        )
    }
}
