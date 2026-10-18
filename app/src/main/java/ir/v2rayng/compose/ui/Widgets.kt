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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.data.StickerFx
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
fun StatusPill(state: VpnState) {
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
        }
    }
}

@Composable
fun PingChip(ping: Int?) {
    val (color, text) = when {
        ping == null -> Color(0xFF9BA8B4) to "-"
        ping <= 150 -> Color(0xFF2BD48A) to "$ping ms"
        ping <= 300 -> Color(0xFFFFB24A) to "$ping ms"
        else -> Color(0xFFFF7C7C) to "$ping ms"
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
