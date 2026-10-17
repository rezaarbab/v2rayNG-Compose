package ir.v2rayng.compose.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.data.VpnState

private fun statusColor(s: VpnState): Color = when (s) {
    VpnState.IDLE -> Color(0xFF9BA8B4)
    VpnState.CONNECTING -> Color(0xFFFFB24A)
    VpnState.CONNECTED -> Color(0xFF2BD48A)
    VpnState.BROKEN -> Color(0xFFFF7C7C)
}

private fun statusText(s: VpnState): String = when (s) {
    VpnState.IDLE -> "قطع شده"
    VpnState.CONNECTING -> "در حال اتصال…"
    VpnState.CONNECTED -> "متصل"
    VpnState.BROKEN -> "اتصال خراب شد"
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
        ping == null -> Color(0xFF9BA8B4) to "—"
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
