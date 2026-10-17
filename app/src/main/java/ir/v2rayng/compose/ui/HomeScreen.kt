package ir.v2rayng.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.data.parseList
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

    val mainSticker = if (settings.mainIndex >= 0) {
        V2Stickers.byIndex(settings.stickerStyle, settings.mainIndex)
    } else {
        V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
    }

    val order = parseList(settings.homeOrder, listOf(0, 1, 2, 3, 4))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        order.forEach { section ->
            when (section) {
                0 -> {
                    Spacer(Modifier.height(10.dp))
                    StatusPill(state.vpnState)
                    if (settings.autoFlag && state.ipCountry.isNotBlank() && state.vpnState == VpnState.IDLE) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = S.IP_COUNTRY.format(state.ipCountry),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                1 -> {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.holdFor(
                            durationMs = 1000L,
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
                        text = S.TAP_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                2 -> {
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
                }
                3 -> {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TrafficCard(S.UPLOAD, state.traffic.upSpeed, state.traffic.up, Color(0xFFEC6E6E), connected)
                        TrafficCard(S.DOWNLOAD, state.traffic.downSpeed, state.traffic.down, Color(0xFF7FD8E8), connected)
                    }
                }
                else -> {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatChip(S.TOTAL_UP, TimeKit.human(state.traffic.up))
                        StatChip(S.TOTAL_DOWN, TimeKit.human(state.traffic.down))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (editorOpen.value) {
        StickerEditorDialog("main", settings, vm) { editorOpen.value = false }
    }
}

@Composable
private fun RowScope.TrafficCard(
    title: String,
    speed: Double,
    total: Long,
    accent: Color,
    connected: Boolean,
) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (title == S.UPLOAD) "▲" else "▼",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
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
                    text = TimeKit.human(total),
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
