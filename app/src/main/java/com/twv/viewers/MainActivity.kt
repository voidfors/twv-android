package com.twv.viewers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.twv.viewers.ui.HomeScreen
import com.twv.viewers.ui.SettingsScreen
import com.twv.viewers.ui.StreamsScreen
import com.twv.viewers.ui.StreamsViewModel
import com.twv.viewers.ui.ViewersScreen
import com.twv.viewers.ui.ViewersViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TwvApp).container
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "streams") {
                    composable("streams") {
                        val vm: StreamsViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    StreamsViewModel(container.streams) as T
                            }
                        )
                        StreamsScreen(
                            vm = vm,
                            onOpen = { nav.navigate("viewers/$it") },
                            onManual = { nav.navigate("home") },
                            onSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(settings = container.settings, onBack = { nav.popBackStack() })
                    }
                    composable("home") {
                        val recent by container.settings.recentChannels.collectAsState(initial = emptyList())
                        HomeScreen(recent = recent, onOpen = { nav.navigate("viewers/$it") })
                    }
                    composable("viewers/{login}") { entry ->
                        val login = entry.arguments?.getString("login").orEmpty()
                        val vm: ViewersViewModel = viewModel(
                            key = "viewers-$login",
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    ViewersViewModel(container, login) as T
                            }
                        )
                        ViewersScreen(vm = vm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
