package com.hp.vpn.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hp.vpn.R
import com.hp.vpn.config.RouteMode

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun routeModeLabel(mode: RouteMode): String = stringResource(
    when (mode) {
        RouteMode.GLOBAL -> R.string.route_global
        RouteMode.INCLUDE -> R.string.route_include
        RouteMode.EXCLUDE -> R.string.route_exclude
    },
)

@Composable
fun routeModeDescription(mode: RouteMode): String = stringResource(
    when (mode) {
        RouteMode.GLOBAL -> R.string.route_global_desc
        RouteMode.INCLUDE -> R.string.route_include_desc
        RouteMode.EXCLUDE -> R.string.route_exclude_desc
    },
)
