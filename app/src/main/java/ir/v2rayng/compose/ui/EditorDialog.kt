package ir.v2rayng.compose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers

private val PALETTE = longArrayOf(
    0xFF2BD48A, 0xFF60A5FA, 0xFFB39BF5, 0xFFFFA94D,
    0xFFEC6E6E, 0xFFF4D27A, 0xFF96B6F5, 0xFF8CDDB2,
    0xFF7FD8E8, 0xFFC9E87F, 0xFFE689C3, 0xFF23262B,
)

@Composable
fun StickerEditorDialog(zone: String, settings: Settings, vm: AppViewModel, onDismiss: () -> Unit) {
    val isWall = zone == "wall"
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (isWall) "سفارشی‌سازی پس‌زمینه" else "سفارشی‌سازی استیکر اصلی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (isWall) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip("Blob", settings.wallpaperStyle == 0, { vm.setWallpaperStyle(0) })
                        MiniChip("عروسکی", settings.wallpaperStyle == 1, { vm.setWallpaperStyle(1) })
                        MiniChip("پرچم", settings.wallpaperStyle == 2, { vm.setWallpaperStyle(2) })
                    }
                }

                val set = if (isWall) {
                    V2Stickers.setFor(settings.wallpaperStyle)
                } else {
                    V2Stickers.setFor(settings.stickerStyle)
                }

                if (!isWall) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip("از سرور", settings.mainIndex < 0, { vm.setMainIndex(-1) })
                        MiniChip(
                            "انتخابی",
                            settings.mainIndex >= 0,
                            { vm.setMainIndex(if (settings.mainIndex >= 0) settings.mainIndex else 0) },
                        )
                    }
                }

                Text(
                    "انتخاب استیکر:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(set) { i, sticker ->
                        val selected = if (isWall) settings.wallpaperIndex == i else settings.mainIndex == i
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
                                .clickable {
                                    if (isWall) vm.setWallpaper(i) else vm.setMainIndex(i)
                                }
                                .padding(5.dp),
                        ) {
                            StickerCharacter(sticker, mood = StickerMood.NORMAL, sizeDp = 52.dp)
                        }
                    }
                }

                Text(
                    "رنگ دلخواه:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isWall) {
                        ColorDot(0xFF888888, settings.wallpaperColor < 0, { vm.setWallpaperColor(-1) })
                    } else {
                        ColorDot(0xFF888888, settings.mainColor < 0, { vm.setMainColor(-1) })
                    }
                    PALETTE.forEach { c ->
                        ColorDot(
                            c,
                            if (isWall) settings.wallpaperColor == c else settings.mainColor == c,
                            { if (isWall) vm.setWallpaperColor(c) else vm.setMainColor(c) },
                        )
                    }
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

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("بستن")
                }
            }
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
