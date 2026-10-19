package com.quantumvikings.weightloss.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** White rounded card with a soft shadow; the building block of every screen. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/**
 * The big ring of the day: how much of the target is eaten. Turns orange once over.
 * [ringColor] is the filled part, [trackColor] the rest.
 */
@Composable
fun CalorieRing(
    eaten: Int,
    target: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    ringColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    textColor: Color = Color.White,
) {
    val progress = if (target == null || target <= 0) 0f else eaten.toFloat() / target
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val over = target != null && eaten > target
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(trackColor, 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                if (over) Color(0xFFFFD166) else ringColor,
                135f,
                270f * animated,
                false,
                topLeft,
                arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (target == null) {
                Text("$eaten", color = textColor, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text("kcal gegeten", color = textColor.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            } else {
                val left = target - eaten
                Text(
                    "${kotlin.math.abs(left)}",
                    color = textColor,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (left >= 0) "kcal over" else "kcal te veel",
                    color = textColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** A macro with its own colour: label, thin bar, "eaten / target g". */
@Composable
fun MacroBar(
    label: String,
    grams: Double,
    target: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val progress = if (target == null || target <= 0) 0f else (grams / target).toFloat()
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = label)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            if (target == null) "${grams.toInt()} g" else "${grams.toInt()} / $target g",
            color = textColor.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Round coloured badge with an emoji, for meals and food groups. */
@Composable
fun EmojiBadge(emoji: String, color: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.48f).sp, textAlign = TextAlign.Center)
    }
}

/** Small rounded label, e.g. "245 kcal". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Three coloured dots with grams: the macro line under a food. */
@Composable
fun MacroLine(protein: Double?, carbs: Double?, fat: Double?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        MacroDot(com.quantumvikings.weightloss.ui.theme.CarbsColor, "K", carbs)
        MacroDot(com.quantumvikings.weightloss.ui.theme.ProteinColor, "E", protein)
        MacroDot(com.quantumvikings.weightloss.ui.theme.FatColor, "V", fat)
    }
}

@Composable
private fun MacroDot(color: Color, letter: String, grams: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            " $letter ${grams?.let { fmt(Math.round(it * 10) / 10.0) } ?: "–"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An emoji for a NEVO food group; branded products without a group get a shopping bag. */
fun categoryEmoji(category: String?): String {
    val c = category?.lowercase() ?: return "🛒"
    return when {
        c == "recept" -> "🍲"
        "aardappel" in c -> "🥔"
        "groente" in c -> "🥦"
        "fruit" in c -> "🍓"
        "vleeswaren" in c -> "🥓"
        "vlees" in c && "vervanger" !in c -> "🍗"
        "vis" in c -> "🐟"
        "gebak" in c || "koek" in c -> "🍰"
        "brood" in c -> "🍞"
        "graan" in c || "meel" in c -> "🌾"
        "kaas" in c -> "🧀"
        "melk" in c -> "🥛"
        "suiker" in c || "snoep" in c -> "🍬"
        "alcohol" in c && "niet-" !in c && "non-" !in c -> "🍷"
        "dranken" in c -> "🧃"
        "samengestelde" in c -> "🍲"
        "sauzen" in c -> "🥫"
        "snacks" in c -> "🥨"
        "vetten" in c || "olie" in c -> "🫒"
        "vervanger" in c -> "🌱"
        "flesvoeding" in c -> "🍼"
        "kruiden" in c -> "🌿"
        "peulvrucht" in c -> "🫘"
        "noten" in c -> "🥜"
        "soep" in c -> "🍜"
        "broodbeleg" in c -> "🥪"
        "eieren" in c -> "🥚"
        // Food groups of the Danish (Frida) and German (BLS) tables are in English.
        "plant-based" in c -> "🌱"
        "potato" in c -> "🥔"
        "vegetable" in c && "fat" !in c && "plant-based" !in c -> "🥦"
        "fruit" in c || "berr" in c -> "🍓"
        "fish" in c || "shellfish" in c || "mollusk" in c -> "🐟"
        "sausage" in c || "cold cut" in c -> "🥓"
        "meat" in c || "pork" in c || "beef" in c || "veal" in c || "lamb" in c || "chicken" in c ||
            "turkey" in c || "duck" in c || "game" in c || "offal" in c -> "🍗"
        "bread" in c -> "🍞"
        "cake" in c || "biscuit" in c || "dessert" in c -> "🍰"
        "pasta" in c || "cereal" in c || "grain" in c || "flour" in c || "breakfast" in c -> "🌾"
        "cheese" in c -> "🧀"
        "ice cream" in c || "ice lolly" in c || "sorbet" in c -> "🍨"
        "milk" in c || "dairy" in c || "whey" in c -> "🥛"
        "sugar" in c || "candy" in c || "chocolate" in c || "sweet" in c || "honey" in c || "marmelade" in c -> "🍬"
        "wine" in c || "beer" in c || "spirit" in c || "liqueur" in c || "cider" in c -> "🍷"
        "egg" in c -> "🥚"
        "legume" in c || "pulse" in c -> "🫘"
        "nut" in c || "seed" in c -> "🥜"
        "fat" in c || "oil" in c || "butter" in c || "margarine" in c -> "🫒"
        "soup" in c || "dish" in c || "meal" in c || "pizza" in c || "sandwich" in c -> "🍲"
        "sauce" in c || "dressing" in c || "mayonnaise" in c || "condiment" in c -> "🥫"
        "spice" in c || "herb" in c || "salt" in c -> "🌿"
        "snack" in c || "chip" in c -> "🥨"
        "drink" in c || "juice" in c || "coffee" in c || "water" in c || "beverage" in c -> "🧃"
        "infant" in c -> "🍼"
        else -> "🍽️"
    }
}

/** "g" or "ml" after an amount. */
fun unitLabel(unit: String) = if (unit == "ml") "ml" else "g"
