package ir.v2rayng.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.core.CrashNote
import ir.v2rayng.compose.data.Settings
import ir.v2rayng.compose.data.SettingsStore
import ir.v2rayng.compose.data.parseFloatList
import ir.v2rayng.compose.data.parseLongList
import ir.v2rayng.compose.data.parseList
import ir.v2rayng.compose.data.parseFxPack
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
    ir.v2rayng.compose.AppCtx.app = context.applicationContext
    val vm: AppViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AppViewModel(SettingsStore(context.applicationContext)) }
        }
    )
    val settings by vm.settings.collectAsState()
    val env = remember { StickerEnv() }
    remember { ir.v2rayng.compose.stickers.CustomStickers.init(context) }
    androidx.compose.runtime.LaunchedEffect(settings.dark) {
        (context as? ir.v2rayng.compose.MainActivity)?.applySystemBars(settings.dark)
    }
    LaunchedEffect(Unit) {
        var prev = 0L
        var animMs = 0L
        var waveMs = 0L
        var lastWave = 0L
        var debt = 0.0
        while (true) {
            withFrameMillis { v ->
                if (prev != 0L && v > prev) {
                    val raw = (v - prev).toDouble()
                    val step = raw.coerceAtMost(25.0)
                    debt += (raw - step)
                    val extra = debt.coerceAtMost(12.5)
                    val adv = (step + extra).toLong()
                    animMs += adv
                    debt -= extra
                    if (v - lastWave >= 15L) {
                        lastWave = v
                        waveMs += adv
                    }
                }
                prev = v
                StickerTicker.anim.longValue = animMs
                StickerTicker.wave.longValue = waveMs
            }
        }
    }
    LaunchedEffect(settings.animate, settings.speed, settings.bgSpeed, settings.flagStretch, settings.flagRound) {
        env.enabled.value = settings.animate
        env.speed.floatValue = settings.speed
        env.bgSpeed.floatValue = settings.bgSpeed
        env.flagStretch.floatValue = settings.flagStretch
        env.flagRound.floatValue = settings.flagRound
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(vm: AppViewModel, settings: Settings) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val gazeLocal = remember { mutableStateOf<Offset?>(null) }
    val outerRoot = remember { mutableStateOf(Offset.Zero) }
    var moveOpen by remember { mutableStateOf(false) }
    var editNav by remember { mutableStateOf<Int?>(null) }

    val order = parseList(settings.navOrder, (0..4).toList()).take(5)
    val navIcons = parseList(settings.navIcons, (0..4).toList())

    LaunchedEffect(settings.dark, tab) {
        CrashNote.ui = (if (settings.dark) "dark" else "light") + " tab=" + tab +
            " navStickers=" + settings.navStickers + " bubble=" + settings.bubbleOn +
            " style=" + settings.stickerStyle
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(S.APP, fontWeight = FontWeight.Bold) },
            )
        },
        bottomBar = {
            NavigationBar {
                order.forEachIndexed { pos, real ->
                    NavigationBarItem(
                        selected = tab == pos,
                        onClick = { tab = pos },
                        icon = {
                            Box(
                                Modifier.holdFor(
                                    durationMs = 1000L,
                                    onTap = { },
                                    onHold = { editNav = real },
                                )
                            ) {
                                if (settings.navStickers) {
                                    val spd = parseFloatList(settings.navSpeeds, listOf(1f, 1f, 1f, 1f, 1f)).getOrNull(real) ?: 1f
                                    val navPacks = parseList(settings.navPacks, listOf(-1, -1, -1, -1, -1))
                                    val st = V2Stickers.byPack(
                                        navPacks.getOrNull(real) ?: -1,
                                        navIcons.getOrNull(real) ?: real,
                                        settings.stickerStyle,
                                        real,
                                    )
                                    val tint = parseLongList(settings.navColors, listOf(-1L, -1L, -1L, -1L, -1L)).getOrNull(real) ?: -1L
                                    val nfx = parseFxPack(settings.navFx.split(";").getOrNull(real) ?: "")
                                    StickerCharacter(
                                        sticker = tinted(st, tint),
                                        mood = if (tab == pos) StickerMood.HAPPY else StickerMood.NORMAL,
                                        sizeDp = 26.dp,
                                        speedScale = spd,
                                        modifier = Modifier.stickerFx(nfx),
                                    )
                                } else {
                                    Icon(tabIcons[real], contentDescription = S.TABS[real])
                                }
                            }
                        },
                        label = { Text(S.TABS[real]) },
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
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .stickerFx(parseFxPack(settings.wallFx)),
                        gaze = gaze,
                        bg = true,
                    )
                }
            }

            if (state.appDisabled) AppDisabledScreen(vm) else when (val real = order[tab]) {
                0 -> HomeScreen(state, settings, vm, gaze, onGoServers = { tab = 1 })
                1 -> ServersScreen(state, settings, vm)
                2 -> RoutingScreen(state, settings, vm)
                3 -> LogScreen(state, vm)
                else -> SettingsScreen(state, settings, vm)
            }
        }
    }

    if (state.remotePopup != null) {
        RemotePopupDialog(state.remotePopup!!, vm)
    }
    if (state.remoteUpdate != null) {
        RemoteUpdateDialog(state.remoteUpdate!!, vm)
    }
    if (state.remoteNotif != null) {
        RemoteNotifDialog(state.remoteNotif!!, vm)
    }

    if (moveOpen) {
        MoveNavDialog(order, vm) { moveOpen = false }
    }
    editNav?.let { slot ->
        StickerEditorDialog("nav:$slot", settings, vm) { editNav = null }
    }
}

@Composable
fun MoveNavDialog(order: List<Int>, vm: AppViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.MENU_LAYOUT_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    S.MENU_LAYOUT_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                order.forEachIndexed { pos, real ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "${pos + 1}. ${S.TABS.getOrNull(real) ?: ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { if (pos > 0) vm.setNavOrder(order.toMutableList().apply { add(pos - 1, removeAt(pos)) }) },
                            enabled = pos > 0,
                        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up") }
                        TextButton(
                            onClick = { if (pos < order.size - 1) vm.setNavOrder(order.toMutableList().apply { add(pos + 1, removeAt(pos)) }) },
                            enabled = pos < order.size - 1,
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down") }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(S.CLOSE)
                }
            }
        }
    }
}

