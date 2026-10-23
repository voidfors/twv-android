package com.twv.viewers.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twv.viewers.StreamItem

@Composable
fun StreamsScreen(vm: StreamsViewModel, onOpen: (String) -> Unit, onManual: () -> Unit, onSettings: () -> Unit) {
    val ui by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.query, onValueChange = vm::setQuery,
                label = { Text("Поиск канала по логину") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onManual) { Text("Логин") }
            TextButton(onClick = onSettings) { Text("⚙") }
        }
        ui.error?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::reload) { Text("Повторить") }
                        TextButton(onClick = onManual) { Text("Ввести логин") }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.items, key = { it.login }) { s -> StreamRow(s, onOpen) }
                if (ui.nextCursor != null) {
                    item {
                        TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Ещё") }
                    }
                }
                if (ui.empty && ui.error == null && !ui.loading) {
                    item { Text("Ничего не найдено", Modifier.padding(16.dp)) }
                }
            }
            if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun StreamRow(s: StreamItem, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(s.login) },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (s.previewUrl != null) {
            AsyncImage(
                model = s.previewUrl, contentDescription = null,
                modifier = Modifier.width(112.dp).height(63.dp).clip(MaterialTheme.shapes.small)
            )
        } else {
            Box(
                Modifier.width(112.dp).height(63.dp).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(s.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            val live = s.viewers > 0 || s.title.isNotEmpty()
            Text(
                if (live) listOfNotNull(s.game, "${s.viewers} зрит.").joinToString(" · ") else "не в эфире",
                style = MaterialTheme.typography.bodySmall, maxLines = 1
            )
            if (s.title.isNotEmpty()) Text(s.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
