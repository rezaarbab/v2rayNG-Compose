package ir.v2rayng.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.stickers.Sticker
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.V2Stickers

private data class RouteUi(
    val key: String,
    val title: String,
    val sub: String,
    val stickerIndex: Int,
)

@Composable
fun RoutingScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    val rows = listOf(
        RouteUi("bypass_ir", "دور زدن ایران", "ترافیک داخلی مستقیم می‌رود", 0),
        RouteUi("block_ads", "مسدودسازی تبلیغات", "GeoSite ads حذف می‌شود", 1),
        RouteUi("udp443", "پذیرش UDP 443", "برای QUIC و بازی‌ها", 2),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("مسیریابی", "قوانین مسیر ترافیک")
        Spacer(Modifier.height(4.dp))

        rows.forEach { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    StickerCharacter(V2Stickers.byIndex(settings.stickerStyle, r.stickerIndex), sizeDp = 42.dp)
                    Spacer(Modifier.height(4.dp))
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
            text = "قوانین روی پروفایل انتخاب‌شده اعمال می‌شوند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun unusedSticker(): Sticker = V2Stickers.setFor(0).first()
