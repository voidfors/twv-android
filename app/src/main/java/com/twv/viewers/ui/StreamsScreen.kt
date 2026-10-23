package com.twv.viewers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.twv.viewers.StreamItem

@Composable
fun StreamsScreen(vm: StreamsViewModel, onOpen: (String) -> Unit, onManual: () -> Unit, onSettings: () -> Unit) {
    val ui by vm.ui.collectAsState()

    // Вернулись на экран (например, с канала) — перепроверить, кто из избранных в эфире.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshFavorites() }

    val searching = ui.query.isNotBlank()
    val liveFavs = if (searching) emptyList() else ui.liveFavorites.filter { it.login in ui.favorites }
    val favLogins = liveFavs.map { it.login }.toSet()
    val rest = if (searching) ui.items else ui.items.filter { it.login !in favLogins }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Стримы", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = vm::reload) { Icon(Icons.Default.Refresh, contentDescription = "Обновить") }
            IconButton(onClick = onManual) { Icon(Icons.Default.Add, contentDescription = "Открыть по логину") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Настройки") }
        }
        SearchField(
            value = ui.query, onValueChange = vm::setQuery, placeholder = "Поиск канала по логину",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ui.error?.let { msg ->
                item(key = "error") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(msg, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::reload) { Text("Повторить") }
                            TextButton(onClick = onManual) { Text("Ввести логин") }
                        }
                    }
                }
            }
            if (liveFavs.isNotEmpty()) {
                item(key = "h-fav") { SectionTitle("Избранные · в эфире", star = true) }
                items(liveFavs, key = { "fav:" + it.login }) { s ->
                    StreamRow(s, favorite = true, onOpen = onOpen, onFavorite = { vm.toggleFavorite(s.login) })
                }
            }
            if (rest.isNotEmpty()) {
                item(key = "h-all") { SectionTitle(if (searching) "Результаты поиска" else "Топ стримов") }
            }
            items(rest, key = { "s:" + it.login }) { s ->
                StreamRow(
                    s, favorite = s.login in ui.favorites,
                    onOpen = onOpen, onFavorite = { vm.toggleFavorite(s.login) }
                )
            }
            if (ui.nextCursor != null) {
                item(key = "more") {
                    TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Показать ещё") }
                }
            }
            if (ui.empty && ui.error == null && !ui.loading) {
                item(key = "empty") {
                    Text(
                        "Ничего не найдено", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, star: Boolean = false) {
    Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (star) {
            Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(16.dp))
            Box(Modifier.size(6.dp))
        }
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StreamRow(s: StreamItem, favorite: Boolean, onOpen: (String) -> Unit, onFavorite: () -> Unit) {
    val live = s.viewers > 0 || s.title.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onOpen(s.login) }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(s.avatarUrl, s.displayName, 52.dp)
            if (live) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp)
                        .clip(CircleShape).background(LiveRed)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                s.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (live) {
                s.game?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (s.title.isNotEmpty()) {
                    Text(
                        s.title, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    "не в эфире", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (live) {
            Text(
                formatCount(s.viewers), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                Icons.Default.Star,
                contentDescription = if (favorite) "Убрать из избранного" else "В избранное",
                tint = if (favorite) StarGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
