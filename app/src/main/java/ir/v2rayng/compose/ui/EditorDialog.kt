package ir.v2rayng.compose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.stickers.tinted

private val PALETTE = longArrayOf(
    0xFF2BD48A, 0xFF60A5FA, 0xFFB39BF5, 0xFFFFA94D,
    0xFFEC6E6E, 0xFFF4D27A, 0xFF96B6F5, 0xFF8CDDB2,
    0xFF7FD8E8, 0xFFC9E87F, 0xFFE689C3, 0xFF23262B,
)

@Composable
fun StickerEditorDialog(zone: String, settings: Settings, vm: AppViewModel, onDismiss: () -> Unit) {
    val isWall = zone == "wall"
    val isMain = zone == "main"
    val slot = if (zone.startsWith("nav:") || zone.startsWith("route:")) zone.split(":").getOrNull(1)?.toIntOrNull() ?: 0 else -1

    val title = when {
        isWall -> "سفارشی‌سازی پس‌زمینه"
        isMain -> "سفارشی‌سازی استیکر اصلی"
        zone.startsWith("nav:") -> "استیکر آیکون منو"
        zone == "up" -> "استیکر کارت خروجی"
        zone == "down" -> "استیکر کارت ورودی"
        zone.startsWith("route:") -> "استیکر قانون مسیریابی"
        else -> "استیکر"
    }

    val pickStyle = settings.stickerStyle
    val set = V2Stickers.setFor(pickStyle)

    val current = when {
        isWall -> settings.wallpaperIndex
        isMain -> settings.mainIndex
        zone.startsWith("nav:") -> parseList(settings.navIcons, (0..4).toList()).getOrNull(slot) ?: slot
        zone == "up" -> settings.upIcon
        zone == "down" -> settings.downIcon
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
            zone.startsWith("route:") -> vm.setRouteIcon(slot, i)
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
                        MiniChip("Blob", settings.wallpaperStyle == 0, { vm.setWallpaperStyle(0) })
                        MiniChip("عروسکی", settings.wallpaperStyle == 1, { vm.setWallpaperStyle(1) })
                        MiniChip("پرچم", settings.wallpaperStyle == 2, { vm.setWallpaperStyle(2) })
                    }
                }

                if (isMain) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniChip("از سرور", settings.mainIndex < 0, { vm.setMainIndex(-1) })
                        MiniChip(
                            "انتخابی",
                            settings.mainIndex >= 0,
                            { if (settings.mainIndex < 0) vm.setMainIndex(0) },
                        )
                    }
                }

                Text(
                    "انتخاب استیکر از پک (${set.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(set) { i, sticker ->
                        val selected = current == i
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
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
                        }
                    }
                }

                if (isMain || isWall) {
                    Text(
                        "رنگ دلخواه:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cur: Long = if (isWall) settings.wallpaperColor else settings.mainColor
                        ColorDot(0xFF888888, cur < 0, { if (isWall) vm.setWallpaperColor(-1) else vm.setMainColor(-1) })
                        PALEASE_COLOR_FOREACH(cur, isWall, vm)
                    }

                    Text(
                        "سایز:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isWall) {
                        Text(
                            text = if (settings.wallpaperSize == 0) "پیش‌فرض (۳۰۰)" else "${settings.wallpaperSize}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = if (settings.wallpaperSize == 0) 300f else settings.wallpaperSize.toFloat(),
                            onValueChange = { vm.setWallpaperSize(it.toInt()) },
                            valueRange = 150f..420f,
                        )
                    } else {
                        Text(
                            text = if (settings.mainSize == 0) "پیش‌فرض (۱۹۰)" else "${settings.mainSize}",
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
                    Text("بستن")
                }
            }
        }
    }
}

@Composable
private fun PALEASE_COLOR_FOREACH(cur: Long, isWall: Boolean, vm: AppViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { c ->
            ColorDot(
                c,
                cur == c,
                { if (isWall) vm.setWallpaperColor(c) else vm.setMainColor(c) },
            )
        }
    }
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
