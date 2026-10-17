package ir.v2rayng.compose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.stickers.CountryFlags
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.stickers.tinted
import ir.v2rayng.compose.ui.theme.AccentOptions

@Composable
fun SettingsScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    var editWall by remember { mutableStateOf(false) }
    var showCustomAccent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("تنظیمات", "تم، رنگ و استیکرها")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectChip("تیره", settings.dark, { vm.setDark(true) })
                    SelectChip("روشن", !settings.dark, { vm.setDark(false) })
                }
                Text("رنگ اصلی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentOptions.forEachIndexed { i, (name, color) ->
                        ColorDot(
                            color,
                            name,
                            isSel = settings.customAccent < 0 && settings.accent % AccentOptions.size == i,
                            onClick = { vm.setAccent(i) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectChip("رنگ دلخواه…", settings.customAccent >= 0, { showCustomAccent = true })
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("استیکرها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("شکل", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Blob", settings.stickerStyle == 0, { vm.setStickerStyle(0) })
                    SelectChip("عروسکی", settings.stickerStyle == 1, { vm.setStickerStyle(1) })
                    SelectChip("پرچم", settings.stickerStyle == 2, { vm.setStickerStyle(2) })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("انیمیشن", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "خاموش = حداکثر سرعت",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.animate, onCheckedChange = { vm.setAnimate(it) })
                }

                Text("سرعت استیکرها: ${"%.1f".format(settings.speed)}x", style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.speed, onValueChange = { vm.setSpeed(it) }, valueRange = 0.25f..2f)

                Text("سرعت پس‌زمینه: ${"%.1f".format(settings.bgSpeed)}x", style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.bgSpeed, onValueChange = { vm.setBgSpeed(it) }, valueRange = 0.25f..2f)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("آیکون‌های منو = استیکر", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = settings.navStickers, onCheckedChange = { vm.setNavStickers(it) })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("چشم‌ها دنبال انگشت", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "لمس/کش روی صفحه",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.followFinger, onCheckedChange = { vm.setFollowFinger(it) })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ادیتور استیکر اصلی: روی استیکر صفحه نگه‌دار",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("پس‌زمینه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("پرچم خودکار", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "وصل: پرچم کشور سرور / قطع: پرچم کشور IP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.autoFlag, onCheckedChange = { vm.setAutoFlag(it) })
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val wallName = when {
                        settings.wallpaperIndex < 0 -> "بدون پس‌زمینه"
                        settings.wallpaperStyle == 2 -> CountryFlags.list.getOrNull(settings.wallpaperIndex)?.name ?: ""
                        else -> V2Stickers.setFor(settings.wallpaperStyle).getOrNull(settings.wallpaperIndex)?.name ?: ""
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "فعلی: $wallName",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.ipCountry.isNotBlank()) {
                            Text(
                                "کشور IP: ${state.ipCountry}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SelectChip("ادیتور", false, { editWall = true })
                    SelectChip("پاک کردن", settings.wallpaperIndex < 0, { vm.setWallpaper(-1) })
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
}

@Composable
private fun CustomAccentDialog(current: Long, vm: AppViewModel, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(if (current >= 0) String.format("%08X", current) else "#FF8CDDB2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رنگ دلخواه تم") },
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
            ) { Text("اعمال") }
        },
        dismissButton = {
            TextButton(onClick = { vm.setCustomAccent(-1L); onDismiss() }) { Text(" ریست") }
        },
    )
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun ColorDot(color: Color, name: String, isSel: Boolean, onClick: () -> Unit) {
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
