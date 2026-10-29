package ir.v2rayng.compose.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ir.v2rayng.compose.MainActivity

/**
 * The few things worth interrupting the user for, delivered where they will actually see them
 * instead of inside a log page: the last run ended in a crash, the tunnel died by itself, or a
 * settings write came back different. Its own high-importance channel, so it never mixes with the
 * silent ongoing "VPN running" notification.
 */
object NotifyKit {

    private const val CHANNEL = "blob_alerts"
    private var seq = 2000

    fun alarm(context: Context, title: String, body: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "هشدارهای Blob", NotificationManager.IMPORTANCE_HIGH),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n: Notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(body.take(120))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(600)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm.notify(seq++, n)
        } catch (_: Exception) {
        }
    }
}
