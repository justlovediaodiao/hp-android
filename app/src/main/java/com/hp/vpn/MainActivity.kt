package com.hp.vpn

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hp.vpn.config.AppConfig
import com.hp.vpn.config.ConfigStore
import com.hp.vpn.config.Profile
import com.hp.vpn.config.ProfileParser
import com.hp.vpn.config.RouteMode
import com.hp.vpn.logging.AppLog
import com.hp.vpn.logging.MemoryLog
import com.hp.vpn.vpn.TunnelVpnService
import com.hp.vpn.vpn.VpnConnectionState
import com.hp.vpn.vpn.VpnStatus
import com.hp.vpn.vpn.VpnStatusValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Page { HOME, APPS, LOGS }
private enum class AppFilter { ALL, USER, SYSTEM }

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Drawable,
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val store by lazy { ConfigStore(this) }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startForegroundService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.START))
        } else {
            VpnStatus.set(VpnConnectionState.STOPPED)
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        prepareVpn()
    }

    private fun connect(config: AppConfig) {
        if (config.mode == RouteMode.INCLUDE && config.packages.isEmpty()) {
            Toast.makeText(this, R.string.include_apps_required, Toast.LENGTH_SHORT).show()
            return
        }
        VpnStatus.set(VpnConnectionState.STARTING)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            prepareVpn()
        }
    }

    private fun prepareVpn() {
        val request = VpnService.prepare(this)
        if (request != null) {
            vpnPermission.launch(request)
        } else {
            startForegroundService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.START))
        }
    }

    private fun disconnect() {
        VpnStatus.set(VpnConnectionState.STOPPING)
        startService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.STOP))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val initial = remember { runCatching { store.read() } }
            var config by remember { mutableStateOf(initial.getOrDefault(AppConfig())) }
            var page by remember { mutableStateOf(Page.HOME) }
            var message by remember {
                mutableStateOf(initial.exceptionOrNull()?.let { getString(R.string.config_read_failed) })
            }
            val status by VpnStatus.state.collectAsState()

            fun save(next: AppConfig) {
                try {
                    store.write(next)
                    config = next
                } catch (e: Exception) {
                    message = e.message ?: getString(R.string.save_failed)
                }
            }

            val colorScheme = remember { lightColorScheme() }
            MaterialTheme(colorScheme = colorScheme) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.page_title, pageLabel(page))) }) },
                    bottomBar = {
                        Column {
                            if (page == Page.HOME) {
                                ConnectionBar(
                                    status = status,
                                    canConnect = config.selectedId != null,
                                    connect = { connect(config) },
                                    disconnect = ::disconnect,
                                )
                            }
                            NavigationBar(modifier = Modifier.height(64.dp)) {
                                Page.entries.forEach { item ->
                                    NavigationBarItem(
                                        selected = page == item,
                                        onClick = { page = item },
                                        label = { Text(pageLabel(item)) },
                                        icon = { Text(pageIcon(item)) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (page) {
                            Page.HOME -> Home(config, ::save) { message = it }
                            Page.APPS -> Apps(config, ::save)
                            Page.LOGS -> Logs()
                        }
                    }
                }
                message?.let { text ->
                    AlertDialog(
                        onDismissRequest = { message = null },
                        confirmButton = {
                            TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) }
                        },
                        title = { Text(stringResource(R.string.notice)) },
                        text = { Text(text) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionBar(
    status: VpnStatusValue,
    canConnect: Boolean,
    connect: () -> Unit,
    disconnect: () -> Unit,
) {
    val context = LocalContext.current
    val stopMode = status.state == VpnConnectionState.RUNNING
    val busy = status.state == VpnConnectionState.STARTING ||
        status.state == VpnConnectionState.STOPPING
    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = if (status.state == VpnConnectionState.RUNNING) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (status.state == VpnConnectionState.RUNNING) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 96.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(statusLabel(status.state), style = MaterialTheme.typography.titleMedium)
                    status.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                when {
                    busy -> Unit
                    stopMode -> disconnect()
                    canConnect -> connect()
                    else -> Toast.makeText(
                        context,
                        context.getString(R.string.vpn_profile_required),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp).offset(y = (-28).dp),
            containerColor = if (stopMode) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            contentColor = if (stopMode) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(if (stopMode) R.drawable.ic_stop else R.drawable.ic_connect),
                contentDescription = stringResource(if (stopMode) R.string.stop else R.string.connect),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun Home(config: AppConfig, save: (AppConfig) -> Unit, alert: (String) -> Unit) {
    val context = LocalContext.current
    var editorOpen by remember { mutableStateOf(false) }
    var editedProfile by remember { mutableStateOf<Profile?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.profiles), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                editedProfile = null
                editorOpen = true
            }) { Text(stringResource(R.string.new_profile)) }
            OutlinedButton(onClick = {
                try {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        ?: error(context.getString(R.string.clipboard_empty))
                    val profile = ProfileParser.parseClipboard(raw)
                    save(config.copy(profiles = config.profiles + profile, selectedId = profile.id))
                    AppLog.write("Config", "Imported profile for ${profile.server}")
                } catch (e: Exception) {
                    alert(e.message ?: context.getString(R.string.import_failed))
                }
            }) { Text(stringResource(R.string.import_clipboard)) }
        }
        config.profiles.forEach { profile ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        RadioButton(
                            selected = config.selectedId == profile.id,
                            onClick = { save(config.copy(selectedId = profile.id)) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(profile.name)
                            Text(profile.server, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            editedProfile = profile
                            editorOpen = true
                        }) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = {
                            save(config.copy(
                                profiles = config.profiles.filterNot { it.id == profile.id },
                                selectedId = if (config.selectedId == profile.id) null else config.selectedId,
                            ))
                        }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
        Text(stringResource(R.string.routing_mode), style = MaterialTheme.typography.titleLarge)
        RouteMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().clickable { save(config.copy(mode = mode)) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = config.mode == mode, onClick = { save(config.copy(mode = mode)) })
                Text(routeModeLabel(mode), modifier = Modifier.padding(top = 12.dp))
            }
        }
    }

    if (editorOpen) {
        ProfileEditor(
            original = editedProfile,
            onDismiss = { editorOpen = false },
            onSave = { profile ->
                if (editedProfile == null) {
                    save(config.copy(profiles = config.profiles + profile, selectedId = profile.id))
                    AppLog.write("Config", "Created profile for ${profile.server}")
                } else {
                    save(config.copy(profiles = config.profiles.map {
                        if (it.id == profile.id) profile else it
                    }))
                    AppLog.write("Config", "Updated profile for ${profile.server}")
                }
                editorOpen = false
            },
            alert = alert,
        )
    }
}

@Composable
private fun ProfileEditor(
    original: Profile?,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
    alert: (String) -> Unit,
) {
    val context = LocalContext.current
    val editorKey = original?.id ?: "new"
    var name by remember(editorKey) { mutableStateOf(original?.name.orEmpty()) }
    var server by remember(editorKey) { mutableStateOf(original?.server.orEmpty()) }
    var password by remember(editorKey) { mutableStateOf(original?.password.orEmpty()) }
    var cert by remember(editorKey) { mutableStateOf(original?.cert.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.new_profile else R.string.edit_profile)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) })
                OutlinedTextField(server, { server = it }, label = { Text(stringResource(R.string.server_hint)) })
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    cert,
                    { cert = it },
                    label = { Text(stringResource(R.string.cert_hint)) },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val cleanServer = server.trim()
                    ProfileParser.validateServer(cleanServer)
                    require(password.isNotEmpty()) { context.getString(R.string.password_required) }
                    ProfileParser.validateCert(cert)
                    onSave(Profile(
                        id = original?.id ?: Profile.newId(),
                        name = name.trim().ifEmpty { cleanServer },
                        server = cleanServer,
                        password = password,
                        cert = cert,
                    ))
                } catch (e: Exception) {
                    alert(e.message ?: context.getString(R.string.save_failed))
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Apps(config: AppConfig, save: (AppConfig) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    val installed by produceState<List<InstalledApp>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val manager = context.packageManager
            manager.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter {
                    manager.checkPermission(Manifest.permission.INTERNET, it.packageName) ==
                        PackageManager.PERMISSION_GRANTED
                }
                .map { app ->
                    InstalledApp(
                        packageName = app.packageName,
                        label = app.loadLabel(manager).toString(),
                        isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        icon = app.loadIcon(manager),
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
        }
    }
    val shown = remember(installed, config.packages, query, filter) {
        installed.asSequence()
            .filter { app ->
                (filter == AppFilter.ALL || (filter == AppFilter.SYSTEM) == app.isSystem) &&
                    (query.isBlank() || app.packageName.contains(query, true) ||
                        app.label.contains(query, true))
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.packageName in config.packages }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenBy { it.packageName },
            )
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(R.string.search_apps)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(appFilterLabel(option)) },
                )
            }
        }
        if (installed.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else LazyColumn {
            items(shown, key = { it.packageName }, contentType = { "app" }) { app ->
                val checked = app.packageName in config.packages
                fun select(next: Boolean) {
                    save(config.copy(packages = if (next) {
                        config.packages + app.packageName
                    } else {
                        config.packages - app.packageName
                    }))
                }
                Row(Modifier.fillMaxWidth().clickable { select(!checked) }.padding(6.dp)) {
                    AndroidView(
                        factory = {
                            ImageView(it).apply {
                                setImageDrawable(app.icon)
                                tag = app.packageName
                            }
                        },
                        update = { view ->
                            if (view.tag != app.packageName) {
                                view.setImageDrawable(app.icon)
                                view.tag = app.packageName
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(app.label)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(checked, onCheckedChange = ::select)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Logs() {
    val context = LocalContext.current
    val entries by MemoryLog.entries.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { MemoryLog.clear() }) { Text(stringResource(R.string.clear)) }
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("HP VPN logs", entries.joinToString("\n")))
                Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.copy)) }
        }
        LazyColumn { items(entries) { Text(it, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun pageLabel(page: Page) = stringResource(when (page) {
    Page.HOME -> R.string.home
    Page.APPS -> R.string.apps
    Page.LOGS -> R.string.logs
})

private fun pageIcon(page: Page) = when (page) {
    Page.HOME -> "●"
    Page.APPS -> "▦"
    Page.LOGS -> "≡"
}

@Composable
private fun statusLabel(state: VpnConnectionState) = stringResource(when (state) {
    VpnConnectionState.STOPPED -> R.string.disconnected
    VpnConnectionState.STARTING -> R.string.disconnected
    VpnConnectionState.RUNNING -> R.string.connected
    VpnConnectionState.STOPPING -> R.string.disconnected
    VpnConnectionState.FAILED -> R.string.failed
})

@Composable
private fun routeModeLabel(mode: RouteMode) = stringResource(when (mode) {
    RouteMode.GLOBAL -> R.string.route_global
    RouteMode.INCLUDE -> R.string.route_include
    RouteMode.EXCLUDE -> R.string.route_exclude
})

@Composable
private fun appFilterLabel(filter: AppFilter) = stringResource(when (filter) {
    AppFilter.ALL -> R.string.filter_all
    AppFilter.USER -> R.string.filter_user
    AppFilter.SYSTEM -> R.string.filter_system
})
