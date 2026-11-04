package com.twv.viewers

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
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
import com.twv.viewers.ui.TwvTheme
import com.twv.viewers.ui.ViewersScreen
import com.twv.viewers.ui.ViewersViewModel

class MainActivity : ComponentActivity() {
    /** true, пока открыт экран канала: только с него можно уйти в картинку в картинке. */
    private val pipReady = mutableStateOf(false)
    private val inPip = mutableStateOf(false)

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)) // высокое окно под список
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(pipReady.value)
        return b.build()
    }

    private fun enterPip() {
        if (pipReady.value) enterPictureInPictureMode(pipParams())
    }

    // Android 12+ уходит в PiP сам (autoEnter); на 8–11 — по нажатию «Домой».
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPip()
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TwvApp).container
        setContent {
            TwvTheme {
              Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
              ) {
                val nav = rememberNavController()
                LaunchedEffect(pipReady.value) { setPictureInPictureParams(pipParams()) }
                NavHost(navController = nav, startDestination = "streams") {
                    composable("streams") {
                        val vm: StreamsViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                    StreamsViewModel(container.streams, container.settings) as T
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
                        DisposableEffect(Unit) {
                            pipReady.value = true
                            onDispose { pipReady.value = false }
                        }
                        ViewersScreen(
                            vm = vm, onBack = { nav.popBackStack() },
                            pip = inPip.value, onPip = ::enterPip
                        )
                    }
                }
              }
            }
        }
    }
}
