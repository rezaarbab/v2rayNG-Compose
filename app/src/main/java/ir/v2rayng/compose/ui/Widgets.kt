package ir.v2rayng.compose.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.data.StickerFx
import ir.v2rayng.compose.data.isDefault
import ir.v2rayng.compose.data.VpnState
import ir.v2rayng.compose.ui.theme.StatusColors
import java.util.Locale

internal fun statusColor(s: VpnState): Color = when (s) {
    VpnState.IDLE -> StatusColors.Idle
    VpnState.CONNECTING -> StatusColors.Connecting
    VpnState.CONNECTED -> StatusColors.Connected
    VpnState.BROKEN -> StatusColors.Error
}

internal fun statusText(s: VpnState): String = when (s) {
    VpnState.IDLE -> S.STATUS_IDLE
    VpnState.CONNECTING -> S.STATUS_CONNECTING
    VpnState.CONNECTED -> S.STATUS_CONNECTED
    VpnState.BROKEN -> S.STATUS_BROKEN
}

/**
 * Wall-clock moment the tunnel was first seen CONNECTED in this process; 0 while not connected.
 * Kept outside the composition so switching tabs does not restart the session timer.
 */
object ConnClock {
    val since = mutableLongStateOf(0L)

    fun observe(s: VpnState) {
        if (s == VpnState.CONNECTED) {
            if (since.longValue == 0L) since.longValue = System.currentTimeMillis()
        } else {
            since.longValue = 0L
        }
    }

    fun format(ms: Long): String {
        val t = (ms / 1000L).coerceAtLeast(0L)
        val h = t / 3600L
        val m = (t % 3600L) / 60L
        val s = t % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}

@Composable
fun StatusPill(state: VpnState, animate: Boolean = true) {
    val color by animateColorAsState(statusColor(state), tween(400), label = "pill")
    val pulse = if (animate && (state == VpnState.CONNECTING || state == VpnState.CONNECTED)) {
        val tr = rememberInfiniteTransition(label = "pillDot")
        val a by tr.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(if (state == VpnState.CONNECTING) 520 else 1400), RepeatMode.Reverse),
            label = "pillDotA",
        )
        a
    } else {
        1f
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = pulse }
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

/** Live hh:mm:ss of the current session; renders nothing while not connected. */
@Composable
fun SessionTimer(state: VpnState) {
    val since = ConnClock.since.longValue
    if (state != VpnState.CONNECTED || since == 0L) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    Text(
        text = ConnClock.format(now - since),
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/**
 * Soft glow + ring drawn behind the connect sticker. The ring spins while connecting and
 * breathes while connected; with motion switched off it is drawn once, statically.
 */
@Composable
fun ConnectHalo(
    state: VpnState,
    size: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val color by animateColorAsState(statusColor(state), tween(500), label = "halo")
    val spin: Float
    val breath: Float
    if (animate && state != VpnState.IDLE) {
        val tr = rememberInfiniteTransition(label = "haloT")
        val s by tr.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "haloSpin",
        )
        val b by tr.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "haloBreath",
        )
        spin = s
        breath = b
    } else {
        spin = 0f
        breath = 0f
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2f
            val glowA = when (state) {
                VpnState.CONNECTED -> 0.30f
                VpnState.CONNECTING -> 0.22f
                VpnState.BROKEN -> 0.20f
                VpnState.IDLE -> 0.10f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = glowA), color.copy(alpha = glowA * 0.35f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
            val ringR = r * 0.80f
            val sw = r * 0.022f
            drawCircle(color = color.copy(alpha = 0.22f), radius = ringR, style = Stroke(width = sw))
            when (state) {
                VpnState.CONNECTING -> {
                    rotate(spin) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, color, color)),
                            startAngle = 0f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringR, center.y - ringR),
                            size = Size(ringR * 2f, ringR * 2f),
                            style = Stroke(width = sw * 2.2f, cap = StrokeCap.Round),
                        )
                    }
                }
                VpnState.CONNECTED -> {
                    drawCircle(color = color.copy(alpha = 0.85f), radius = ringR, style = Stroke(width = sw * 1.6f))
                    if (breath > 0f) {
                        drawCircle(
                            color = color.copy(alpha = 0.40f * (1f - breath)),
                            radius = ringR + (r - ringR) * breath,
                            style = Stroke(width = sw * 1.2f),
                        )
                    }
                }
                VpnState.BROKEN -> {
                    drawCircle(color = color.copy(alpha = 0.6f), radius = ringR, style = Stroke(width = sw * 1.4f))
                }
                VpnState.IDLE -> Unit
            }
        }
        content()
    }
}

/** Explicit connect / disconnect pill under the sticker - the sticker tap still works too. */
@Composable
fun ConnectButton(state: VpnState, onClick: () -> Unit) {
    val target = when (state) {
        VpnState.CONNECTED -> StatusColors.Error
        VpnState.CONNECTING -> StatusColors.Connecting
        else -> MaterialTheme.colorScheme.primary
    }
    val bg by animateColorAsState(target, tween(350), label = "btn")
    val fg = if (bg.luminance() >= 0.55f) Color(0xFF0B1A12) else Color.White
    val label = when (state) {
        VpnState.CONNECTED -> S.DISCONNECT
        VpnState.CONNECTING -> S.CANCEL_CONNECT
        VpnState.BROKEN -> S.RETRY
        VpnState.IDLE -> S.CONNECT
    }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        modifier = Modifier.widthIn(min = 200.dp),
    ) {
        Icon(
            imageVector = if (state == VpnState.CONNECTED || state == VpnState.CONNECTING) Icons.Filled.Close else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PingChip(ping: Int?, err: String? = null) {
    val (color, text) = when {
        // tcp fallback = server answers syn (maybe DPI middlebox) - never show it as healthy green
        ping != null && err == "tcp" -> StatusColors.Idle to "tcp $ping ms"
        ping != null && ping <= 150 -> StatusColors.Connected to "$ping ms"
        ping != null && ping <= 300 -> StatusColors.Connecting to "$ping ms"
        ping != null -> StatusColors.Error to "$ping ms"
        !err.isNullOrBlank() -> StatusColors.Error to err
        else -> StatusColors.Idle to "-"
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (ping != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
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
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .onSizeChanged { popW = it.width },
                    )
                }
            }
        }
    }
}
