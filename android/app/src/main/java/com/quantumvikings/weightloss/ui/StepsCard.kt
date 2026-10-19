package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

val StepsColor = Color(0xFF2BB673)

/**
 * Steps from Health Connect and what they add to the day's target.
 * [connected] null = still checking; false shows the button that asks for access.
 */
@Composable
fun StepsCard(
    connected: Boolean?,
    installable: Boolean,
    steps: Long?,
    stepsKcal: Int,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("👟", StepsColor)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Stappen", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        connected != true -> "Je stappen verhogen je dagdoel"
                        steps == null -> "Nog geen stappen vandaag"
                        stepsKcal > 0 -> "+$stepsKcal kcal in je dagdoel"
                        else -> "Telt mee boven je gewone dagelijkse stappen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connected == true && steps != null) {
                Text(NumberFormat.getIntegerInstance(Locale.GERMANY).format(steps), style = MaterialTheme.typography.titleLarge)
            }
        }
        if (connected == false) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Koppel Health Connect, dan leest de app je stappen (bijvoorbeeld uit Samsung Health). " +
                    "Wat je meer loopt dan bij je activiteitsniveau hoort, mag je erbij eten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = if (installable) onInstall else onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = StepsColor),
            ) { Text(if (installable) "Health Connect installeren" else "Health Connect koppelen") }
        }
    }
}
