package ir.v2rayng.compose.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.ui.theme.StatusColors
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.StickerFx
import ir.v2rayng.compose.data.TimeKit
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.data.parseFxPack
import ir.v2rayng.compose.stickers.CountryFlags
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.stickers.tinted

@Composable
fun HomeScreen(state: AppState, settings: Settings, vm: AppViewModel, gaze: Offset? = null, onGoServers: () -> Unit = {}) {
    val sel = state.selected
    val mood = if (settings.stateMotion && settings.motionGlobal) {
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
        if (state.servers.isEmpty()) {
            Toast.makeText(ctx, "Select a server first", Toast.LENGTH_SHORT).show()
            onGoServers()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        proceedAfterNotif()
    }

    val mainSticker = when {
        settings.mainIndex >= 0 -> V2Stickers.byPack(settings.mainPack, settings.mainIndex, settings.stickerStyle, 0)
        settings.connectFlagDancer && sel.country.isNotBlank() -> {
            val fi = CountryFlags.byCode(sel.country)
            if (fi >= 0) CountryFlags.list[fi] else V2Stickers.byPack(settings.mainPack, settings.mainIndex, settings.stickerStyle, sel.stickerIndex)
        }
        else -> V2Stickers.byIndex(settings.stickerStyle, sel.stickerIndex)
    }

    val order = parseList(settings.homeOrder, listOf(0, 1, 2, 3)).take(4)
    val motion = settings.motionGlobal && settings.animate

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
                    Spacer(Modifier.height(12.dp))
                    StatusPill(state.vpnState, animate = motion)
                    Spacer(Modifier.height(6.dp))
                    SessionTimer(state.vpnState)
                    Spacer(Modifier.height(4.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val flagCode = state.ipCountry.uppercase()
                        val altCode = state.altCountry.uppercase()
                        val disagree = state.altCountry.isNotBlank() && altCode != flagCode && state.altIp.isNotBlank()
                        IpRow(flagCode, state.myIp, { vm.checkMyIp() }, "CF")
                        if (disagree) {
                            Spacer(Modifier.height(4.dp))
                            IpRow(altCode, state.altIp, { vm.checkMyIp() }, "WHO")
                        }
                    }
                }
                1 -> {
                    Spacer(Modifier.height(8.dp))
                    val stickerSize = if (settings.mainSize > 0) settings.mainSize.dp else 190.dp
                    val haloSize = (stickerSize * 1.4f).coerceAtMost(340.dp).coerceAtLeast(stickerSize + 24.dp)
                    ConnectHalo(state = state.vpnState, size = haloSize, animate = motion) {
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
                                sizeDp = stickerSize,
                                gaze = gaze,
                                modifier = Modifier.stickerFx(parseFxPack(settings.mainFx)),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ConnectButton(state.vpnState) { connectOrConsent() }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = S.TAP_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                2 -> {
                    Spacer(Modifier.height(20.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onGoServers() },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                StickerCharacter(tinted(selAvatar, settings.mainColor), mood = if (connected && settings.stateMotion && settings.motionGlobal) StickerMood.HAPPY else StickerMood.NORMAL, sizeDp = 34.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = S.CURRENT_SERVER,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (state.servers.isEmpty()) S.NO_SERVER else sel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (state.servers.isNotEmpty()) {
                                    Text(
                                        text = sel.host,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                PingChip(sel.ping, sel.pingErr)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = S.CHANGE_SERVER,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TrafficCard(S.UPLOAD, state.traffic.upSpeed, state.traffic.up, StatusColors.Up, settings.upIcon, settings.upPack, settings.stickerStyle, settings.upColor, settings.upSpeed, { editZone.value = "up" }, settings.navStickers, parseFxPack(settings.upFx))
                        TrafficCard(S.DOWNLOAD, state.traffic.downSpeed, state.traffic.down, StatusColors.Down, settings.downIcon, settings.downPack, settings.stickerStyle, settings.downColor, settings.downSpeed, { editZone.value = "down" }, settings.navStickers, parseFxPack(settings.downFx))
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { vm.resetTotalTraffic() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(S.RESET_TRAFFIC_HOME, style = MaterialTheme.typography.labelMedium)
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
    fx: StickerFx = StickerFx(),
) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)),
                ),
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
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
                        modifier = Modifier.stickerFx(fx),
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
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = TimeKit.speed(speed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = TimeKit.human(total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
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



@Composable
private fun IpRow(flagCode: String, ip: String, onCheck: () -> Unit, prefix: String = "CF") {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onCheck() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        ) {
            val fi = if (flagCode.isNotBlank()) ir.v2rayng.compose.stickers.CountryFlags.byCode(flagCode) else -1
            if (fi >= 0) {
                StickerCharacter(
                    ir.v2rayng.compose.stickers.CountryFlags.list[fi],
                    sizeDp = 22.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = prefix,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (ip.isBlank()) {
                    flagCode.ifBlank { S.MY_IP }
                } else {
                    ip.plus(if (flagCode.isNotBlank()) "  ·  $flagCode" else "")
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = if (ip.isBlank()) FontFamily.Default else FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Refresh,
                contentDescription = S.MY_IP,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
