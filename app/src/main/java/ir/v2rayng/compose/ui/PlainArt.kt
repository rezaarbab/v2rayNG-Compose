package ir.v2rayng.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ir.v2rayng.compose.stickers.BlobArt
import ir.v2rayng.compose.stickers.FlagArt
import ir.v2rayng.compose.stickers.ImageArt
import ir.v2rayng.compose.stickers.Sticker
import ir.v2rayng.compose.stickers.ToyArt

/* Plain mode: the app is a client, not a sticker book. Every place that would draw a sticker draws
 * one of these instead - a real icon, not the same blob with the face taken off. A country flag is
 * the one exception: it is drawn as a flag (StickerCharacter, flat), because the flag is the data
 * the row is about. */

/** The single colour a sticker's art carries, so two servers stay distinguishable. Flags have no
 *  one colour - the caller draws the flag instead of asking. */
fun plainTint(sticker: Sticker): Color? = when (val a = sticker.art) {
    is BlobArt -> a.color
    is ToyArt -> a.color
    is ImageArt -> a.color
    is FlagArt -> null
}

@Composable
fun PlainServerIcon(sticker: Sticker, sizeDp: Dp) {
    val tint = plainTint(sticker) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(sizeDp * 0.78f),
        )
    }
}

/** The connect control, drawn the way every other client draws it: a round button with a power
 *  glyph. The ring is the tunnel, the bar is the switch. */
@Composable
fun PlainConnectButton(sizeDp: Dp, tint: Color) {
    val plate = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.size(sizeDp)) {
        val r = size.minDimension / 2f
        drawCircle(plate, radius = r, center = center)
        val ring = r * 0.34f
        val stroke = r * 0.075f
        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(center.x - ring, center.y - ring),
            size = Size(ring * 2f, ring * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(center.x, center.y - ring * 1.30f),
            end = Offset(center.x, center.y - ring * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
