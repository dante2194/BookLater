@file:OptIn(ExperimentalMaterial3Api::class)

package com.booklater.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun App() {
    val vm: MainVm = viewModel()
    val theme by vm.theme.collectAsState()
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    BookTheme(dark) { Shell(vm) }
}

@Composable
private fun Shell(vm: MainVm) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val tabs = listOf(
        Tab("search", "Search", Icons.Filled.Search),
        Tab("saved", "Saved", Icons.Filled.Bookmark),
        Tab("settings", "Settings", Icons.Filled.Settings)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "search", modifier = Modifier.padding(pad)) {
            composable("search") {
                SearchScreen(vm, onOpen = { nav.navigate("book/$it") }, onVerify = { nav.navigate("verify") })
            }
            composable("saved") {
                SavedScreen(vm, onOpen = { nav.navigate("book/$it") })
            }
            composable("settings") {
                SettingsScreen(vm, onVerify = { nav.navigate("verify") })
            }
            composable(
                "book/{asin}",
                arguments = listOf(navArgument("asin") { type = NavType.StringType })
            ) { e ->
                DetailScreen(
                    vm,
                    asin = e.arguments?.getString("asin").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate("book/$it") },
                    onVerify = { nav.navigate("verify") }
                )
            }
            composable("verify") {
                VerifyScreen(vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
