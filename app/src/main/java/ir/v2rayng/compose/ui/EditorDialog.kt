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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.parseFloatList
import ir.v2rayng.compose.data.parseLongList
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers

private val PALETTE = longArrayOf(
    0xFF2BD48A, 0xFF60A5FA, 0xFFB39BF5, 0xFFFFA94D,
    0xFFEC6E6E, 0xFFF4D27A, 0xFF96B6F5, 0xFF8CDDB2,
    0xFF7FD8E8, 0xFFC9E87F, 0xFFE689C3, 0xFF23262B,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerEditorDialog(zone: String, settings: Settings, vm: AppViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val isWall = zone == "wall"
    val isMain = zone == "main"
    val slot = if (zone.startsWith("nav:") || zone.startsWith("route:")) zone.split(":").getOrNull(1)?.toIntOrNull() ?: 0 else -1

    val title = when {
        isWall -> S.EDITOR_WALL
        isMain -> S.EDITOR_MAIN
        zone.startsWith("nav:") -> S.EDITOR_NAV
        zone == "up" -> S.EDITOR_UP
        zone == "down" -> S.EDITOR_DOWN
        zone.startsWith("srv:") -> S.EDITOR_SRV
        zone.startsWith("route:") -> S.EDITOR_ROUTE
        else -> "Sticker"
    }

    val srvId = if (zone.startsWith("srv:")) zone.removePrefix("srv:") else null

    val pickStyle = if (isWall) settings.wallpaperStyle else settings.stickerStyle
    val set = V2Stickers.setFor(pickStyle)

    val current = when {
        isWall -> settings.wallpaperIndex
        isMain -> settings.mainIndex
        zone.startsWith("nav:") -> parseList(settings.navIcons, (0..4).toList()).getOrNull(slot) ?: slot
        zone == "up" -> settings.upIcon
        zone == "down" -> settings.downIcon
        srvId != null -> vm.srvStickerIndex(srvId)
        zone.startsWith("route:") -> parseList(settings.routeIcons, listOf(0, 1, 2)).getOrNull(slot) ?: slot
        else -> -1
    }

    fun pick(i: Int) {
        when {
            isWall -> vm.setWallpaper(i)
            isMain -> vm.setMainIndex(i)
            zone.startsWith("nav:") -> vm.setNavIcon(slot, i)
            zone == "up" -> vm.setUpIcon(i)
            zone == "down" -> vm.setDownIcon(i)
            srvId != null -> vm.setServerIcon(srvId, i)
            zone.startsWith("route:") -> vm.setRouteIcon(slot, i)
        }
    }

    val colorCur = when {
        isWall -> settings.wallpaperColor
        isMain -> settings.mainColor
        zone.startsWith("nav:") -> parseLongList(settings.navColors, listOf(-1L, -1L, -1L, -1L, -1L)).getOrNull(slot) ?: -1L
        zone == "up" -> settings.upColor
        zone == "down" -> settings.downColor
        zone.startsWith("route:") -> parseLongList(settings.routeColors, listOf(-1L, -1L, -1L)).getOrNull(slot) ?: -1L
        else -> -1L
    }
    val styleId = if (isWall) settings.wallpaperStyle else settings.stickerStyle
    val hasColor = srvId == null && styleId != 2
    val showHex = remember { mutableStateOf(false) }

    fun setColor(v: Long) {
        when {
            isWall -> vm.setWallpaperColor(v)
            isMain -> vm.setMainColor(v)
            zone.startsWith("nav:") -> vm.setNavColor(slot, v)
            zone == "up" -> vm.setUpColor(v)
            zone == "down" -> vm.setDownColor(v)
            zone.startsWith("route:") -> vm.setRouteColor(slot, v)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (isWall) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniChip(S.STYLE_BLOB, settings.wallpaperStyle == 0, { vm.setWallpaperStyle(0) })
                        MiniChip(S.STYLE_TOY, settings.wallpaperStyle == 1, { vm.setWallpaperStyle(1) })
                        MiniChip(S.STYLE_FLAG, settings.wallpaperStyle == 2, { vm.setWallpaperStyle(2) })
                    }
                }

                if (isMain) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniChip(S.FROM_SERVER, settings.mainIndex < 0, { vm.setMainIndex(-1) })
                        MiniChip(
                            S.CHOSEN,
                            settings.mainIndex >= 0,
                            { if (settings.mainIndex < 0) vm.setMainIndex(0) },
                        )
                    }
                }

                Text(
                    S.PICK_STICKER.format(set.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(S.SEARCH_STICKERS) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val shown = set.withIndex().filter { (_, s) ->
                    query.isBlank() || s.name.contains(query, ignoreCase = true) || s.id.contains(query, ignoreCase = true)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(shown) { _, entry ->
                        val i = entry.index
                        val sticker = entry.value
                        val selected = current == i                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable { pick(i) }
                                .padding(5.dp),
                        ) {
                            StickerCharacter(sticker, mood = StickerMood.NORMAL, sizeDp = 52.dp)
                            Text(
                                text = sticker.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val speedCur = when {
                    zone.startsWith("nav:") -> parseFloatList(settings.navSpeeds, listOf(1f, 1f, 1f, 1f, 1f)).getOrNull(slot) ?: 1f
                    zone == "up" -> settings.upSpeed
                    zone == "down" -> settings.downSpeed
                    zone.startsWith("route:") -> parseFloatList(settings.routeSpeeds, listOf(1f, 1f, 1f)).getOrNull(slot) ?: 1f
                    else -> 0f
                }
                if (speedCur > 0f) {
                    Text(
                        text = "Dance speed: %.1fx".format(speedCur),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = speedCur,
                        onValueChange = { v ->
                            when {
                                zone.startsWith("nav:") -> vm.setNavSpeed(slot, v)
                                zone == "up" -> vm.setUpSpeed(v)
                                zone == "down" -> vm.setDownSpeed(v)
                                zone.startsWith("route:") -> vm.setRouteSpeed(slot, v)
                            }
                        },
                        valueRange = 0.4f..2.2f,
                    )
                }

                if (hasColor) {
                    Text(
                        S.COLOR,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ColorDot(0xFF888888, colorCur < 0, { setColor(-1L) })
                        PALETTE.forEach { c ->
                            ColorDot(
                                c,
                                colorCur == c,
                                { setColor(c) },
                            )
                        }
                        MiniChip("Custom", false, { showHex.value = true })
                        MiniChip("Random", false, {
                            setColor(PALETTE[(0 until PALETTE.size).random()])
                        })
                    }
                }

                if (isMain || isWall) {
                    Text(
                        S.SIZE,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isWall) {
                        Text(
                            text = if (settings.wallpaperSize == 0) S.SIZE_DEFAULT.format("300") else "${settings.wallpaperSize}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = if (settings.wallpaperSize == 0) 300f else settings.wallpaperSize.toFloat(),
                            onValueChange = { vm.setWallpaperSize(it.toInt()) },
                            valueRange = 150f..420f,
                        )
                    } else {
                        Text(
                            text = if (settings.mainSize == 0) S.SIZE_DEFAULT.format("190") else "${settings.mainSize}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = if (settings.mainSize == 0) 190f else settings.mainSize.toFloat(),
                            onValueChange = { vm.setMainSize(it.toInt()) },
                            valueRange = 80f..340f,
                        )
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(S.CLOSE)
                }
            }
        }
    }

    if (showHex.value) {
        HexColorDialog(colorCur, ::setColor) { showHex.value = false }
    }
}

@Composable
private fun HexColorDialog(current: Long, onSet: (Long) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(if (current >= 0) String.format("%08X", current) else "#FF8CDDB2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
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
            TextButton(onClick = onDismiss) { Text(S.CANCEL) }
        },
    )
}

@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ColorDot(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(color))
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground),
                RoundedCornerShape(50),
            )
            .clickable { onClick() },
    )
}
