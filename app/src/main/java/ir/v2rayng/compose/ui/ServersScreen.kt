package ir.v2rayng.compose.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var query by remember { mutableStateOf("") }
    var catSel by remember { mutableStateOf("") } // empty string means All
    var delSrv by remember { mutableStateOf<ServerProfile?>(null) }
    var askDead by remember { mutableStateOf(false) }
    val deadCount = state.servers.count { it.ping == null && !it.pingErr.isNullOrBlank() }

    val categories = state.servers.mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }.distinct()
    val filtered = state.servers.filter { srv ->
        (catSel.isBlank() || srv.category == catSel) &&
            (query.isBlank() || srv.name.contains(query, true) || srv.host.contains(query, true) || srv.category.contains(query, true))
    }

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
            TipIconButton(Icons.Filled.Add, S.IMPORT_TITLE) { showImport = true }
            TipIconButton(Icons.Filled.Delete, S.RESET_TRAFFIC, tint = MaterialTheme.colorScheme.onSurfaceVariant) { vm.resetAllServerTraffic() }
            TipIconButton(Icons.Filled.Refresh, S.REMOTE_SYNC) { vm.syncNow() }
            TipIconButton(Icons.Filled.Send, S.PING_TEST) { vm.pingAll() }
            TipIconButton(
                Icons.Filled.Clear,
                if (deadCount > 0) S.DELETE_DEAD + " (" + deadCount + ")" else S.NOTHING_TO_DELETE,
                tint = if (deadCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { askDead = true }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(S.SEARCH_SERVERS) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("") + categories) { c ->
                    SelectChip(c.ifBlank { S.CAT_ALL }, catSel == (c.ifBlank { S.CAT_ALL }) || (c.isBlank() && catSel.isBlank()), {
                        catSel = if (c.isBlank()) "" else c
                    })
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = S.IMPORT_EMPTY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val sorted = filtered.sortedWith(
                    compareByDescending<ir.v2rayng.compose.data.ServerProfile> { it.pinned }
                        .thenBy { state.servers.indexOfFirst { x -> x.id == it.id } },
                )
                items(sorted, key = { it.id }) { srv ->
                    val avatar = if (settings.flagAvatars && srv.country.isNotBlank()) {
                        val fi = CountryFlags.byCode(srv.country)
                        if (fi >= 0) CountryFlags.list[fi] else V2Stickers.byPack(srv.pack, srv.stickerIndex, settings.stickerStyle, srv.stickerIndex)
                    } else {
                        V2Stickers.byPack(srv.pack, srv.stickerIndex, settings.stickerStyle, srv.stickerIndex)
                    }
                    ServerRow(
                        profile = srv,
                        sticker = avatar,
                        settings = settings,
                        selected = srv.id == state.selectedId,
                        onPick = { vm.select(srv.id) },
                        onIconHold = { editSrv = srv.id },
                        onPin = { vm.togglePin(srv.id) },
                        onPingOne = { vm.pingServers(listOf(srv.id), false) },
                        onDelete = { delSrv = srv },
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

    delSrv?.let { srv ->
        AlertDialog(
            onDismissRequest = { delSrv = null },
            title = { Text(S.DELETE_Q.format(srv.name)) },
            text = { if (srv.id.startsWith("rem-")) Text(S.REMOTE_COMES_BACK) },
            confirmButton = {
                TextButton(onClick = { vm.deleteServer(srv.id); delSrv = null }) { Text(S.DELETE_ONE) }
            },
            dismissButton = { TextButton(onClick = { delSrv = null }) { Text(S.CANCEL) } },
        )
    }

    if (askDead) {
        AlertDialog(
            onDismissRequest = { askDead = false },
            title = { Text(S.DELETE_DEAD_Q.format(deadCount)) },
            text = { if (deadCount > 0) Text(S.REMOTE_COMES_BACK) else Text(S.NOTHING_TO_DELETE) },
            confirmButton = {
                TextButton(onClick = { vm.deleteUnpingable(); askDead = false }, enabled = deadCount > 0) {
                    Text(S.DELETE_DEAD)
                }
            },
            dismissButton = { TextButton(onClick = { askDead = false }) { Text(S.CANCEL) } },
        )
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
    settings: Settings,
    selected: Boolean,
    onPick: () -> Unit,
    onIconHold: () -> Unit,
    onPin: () -> Unit,
    onPingOne: () -> Unit,
    onDelete: () -> Unit,
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
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                    Box(
                        Modifier.holdFor(1000L, onHold = onIconHold),
                    ) {
                        if (settings.bubbleOn) {
                            // circle behind stickers, rectangle behind flags
                            val shape = if (sticker.art is ir.v2rayng.compose.stickers.FlagArt) {
                                RoundedCornerShape(8.dp)
                            } else {
                                CircleShape
                            }
                            val bubCol = when {
                                profile.bubbleColor >= 0L -> Color(profile.bubbleColor)
                                settings.bubbleColor >= 0L -> Color(settings.bubbleColor)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            }
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(shape)
                                    .background(bubCol),
                                contentAlignment = Alignment.Center,
                            ) {
                                StickerCharacter(sticker, sizeDp = 46.dp)
                            }
                        } else {
                            StickerCharacter(sticker, sizeDp = 46.dp)
                        }
                    }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    if (profile.note.isNotBlank() || !profile.id.startsWith("rem-")) append(profile.note.ifBlank { profile.host })
                    if (profile.category.isNotBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(profile.category)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            PingChip(profile.ping, profile.pingErr)
            IconButton(onClick = onPingOne, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = S.PING_ONE, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = S.DELETE_ONE, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onPin, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Pin",
                    modifier = Modifier.size(20.dp),
                    tint = if (profile.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = S.SELECTED,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                )
            }
            }
            if (profile.up > 0 || profile.down > 0) {
                // under the whole row and at card width: as wrap-content inside the text column
                // the download counter only got the leftover width and was clipped away
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "↑ " + ir.v2rayng.compose.data.TimeKit.human(profile.up),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "↓ " + ir.v2rayng.compose.data.TimeKit.human(profile.down),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


