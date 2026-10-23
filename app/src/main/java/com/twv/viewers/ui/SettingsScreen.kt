package com.twv.viewers.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twv.viewers.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val chat by settings.chatEnabled.collectAsState(initial = true)
    val notify by settings.notifyFavorites.collectAsState(initial = false)
    val favorites by settings.favorites.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { settings.setNotifyFavorites(granted) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
        }
        SettingRow(
            title = "Читать чат",
            hint = "Добавляет в список авторов сообщений, которых нет в списке Twitch",
            checked = chat,
            onChange = { scope.launch { settings.setChatEnabled(it) } }
        )
        SettingRow(
            title = "Уведомлять об избранных",
            hint = "Когда избранный зритель заходит в канал. Работает, пока приложение открыто на канале",
            checked = notify,
            onChange = { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { settings.setNotifyFavorites(on) }
                }
            }
        )
        Text("Избранных зрителей: ${favorites.size}", style = MaterialTheme.typography.bodyMedium)
        if (favorites.isNotEmpty()) {
            TextButton(onClick = { scope.launch { settings.clearFavorites() } }) { Text("Очистить избранных") }
        }
    }
}

@Composable
private fun SettingRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
