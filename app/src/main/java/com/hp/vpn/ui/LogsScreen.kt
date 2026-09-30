package com.hp.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hp.vpn.R
import com.hp.vpn.logging.MemoryLog

// MemoryLog prepends "HH:mm:ss.SSS " to every captured line; split it for display only.
private val TimestampPattern = Regex("""^(\d{2}:\d{2}:\d{2}\.\d{3})\s+(.*)$""")

@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val entries by MemoryLog.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    // Stop following the tail as soon as the user scrolls away from the bottom.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) follow = !listState.canScrollForward
        }
    }
    // A short log stays at the top: scrollToItem cannot scroll past the content.
    LaunchedEffect(entries.size) {
        if (follow && entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.logs),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("HP VPN logs", entries.joinToString("\n")))
                Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.copy),
                )
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clear))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(entries) { line -> LogLine(line) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_logs_title)) },
            text = { Text(stringResource(R.string.clear_logs_message)) },
            confirmButton = {
                TextButton(onClick = {
                    MemoryLog.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LogLine(line: String) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val match = remember(line) { TimestampPattern.find(line) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
        if (match == null) {
            Text(text = line, style = mono, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Text(
                text = match.groupValues[1],
                style = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(text = match.groupValues[2], style = mono, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
