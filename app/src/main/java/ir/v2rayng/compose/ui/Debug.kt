package ir.v2rayng.compose.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

object DebugKit {
    val ticks = AtomicLong()
    val composes = AtomicLong()
    val layers = AtomicLong()
    val draws = AtomicLong()
    val drawNanos = AtomicLong()
}

object FrameWatch {
    private val gaps = LongArray(240)
    private var idx = 0
    private var last = 0L

    @Synchronized
    fun tick(v: Long) {
        if (last > 0L) {
            gaps[idx % gaps.size] = v - last
            idx++
        }
        last = v
    }

    @Synchronized
    fun maxGap(): Long {
        var m = 0L
        for (g in gaps) if (g > m) m = g
        return m
    }

    @Synchronized
    fun dropped(thresholdMs: Long): Int {
        var c = 0
        for (g in gaps) if (g > thresholdMs) c++
        return c
    }
}

private class DiagSnap {
    val device = mutableStateOf("")
    val display = mutableStateOf("")
    val vsyncHz = mutableStateOf(0f)
    val maxGap = mutableStateOf(0L)
    val dropped = mutableStateOf(0)
    val layersRate = mutableStateOf(0f)
    val drawsRate = mutableStateOf(0f)
    val drawMsRate = mutableStateOf(0f)
    val composeRate = mutableStateOf(0f)
}

@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val s = remember { DiagSnap() }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val d = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val model = android.os.Build.MODEL
        val maker = android.os.Build.MANUFACTURER
        val sdk = android.os.Build.VERSION.SDK_INT
        val rel = android.os.Build.VERSION.RELEASE
        s.device.value = String.format("%s %s | Android %s (SDK %d)", maker, model, rel, sdk)
        if (d != null) {
            s.display.value = String.format(
                "%.1f Hz | %dx%d px | %.2f dpi",
                d.refreshRate, d.mode.physicalWidth, d.mode.physicalHeight, d.densityDpi,
            )
        } else {
            s.display.value = "display n/a"
        }
        var lastT = System.nanoTime()
        var k0 = DebugKit.ticks.get()
        var l0 = DebugKit.layers.get()
        var dr0 = DebugKit.draws.get()
        var n0 = DebugKit.drawNanos.get()
        var c0 = DebugKit.composes.get()
        while (true) {
            delay(700)
            val now = System.nanoTime()
            val dt = ((now - lastT) / 1e9).toFloat()
            if (dt <= 0f) continue
            val kw = DebugKit.ticks.get()
            val lw = DebugKit.layers.get()
            val dw = DebugKit.draws.get()
            val nw = DebugKit.drawNanos.get()
            val cw = DebugKit.composes.get()
            s.vsyncHz.value = (kw - k0) / dt
            s.layersRate.value = (lw - l0) / dt
            s.drawsRate.value = (dw - dr0) / dt
            s.drawMsRate.value = ((nw - n0) / dt).toFloat() / 1_000_000f
            s.composeRate.value = (cw - c0) / dt
            s.maxGap.value = FrameWatch.maxGap()
            s.dropped.value = FrameWatch.dropped(20)
            lastT = now
            k0 = kw; l0 = lw; dr0 = dw; n0 = nw; c0 = cw
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diagnostics") },
        text = {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DiagLine("Device", s.device.value)
                    DiagLine("Display", s.display.value)
                    DiagLine("Vsync measured", String.format("%.1f callbacks/s", s.vsyncHz.value))
                    DiagLine("Worst frame gap", String.format("%d ms (of last 240 frames)", s.maxGap.value))
                    DiagLine("Dropped frames", String.format("%d gaps > 20 ms", s.dropped.value))
                    DiagLine("Ticker writes", String.format("%.1f/s", s.vsyncHz.value))
                    DiagLine("Sticker recompositions", String.format("%.1f/s (expect near 0 while idle)", s.composeRate.value))
                    DiagLine("Layer matrix updates", String.format("%.1f/s (expect vsync x sticker count)", s.layersRate.value))
                    DiagLine("Canvas redraws", String.format("%.1f/s", s.drawsRate.value))
                    DiagLine("Time spent drawing", String.format("%.2f ms per second", s.drawMsRate.value))
                    Text(
                        "How to read: watch the scene idle, then while connected and touching the screen. If 'Sticker recompositions' stays near 0 and drawing time is low, the renderer is fine and jitter comes from the display refresh switching. Tell the numbers you see to the developer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.CLOSE) } },
    )
}

@Composable
private fun DiagLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
