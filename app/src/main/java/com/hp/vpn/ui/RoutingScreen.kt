package com.hp.vpn.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.hp.vpn.R
import com.hp.vpn.config.AppConfig
import com.hp.vpn.config.RouteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AppFilter { ALL, USER, SYSTEM }

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Drawable,
)

@Composable
fun RoutingScreen(config: AppConfig, save: (AppConfig) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }

    val installed by produceState<List<InstalledApp>?>(null) {
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

    val matched = remember(installed, query, filter) {
        installed?.filter { app ->
            (filter == AppFilter.ALL || (filter == AppFilter.SYSTEM) == app.isSystem) &&
                (query.isBlank() || app.packageName.contains(query, true) ||
                    app.label.contains(query, true))
        }
    }
    val selected = remember(matched, config.packages) {
        matched?.filter { it.packageName in config.packages }
    }
    val others = remember(matched, config.packages) {
        matched?.filterNot { it.packageName in config.packages }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.tab_routing),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RouteMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = config.mode == mode,
                        onClick = { save(config.copy(mode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, RouteMode.entries.size),
                    ) {
                        Text(
                            text = routeModeLabel(mode),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = routeModeDescription(config.mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
            )
        }

        if (config.mode == RouteMode.GLOBAL) {
            GlobalRoutingNote()
        } else {
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(appFilterLabel(option)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.selected_count, config.packages.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    installed == null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    installed?.isEmpty() == true -> CenteredNotice(stringResource(R.string.apps_empty))
                    matched.isNullOrEmpty() -> CenteredNotice(
                        text = stringResource(R.string.apps_no_match),
                        action = {
                            TextButton(onClick = { query = ""; filter = AppFilter.ALL }) {
                                Text(stringResource(R.string.clear_filter))
                            }
                        },
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        val chosen = selected.orEmpty()
                        val rest = others.orEmpty()
                        if (chosen.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.section_selected_apps)) }
                            items(chosen, key = { it.packageName }) { app ->
                                AppRow(app = app, checked = true, onToggle = {
                                    save(config.copy(packages = config.packages - app.packageName))
                                })
                            }
                        }
                        item {
                            SectionHeader(
                                stringResource(
                                    if (chosen.isEmpty()) R.string.section_apps
                                    else R.string.section_other_apps,
                                ),
                            )
                        }
                        items(rest, key = { it.packageName }) { app ->
                            AppRow(app = app, checked = false, onToggle = {
                                save(config.copy(packages = config.packages + app.packageName))
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    val bitmap = remember(app.packageName) {
        runCatching { app.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    ListItem(
        headlineContent = {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
        },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        ),
    )
}

@Composable
private fun GlobalRoutingNote() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.global_note_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.global_note_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CenteredNotice(text: String, action: (@Composable () -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun appFilterLabel(filter: AppFilter): String = stringResource(
    when (filter) {
        AppFilter.ALL -> R.string.filter_all
        AppFilter.USER -> R.string.filter_user
        AppFilter.SYSTEM -> R.string.filter_system
    },
)
