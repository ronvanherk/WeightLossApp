package com.quantumvikings.weightloss.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quantumvikings.weightloss.data.Session
import java.time.LocalDate

@Composable
fun WeightLossApp(session: Session) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val token by session.token.collectAsStateWithLifecycle()
        if (token == null) {
            LoginScreen(session)
            return@MaterialTheme
        }
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = "today") {
            composable("today") {
                TodayScreen(
                    session = session,
                    onAdd = { meal, date -> nav.navigate("add/$meal/$date") },
                    onWeight = { nav.navigate("weight") },
                    onProfile = { nav.navigate("profile") },
                )
            }
            composable("add/{meal}/{date}") { entry ->
                AddFoodScreen(
                    session = session,
                    meal = entry.arguments?.getString("meal") ?: "SNACK",
                    date = entry.arguments?.getString("date") ?: LocalDate.now().toString(),
                    onDone = { nav.popBackStack() },
                )
            }
            composable("weight") { WeightScreen(session, onBack = { nav.popBackStack() }) }
            composable("profile") { ProfileScreen(session, onBack = { nav.popBackStack() }) }
        }
    }
}

val MEALS = listOf("BREAKFAST" to "Ontbijt", "LUNCH" to "Lunch", "DINNER" to "Avondeten", "SNACK" to "Tussendoor")

fun mealLabel(meal: String) = MEALS.firstOrNull { it.first == meal }?.second ?: meal

/** "12.5" -> "12,5", "12.0" -> "12": Dutch decimal comma, no trailing zero. */
fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.GERMANY, "%.1f", v)

/** Accepts both "72,5" and "72.5". */
fun parseDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
