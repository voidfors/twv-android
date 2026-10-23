package com.twv.viewers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twv.viewers.logic.MonthKey

/** График регистраций: помещается в ширину экрана, кнопка «На весь экран» открывает его крупно. */
@Composable
fun RegistrationsChartPanel(data: List<Pair<MonthKey, Int>>, selected: MonthKey?, onSelect: (MonthKey) -> Unit) {
    var full by remember { mutableStateOf(false) }
    if (data.isEmpty()) {
        Text(
            "Нет данных о датах регистрации", Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Регистрации по месяцам", Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = { full = true }) { Text("На весь экран") }
        }
        RegistrationsChart(data, selected, onSelect, Modifier.fillMaxWidth().height(160.dp), maxLabels = 6)
    }
    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.systemBarsPadding().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Регистрации по месяцам", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        IconButton(onClick = { full = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }
                    RegistrationsChart(data, selected, onSelect, Modifier.fillMaxWidth().weight(1f), maxLabels = 12)
                }
            }
        }
    }
}

/**
 * Столбцы делят всю ширину поровну, сколько бы месяцев ни было. Тап по столбцу — фильтр по месяцу,
 * проведение пальцем — выбор нужного столбца (применяется, когда палец отпущен).
 */
@Composable
private fun RegistrationsChart(
    data: List<Pair<MonthKey, Int>>,
    selected: MonthKey?,
    onSelect: (MonthKey) -> Unit,
    modifier: Modifier,
    maxLabels: Int
) {
    val n = data.size
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    val onSelectNow by rememberUpdatedState(onSelect)
    var widthPx by remember { mutableStateOf(0) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    val shown = scrub ?: data.indexOfFirst { it.first == selected }.takeIf { it >= 0 }
    val primary = MaterialTheme.colorScheme.primary
    val gap = if (n > 40) 0.5.dp else 1.5.dp
    val step = ((n + maxLabels - 1) / maxLabels).coerceAtLeast(1)

    fun indexAt(x: Float): Int = if (widthPx <= 0) 0 else (x / widthPx * n).toInt().coerceIn(0, n - 1)

    Column(modifier) {
        Text(
            shown?.let { "${monthLabel(data[it].first)} · ${data[it].second} акк." }
                ?: "Коснитесь столбца или проведите пальцем",
            style = MaterialTheme.typography.labelMedium,
            color = if (shown != null) primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(data) {
                    detectTapGestures { off -> onSelectNow(data[indexAt(off.x)].first) }
                }
                .pointerInput(data) {
                    detectHorizontalDragGestures(
                        onDragStart = { off -> scrub = indexAt(off.x) },
                        onDragEnd = { scrub?.let { onSelectNow(data[it].first) }; scrub = null },
                        onDragCancel = { scrub = null },
                        onHorizontalDrag = { change, _ -> scrub = indexAt(change.position.x) }
                    )
                },
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { i, (_, count) ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = gap)
                            .fillMaxHeight((count.toFloat() / max).coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (i == shown) primary else primary.copy(alpha = 0.45f))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            data.forEachIndexed { i, (m, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % step == 0) {
                        Text(
                            monthLabel(m), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}
