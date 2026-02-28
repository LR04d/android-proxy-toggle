package com.kinandcarta.create.proxytoggle.manager.view.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kinandcarta.create.proxytoggle.R
import com.kinandcarta.create.proxytoggle.core.common.proxy.ProxyProfile

private const val MAX_PORT_VALUE = 65535
private const val MAX_PORT_LENGTH = 5

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    existingProfile: ProxyProfile? = null,
    onSave: (ProxyProfile) -> Unit,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var address by remember { mutableStateOf(existingProfile?.address ?: "") }
    var port by remember { mutableStateOf(existingProfile?.port ?: "") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }

    val isEditMode = existingProfile != null

    fun validate(): Boolean {
        var isValid = true

        if (name.isBlank()) {
            nameError = "Profile name is required"
            isValid = false
        } else {
            nameError = null
        }

        if (address.isBlank()) {
            addressError = "IP address is required"
            isValid = false
        } else {
            addressError = null
        }

        if (port.isBlank()) {
            portError = "Port is required"
            isValid = false
        } else {
            val portNum = port.toIntOrNull()
            if (portNum == null || portNum < 1 || portNum > MAX_PORT_VALUE) {
                portError = "Invalid port (1-$MAX_PORT_VALUE)"
                isValid = false
            } else {
                portError = null
            }
        }

        return isValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.edit_profile)
                        } else {
                            stringResource(R.string.add_profile)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (validate()) {
                        val profile = if (isEditMode && existingProfile != null) {
                            existingProfile.copy(
                                name = name.trim(),
                                address = address.trim(),
                                port = port.trim()
                            )
                        } else {
                            ProxyProfile(
                                name = name.trim(),
                                address = address.trim(),
                                port = port.trim()
                            )
                        }
                        onSave(profile)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.save_profile)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = { Text(stringResource(R.string.profile_name)) },
                placeholder = { Text(stringResource(R.string.profile_name_hint)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it.filter { char -> char.isDigit() || char == '.' }
                    addressError = null
                },
                label = { Text(stringResource(R.string.hint_ip_address)) },
                placeholder = { Text("192.168.1.1") },
                isError = addressError != null,
                supportingText = addressError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter { char -> char.isDigit() }.take(MAX_PORT_LENGTH)
                    portError = null
                },
                label = { Text(stringResource(R.string.hint_port)) },
                placeholder = { Text("8080") },
                isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
