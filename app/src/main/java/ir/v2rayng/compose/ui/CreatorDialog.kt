package ir.v2rayng.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.stickers.CustomStickerDef
import ir.v2rayng.compose.stickers.CustomStickers
import ir.v2rayng.compose.stickers.StickerCharacter
import ir.v2rayng.compose.stickers.suggestion

private val CreatorPalette = longArrayOf(
    0xFF2BD48A, 0xFF60A5FA, 0xFFB39BF5, 0xFFFFA94D,
    0xFFEC6E6E, 0xFFF4D27A, 0xFF96B6F5, 0xFF8CDDB2,
    0xFF7FD8E8, 0xFFC9E87F, 0xFFE689C3, 0xFF23262B,
    0xFF1F9D55, 0xFFF7F7F2, 0xFFE03C31, 0xFF2C5BD6,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatorDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("My sticker") }
    var kind by remember { mutableStateOf(0) }
    var color by remember { mutableStateOf(0xFF2C5BD6L) }
    var blobA by remember { mutableStateOf(1f) }
    var blobB by remember { mutableStateOf(1f) }
    var toyShape by remember { mutableStateOf(0) }
    var bands by remember { mutableStateOf(listOf(0xFF1F9D55L, 0xFFF7F7F2L, 0xFFE03C31L)) }
    var vertical by remember { mutableStateOf(false) }
    var emblem by remember { mutableStateOf(0) }
    var emblemColor by remember { mutableStateOf(0xFFF7CE1BL) }
    var emblemX by remember { mutableStateOf(0.5f) }
    var speed by remember { mutableStateOf(1f) }
    var size by remember { mutableStateOf(190f) }
    var editingId by remember { mutableStateOf<String?>(null) }

    val shapeNames = listOf(
        "Drop", "Triangle", "Cloud", "Ball", "Capsule", "Bean", "Fire", "Star",
        "Heart", "Bolt", "Moon", "Cup", "Rocket", "Balloon", "Shroom", "Gem",
    )
    val emblemNames = listOf("None", "Circle", "Star", "Crescent", "Cross", "Diamond", "Sun")
    var showExitConfirm by remember { mutableStateOf(false) }
    var savedHash by remember { mutableStateOf(Int.MIN_VALUE) }

    fun currentDef(): CustomStickerDef = CustomStickerDef(
        id = editingId ?: "c${System.currentTimeMillis()}",
        name = name.ifBlank { "My sticker" },
        kind = kind,
        color = color,
        blobA = blobA,
        blobB = blobB,
        toyShape = toyShape,
        flagBands = bands.joinToString(",") { "0x%08X".format(it) },
        flagVertical = vertical,
        emblem = emblem,
        emblemColor = emblemColor,
        emblemX = emblemX,
        speed = speed,
        size = size.toInt(),
    )

    fun previewDef(): CustomStickerDef = currentDef().copy(id = editingId ?: "preview")

    fun isDirty(): Boolean = previewDef().hashCode() != savedHash

    Dialog(onDismissRequest = { if (isDirty()) showExitConfirm = true else onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(S.CREATOR_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previewDef = currentDef().copy(id = editingId ?: "preview")
                    StickerCharacter(previewDef.toSticker(), sizeDp = 96.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(S.CREATOR_SUGGEST.format(currentDef().suggestion()), style = MaterialTheme.typography.labelMedium)
                        Text(
                            S.CREATOR_PREVIEW,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(S.CREATOR_NAME) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(S.CREATOR_KIND, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(S.CREATOR_BLOB, kind == 0, { kind = 0 })
                    SelectChip(S.CREATOR_SHAPE, kind == 1, { kind = 1 })
                    SelectChip(S.CREATOR_FLAG, kind == 2, { kind = 2 })
                }

                when (kind) {
                    0 -> {
                        Text(S.CREATOR_COLOR, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CreatorPalette.forEach { c -> ColorDot(Color(c), "", color == c) { color = c } }
                        }
                        Text(S.CREATOR_WOBBLE.format(blobA, blobB), style = MaterialTheme.typography.labelMedium)
                        Slider(value = blobA, onValueChange = { blobA = it }, valueRange = 0.1f..2f)
                        Slider(value = blobB, onValueChange = { blobB = it }, valueRange = 0.1f..2f)
                    }
                    1 -> {
                        Text(S.CREATOR_SHAPE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            shapeNames.forEachIndexed { i, n -> SelectChip(n, toyShape == i, { toyShape = i }) }
                        }
                        Text(S.CREATOR_COLOR, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CreatorPalette.forEach { c -> ColorDot(Color(c), "", color == c) { color = c } }
                        }
                    }
                    else -> {
                        Text(S.CREATOR_DIRECTION, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip(S.CREATOR_H, !vertical, { vertical = false })
                            SelectChip(S.CREATOR_V, vertical, { vertical = true })
                        }
                        Text(S.CREATOR_BANDS, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            bands.forEachIndexed { bi, bc ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(bc)),
                                    )
                                    TextButton(onClick = { bands = bands.filterIndexed { i, _ -> i != bi } }) { Text("x") }
                                }
                            }
                        }
                        Text(S.CREATOR_BAND_ADD, style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CreatorPalette.forEach { c ->
                                ColorDot(Color(c), "", false) { bands = bands + c }
                            }
                        }
                        Text(S.CREATOR_EMBLEM, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            emblemNames.forEachIndexed { i, n -> SelectChip(n, emblem == i, { emblem = i }) }
                        }
                        if (emblem > 0) {
                            Text(S.CREATOR_EMBLEM_POS, style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SelectChip(S.CREATOR_POS_CENTER, emblemX == 0.5f, { emblemX = 0.5f })
                                SelectChip(S.CREATOR_POS_LEFT, emblemX == 0.35f, { emblemX = 0.35f })
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CreatorPalette.forEach { c -> ColorDot(Color(c), "", emblemColor == c) { emblemColor = c } }
                            }
                        }
                    }
                }

                Text(S.SPEED_STICKERS.format(speed), style = MaterialTheme.typography.labelMedium)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.4f..2.2f)
                Text(S.SIZE, style = MaterialTheme.typography.labelMedium)
                Slider(value = size, onValueChange = { size = it }, valueRange = 60f..340f)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            val def = currentDef().copy(id = editingId ?: "c${System.currentTimeMillis()}")
                            CustomStickers.upsert(def)
                            editingId = def.id
                            savedHash = previewDef().hashCode()
                        },
                    ) { Text(S.CREATOR_SAVE) }
                    TextButton(onClick = onDismiss) { Text(S.CLOSE) }
                }

                Text(S.CREATOR_MINE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CustomStickers.all()) { def ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .clickable {
                                    name = def.name; kind = def.kind; color = def.color
                                    blobA = def.blobA; blobB = def.blobB; toyShape = def.toyShape
                                    bands = def.bandColors(); vertical = def.flagVertical
                                    emblem = def.emblem; emblemColor = def.emblemColor; emblemX = def.emblemX
                                    speed = def.speed; size = def.size.toFloat(); editingId = def.id
                                    savedHash = previewDef().hashCode()
                                }
                                .padding(6.dp),
                        )                          {
                            StickerCharacter(def.toSticker(), sizeDp = 40.dp)
                            Text(def.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            TextButton(onClick = { CustomStickers.delete(def.id) }) { Text("Del", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Unsaved changes") },
            text = { Text("Do you want to save your sticker before closing?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val def = currentDef().copy(id = editingId ?: "c${System.currentTimeMillis()}")
                        CustomStickers.upsert(def)
                        editingId = def.id
                        showExitConfirm = false
                        onDismiss()
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        onDismiss()
                    },
                ) { Text("Discard") }
            },
        )
    }
}
