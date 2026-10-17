package com.quantumvikings.weightloss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.ui.WeightLossApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = Session(applicationContext)
        setContent { WeightLossApp(session) }
    }
}
