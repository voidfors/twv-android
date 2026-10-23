package com.twv.viewers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twv.viewers.Source
import com.twv.viewers.Viewer
import com.twv.viewers.logic.MonthKey
import com.twv.viewers.logic.SortDir
import com.twv.viewers.logic.SortMode
import java.time.Instant
import java.time.ZoneId

private val SORT_LABELS = listOf(
    SortMode.FOLLOWERS to "Фолловеры",
    SortMode.ALPHA to "Имя",
    SortMode.DATE to "Регистрация",
    SortMode.DURATION to "В чате",
    SortMode.SOURCE to "Источник",
    SortMode.MSGS to "Сообщения",
    SortMode.LASTMSG to "Последнее"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewersScreen(vm: ViewersViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val chat by vm.chatEnabled.collectAsState()
    var addDialog by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Column {
                        Text(
                            ui.channels.joinToString(" + ").ifEmpty { "…" },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Зрителей: ${ui.total} · в списке: ${ui.loadedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { addDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить канал")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(
                value = ui.filter.query, onValueChange = vm::setQuery, placeholder = "Поиск по нику",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            // Фильтр по стримеру (G4): только когда в группе больше одного канала.
            if (ui.channels.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = ui.filter.channel == null,
                            onClick = { vm.setChannelFilter(null) },
                            label = { Text("Все") }
                        )
                    }
                    items(ui.channels) { ch ->
                        FilterChip(
                            selected = ui.filter.channel.equals(ch, true),
                            onClick = { vm.setChannelFilter(ch) },
                            label = { Text(ch) }
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SORT_LABELS) { (mode, label) ->
                    val active = ui.filter.mode == mode
                    val arrow = if (!active) "" else if (ui.filter.dir == SortDir.DESC) " ↓" else " ↑"
                    FilterChip(selected = active, onClick = { vm.setSort(mode) }, label = { Text(label + arrow) })
                }
            }

            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = chat, onCheckedChange = vm::setChatEnabled)
                Box(Modifier.width(10.dp))
                Text("Читать чат", color = MaterialTheme.colorScheme.onSurface)
                Box(Modifier.weight(1f))
                TextButton(onClick = { showChart = !showChart }) { Text(if (showChart) "Скрыть график" else "График") }
            }
            ui.filter.month?.let { m ->
                FilterChip(
                    selected = true, onClick = { vm.setMonth(null) },
                    label = { Text("Месяц: ${monthLabel(m)} ✕") },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (showChart) RegistrationsChart(
                data = ui.chart, selected = ui.filter.month,
                onSelect = { m -> vm.setMonth(if (m == ui.filter.month) null else m) }
            )

            val favs = ui.filter.watched
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ui.shown, key = { it.login }) { v ->
                    ViewerRow(
                        v = v, favorite = v.login.lowercase() in favs,
                        showChannel = ui.channels.size > 1,
                        onFavorite = { vm.toggleFavorite(v.login) }
                    )
                }
            }
        }
    }

    if (addDialog) AddChannelDialog(
        current = ui.channels,
        onAdd = { vm.addChannel(it) },
        onRemove = vm::removeChannel,
        onDismiss = { addDialog = false }
    )
}

@Composable
private fun ViewerRow(v: Viewer, favorite: Boolean, showChannel: Boolean, onFavorite: () -> Unit) {
    // «Ушедшие» (первый пропуск прохода) приглушаются, но остаются читаемыми.
    val dim = if (v.missedPasses > 0) 0.6f else 1f
    Row(
        Modifier.fillMaxWidth().alpha(dim).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val name = v.displayName.ifEmpty { v.login }
        Avatar(v.avatarUrl, name, 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val parts = buildList {
                add(v.followers?.let { "$it фолл." } ?: "…")
                formatDate(v.createdAt)?.let { add("с $it") }
                if (v.msgCount > 0) add("${v.msgCount} сообщ.")
                add(if (v.source == Source.CHAT) "чат" else "список")
                if (v.moved) add("перешёл")
                if (showChannel) add(v.channel)
            }
            Text(
                parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
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

private fun formatDate(iso: String?): String? = iso?.let {
    runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.getOrNull()
}

@Composable
private fun AddChannelDialog(
    current: List<String>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Группа каналов (коллаб)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    label = { Text("Логин канала") }, singleLine = true,
                    isError = error != null, supportingText = { error?.let { Text(it) } }
                )
                current.forEach { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ch, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = { onRemove(ch) }) { Text("Убрать") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) onDismiss()
                else if (onAdd(text)) { text = ""; onDismiss() }
                else error = "Некорректный логин или канал уже добавлен"
            }) { Text(if (text.isBlank()) "Готово" else "Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun monthLabel(m: MonthKey) = "%02d.%02d".format(m.month, m.year % 100)

/** График регистраций аккаунтов по месяцам: тап по столбцу фильтрует список по этому месяцу. */
@Composable
private fun RegistrationsChart(data: List<Pair<MonthKey, Int>>, selected: MonthKey?, onSelect: (MonthKey) -> Unit) {
    if (data.isEmpty()) {
        Text(
            "Нет данных о датах регистрации", Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items(data) { (m, n) ->
            val active = m == selected
            Column(
                Modifier.clickable { onSelect(m) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.width(22.dp).height((4 + 80 * n / max).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        )
                )
                Text(monthLabel(m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
