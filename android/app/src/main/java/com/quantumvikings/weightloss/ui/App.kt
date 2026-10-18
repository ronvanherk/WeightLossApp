package com.quantumvikings.weightloss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quantumvikings.weightloss.data.Session
import com.quantumvikings.weightloss.ui.theme.MEAL_STYLES
import com.quantumvikings.weightloss.ui.theme.WeightLossTheme
import java.time.LocalDate

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("diary", "Dagboek", Icons.Filled.Restaurant),
    Tab("progress", "Voortgang", Icons.Filled.ShowChart),
    Tab("profile", "Profiel", Icons.Filled.Person),
)

@Composable
fun WeightLossApp(session: Session) {
    WeightLossTheme {
        val token by session.token.collectAsStateWithLifecycle()
        if (token == null) {
            LoginScreen(session)
            return@WeightLossTheme
        }
        val nav = rememberNavController()
        // The diary's day survives a trip to the add screen and back.
        var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        val current by nav.currentBackStackEntryAsState()
        val route = current?.destination?.route

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Each screen handles the status bar itself; the bottom bar handles the navigation bar.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(visible = TABS.any { it.route == route }, enter = fadeIn(), exit = fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        for (tab in TABS) {
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            },
        ) { outer ->
            NavHost(
                navController = nav,
                startDestination = "diary",
                modifier = Modifier.padding(bottom = outer.calculateBottomPadding()),
            ) {
                composable("diary") {
                    DiaryScreen(
                        session = session,
                        date = day,
                        onDateChange = { day = it },
                        onAdd = { meal -> nav.navigate("add/$meal/$day") },
                        onProfile = { nav.navigate("profile") },
                    )
                }
                composable("progress") { ProgressScreen(session) }
                composable("profile") { ProfileScreen(session) }
                composable("add/{meal}/{date}") { entry ->
                    val meal = entry.arguments?.getString("meal") ?: "SNACK"
                    val date = entry.arguments?.getString("date") ?: day
                    AddFoodScreen(
                        session = session,
                        meal = meal,
                        date = date,
                        onOpenFood = { id, grams -> nav.navigate("food/$id?meal=$meal&date=$date&grams=${grams ?: ""}") },
                        onCreate = { nav.navigate("create/$meal/$date") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "food/{id}?meal={meal}&date={date}&grams={grams}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.IntType },
                        navArgument("meal") { nullable = true; defaultValue = null },
                        navArgument("date") { nullable = true; defaultValue = null },
                        navArgument("grams") { nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    FoodDetailScreen(
                        session = session,
                        foodId = args?.getInt("id") ?: 0,
                        meal = args?.getString("meal")?.takeIf { it in MEAL_STYLES } ?: "SNACK",
                        date = args?.getString("date")?.takeIf { it.isNotEmpty() } ?: day,
                        initialGrams = args?.getString("grams")?.let(::parseDecimal),
                        onOpenFood = { id -> nav.navigate("food/$id?meal=${args?.getString("meal") ?: ""}&date=${args?.getString("date") ?: ""}&grams=") },
                        onAdded = {
                            // Back to the diary, past the add screen.
                            nav.popBackStack("diary", inclusive = false)
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("create/{meal}/{date}") { entry ->
                    val meal = entry.arguments?.getString("meal") ?: "SNACK"
                    val date = entry.arguments?.getString("date") ?: day
                    CreateFoodScreen(
                        session = session,
                        onCreated = { id -> nav.navigate("food/$id?meal=$meal&date=$date&grams=") { popUpTo("add/{meal}/{date}") } },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

val MEALS = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK").map { it to MEAL_STYLES.getValue(it).label }

fun mealLabel(meal: String) = MEAL_STYLES[meal]?.label ?: meal

/** "12.5" -> "12,5", "12.0" -> "12": Dutch decimal comma, no trailing zero. */
fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.GERMANY, "%.1f", v)

/** Accepts both "72,5" and "72.5". */
fun parseDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
