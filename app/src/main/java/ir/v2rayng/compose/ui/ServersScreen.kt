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
    var editSrv by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }

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
            IconButton(onClick = { showImport = true }) {
                Icon(Icons.Filled.Add, contentDescription = S.IMPORT_TITLE)
            }
            androidx.compose.material3.TextButton(onClick = { vm.resetAllServerTraffic() }) {
                Text("Reset", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { vm.pingAll() }) {
                Icon(Icons.Filled.Refresh, contentDescription = S.PING_TEST)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (state.servers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = S.IMPORT_EMPTY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.servers, key = { it.id }) { srv ->
                    val avatar = if (settings.flagAvatars && srv.country.isNotBlank()) {
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
                        onIconHold = { editSrv = srv.id },
                    )
                }
            }
        }
    }

    editSrv?.let { id ->
        StickerEditorDialog("srv:$id", settings, vm) { editSrv = null }
    }
    if (showImport) {
        ImportLinkDialog(vm) { showImport = false }
    }
}

@Composable
private fun ImportLinkDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.IMPORT_TITLE) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(S.IMPORT_HINT) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        vm.addServerFromLink(text)
                    }
                    onDismiss()
                },
            ) { Text(S.ADD) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.CANCEL) } },
    )
}

@Composable
private fun ServerRow(
    profile: ServerProfile,
    sticker: Sticker,
    selected: Boolean,
    onPick: () -> Unit,
    onIconHold: () -> Unit,
) {
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
                    Box(
                        Modifier.holdFor(1000L, onHold = onIconHold),
                    ) {
                        StickerCharacter(sticker, sizeDp = 46.dp)
                    }
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
                if (profile.up > 0 || profile.down > 0) {
                    Text(
                        text = "Up ${ir.v2rayng.compose.data.TimeKit.human(profile.up)} - Down ${ir.v2rayng.compose.data.TimeKit.human(profile.down)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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


