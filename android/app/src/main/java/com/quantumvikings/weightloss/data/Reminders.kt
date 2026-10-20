package com.quantumvikings.weightloss.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quantumvikings.weightloss.MainActivity
import com.quantumvikings.weightloss.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Daily reminders, each at its own time and only when there is something to remind of: a meal
 * still empty, no weight today, behind on water. The receiver asks the server before it shows
 * anything, so a reminder never nags about what is already logged. Inexact, like the fasting alarm.
 * The weekly review is the exception: it comes every Sunday once anything was logged that week.
 */
object Reminders {
    enum class Kind(val label: String, val defaultMinute: Int) {
        BREAKFAST("Ontbijt", 10 * 60),
        LUNCH("Lunch", 14 * 60),
        DINNER("Avondeten", 20 * 60 + 30),
        WEIGHT("Wegen", 8 * 60),
        WATER("Water", 15 * 60),
        // Weekly, on Sunday; the others are daily.
        WEEK("Weekoverzicht", 19 * 60),
    }

    /** Extra on the launch intent: which screen to open. */
    const val EXTRA_OPEN = "open"

    private const val PREFS = "reminders"
    private const val EXTRA_KIND = "kind"
    const val CHANNEL = "reminders"

    /** Water target and the hours over which it is spread, for "behind on water". */
    private const val WATER_GOAL_ML = 2000
    private const val DAY_START = 8
    private const val DAY_END = 22

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context, kind: Kind) = prefs(context).getBoolean("${kind.name}_on", false)

    /** Minutes after midnight. */
    fun minute(context: Context, kind: Kind) = prefs(context).getInt("${kind.name}_at", kind.defaultMinute)

    fun set(context: Context, kind: Kind, on: Boolean, minute: Int = minute(context, kind)) {
        prefs(context).edit().putBoolean("${kind.name}_on", on).putInt("${kind.name}_at", minute).apply()
        if (on) schedule(context, kind) else cancel(context, kind)
    }

    /** Re-arms every enabled reminder: on app start and after a reboot. */
    fun restoreAll(context: Context) {
        for (kind in Kind.entries) if (enabled(context, kind)) schedule(context, kind)
    }

    private fun schedule(context: Context, kind: Kind) {
        val m = minute(context, kind)
        val now = LocalDateTime.now()
        var at = LocalDate.now().atTime(LocalTime.of(m / 60, m % 60))
        if (kind == Kind.WEEK) {
            at = at.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            if (!at.isAfter(now)) at = at.plusWeeks(1)
        } else if (!at.isAfter(now)) {
            at = at.plusDays(1)
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context, kind))
    }

    private fun cancel(context: Context, kind: Kind) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, kind))
    }

    private fun pending(context: Context, kind: Kind) = PendingIntent.getBroadcast(
        context,
        100 + kind.ordinal,
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The reminder text, or null when there is nothing to remind of (or the server cannot say). */
    internal suspend fun message(context: Context, kind: Kind): String? {
        val session = Session(context)
        if (session.token.value == null) return null
        val today = LocalDate.now().toString()
        return runCatching {
            when (kind) {
                Kind.BREAKFAST, Kind.LUNCH, Kind.DINNER -> {
                    val diary = session.api.diary(today, today)
                    if (diary.entries.any { it.meal == kind.name }) null
                    else "Nog niets bij ${kind.label.lowercase()} vandaag. Even bijhouden?"
                }
                Kind.WEIGHT -> {
                    val weights = session.api.weights(from = today).weights
                    if (weights.any { it.date == today }) null else "Goedemorgen! Stap even op de weegschaal."
                }
                Kind.WEEK -> {
                    val w = session.api.week(today, today)
                    if (w.daysLogged == 0) null else weekMessage(w)
                }
                Kind.WATER -> {
                    val ml = session.api.diary(today, today).waterMl
                    val hour = LocalTime.now().hour.coerceIn(DAY_START, DAY_END)
                    val expected = WATER_GOAL_ML * (hour - DAY_START) / (DAY_END - DAY_START)
                    if (ml >= expected) null
                    else "Je hebt vandaag ${liters(ml)} liter gedronken. Tijd voor een glas water 💧"
                }
            }
        }.getOrNull()
    }

    /** "5 van 7 dagen binnen je doel · gemiddeld 1850 kcal · 0,4 kg lichter" */
    private fun weekMessage(w: WeekResponse): String = listOfNotNull(
        "${w.daysOnTarget} van ${w.daysCounted} dagen binnen je doel",
        w.average.kcal?.let { "gemiddeld $it kcal" },
        w.weight.change?.let { c ->
            val kg = String.format(Locale.GERMANY, "%.1f", kotlin.math.abs(c)).removeSuffix(",0")
            when {
                c < 0 -> "$kg kg lichter"
                c > 0 -> "$kg kg zwaarder"
                else -> "gewicht gelijk"
            }
        },
    ).joinToString(" · ")

    /** 1250 -> "1,25", 1000 -> "1". */
    private fun liters(ml: Int) = String.format(Locale.GERMANY, "%.2f", ml / 1000.0).trimEnd('0').trimEnd(',')

    internal fun notify(context: Context, kind: Kind, text: String) {
        if (!FastingAlarm.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Herinneringen", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Maaltijden, wegen en water" },
        )
        val open = PendingIntent.getActivity(
            context,
            100 + kind.ordinal,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { if (kind == Kind.WEEK) putExtra(EXTRA_OPEN, "week") },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(kind.label)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(100 + kind.ordinal, notification) }
    }

    internal fun kindOf(intent: Intent) = intent.getStringExtra(EXTRA_KIND)?.let { name -> Kind.entries.firstOrNull { it.name == name } }

    internal fun rearm(context: Context, kind: Kind) {
        if (enabled(context, kind)) schedule(context, kind)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = Reminders.kindOf(intent) ?: return
        val app = context.applicationContext
        // Tomorrow's alarm first, so a failed check never ends the series.
        Reminders.rearm(app, kind)
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.message(app, kind)?.let { Reminders.notify(app, kind, it) }
            } finally {
                done.finish()
            }
        }
    }
}
