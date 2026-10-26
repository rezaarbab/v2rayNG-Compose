package ir.v2rayng.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.core.Remote
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.Settings

/** The whole page is written by the admin panel: heading, description, main button, privacy note
 *  and the ad slots. Nothing here is decided on the phone. */
@Composable
fun RoutingScreen(state: AppState, settings: Settings, vm: AppViewModel) {
    val ctx = LocalContext.current
    val r = state.routing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (r == null) {
            SectionTitle(S.ROUTING_TITLE, S.ROUTING_SUB)
            Spacer(Modifier.height(4.dp))
            Text(
                text = S.ROUTING_EMPTY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        SectionTitle(r.title.ifBlank { S.ROUTING_TITLE }, r.desc)

        if (r.btnText.isNotBlank()) {
            Button(
                onClick = { if (r.btnUrl.isNotBlank()) openUrl(ctx, r.btnUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(r.btnText) }
        }

        if (r.privacy.isNotBlank()) {
            InfoCard(r.privacyTitle.ifBlank { S.ROUTING_PRIVACY }, r.privacy)
        }

        r.ads.forEach { ad ->
            AdCard(ad, onOpen = { if (ad.url.isNotBlank()) openUrl(ctx, ad.url) })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdCard(ad: Remote.RoutingAd, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ad.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ad.url.isNotBlank()) {
                TextButton(onClick = onOpen) { Text(S.ROUTING_OPEN) }
            }
        }
    }
}
