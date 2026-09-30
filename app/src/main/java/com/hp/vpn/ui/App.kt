package com.hp.vpn.ui

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hp.vpn.R
import com.hp.vpn.config.AppConfig
import com.hp.vpn.config.ConfigStore
import com.hp.vpn.config.Profile
import com.hp.vpn.config.ProfileParser
import com.hp.vpn.logging.AppLog
import com.hp.vpn.vpn.VpnStatus
import kotlinx.coroutines.launch

private enum class Page(val labelRes: Int, val icon: ImageVector) {
    CONNECT(R.string.tab_connect, Icons.Filled.Home),
    ROUTING(R.string.tab_routing, Icons.Filled.Share),
    LOGS(R.string.logs, Icons.AutoMirrored.Filled.List),
}

@Composable
fun HpVpnApp(
    store: ConfigStore,
    onConnect: (AppConfig) -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { runCatching { store.read() } }
    var config by remember { mutableStateOf(initial.getOrDefault(AppConfig())) }
    val snackbarHostState = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(Page.CONNECT) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    val status by VpnStatus.state.collectAsState()

    fun notify(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun save(next: AppConfig) {
        try {
            store.write(next)
            config = next
        } catch (e: Exception) {
            notify(e.message ?: context.getString(R.string.save_failed))
        }
    }

    fun importFromClipboard() {
        try {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                ?: error(context.getString(R.string.clipboard_empty))
            val profile = ProfileParser.parseClipboard(raw)
            save(config.copy(profiles = config.profiles + profile, selectedId = profile.id))
            AppLog.write("Config", "Imported profile for ${profile.server}")
        } catch (e: Exception) {
            notify(e.message ?: context.getString(R.string.import_failed))
        }
    }

    LaunchedEffect(Unit) {
        if (initial.isFailure) {
            snackbarHostState.showSnackbar(context.getString(R.string.config_read_failed))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    Page.entries.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (page) {
                    Page.CONNECT -> ConnectScreen(
                        config = config,
                        status = status,
                        onConnect = { onConnect(config) },
                        onDisconnect = onDisconnect,
                        onOpenProfiles = { sheetOpen = true },
                        onOpenRouting = { page = Page.ROUTING },
                        onImport = ::importFromClipboard,
                        onNewProfile = {
                            editingProfile = null
                            editorOpen = true
                        },
                    )
                    Page.ROUTING -> RoutingScreen(config = config, save = ::save)
                    Page.LOGS -> LogsScreen()
                }
            }
        }

        if (sheetOpen) {
            ProfileSheet(
                profiles = config.profiles,
                selectedId = config.selectedId,
                onSelect = { profile ->
                    save(config.copy(selectedId = profile.id))
                    sheetOpen = false
                },
                onEdit = { profile ->
                    editingProfile = profile
                    editorOpen = true
                    sheetOpen = false
                },
                onImport = {
                    sheetOpen = false
                    importFromClipboard()
                },
                onNewProfile = {
                    editingProfile = null
                    editorOpen = true
                    sheetOpen = false
                },
                onDismiss = { sheetOpen = false },
            )
        }

        if (editorOpen) {
            ProfileEditorScreen(
                original = editingProfile,
                onDismiss = { editorOpen = false },
                onSave = { profile ->
                    val exists = config.profiles.any { it.id == profile.id }
                    save(
                        config.copy(
                            profiles = if (exists) {
                                config.profiles.map { if (it.id == profile.id) profile else it }
                            } else {
                                config.profiles + profile
                            },
                            selectedId = if (exists) config.selectedId else profile.id,
                        ),
                    )
                    AppLog.write(
                        "Config",
                        if (exists) {
                            "Updated profile for ${profile.server}"
                        } else {
                            "Created profile for ${profile.server}"
                        },
                    )
                    editorOpen = false
                },
                onDelete = editingProfile?.let { profile -> { pendingDelete = profile } },
            )
        }

        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete_profile_title)) },
                text = { Text(stringResource(R.string.delete_profile_message, target.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        save(
                            config.copy(
                                profiles = config.profiles.filterNot { it.id == target.id },
                                selectedId = if (config.selectedId == target.id) {
                                    null
                                } else {
                                    config.selectedId
                                },
                            ),
                        )
                        pendingDelete = null
                        editorOpen = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    BackHandler(enabled = editorOpen) { editorOpen = false }
}
