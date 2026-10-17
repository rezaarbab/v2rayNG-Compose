package ir.v2rayng.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.stickers.Sticker
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.stickers.tinted

@Composable
fun HomeScreen(state: AppState, settings: Settings, vm: AppViewModel, gaze: Offset? = null) {
    val sel = state.selected
    val mood = when (state.vpnState) {
        VpnState.IDLE -> StickerMood.NORMAL
        VpnState.CONNECTING -> StickerMood.WOBBLE
        VpnState.CONNECTED -> StickerMood.HAPPY
        VpnState.BROKEN -> StickerMood.SLEEP
    }
    val connected = state.vpnState == VpnState.CONNECTED
    val editorOpen = remember { mutableStateOf(false) }
    val editZone = remember { mutableStateOf<String?>(null) }

    val mainSticker = if (settings.mainIndex >= 0) {
        V2Stickers.byIndex(settings.stickerStyle, settings.mainIndex)
    } else {
        V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        StatusPill(state.vpnState)
        if (settings.autoFlag && state.ipCountry.isNotBlank() && state.vpnState == VpnState.IDLE) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "کشور IP: ${state.ipCountry}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.holdFor(
                durationMs = 5000L,
                onTap = { vm.toggleVpn() },
                onHold = { editorOpen.value = true },
            )
        ) {
            StickerCharacter(
                sticker = tinted(mainSticker, settings.mainColor),
                mood = mood,
                sizeDp = if (settings.mainSize > 0) settings.mainSize.dp else 190.dp,
                gaze = gaze,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "لمس = اتصال/قطع • نگه‌داشتن = سفارشی‌سازی",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                StickerCharacter(tinted(mainSticker, settings.mainColor), sizeDp = 34.dp, decorative = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = sel.host,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PingChip(sel.ping)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrafficCard("خروجی", state.traffic.upSpeed, state.traffic.up, V2Stickers.byIndex(settings.stickerStyle, settings.upIcon), connected, onHold = { editZone.value = "up" })
            TrafficCard("ورودی", state.traffic.downSpeed, state.traffic.down, V2Stickers.byIndex(settings.stickerStyle, settings.downIcon), connected, onHold = { editZone.value = "down" })
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatChip("کل خروجی", TimeKit.human(state.traffic.up))
            StatChip("کل ورودی", TimeKit.human(state.traffic.down))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "نگه‌داشتن ۵ ثانیه روی هر استیکر: ادیتور و جابجایی",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
    }

    if (editorOpen.value) {
        StickerEditorDialog("main", settings, vm) { editorOpen.value = false }
    }
    editZone.value?.let { zone ->
        StickerEditorDialog(zone, settings, vm) { editZone.value = null }
    }
}

@Composable
private fun RowScope.TrafficCard(
    title: String,
    speed: Double,
    total: Long,
    sticker: Sticker,
    connected: Boolean,
    onHold: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .holdFor(5000L, onHold = onHold),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            StickerCharacter(sticker, mood = if (connected) StickerMood.HAPPY else StickerMood.SLEEP, sizeDp = 30.dp, decorative = true)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = TimeKit.speed(speed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "کل: ${TimeKit.human(total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatChip(title: String, value: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
