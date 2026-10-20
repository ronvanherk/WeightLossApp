package com.quantumvikings.weightloss

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quantumvikings.weightloss.data.Reminders
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.ui.WeightLossApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen starts with a coloured header, so the status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val session = Session(applicationContext)
        // Alarms do not survive a force stop; re-arming is harmless when they did.
        Reminders.restoreAll(applicationContext)
        // A notification can ask for a screen (the weekly review); read once, not on recreation.
        val open = if (savedInstanceState == null) intent?.getStringExtra(Reminders.EXTRA_OPEN) else null
        setContent { WeightLossApp(session, open) }
    }
}
