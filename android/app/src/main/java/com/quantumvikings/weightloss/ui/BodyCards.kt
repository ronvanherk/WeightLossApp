package com.quantumvikings.weightloss.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumvikings.weightloss.data.MeasurementDto
import com.quantumvikings.weightloss.data.PhotoDto
import com.quantumvikings.weightloss.data.Photos
import com.quantumvikings.weightloss.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NL = Locale.forLanguageTag("nl")

/** One kind of body measurement: label, unit and how to read it from a [MeasurementDto]. */
private data class BodyMeasure(val label: String, val unit: String, val get: (MeasurementDto) -> Double?)

private val MEASURES = listOf(
    BodyMeasure("Taille", "cm") { it.waistCm },
    BodyMeasure("Heupen", "cm") { it.hipCm },
    BodyMeasure("Borst", "cm") { it.chestCm },
    BodyMeasure("Bovenbeen", "cm") { it.thighCm },
    BodyMeasure("Bovenarm", "cm") { it.armCm },
    BodyMeasure("Nek", "cm") { it.neckCm },
    BodyMeasure("Vetpercentage", "%") { it.bodyFatPct },
)

/** Waist, hips and the rest over the last year: the latest values and a line for one of them. */
@Composable
fun MeasurementsCard(session: Session, heightCm: Double?) {
    var rows by remember { mutableStateOf<List<MeasurementDto>>(emptyList()) }
    var shown by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()

    LaunchedEffect(reload) {
        runCatching { session.api.measurements(LocalDate.now().minusYears(1).toString()) }
            .onSuccess { rows = it.measurements }
            .onFailure { error = session.describe(it) }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lichaamsmaten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Add, null)
                Text(" Maten")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val measured = MEASURES.withIndex().filter { (_, m) -> rows.any { m.get(it) != null } }
        if (measured.isEmpty()) {
            Text(
                "Meet je taille af en toe: die zegt vaak meer dan de weegschaal. Meet op dezelfde plek, ter hoogte van je navel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (measured.none { it.index == shown }) shown = measured.first().index
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for ((i, m) in measured) {
                    val last = rows.lastOrNull { m.get(it) != null }?.let(m.get)
                    FilterChip(
                        selected = i == shown,
                        onClick = { shown = i },
                        label = { Text("${m.label} ${last?.let { fmt(it) } ?: ""}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
            val m = MEASURES[shown]
            val points = rows.filter { m.get(it) != null }
            if (points.size >= 2) {
                TrendChart(points.map { it.date }, points.map { m.get(it)!! }, margin = 1.0)
                val change = m.get(points.last())!! - m.get(points.first())!!
                Text(
                    "${signed(change)} ${m.unit} sinds ${LocalDate.parse(points.first().date).format(DateTimeFormatter.ofPattern("d MMM", NL))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Na twee metingen zie je hier je lijn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            // Waist-to-height: under 0.5 is the usual healthy line, for men and women alike.
            val waist = rows.lastOrNull { it.waistCm != null }?.waistCm
            if (shown == 0 && waist != null && heightCm != null && heightCm > 0) {
                val ratio = waist / heightCm
                Text(
                    "Taille gedeeld door lengte: ${String.format(Locale.GERMANY, "%.2f", ratio)} " +
                        if (ratio < 0.5) "(onder 0,5: gezond)" else "(streven: onder 0,5)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editing) {
        MeasurementDialog(
            today = rows.lastOrNull { it.date == today },
            previous = rows.lastOrNull { it.date != today },
            onDismiss = { editing = false },
            onSave = { m ->
                scope.launch {
                    runCatching { session.api.saveMeasurement(m.copy(date = today)) }
                        .onSuccess { editing = false; reload++ }
                        .onFailure { error = session.describe(it); editing = false }
                }
            },
        )
    }
}

/** Today's measurements; fields left empty are not measured. The last values show as a hint. */
@Composable
private fun MeasurementDialog(today: MeasurementDto?, previous: MeasurementDto?, onDismiss: () -> Unit, onSave: (MeasurementDto) -> Unit) {
    val inputs = remember { MEASURES.map { m -> mutableStateOf(today?.let(m.get)?.let { fmt(it) } ?: "") } }
    val values = inputs.map { parseDecimal(it.value) }
    val ok = inputs.indices.all { i ->
        val v = values[i]
        inputs[i].value.isBlank() || (v != null && if (i == MEASURES.lastIndex) v in 3.0..70.0 else v in 10.0..250.0)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maten vandaag") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MEASURES.forEachIndexed { i, m ->
                    OutlinedTextField(
                        value = inputs[i].value,
                        onValueChange = { inputs[i].value = it },
                        label = { Text(m.label) },
                        placeholder = { previous?.let(m.get)?.let { Text("vorige keer ${fmt(it)}") } },
                        suffix = { Text(m.unit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(
                    MeasurementDto(
                        date = "",
                        waistCm = values[0],
                        hipCm = values[1],
                        chestCm = values[2],
                        thighCm = values[3],
                        armCm = values[4],
                        neckCm = values[5],
                        bodyFatPct = values[6],
                    ),
                )
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** Progress photos, newest first; they stay on our server and only their owner sees them. */
@Composable
fun PhotosCard(session: Session) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoDto>>(emptyList()) }
    var open by remember { mutableStateOf<PhotoDto?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        runCatching { session.api.photos() }.onSuccess { photos = it.photos }.onFailure { error = session.describe(it) }
    }
    // The system photo picker: the app sees only the photo chosen, no gallery permission needed.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        error = null
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.Default) { Photos.jpeg(context, uri) }
                session.api.uploadPhoto(
                    LocalDate.now().toString().toRequestBody("text/plain".toMediaType()),
                    MultipartBody.Part.createFormData("photo", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType())),
                )
            }.onSuccess { reload++ }.onFailure { error = session.describe(it) }
            uploading = false
        }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Foto's", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(enabled = !uploading, onClick = {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.Add, null)
                Text(" Foto")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (photos.isEmpty()) {
            Text(
                "Een foto per maand laat verschil zien dat de weegschaal mist. Alleen jij ziet ze; ze staan op onze eigen server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (p in photos) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoImage(session, p.id, 300, Modifier.size(96.dp, 128.dp).clip(RoundedCornerShape(12.dp)).clickable { open = p })
                        Text(
                            LocalDate.parse(p.date).format(DateTimeFormatter.ofPattern("d MMM", NL)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    open?.let { p ->
        var confirming by remember(p.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(LocalDate.parse(p.date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", NL))) },
            text = { PhotoImage(session, p.id, 1600, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)), ContentScale.Fit) },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Sluiten") } },
            dismissButton = {
                TextButton(onClick = {
                    if (!confirming) {
                        confirming = true
                        return@TextButton
                    }
                    scope.launch {
                        runCatching { session.api.deletePhoto(p.id) }
                            .onSuccess { open = null; reload++ }
                            .onFailure { error = session.describe(it); open = null }
                    }
                }) { Text(if (confirming) "Echt verwijderen" else "Verwijderen", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun PhotoImage(session: Session, id: Int, maxEdge: Int, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(id, maxEdge) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id, maxEdge) {
        bitmap = runCatching { withContext(Dispatchers.IO) { Photos.load(session, id, maxEdge) } }.getOrNull()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Image(b.asImageBitmap(), contentDescription = null, contentScale = scale, modifier = Modifier.matchParentSize())
    }
}
