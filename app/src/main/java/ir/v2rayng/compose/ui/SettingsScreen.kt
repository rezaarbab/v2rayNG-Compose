package ir.v2rayng.compose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.stickers.CountryFlags
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.ui.theme.AccentOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    var editWall by remember { mutableStateOf(false) }
    var showCustomAccent by remember { mutableStateOf(false) }
    var editNavSlot by remember { mutableStateOf<Int?>(null) }
    var moveMenu by remember { mutableStateOf(false) }
    var moveHome by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showNotifHex by remember { mutableStateOf(false) }
    var showBubbleHex by remember { mutableStateOf(false) }
    var showCreator by remember { mutableStateOf(false) }
    var showRemoteUrl by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadApps(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(S.SETTINGS_TITLE, S.SETTINGS_SUB)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.THEME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectChip(S.DARK, settings.dark, { vm.setDark(true) })
                    SelectChip(S.LIGHT, !settings.dark, { vm.setDark(false) })
                }
                Text(S.PRIMARY_COLOR, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AccentOptions.forEachIndexed { i, (name, color) ->
                        ColorDot(
                            color,
                            name,
                            settings.customAccent < 0 && settings.accent % AccentOptions.size == i,
                        ) { vm.setAccent(i) }
                    }
                }
                SelectChip(S.CUSTOM_COLOR, settings.customAccent >= 0, { showCustomAccent = true })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.STICKERS, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(S.SHAPE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(S.STYLE_BLOB, settings.stickerStyle == 0, { vm.setStickerStyle(0) })
                    SelectChip(S.STYLE_TOY, settings.stickerStyle == 1, { vm.setStickerStyle(1) })
                    SelectChip(S.STYLE_FLAG, settings.stickerStyle == 2, { vm.setStickerStyle(2) })
                    SelectChip(S.MINE, settings.stickerStyle == 3, { vm.setStickerStyle(3) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.CREATOR_TITLE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    SelectChip(S.CREATOR_TITLE, false, { showCreator = true })
                }

                ToggleRow(S.MOTION_ALL, S.MOTION_ALL_SUB, settings.motionGlobal) { vm.setMotionGlobal(it) }

                ToggleRow(S.ANIMATION, S.ANIMATION_SUB, settings.animate && settings.motionGlobal) { vm.setAnimate(it) }

                Text(S.SPEED_STICKERS.format(settings.speed), style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.speed, onValueChange = { vm.setSpeed(it) }, valueRange = 0.25f..2f)

                Text(S.SPEED_BG.format(settings.bgSpeed), style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.bgSpeed, onValueChange = { vm.setBgSpeed(it) }, valueRange = 0.25f..2f)

                ToggleRow(S.STATE_MOTION, S.STATE_MOTION_SUB, settings.stateMotion) { vm.setStateMotion(it) }
                ToggleRow(S.NOTIF_TITLE, S.NOTIF_SUB, settings.showNotification) { vm.setShowNotification(it) }

                Text(S.NOTIF_STYLE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(S.NOTIF_STICKER, settings.notifSticker, { vm.setNotifSticker(true) })
                    SelectChip(S.NOTIF_LOCK, !settings.notifSticker, { vm.setNotifSticker(false) })
                }

                if (settings.notifSticker) {
                    Text(S.NOTIF_COLOR, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ColorDot(Color(0xFF888888), "", settings.notifColor < 0) { vm.setNotifColor(-1) }
                        NotifPalette.forEach { c ->
                            ColorDot(Color(c), "", settings.notifColor == c) { vm.setNotifColor(c) }
                        }
                        SelectChip("Custom", false, { showNotifHex = true })
                    }
                }

                ToggleRow(S.FLAG_AVATARS, S.FLAG_AVATARS_SUB, settings.flagAvatars) { vm.setFlagAvatars(it) }
                ToggleRow(S.BUBBLE, S.BUBBLE_SUB, settings.bubbleOn) { vm.setBubbleOn(it) }
                if (settings.bubbleOn) {
                    Text(S.BUBBLE_COLOR, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ColorDot(Color(0xFF888888), S.BUBBLE_GLOBAL, settings.bubbleColor < 0) { vm.setBubbleColor(-1) }
                        NotifPalette.forEach { c ->
                            ColorDot(Color(c), "", settings.bubbleColor == c) { vm.setBubbleColor(c) }
                        }
                        SelectChip("Custom", false, { showBubbleHex = true })
                    }
                }
                ToggleRow(S.CONNECT_FLAG_DANCER, S.CONNECT_FLAG_DANCER_SUB, settings.connectFlagDancer) { vm.setConnectFlagDancer(it) }
                ToggleRow(S.FOLLOW_FINGER, S.FOLLOW_SUB, settings.followFinger) { vm.setFollowFinger(it) }

                Text(S.FLAG_STRETCH.format((settings.flagStretch * 100f).toInt()), style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.flagStretch, onValueChange = { vm.setFlagStretch(it) }, valueRange = 0.5f..1f)

                Text(S.FLAG_ROUND.format((settings.flagRound * 100f).toInt()), style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.flagRound, onValueChange = { vm.setFlagRound(it) }, valueRange = 0f..1f)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.MENU_LAYOUT, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                ToggleRow(S.NAV_STICKERS, S.ICON_STICKERS_SUB, settings.navStickers) { vm.setNavStickers(it) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.MENU_LAYOUT_SUB, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    SelectChip(S.EDITOR, false, { moveMenu = true })
                }

                Text(S.NAV_ICONS_TITLE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val navIcons = parseList(settings.navIcons, (0..4).toList())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(navIcons) { slot, idx ->
                        val sticker = V2Stickers.byIndex(settings.stickerStyle, idx)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { editNavSlot = slot }
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(14.dp))
                                .padding(5.dp),
                        ) {
                            StickerCharacter(sticker, mood = StickerMood.NORMAL, sizeDp = 40.dp)
                        }
                    }
                }

                Text(S.HOME_LAYOUT, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        S.HOME_SECTIONS.joinToString(" | "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SelectChip(S.EDITOR, false, { moveHome = true })
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.BACKGROUND, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                ToggleRow(S.AUTO_FLAG, S.AUTO_FLAG_SUB, settings.autoFlag) { vm.setAutoFlag(it) }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val wallName = when {
                        settings.wallpaperIndex < 0 -> S.NO_BG
                        settings.wallpaperStyle == 2 -> CountryFlags.list.getOrNull(settings.wallpaperIndex)?.name ?: ""
                        else -> V2Stickers.setFor(settings.wallpaperStyle).getOrNull(settings.wallpaperIndex)?.name ?: ""
                    }
                    Column(Modifier.weight(1f)) {
                        Text(S.CURRENT_BG.format(wallName), style = MaterialTheme.typography.bodyMedium)
                        if (state.ipCountry.isNotBlank()) {
                            Text(
                                S.IP_COUNTRY.format(state.ipCountry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SelectChip(S.EDITOR, false, { editWall = true })
                    SelectChip(S.CLEAR_BG, settings.wallpaperIndex < 0, { vm.setWallpaper(-1) })
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.ANTI_FILTER, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val selectedApps = settings.antiFilterApps.split(",").filter { it.isNotBlank() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        S.ANTI_FILTER_APPS.format(selectedApps.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    SelectChip(S.EDITOR, false, { showAppPicker = true })
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.SERVERS_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                ToggleRow(S.PING_ON_START, S.PING_ON_START_SUB, settings.pingOnStart) { vm.setPingOnStart(it) }
                ToggleRow(S.FLAG_ON_START, S.FLAG_ON_START_SUB, settings.flagOnStart) { vm.setFlagOnStart(it) }
                ToggleRow(S.AUTO_PING_NEW, S.AUTO_PING_NEW_SUB, settings.autoPingNew) { vm.setAutoPingNew(it) }
                ToggleRow(S.AUTO_FLAG_NEW, S.AUTO_FLAG_NEW_SUB, settings.autoFlagNew) { vm.setAutoFlagNew(it) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        S.SERVERS_COUNT.format(state.servers.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SelectChip(S.REMOTE_SYNC, false, { vm.syncNow() })
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.LANGUAGE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.RESET, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    SelectChip(S.RESET, false, { vm.resetSettings() })
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }

    if (editWall) {
        StickerEditorDialog("wall", settings, vm) { editWall = false }
    }
    if (showCustomAccent) {
        CustomAccentDialog(settings.customAccent, vm) { showCustomAccent = false }
    }
    if (showNotifHex) {
        NotifColorDialog(settings.notifColor, vm) { showNotifHex = false }
    }
    if (showBubbleHex) {
        SimpleHexColorDialog(S.BUBBLE_COLOR, settings.bubbleColor, { vm.setBubbleColor(it) }) { showBubbleHex = false }
    }
    if (showCreator) {
        CreatorDialog(vm) { showCreator = false }
    }
    editNavSlot?.let { slot ->
        StickerEditorDialog("nav:$slot", settings, vm) { editNavSlot = null }
    }
    if (moveMenu) {
        MoveNavDialog(parseList(settings.navOrder, (0..4).toList()), vm) { moveMenu = false }
    }
    if (moveHome) {
        HomeLayoutDialog(parseList(settings.homeOrder, listOf(0, 1, 2, 3, 4)), vm) { moveHome = false }
    }
    if (showAppPicker) {
        AppPickerDialog(state.apps, settings.antiFilterApps, vm) { showAppPicker = false }
    }
    if (showRemoteUrl) {
        RemoteUrlDialog(vm.remoteBase(), vm) { showRemoteUrl = false }
    }
}

@Composable
private fun RemoteUrlDialog(current: String, vm: AppViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.REMOTE_TITLE) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text("https://...workers.dev") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.setRemoteBase(url); onDismiss() }) { Text(S.APPLY) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.CANCEL) } },
    )
}

@Composable
private fun AppPickerDialog(apps: List<ir.v2rayng.compose.data.AppInfo>, selectedCsv: String, vm: AppViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val selected = selectedCsv.split(",").filter { it.isNotBlank() }.toSet()
    val filtered = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
    val sorted = filtered.sortedWith(
        compareByDescending<ir.v2rayng.compose.data.AppInfo> { selected.contains(it.pkg) }
            .thenBy { it.label.lowercase() },
    )

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(S.ALL_APPS, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(S.SEARCH_APPS) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sorted, key = { it.pkg }) { app ->
                        val checked = selected.contains(app.pkg)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleAntiFilterApp(app.pkg) }
                                .padding(vertical = 4.dp),
                        ) {
                            androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { vm.toggleAntiFilterApp(app.pkg) })
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(S.CLOSE)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun HomeLayoutDialog(order: List<Int>, vm: AppViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.HOME_LAYOUT_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                order.forEachIndexed { pos, real ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${pos + 1}. ${S.HOME_SECTIONS.getOrNull(real) ?: ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { if (pos > 0) vm.setHomeOrder(order.toMutableList().apply { add(pos - 1, removeAt(pos)) }) },
                            enabled = pos > 0,
                        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up") }
                        IconButton(
                            onClick = { if (pos < order.size - 1) vm.setHomeOrder(order.toMutableList().apply { add(pos + 1, removeAt(pos)) }) },
                            enabled = pos < order.size - 1,
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down") }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(S.CLOSE)
                }
            }
        }
    }
}

@Composable
private fun CustomAccentDialog(current: Long, vm: AppViewModel, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(if (current >= 0) String.format("%08X", current) else "#FF8CDDB2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.CUSTOM_COLOR) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it },
                    singleLine = true,
                    label = { Text("#AARRGGBB") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleaned = hex.removePrefix("#").trim()
                    val v = cleaned.toLongOrNull(16)
                    if (cleaned.length == 6) {
                        vm.setCustomAccent(0xFF000000L or v!!)
                    } else if (cleaned.length == 8) {
                        vm.setCustomAccent(v!!)
                    }
                    onDismiss()
                },
            ) { Text(S.APPLY) }
        },
        dismissButton = {
            TextButton(onClick = { vm.setCustomAccent(-1L); onDismiss() }) { Text(S.CANCEL) }
        },
    )
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ColorDot(color: Color, name: String, isSel: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
                .border(
                    BorderStroke(if (isSel) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground),
                    RoundedCornerShape(50),
                )
        )
        if (name.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val NotifPalette = longArrayOf(
    0xFF2BD48A, 0xFF60A5FA, 0xFFB39BF5, 0xFFFFA94D,
    0xFFEC6E6E, 0xFFF4D27A, 0xFF96B6F5, 0xFF8CDDB2,
    0xFF7FD8E8, 0xFFC9E87F, 0xFFE689C3, 0xFF23262B,
)

@Composable
private fun NotifColorDialog(current: Long, vm: AppViewModel, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(if (current >= 0) String.format("%08X", current) else "#FF8CDDB2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.NOTIF_COLOR) },
        text = {
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = it },
                singleLine = true,
                label = { Text("#AARRGGBB") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleaned = hex.removePrefix("#").trim()
                    val v = cleaned.toLongOrNull(16)
                    if (cleaned.length == 6) {
                        vm.setNotifColor(0xFF000000L or v!!)
                    } else if (cleaned.length == 8) {
                        vm.setNotifColor(v!!)
                    }
                    onDismiss()
                },
            ) { Text(S.APPLY) }
        },
        dismissButton = {
            TextButton(onClick = { vm.setNotifColor(-1L); onDismiss() }) { Text(S.CANCEL) }
        },
    )
}

/** Generic hex color entry used by the bubble color pickers. */
@Composable
fun SimpleHexColorDialog(title: String, current: Long, onSet: (Long) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(if (current >= 0) String.format("%08X", current) else "#FF8CDDB2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = it },
                singleLine = true,
                label = { Text("#AARRGGBB") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleaned = hex.removePrefix("#").trim()
                    val v = cleaned.toLongOrNull(16)
                    if (cleaned.length == 6) {
                        onSet(0xFF000000L or v!!)
                    } else if (cleaned.length == 8) {
                        onSet(v!!)
                    }
                    onDismiss()
                },
            ) { Text(S.APPLY) }
        },
        dismissButton = {
            TextButton(onClick = { onSet(-1L); onDismiss() }) { Text(S.CANCEL) }
        },
    )
}

