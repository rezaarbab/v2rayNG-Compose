package ir.v2rayng.compose.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.stickers.CountryFlags
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.stickers.tinted

@Composable
fun HomeScreen(state: AppState, settings: Settings, vm: AppViewModel, gaze: Offset? = null) {
    val sel = state.selected
    val mood = if (settings.stateMotion) {
        when (state.vpnState) {
            VpnState.IDLE -> StickerMood.NORMAL
            VpnState.CONNECTING -> StickerMood.WOBBLE
            VpnState.CONNECTED -> StickerMood.HAPPY
            VpnState.BROKEN -> StickerMood.SAD
        }
    } else {
        StickerMood.NORMAL
    }
    val connected = state.vpnState == VpnState.CONNECTED
    val editorOpen = remember { mutableStateOf(false) }
    val editZone = remember { mutableStateOf<String?>(null) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (vm.needsVpnConsent() == null) {
            vm.toggleVpn()
        }
    }
    val proceedAfterNotif = {
        val intent = vm.needsVpnConsent()
        if (intent != null) {
            consentLauncher.launch(intent)
        } else {
            vm.toggleVpn()
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        proceedAfterNotif()
    }
    fun connectOrConsent() {
        val ctx = ir.v2rayng.compose.AppCtx.app
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        proceedAfterNotif()
    }

    val mainSticker = when {
        settings.connectFlagDancer && sel.country.isNotBlank() -> {
            val fi = CountryFlags.byCode(sel.country)
            if (fi >= 0) CountryFlags.list[fi] else V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
        }
        settings.mainIndex >= 0 -> V2Stickers.byIndex(settings.stickerStyle, settings.mainIndex)
        else -> V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
    }

    val order = parseList(settings.homeOrder, listOf(0, 1, 2, 3)).take(4)

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
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val flagCode = if (state.vpnState == VpnState.IDLE) state.ipCountry else state.ipCountry
                        val fi = if (flagCode.isNotBlank()) ir.v2rayng.compose.stickers.CountryFlags.byCode(flagCode) else -1
                        if (fi >= 0) {
                            StickerCharacter(
                                ir.v2rayng.compose.stickers.CountryFlags.list[fi],
                                sizeDp = 26.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = if (state.myIp.isBlank()) {
                                flagCode.ifBlank { S.MY_IP }
                            } else {
                                "${S.MY_IP}: ${state.myIp}${if (flagCode.isNotBlank()) " ($flagCode)" else ""}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.TextButton(
                            onClick = { vm.checkMyIp() },
                        ) { Text(S.MY_IP, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                1 -> {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.holdFor(
                            durationMs = 1000L,
                            onTap = { connectOrConsent() },
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
                            val selAvatar = if (settings.flagAvatars && sel.country.isNotBlank()) {
                                val fi = CountryFlags.byCode(sel.country)
                                if (fi >= 0) CountryFlags.list[fi] else V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
                            } else {
                                V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
                            }
                            StickerCharacter(tinted(selAvatar, settings.mainColor), mood = if (connected && settings.stateMotion) StickerMood.HAPPY else StickerMood.NORMAL, sizeDp = 34.dp)
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
                        TrafficCard(S.UPLOAD, state.traffic.upSpeed, state.traffic.up, Color(0xFFEC6E6E), settings.upIcon, settings.upPack, settings.stickerStyle, settings.upColor, settings.upSpeed, { editZone.value = "up" }, settings.navStickers)
                        TrafficCard(S.DOWNLOAD, state.traffic.downSpeed, state.traffic.down, Color(0xFF7FD8E8), settings.downIcon, settings.downPack, settings.stickerStyle, settings.downColor, settings.downSpeed, { editZone.value = "down" }, settings.navStickers)
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { vm.resetTotalTraffic() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset traffic", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

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
    accent: Color,
    iconIndex: Int,
    iconPack: Int,
    fallbackStyle: Int,
    iconColor: Long,
    iconSpeed: Float,
    onIconHold: () -> Unit,
    stickers: Boolean,
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
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .holdFor(1000L, onHold = onIconHold),
                contentAlignment = Alignment.Center,
            ) {
                if (stickers && iconIndex >= 0) {
                    StickerCharacter(
                        sticker = tinted(
                            V2Stickers.byPack(iconPack, iconIndex, fallbackStyle, if (title == S.UPLOAD) 5 else 6),
                            iconColor,
                        ),
                        sizeDp = 30.dp,
                        speedScale = iconSpeed,
                    )
                } else {
                    TrafficArrow(up = title == S.UPLOAD, accent = accent)
                }
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
private fun TrafficArrow(up: Boolean, accent: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = size.width * 0.09f
        val tip = Offset(size.width * 0.5f, size.height * (if (up) 0.20f else 0.80f))
        val tail = Offset(size.width * 0.5f, size.height * (if (up) 0.80f else 0.20f))
        val h = size.height * (if (up) 0.40f else 0.60f)
        drawLine(
            accent, tail, tip,
            strokeWidth = sw, cap = StrokeCap.Round,
        )
        drawLine(
            accent, tip, Offset(size.width * 0.28f, h),
            strokeWidth = sw, cap = StrokeCap.Round,
        )
        drawLine(
            accent, tip, Offset(size.width * 0.72f, h),
            strokeWidth = sw, cap = StrokeCap.Round,
        )
    }
}

