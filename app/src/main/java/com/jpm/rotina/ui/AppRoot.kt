package com.jpm.rotina.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jpm.rotina.AppViewModel
import com.jpm.rotina.R

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val tabs = listOf(
        Triple("home", Icons.Filled.Home, R.string.home_title),
        Triple("calendar", Icons.Filled.CalendarMonth, R.string.calendar_title),
        Triple("stats", Icons.Filled.BarChart, R.string.stats_title),
        Triple("settings", Icons.Filled.Settings, R.string.settings_title)
    )

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.first }) {
                NavigationBar {
                    tabs.forEach { (r, icon, label) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    vm,
                    onEdit = { id -> nav.navigate("edit/$id") },
                    onEditReminder = { id, date -> nav.navigate("reminder/$id/$date") }
                )
            }
            composable("calendar") {
                CalendarScreen(
                    vm,
                    onEditReminder = { id, date -> nav.navigate("reminder/$id/$date") }
                )
            }
            composable("stats") { StatsScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
            composable("edit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                EditHabitScreen(vm, habitId = id, onBack = { nav.popBackStack() })
            }
            composable("reminder/{id}/{date}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                val date = entry.arguments?.getString("date")?.toLongOrNull()
                    ?: java.time.LocalDate.now().toEpochDay()
                EditReminderScreen(
                    vm,
                    reminderId = id,
                    initialDate = date,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
