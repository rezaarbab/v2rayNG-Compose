package ir.v2rayng.compose

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import ir.v2rayng.compose.data.SettingsStore
import ir.v2rayng.compose.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir.v2rayng.compose.core.CrashNote.install(this)
        lockHighRefreshRate()
        applySystemBars(SettingsStore(this).load().dark)
        setContent {
            AppRoot()
        }
    }

    fun applySystemBars(dark: Boolean) {
        if (dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        }
    }

    private fun lockHighRefreshRate() {
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val current = display.mode ?: return
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val attrs = window.attributes
            attrs.preferredDisplayModeId = best.modeId
            attrs.preferredRefreshRate = best.refreshRate
            window.attributes = attrs
        } catch (_: Exception) {
        }
    }
}
