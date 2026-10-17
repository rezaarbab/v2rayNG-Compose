package ir.v2rayng.compose.core

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import ir.v2rayng.compose.data.SettingsStore
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class XrayVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "ir.blob.vpn.CONNECT"
        const val ACTION_STOP = "ir.blob.vpn.STOP"
        private const val EXTRA_LINK = "link"
        var pendingLink: VlessLink? = null

        fun start(context: android.content.Context, link: VlessLink, raw: String) {
            pendingLink = link
            context.startService(
                Intent(context, XrayVpnService::class.java)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_LINK, raw),
            )
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, XrayVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private var controller: CoreController? = null
    private var tunFd = -1
    private var lastUp = 0L
    private var lastDown = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null) {
                try {
                    if (XrayBridge.takeCounterReset()) {
                        lastUp = 0
                        lastDown = 0
                        zeroStreak = 0
                        XrayBridge.setTraffic(0, 0, 0.0, 0.0)
                    }
                    val raw = c.queryAllOutboundTrafficStats()
                    val d = parseTrafficDeltas(raw)
                    if (d.up > 0 || d.down > 0) {
                        lastUp += d.up
                        lastDown += d.down
                        XrayBridge.setTraffic(lastUp, lastDown, d.up.toDouble(), d.down.toDouble())
                    } else {
                        zeroStreak++
                        if (zeroStreak == 8 && c.getIsRunning()) {
                            XrayBridge.addLog("CORE", "stats empty after 8s; isRunning=${c.getIsRunning()}")
                        }
                    }
                } catch (_: Exception) {
                }
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }
    private var zeroStreak = 0
    private val stateSetter = Runnable { XrayBridge.setState(RealState.CONNECTED) }

    private val callback = object : CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            XrayBridge.addLog("CORE", p1 ?: "")
            mainHandler.removeCallbacks(stateSetter)
            mainHandler.postDelayed(stateSetter, 900L)
            return 0L
        }

        override fun shutdown(): Long {
            XrayBridge.addLog("CORE", "Core shutdown")
            XrayBridge.setState(RealState.IDLE)
            return 0L
        }

        override fun startup(): Long {
            XrayBridge.addLog("CORE", "Core started")
            XrayBridge.setState(RealState.CONNECTED)
            return 0L
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCore()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val raw = intent?.getStringExtra(EXTRA_LINK)
                val link = if (raw != null) parseVlessLink(raw) else null
                val effective = link ?: pendingLink
                if (effective == null) {
                    stopForegroundCompat()
                    stopSelf()
                    return START_NOT_STICKY
                }
                stopCore()
                startForegroundCompat(effective)
                startCore(effective)
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun stickerBitmap(colorHex: Long, dark: Boolean, show: Boolean): Bitmap? {
        if (!show) return null
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = if (colorHex >= 0) colorHex.toInt() else if (dark) 0xFF7FD8E8.toInt() else 0xFF2C5BD6.toInt()
        c.drawCircle(size / 2f, size / 2f, size * 0.34f, paint)
        paint.color = 0x2EFFFFFF.toInt()
        c.drawCircle(size * 0.40f, size * 0.36f, size * 0.11f, paint)
        paint.color = 0xFF241E1E.toInt()
        val hw = size * 0.028f
        val hh = size * 0.062f
        for (ex in listOf(0.40f, 0.60f)) {
            c.drawRoundRect(
                android.graphics.RectF(
                    size * ex - hw, size * 0.47f - hh,
                    size * ex + hw, size * 0.47f + hh,
                ),
                hw, hw, paint,
            )
        }
        paint.color = 0x99FFFFFF.toInt()
        c.drawCircle(size * 0.373f, size * 0.44f, size * 0.012f, paint)
        c.drawCircle(size * 0.573f, size * 0.44f, size * 0.012f, paint)
        return bmp
    }

    private fun startForegroundCompat(link: VlessLink) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = android.app.NotificationChannel("blob_vpn", "Blob VPN", android.app.NotificationManager.IMPORTANCE_LOW)
            manager?.createNotificationChannel(channel)
        }
        val settings = SettingsStore(applicationContext).load()
        val big = stickerBitmap(
            colorHex = settings.mainColor,
            dark = settings.dark,
            show = settings.showNotification,
        )
        val builder = NotificationCompat.Builder(this, "blob_vpn")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Blob")
            .setOngoing(true)
        big?.let {
            builder.setLargeIcon(it)
            builder.setColor(it.getPixel(128, 128))
        }
        builder.setContentText(link.name.ifBlank { "Connected - ${link.address}" })
        val stopIntent = android.app.PendingIntent.getService(
            this,
            7,
            Intent(this, XrayVpnService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(0, "Disconnect", stopIntent)
        androidx.core.app.ServiceCompat.startForeground(this, 1001, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun startCore(link: VlessLink) {
        try {
            Libv2ray.initCoreEnv(File(filesDir, "core").apply { mkdirs() }.absolutePath, "")
            XrayBridge.addLog("INFO", "Starting core: ${link.address}:${link.port} (${link.type}/${link.security})")
            XrayBridge.setState(RealState.CONNECTING)
            val builder = Builder()
                .setSession("Blob")
                .setMtu(1500)
                .addAddress("10.9.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
            var excluded = 1
            try {
                val anti = SettingsStore(applicationContext).load().antiFilterApps
                for (pkg in anti.split(",").map { it.trim() }.filter { it.isNotBlank() }) {
                    try {
                        builder.addDisallowedApplication(pkg)
                        excluded++
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            XrayBridge.addLog("INFO", "Apps excluded from tunnel: $excluded")
            val tun = builder.setBlocking(true).establish()
            if (tun == null) {
                XrayBridge.addLog("ERROR", "TUN create failed (another VPN active or duplicate)")
                XrayBridge.setState(RealState.BROKEN, "TUN create failed")
                return
            }
            tunFd = tun.detachFd()
            // NOTE: do NOT call protect() on the TUN fd - protect() is only valid
            // for socket fds; the tun inbound inside xray-core owns this fd.
            lastUp = 0
            lastDown = 0
            zeroStreak = 0
            controller = Libv2ray.newCoreController(callback)
            controller?.startLoop(buildXrayConfig(link), tunFd)
            XrayBridge.addLog("INFO", "Core loop entered")
            mainHandler.removeCallbacks(statsRunnable)
            mainHandler.postDelayed(statsRunnable, 1200L)
        } catch (e: Exception) {
            XrayBridge.addLog("ERROR", "Core error: ${e.message}")
            XrayBridge.setState(RealState.BROKEN, e.message ?: "core error")
        }
    }

    private fun stopCore() {
        mainHandler.removeCallbacks(statsRunnable)
        mainHandler.removeCallbacks(stateSetter)
        try {
            controller?.stopLoop()
        } catch (_: Exception) {
        }
        controller = null
        if (tunFd > 0) {
            try {
                android.os.ParcelFileDescriptor.adoptFd(tunFd).close()
            } catch (_: Exception) {
            }
            tunFd = -1
        }
    }

    override fun onRevoke() {
        stopCore()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopCore()
        super.onDestroy()
    }
}
