package com.hp.vpn.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hp.vpn.R
import com.hp.vpn.config.Profile
import com.hp.vpn.config.ProfileParser

@Composable
fun ProfileEditorScreen(
    original: Profile?,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val editorKey = original?.id ?: "new"
    var name by rememberSaveable(editorKey) { mutableStateOf(original?.name.orEmpty()) }
    var server by rememberSaveable(editorKey) { mutableStateOf(original?.server.orEmpty()) }
    var password by rememberSaveable(editorKey) { mutableStateOf(original?.password.orEmpty()) }
    var cert by rememberSaveable(editorKey) { mutableStateOf(original?.cert.orEmpty()) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val cleanServer = server.trim()
    val serverError = submitted && runCatching { ProfileParser.validateServer(cleanServer) }.isFailure
    val passwordError = submitted && password.isEmpty()
    val certError = submitted && runCatching { ProfileParser.validateCert(cert) }.isFailure

    // Swallow taps that miss the fields so they cannot reach the screen behind this overlay.
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(
                        if (original == null) R.string.new_profile else R.string.edit_profile,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text(stringResource(R.string.server_hint)) },
                    isError = serverError,
                    supportingText = {
                        Text(
                            stringResource(
                                if (serverError) R.string.error_server else R.string.server_helper,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    isError = passwordError,
                    supportingText = if (passwordError) {
                        { Text(stringResource(R.string.error_password)) }
                    } else {
                        null
                    },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(
                                stringResource(
                                    if (showPassword) R.string.hide_password else R.string.show_password,
                                ),
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cert,
                    onValueChange = { cert = it },
                    label = { Text(stringResource(R.string.cert_hint)) },
                    isError = certError,
                    supportingText = {
                        Text(
                            stringResource(if (certError) R.string.error_cert else R.string.cert_helper),
                        )
                    },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = {
                    submitted = true
                    val valid = runCatching {
                        ProfileParser.validateServer(cleanServer)
                        require(password.isNotEmpty()) { "password" }
                        ProfileParser.validateCert(cert)
                    }.isSuccess
                    if (valid) {
                        onSave(
                            Profile(
                                id = original?.id ?: Profile.newId(),
                                name = name.trim().ifEmpty { cleanServer },
                                server = cleanServer,
                                password = password,
                                cert = cert,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
