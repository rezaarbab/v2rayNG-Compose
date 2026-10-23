package ir.v2rayng.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.v2rayng.compose.AppViewModel
import ir.v2rayng.compose.data.AppState
import ir.v2rayng.compose.data.LogLine

@Composable
fun LogScreen(state: AppState, vm: AppViewModel) {
    val listState: LazyListState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }
    val empty by remember { derivedStateOf { state.logs.isEmpty() } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = S.LOGS_TITLE,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(
                onClick = {
                    if (state.logs.isNotEmpty()) {
                        val text = buildString {
                            state.logs.forEach { line ->
                                appendLine("${line.time} [${line.level}] ${line.msg}")
                            }
                        }
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                },
            ) {
                Text(if (copied) "Copied!" else "Copy")
            }
            IconButton(onClick = { vm.clearLogs() }, modifier = Modifier.tooltipHold(S.CLEAR)) {
                Icon(Icons.Filled.Delete, contentDescription = S.CLEAR)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (empty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = S.LOGS_EMPTY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.logs) { _, line ->
                    LogRow(line)
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val (bg, fg) = when (line.level) {
        "ERROR" -> Color(0xFF5C1A1A) to Color(0xFFFFC2C2)
        "WARN" -> Color(0xFF4A3410) to Color(0xFFFFD9A0)
        "CORE" -> Color(0xFF1A2A5C) to Color(0xFFBCD2FF)
        else -> Color(0xFF14351F) to Color(0xFF9BF3CB)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = line.time,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Surface(color = bg, shape = RoundedCornerShape(50)) {
                Text(
                    text = line.level,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = line.msg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
