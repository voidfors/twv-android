package com.twv.viewers.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twv.viewers.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val chat by settings.chatEnabled.collectAsState(initial = true)
    val notify by settings.notifyFavorites.collectAsState(initial = false)
    val favorites by settings.favorites.collectAsState(initial = emptySet())
    val favChannels by settings.favoriteChannels.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { settings.setNotifyFavorites(granted) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Избранных зрителей: ${favorites.size}", Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (favorites.isNotEmpty()) {
                TextButton(onClick = { scope.launch { settings.clearFavorites() } }) { Text("Очистить") }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Избранных каналов: ${favChannels.size}", Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (favChannels.isNotEmpty()) {
                TextButton(onClick = { scope.launch { settings.clearFavoriteChannels() } }) { Text("Очистить") }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
