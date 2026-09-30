package com.hp.vpn.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hp.vpn.R
import com.hp.vpn.config.AppConfig
import com.hp.vpn.vpn.VpnConnectionState
import com.hp.vpn.vpn.VpnStatusValue

@Composable
fun ConnectScreen(
    config: AppConfig,
    status: VpnStatusValue,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenRouting: () -> Unit,
    onImport: () -> Unit,
    onNewProfile: () -> Unit,
) {
    val context = LocalContext.current
    val selected = config.profiles.firstOrNull { it.id == config.selectedId }
    val connected = status.state == VpnConnectionState.RUNNING
    val failed = status.state == VpnConnectionState.FAILED
    val busy = status.state == VpnConnectionState.STARTING ||
        status.state == VpnConnectionState.STOPPING

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        if (config.profiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyProfiles(onImport = onImport, onNewProfile = onNewProfile)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ConnectButton(
                        connected = connected,
                        failed = failed,
                        busy = busy,
                        onClick = {
                            when {
                                connected -> onDisconnect()
                                selected == null -> Toast.makeText(
                                    context,
                                    R.string.vpn_profile_required,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                else -> onConnect()
                            }
                        },
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(
                            when {
                                connected -> R.string.connected
                                failed -> R.string.failed
                                else -> R.string.disconnected
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = when {
                            failed -> status.detail ?: selected?.server.orEmpty()
                            selected == null -> ""
                            selected.name != selected.server -> "${selected.name} · ${selected.server}"
                            else -> selected.server
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                SettingRow(
                    label = stringResource(R.string.set_profile),
                    value = selected?.name ?: stringResource(R.string.profile_none),
                    onClick = onOpenProfiles,
                )
                HorizontalDivider(Modifier.padding(start = 16.dp))
                SettingRow(
                    label = stringResource(R.string.set_routing),
                    value = routeModeLabel(config.mode),
                    onClick = onOpenRouting,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectButton(
    connected: Boolean,
    failed: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when {
        connected -> scheme.tertiary
        failed -> scheme.errorContainer
        else -> scheme.primaryContainer
    }
    val contentColor = when {
        connected -> scheme.onTertiary
        failed -> scheme.onErrorContainer
        else -> scheme.onPrimaryContainer
    }
    Box(contentAlignment = Alignment.Center) {
        if (connected) {
            Box(Modifier.size(190.dp).background(scheme.tertiary.copy(alpha = 0.16f), CircleShape))
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(enabled = !busy, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (connected) R.drawable.ic_stop else R.drawable.ic_connect),
                contentDescription = stringResource(if (connected) R.string.stop else R.string.connect),
                tint = contentColor,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun EmptyProfiles(onImport: () -> Unit, onNewProfile: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.profiles_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.profiles_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_clipboard))
            }
            OutlinedButton(
                onClick = onNewProfile,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.new_profile))
            }
        }
    }
}
