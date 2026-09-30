package com.hp.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hp.vpn.R
import com.hp.vpn.config.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    profiles: List<Profile>,
    selectedId: String?,
    onSelect: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onImport: () -> Unit,
    onNewProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.choose_profile),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                profiles.forEach { profile ->
                    val isSelected = profile.id == selectedId
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = { Text(profile.server) },
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        trailingContent = {
                            IconButton(onClick = { onEdit(profile) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                )
                            }
                        },
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(profile) },
                        ),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_clipboard))
                }
                OutlinedButton(onClick = onNewProfile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.new_profile))
                }
            }
        }
    }
}
