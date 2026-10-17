package ir.v2rayng.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.SettingsStore
import ir.v2rayng.compose.stickers.LocalStickerEnv
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.StickerEnv
import ir.v2rayng.compose.stickers.StickerMood
import ir.v2rayng.compose.stickers.StickerTicker
import ir.v2rayng.compose.stickers.V2Stickers
import ir.v2rayng.compose.stickers.tinted
import ir.v2rayng.compose.ui.theme.V2rayNGTheme

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AppViewModel(SettingsStore(context.applicationContext)) }
        }
    )
    val settings by vm.settings.collectAsState()
    val env = remember { StickerEnv() }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { v ->
                if (v - last >= 31L) {
                    last = v
                    StickerTicker.ms.longValue = v
                }
            }
        }
    }
    LaunchedEffect(settings.animate, settings.speed, settings.bgSpeed) {
        env.enabled.value = settings.animate
        env.speed.floatValue = settings.speed
        env.bgSpeed.floatValue = settings.bgSpeed
    }
    CompositionLocalProvider(LocalStickerEnv provides env) {
        V2rayNGTheme(dark = settings.dark, accent = settings.accent, custom = settings.customAccent) {
            AppScaffold(vm, settings)
        }
    }
}

private val tabIcons = listOf(
    Icons.Filled.Home, Icons.Filled.List, Icons.Filled.Send, Icons.Filled.Info, Icons.Filled.Settings,
)
private val tabLabels = listOf("ارتباط", "سرورها", "مسیریابی", "گزارش", "تنظیمات")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(vm: AppViewModel, settings: Settings) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val gazeLocal = remember { mutableStateOf<Offset?>(null) }
    val outerRoot = remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("v2rayNG", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.pingAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "آزمون تاخیر")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabLabels.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            if (settings.navStickers) {
                                val s = V2Stickers.byIndex(settings.stickerStyle, i)
                                StickerCharacter(
                                    sticker = s,
                                    mood = if (tab == i) StickerMood.HAPPY else StickerMood.NORMAL,
                                    sizeDp = 26.dp,
                                )
                            } else {
                                Icon(tabIcons[i], contentDescription = label)
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        val gaze = if (settings.followFinger) {
            gazeLocal.value?.let { it + outerRoot.value }
        } else null

        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .onGloballyPositioned { outerRoot.value = it.positionInRoot() }
                .pointerInput(settings.followFinger) {
                    if (!settings.followFinger) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val p = e.changes.firstOrNull { it.pressed }
                            if (p != null) gazeLocal.value = p.position
                        }
                    }
                }
        ) {
            if (settings.wallpaperIndex >= 0) {
                val wall = tinted(
                    V2Stickers.byIndex(settings.wallpaperStyle, settings.wallpaperIndex),
                    settings.wallpaperColor,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (settings.dark) 0.38f else 0.22f },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    StickerCharacter(
                        sticker = wall,
                        mood = StickerMood.HAPPY,
                        sizeDp = if (settings.wallpaperSize > 0) settings.wallpaperSize.dp else 300.dp,
                        modifier = Modifier.padding(bottom = 8.dp),
                        gaze = gaze,
                        bg = true,
                    )
                }
            }

            when (tab) {
                0 -> HomeScreen(state, settings, vm, gaze)
                1 -> ServersScreen(state, settings, vm)
                2 -> RoutingScreen(state, settings, vm)
                3 -> LogScreen(state, vm)
                else -> SettingsScreen(state, settings, vm)
            }
        }
    }
}
