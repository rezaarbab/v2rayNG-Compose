package ir.v2rayng.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.V2Stickers

private data class RouteUi(
    val key: String,
    val title: String,
    val sub: String,
)

@Composable
fun RoutingScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    val rows = listOf(
        RouteUi("bypass_ir", S.RULE_BYPASS, S.RULE_BYPASS_SUB),
        RouteUi("block_ads", S.RULE_ADS, S.RULE_ADS_SUB),
        RouteUi("udp443", S.RULE_UDP, S.RULE_UDP_SUB),
    )
    val routeIcons = parseList(settings.routeIcons, listOf(0, 1, 2))
    var editZone by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(S.ROUTING_TITLE, S.HOLD_HINT)
        Spacer(Modifier.height(4.dp))

        rows.forEachIndexed { i, r ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .holdFor(1000L, onHold = { editZone = "route:$i" }),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (settings.navStickers) {
                        StickerCharacter(V2Stickers.byIndex(settings.stickerStyle, routeIcons[i]), sizeDp = 42.dp)
                    } else {
                        RouteVectorIcon(kind = r.key)
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = r.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = r.sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.routes[r.key] == true,
                        onCheckedChange = { vm.setRoute(r.key, it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = S.ROUTING_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editZone?.let { zone ->
        StickerEditorDialog(zone, settings, vm) { editZone = null }
    }
}

@Composable
private fun RouteVectorIcon(kind: String) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp)) {
        val sw = size.width * 0.10f
        when (kind) {
            "bypass_ir" -> {
                val y = size.height * 0.5f
                drawLine(accent, Offset(size.width * 0.12f, y), Offset(size.width * 0.74f, y), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * 0.52f, size.height * 0.22f), Offset(size.width * 0.78f, y), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(accent, Offset(size.width * 0.52f, size.height * 0.78f), Offset(size.width * 0.78f, y), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "block_ads" -> {
                drawCircle(accent, center = Offset(size.width * 0.5f, size.height * 0.5f), radius = size.width * 0.40f, style = Stroke(width = sw, cap = StrokeCap.Round))
                drawLine(accent, Offset(size.width * 0.19f, size.height * 0.19f), Offset(size.width * 0.81f, size.height * 0.81f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            else -> {
                val pts = listOf(
                    Offset(size.width * 0.58f, size.height * 0.06f),
                    Offset(size.width * 0.28f, size.height * 0.52f),
                    Offset(size.width * 0.47f, size.height * 0.52f),
                    Offset(size.width * 0.38f, size.height * 0.94f),
                    Offset(size.width * 0.74f, size.height * 0.42f),
                    Offset(size.width * 0.55f, size.height * 0.42f),
                    Offset(size.width * 0.68f, size.height * 0.06f),
                )
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                path.close()
                drawPath(path, accent)
            }
        }
    }
}

