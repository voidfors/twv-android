package com.twv.viewers.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction

/**
 * Временный вход, пока нет экрана стримов (B2): ввод логина и последние каналы.
 * Это же — запасной путь, если запрос списка стримов не сработает.
 */
@Composable
fun HomeScreen(recent: List<String>, onOpen: (String) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    val submit = { if (login.isNotBlank()) onOpen(login.trim().removePrefix("@").lowercase()) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Twitch Viewers", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = login, onValueChange = { login = it },
                label = { Text("Логин канала") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = submit, enabled = login.isNotBlank()) { Text("Открыть") }
        }
        if (recent.isNotEmpty()) {
            Text("Недавние", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxWidth()) {
                items(recent) { l ->
                    Text(
                        l, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(l) }.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}
