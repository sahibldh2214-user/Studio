package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.ConnectionEntity
import com.example.model.CloudAndNetworkCatalog
import com.example.model.ConnectionCategory
import com.example.model.ProviderTemplate
import com.example.ui.components.VaultFocusCard
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardFocused
import com.example.ui.theme.TextSecondary

@Composable
fun CloudHubScreen(
    connections: List<ConnectionEntity>,
    onOpenConnection: (ConnectionEntity) -> Unit,
    onSaveAndTestConnection: (ConnectionEntity, (Result<String>) -> Unit) -> Unit,
    onSaveConnectionOnly: (ConnectionEntity) -> Unit,
    onDeleteConnection: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cloudConnections = connections.filter { it.category == ConnectionCategory.CLOUD }
    var editingTemplate by remember { mutableStateOf<ProviderTemplate?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Cloud Drive Hub",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Mount Google Drive, Microsoft OneDrive, Box Cloud, Dropbox, Nextcloud, and unlimited custom WebDAV/Cloud drives.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        if (cloudConnections.isNotEmpty()) {
            item {
                Text(
                    text = "Configured Cloud Drives (${cloudConnections.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cloudConnections.forEach { conn ->
                        val accent = Color(conn.accentHex)
                        var rowHasFocus by remember { mutableStateOf(false) }
                        val rowScale by animateFloatAsState(if (rowHasFocus) 1.015f else 1f, label = "cloudRowScale")
                        val rowBg by animateColorAsState(if (rowHasFocus) ObsidianCardFocused else ObsidianCard, label = "cloudRowBg")
                        val rowBorder by animateColorAsState(if (rowHasFocus) accent else ObsidianBorder, label = "cloudRowBorder")
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = rowBg,
                            border = BorderStroke(if (rowHasFocus) 2.5.dp else 1.dp, rowBorder),
                            shadowElevation = if (rowHasFocus) 8.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(rowScale)
                                .onFocusChanged { rowHasFocus = it.hasFocus }
                                .testTag("saved_cloud_${conn.displayName}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = accent)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conn.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${conn.sourceType.name} · ${conn.endpointUrl}",
                                        style = MonoTextStyle,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                                VaultFocusPill(
                                    onClick = { onOpenConnection(conn) },
                                    selected = true,
                                    accentColor = accent,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse", style = MaterialTheme.typography.labelMedium)
                                }
                                VaultFocusPill(
                                    onClick = { onDeleteConnection(conn.id) },
                                    accentColor = CoralAlert,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${conn.displayName}", tint = CoralAlert, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Add Cloud Provider",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            val cols = 3
            val rows = CloudAndNetworkCatalog.cloudProviders.chunked(cols)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { template ->
                                val accent = Color(template.accentHex)
                                VaultFocusCard(
                                    onClick = { editingTemplate = template },
                                    accentColor = accent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_cloud_${template.id}")
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.CloudQueue, contentDescription = template.title, tint = accent, modifier = Modifier.size(30.dp))
                                            Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(template.title, style = MaterialTheme.typography.titleMedium)
                                        Text(template.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                }
            }
        }
    }

    editingTemplate?.let { template ->
        ConnectionSetupDialog(
            template = template,
            onDismiss = { editingTemplate = null },
            onSaveAndTest = onSaveAndTestConnection,
            onSaveOnly = { entity ->
                onSaveConnectionOnly(entity)
                editingTemplate = null
            }
        )
    }
}

@Composable
fun ConnectionSetupDialog(
    template: ProviderTemplate,
    initialHost: String = template.defaultEndpoint,
    initialPort: Int = template.defaultPort,
    onDismiss: () -> Unit,
    onSaveAndTest: (ConnectionEntity, (Result<String>) -> Unit) -> Unit,
    onSaveOnly: (ConnectionEntity) -> Unit
) {
    var displayName by remember { mutableStateOf(template.title) }
    var endpoint by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    var shareOrRoot by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun buildEntity() = ConnectionEntity(
        displayName = displayName.trim().ifBlank { template.title },
        sourceTypeName = template.sourceType.name,
        categoryName = template.category.name,
        endpointUrl = endpoint.trim(),
        port = portText.toIntOrNull() ?: template.defaultPort,
        shareOrRootPath = shareOrRoot.trim(),
        username = username.trim(),
        secretTokenOrPassword = secret.trim(),
        accentHex = template.accentHex
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Connect ${template.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(template.setupHint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Drive Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = {
                        Text(
                            if (template.category == ConnectionCategory.NETWORK && !template.defaultEndpoint.startsWith("http"))
                                "Host / IP Address" else "Server / API URL"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shareOrRoot,
                        onValueChange = { shareOrRoot = it },
                        label = {
                            Text(
                                if (template.sourceType == com.example.model.SourceType.SMB) "Share Name (e.g. Movies)"
                                else "Root Path / Folder ID (Optional)"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                if (template.supportsUsernamePassword) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = {
                        Text(if (template.requiresToken) "OAuth2 Bearer Access Token" else "Password / Token")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing connection...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                testResultMsg?.let { (ok, msg) ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) EmeraldLive else CoralAlert
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isTesting = true
                    testResultMsg = null
                    onSaveAndTest(buildEntity()) { res ->
                        isTesting = false
                        testResultMsg = res.fold(
                            onSuccess = { true to it },
                            onFailure = { false to (it.message ?: "Connection failed") }
                        )
                    }
                }
            ) {
                Text("Save & Test")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSaveOnly(buildEntity()) }) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
