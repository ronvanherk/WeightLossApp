package com.quantumvikings.weightloss.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.quantumvikings.weightloss.MainActivity
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.data.WaterDto
import java.time.LocalDate
import java.util.Locale

private val GREEN = Color(0xFF22B573)
private val WATER = Color(0xFF3BA7F5)
private val OVER = Color(0xFFF2545B)
private val TEXT = Color(0xFF1C1C1E)
private val MUTED = Color(0xFF6B6B70)
private const val GLASS_ML = 250

/** What the widget shows; null fields when not logged in or the server cannot be reached. */
private data class Today(val eaten: Int, val target: Int?, val waterMl: Int)

/**
 * Home screen widget: kcal left today with a bar, and water with a "+ glas" button.
 * It fetches the day itself; the app calls [DayWidget.refresh] after every change.
 */
class DayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val session = Session(context.applicationContext)
        val loggedIn = session.token.value != null
        val today = if (!loggedIn) null else runCatching {
            val d = LocalDate.now().toString()
            val diary = session.api.diary(d, d)
            Today(diary.totals.kcal.toInt(), diary.target?.kcal, diary.waterMl)
        }.getOrNull()
        provideContent { Content(loggedIn, today) }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { DayWidget().updateAll(context.applicationContext) }
        }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}

/** "+ glas": adds 250 ml to today's water, then redraws. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val session = Session(context.applicationContext)
        val d = LocalDate.now().toString()
        runCatching {
            val ml = session.api.diary(d, d).waterMl
            session.api.saveWater(WaterDto(d, ml + GLASS_ML))
        }
        DayWidget().update(context, glanceId)
    }
}

@Composable
private fun Content(loggedIn: Boolean, today: Today?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color.White)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when {
            !loggedIn -> Message("Log in in de app")
            today == null -> Message("Geen verbinding")
            else -> Day(today)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text("🥗 WeightLoss", style = TextStyle(color = ColorProvider(GREEN), fontWeight = FontWeight.Bold, fontSize = 14.sp))
    Spacer(GlanceModifier.height(6.dp))
    Text(text, style = TextStyle(color = ColorProvider(MUTED), fontSize = 13.sp))
}

@Composable
private fun Day(t: Today) {
    val left = t.target?.let { it - t.eaten }
    Text(
        when {
            left == null -> "${t.eaten} kcal"
            left >= 0 -> "Nog $left kcal"
            else -> "${-left} kcal te veel"
        },
        style = TextStyle(
            color = ColorProvider(if (left != null && left < 0) OVER else TEXT),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        ),
    )
    Text(
        if (t.target != null) "${t.eaten} van ${t.target} kcal gegeten" else "gegeten vandaag",
        style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp),
    )
    if (t.target != null && t.target > 0) {
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (t.eaten.toFloat() / t.target).coerceIn(0f, 1f),
            color = ColorProvider(if (t.eaten > t.target) OVER else GREEN),
            backgroundColor = ColorProvider(Color(0xFFE8F5EE)),
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
        )
    }
    Spacer(GlanceModifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            "💧 ${String.format(Locale.GERMANY, "%.2f", t.waterMl / 1000.0).trimEnd('0').trimEnd(',')} l",
            style = TextStyle(color = ColorProvider(TEXT), fontSize = 15.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Button(
            text = "+ glas",
            onClick = actionRunCallback<AddWaterAction>(),
            colors = ButtonDefaults.buttonColors(backgroundColor = ColorProvider(WATER), contentColor = ColorProvider(Color.White)),
        )
    }
}
