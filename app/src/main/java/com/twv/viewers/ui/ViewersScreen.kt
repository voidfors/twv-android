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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.channels.joinToString(" + ").ifEmpty { "…" }, maxLines = 1)
                        Text(
                            "Зрителей: ${ui.total} · в списке: ${ui.loadedCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = { TextButton(onClick = { addDialog = true }) { Text("+ Канал") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = ui.filter.query, onValueChange = vm::setQuery,
                label = { Text("Поиск по нику") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            // Фильтр по стримеру (G4): только когда в группе больше одного канала.
            if (ui.channels.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
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
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SORT_LABELS) { (mode, label) ->
                    val active = ui.filter.mode == mode
                    val arrow = if (!active) "" else if (ui.filter.dir == SortDir.DESC) " ↓" else " ↑"
                    FilterChip(selected = active, onClick = { vm.setSort(mode) }, label = { Text(label + arrow) })
                }
            }

            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = chat, onCheckedChange = vm::setChatEnabled)
                Text("Читать чат")
                Box(Modifier.weight(1f))
                TextButton(onClick = { showChart = !showChart }) { Text(if (showChart) "Скрыть график" else "График") }
            }
            ui.filter.month?.let { m ->
                FilterChip(
                    selected = true, onClick = { vm.setMonth(null) },
                    label = { Text("Месяц: ${monthLabel(m)} ✕") },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (showChart) RegistrationsChart(
                data = ui.chart, selected = ui.filter.month,
                onSelect = { m -> vm.setMonth(if (m == ui.filter.month) null else m) }
            )

            val favs = ui.filter.watched
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
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
    // Затемнение «ушедших»: первый пропуск прохода.
    val dim = if (v.missedPasses > 0) 0.45f else 1f
    Row(
        Modifier.fillMaxWidth().alpha(dim).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (v.avatarUrl != null) {
            AsyncImage(
                model = v.avatarUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(Modifier.weight(1f)) {
            Text(v.displayName.ifEmpty { v.login }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            val parts = buildList {
                add(v.followers?.let { "$it фолл." } ?: "…")
                formatDate(v.createdAt)?.let { add("с $it") }
                if (v.msgCount > 0) add("${v.msgCount} сообщ.")
                add(if (v.source == Source.CHAT) "чат" else "список")
                if (v.moved) add("перешёл")
                if (showChannel) add(v.channel)
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        TextButton(onClick = onFavorite) { Text(if (favorite) "★" else "☆") }
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
                        Text(ch, Modifier.weight(1f))
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
        Text("Нет данных о датах регистрации", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items(data) { (m, n) ->
            val active = m == selected
            Column(
                Modifier.clickable { onSelect(m) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$n", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.width(22.dp).height((4 + 80 * n / max).dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        )
                )
                Text(monthLabel(m), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
