package ir.v2rayng.compose.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.core.Remote

internal fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

@Composable
fun RemotePopupDialog(p: Remote.PopupCfg, vm: AppViewModel) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (p.cancelEnabled) vm.ackPopup(p.id, p.nonce) },
        title = { Text(p.title, fontWeight = FontWeight.Bold) },
        text = { Text(p.body) },
        confirmButton = {
            if (p.btnEnabled && p.btnText.isNotBlank()) {
                TextButton(onClick = {
                    if (p.btnUrl.isNotBlank()) openUrl(ctx, p.btnUrl)
                    vm.ackPopup(p.id, p.nonce)
                }) { Text(p.btnText) }
            } else {
                TextButton(onClick = { vm.ackPopup(p.id, p.nonce) }) { Text(S.POPUP_BTN_CLOSE) }
            }
        },
        dismissButton = {
            if (p.cancelEnabled) {
                TextButton(onClick = { vm.ackPopup(p.id, p.nonce) }) { Text(S.CANCEL) }
            }
        },
    )
}

@Composable
fun RemoteUpdateDialog(u: Remote.UpdateCfg, vm: AppViewModel) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!u.forced) vm.dismissUpdateDialog() },
        title = {
            Text(
                if (u.forced) S.UPDATE_FORCED else S.UPDATE_OPTIONAL,
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(u.text) },
        confirmButton = {
            TextButton(onClick = {
                if (u.url.isNotBlank()) openUrl(ctx, u.url)
                if (!u.forced) vm.dismissUpdateDialog()
            }) { Text(u.btnText) }
        },
        dismissButton = {
            if (!u.forced) {
                TextButton(onClick = { vm.dismissUpdateDialog() }) { Text(S.CANCEL) }
            }
        },
    )
}

@Composable
fun RemoteNotifDialog(n: Remote.NotifLine, vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.ackNotif(n.id) },
        title = { Text(if (n.title.isNotBlank()) n.title else S.NOTIF_TITLE_, fontWeight = FontWeight.Bold) },
        text = { Text(n.body) },
        confirmButton = {
            TextButton(onClick = { vm.ackNotif(n.id) }) { Text(S.POPUP_BTN_CLOSE) }
        },
    )
}

@Composable
fun AppDisabledScreen(vm: AppViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(S.APP_DISABLED, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.syncNow() }) { Text(S.REMOTE_SYNC) }
        }
    }
}
