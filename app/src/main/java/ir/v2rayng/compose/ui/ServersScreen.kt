package ir.v2rayng.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.ServerProfile
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.stickers.CountryFlags
import ir.v2rayng.compose.stickers.Sticker
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.V2Stickers

@Composable
fun ServersScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = S.SERVERS_TITLE,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = S.SERVERS_COUNT.format(state.servers.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.pingAll() }) {
                Icon(Icons.Filled.Refresh, contentDescription = S.PING_TEST)
            }
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = S.ADD_SERVER)
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.servers, key = { it.id }) { srv ->
                val avatar = if (srv.country.isNotBlank()) {
                    val fi = CountryFlags.byCode(srv.country)
                    if (fi >= 0) CountryFlags.list[fi] else V2Stickers.byIndex(settings.stickerStyle, srv.stickerIndex)
                } else {
                    V2Stickers.byIndex(settings.stickerStyle, srv.stickerIndex)
                }
                ServerRow(
                    profile = srv,
                    sticker = avatar,
                    selected = srv.id == state.selectedId,
                    onPick = { vm.select(srv.id) },
                )
            }
        }
    }

    if (showAdd) {
        AddServerDialog(onDone = { vm.addServer(it.first, it.second) }, onDismiss = { showAdd = false })
    }
}

@Composable
private fun ServerRow(profile: ServerProfile, sticker: Sticker, selected: Boolean, onPick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onPick() }
            .border(
                androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
                RoundedCornerShape(20.dp),
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            StickerCharacter(sticker, sizeDp = 46.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = profile.host,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PingChip(profile.ping)
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = S.SELECTED,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AddServerDialog(onDone: (Pair<String, String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.ADD_SERVER) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(S.FIELD_NAME) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(S.FIELD_ADDRESS) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && host.isNotBlank()) {
                        onDone(name.trim() to host.trim())
                        onDismiss()
                    }
                },
            ) { Text(S.ADD) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S.CANCEL) }
        },
    )
}

