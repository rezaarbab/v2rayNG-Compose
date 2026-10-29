package ir.v2rayng.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.data.StickerFx
import ir.v2rayng.compose.data.isDefault
import ir.v2rayng.compose.data.VpnState

private fun statusColor(s: VpnState): Color = when (s) {
    VpnState.IDLE -> Color(0xFF9BA8B4)
    VpnState.CONNECTING -> Color(0xFFFFB24A)
    VpnState.CONNECTED -> Color(0xFF2BD48A)
    VpnState.BROKEN -> Color(0xFFFF7C7C)
}

private fun statusText(s: VpnState): String = when (s) {
    VpnState.IDLE -> S.STATUS_IDLE
    VpnState.CONNECTING -> S.STATUS_CONNECTING
    VpnState.CONNECTED -> S.STATUS_CONNECTED
    VpnState.BROKEN -> S.STATUS_BROKEN
}

@Composable
fun StatusPill(state: VpnState, since: Long = 0L) {
    val color = statusColor(state)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            if (state == VpnState.CONNECTED && since > 0L) {
                // one small clock inside the pill: it counts wall time, so it keeps running with
                // the app closed and does not restart when the user switches tabs
                val nowMs = remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(since) {
                    while (true) {
                        nowMs.value = System.currentTimeMillis()
                        kotlinx.coroutines.delay(1000L)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ir.v2rayng.compose.data.TimeKit.clock(nowMs.value - since),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
            }
        }
    }
}

@Composable
fun PingChip(ping: Int?, err: String? = null) {
    val (color, text) = when {
        // tcp fallback = server answers syn (maybe DPI middlebox) - never show it as healthy green
        ping != null && err == "tcp" -> Color(0xFF9BA8B4) to "tcp $ping ms"
        ping != null && ping <= 150 -> Color(0xFF2BD48A) to "$ping ms"
        ping != null && ping <= 300 -> Color(0xFFFFB24A) to "$ping ms"
        ping != null -> Color(0xFFFF7C7C) to "$ping ms"
        !err.isNullOrBlank() -> Color(0xFFFF7C7C) to err
        else -> Color(0xFF9BA8B4) to "-"
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String, subtitle: String = "") {
    androidx.compose.foundation.layout.Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Applies per-sticker transparency / blur / stretch on top of any modifier chain. */
fun Modifier.stickerFx(fx: StickerFx): Modifier {
    if (fx.isDefault()) return this
    var m = this
    if (fx.blur > 0.05f) m = m.blur(fx.blur.dp)
    m = m.then(
        Modifier.graphicsLayer {
            scaleX = fx.scale
            scaleY = fx.scale
            alpha = fx.alpha
        },
    )
    return m
}

fun Modifier.holdFor(
    durationMs: Long,
    onTap: (() -> Unit)? = null,
    onHold: () -> Unit,
): Modifier = this.pointerInput(durationMs) {
    kotlinx.coroutines.coroutineScope {
        while (true) {
            awaitPointerEventScope {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startX = down.position.x
                val startY = down.position.y
                val startT = down.uptimeMillis
                val holdJob = launch {
                    delay(durationMs)
                    onHold()
                }
                var movedFar = false
                var released = false
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: continue
                    val dx = c.position.x - startX
                    val dy = c.position.y - startY
                    if (dx * dx + dy * dy > 3600f) movedFar = true
                    if (movedFar) break
                    if (!c.pressed) {
                        released = true
                        break
                    }
                    if (c.uptimeMillis - startT >= durationMs) {
                        holdJob.cancel()
                        onHold()
                        e.changes.forEach { it.consume() }
                        break
                    }
                }
                holdJob.cancel()
                if (released && !movedFar && onTap != null) onTap()
            }
        }
    }
}

/** Hold-any-icon-button tooltip: long-press shows a small label bubble right under the button. */
@Composable
fun TipIconButton(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    var tip by remember { mutableStateOf(false) }
    var popW by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val btnPx = with(density) { 48.dp.roundToPx() }
    Box {
        IconButton(onClick = onClick, modifier = Modifier.holdFor(600L, onHold = { tip = true })) {
            Icon(icon, contentDescription = label, tint = tint ?: LocalContentColor.current)
        }
        if (tip) {
            LaunchedEffect(tip) {
                delay(1700L)
                tip = false
            }
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(
                    (btnPx - popW) / 2,
                    btnPx + with(density) { 8.dp.roundToPx() },
                ),
            ) {
                Surface(
                    color = Color(0xFF141A23).copy(alpha = 0.96f),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9BF3CB),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .onSizeChanged { popW = it.width },
                    )
                }
            }
        }
    }
}
