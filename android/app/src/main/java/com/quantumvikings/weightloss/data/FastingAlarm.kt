package com.quantumvikings.weightloss.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quantumvikings.weightloss.MainActivity
import com.quantumvikings.weightloss.R
import java.time.Instant

/**
 * A local alarm at the end of the running fast. The fast lives on the server; the app re-arms
 * the alarm every time it loads it, and [BootReceiver] after a restart. Inexact on purpose:
 * exact alarms need a permission Play reserves for alarm clocks, and a few minutes late is fine.
 */
object FastingAlarm {
    private const val PREFS = "fasting_alarm"
    private const val KEY_END = "end"
    private const val KEY_GOAL = "goal"
    const val CHANNEL = "fasting"

    fun schedule(context: Context, end: Instant, goalHours: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Already notified for this end time, or it has passed: nothing to arm.
        if (!end.isAfter(Instant.now())) return
        prefs.edit().putLong(KEY_END, end.toEpochMilli()).putInt(KEY_GOAL, goalHours).apply()
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end.toEpochMilli(), pending(context))
    }

    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val end = prefs.getLong(KEY_END, 0)
        if (end > 0) schedule(context, Instant.ofEpochMilli(end), prefs.getInt(KEY_GOAL, 16))
    }

    /** The goal of the alarm that just went off; forgets it, so a restart does not repeat it. */
    internal fun consume(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_GOAL, 16).also { prefs.edit().clear().apply() }
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, FastingDoneReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class FastingDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val goal = FastingAlarm.consume(context)
        if (!FastingAlarm.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(FastingAlarm.CHANNEL, "Vasten", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Melding als je vastendoel gehaald is" },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, FastingAlarm.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fasting)
            .setContentTitle("Vastendoel gehaald 🎉")
            .setContentText("$goal uur gevast. Je mag weer eten; stop het vasten in de app.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) FastingAlarm.restore(context)
    }
}
