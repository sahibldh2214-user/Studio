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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConnectionEntity
import com.example.model.CloudAndNetworkCatalog
import com.example.model.ConnectionCategory
import com.example.model.DiscoveredLanHost
import com.example.model.ProviderTemplate
import com.example.model.SourceType
import com.example.ui.NetworkScanState
import com.example.ui.components.VaultFocusCard
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.AmberVault
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardFocused
import com.example.ui.theme.TextSecondary

@Composable
fun NetworkHubScreen(
    connections: List<ConnectionEntity>,
    scanState: NetworkScanState,
    onScanLan: () -> Unit,
    onQuickConnectHost: (DiscoveredLanHost) -> Unit,
    onOpenConnection: (ConnectionEntity) -> Unit,
    onSaveAndTestConnection: (ConnectionEntity, (Result<String>) -> Unit) -> Unit,
    onSaveConnectionOnly: (ConnectionEntity) -> Unit,
    onDeleteConnection: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val networkConnections = connections.filter { it.category == ConnectionCategory.NETWORK }
    var dialogConfig by remember { mutableStateOf<Triple<ProviderTemplate, String, Int>?>(null) }

    LaunchedEffect(Unit) {
        if (!scanState.hasScannedOnce && !scanState.isScanning) {
            onScanLan()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Network & LAN Shares",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Commons-Net LAN Discovery · Connect to Windows PCs, NAS boxes, FTP servers, and WebDAV shares.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (scanState.networkSummary.localIp.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "TV Interface: ${scanState.networkSummary.interfaceName} · ${scanState.networkSummary.localIp} · Subnet ${scanState.networkSummary.subnetCidr} (${scanState.networkSummary.hostCount} hosts)",
                            style = MonoTextStyle,
                            color = ElectricCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                VaultFocusPill(
                    onClick = onScanLan,
                    selected = !scanState.isScanning,
                    accentColor = EmeraldLive,
                    modifier = Modifier.testTag("scan_lan_button")
                ) {
                    if (scanState.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = EmeraldLive, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning Subnet...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Filled.Radar, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan Local Network", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (scanState.scanError != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CoralAlert.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = scanState.scanError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoralAlert,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (scanState.hasScannedOnce || scanState.isScanning) {
            item {
                Text(
                    text = when {
                        scanState.isScanning && scanState.discoveredHosts.isEmpty() ->
                            "Scanning local network devices via Commons-Net..."
                        scanState.discoveredHosts.isEmpty() ->
                            "No SMB (445), FTP (21/2121), or HTTP/WebDAV (80/5005/8080) shares detected on ${scanState.networkSummary.subnetCidr.ifBlank { "current subnet" }}."
                        else ->
                            "Discovered Local Network Devices (${scanState.discoveredHosts.size})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldLive
                )
                if (scanState.discoveredHosts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val hostRows = scanState.discoveredHosts.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        hostRows.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { host ->
                                    DiscoveredDeviceCard(
                                        host = host,
                                        onConfigure = {
                                            val template = CloudAndNetworkCatalog.networkProviders.first {
                                                it.sourceType == host.suggestedProtocol
                                            }
                                            val port = when (host.suggestedProtocol) {
                                                SourceType.FTP -> host.openPorts.firstOrNull { it == 21 || it == 2121 } ?: host.openPorts.first()
                                                SourceType.HTTP_WEBDAV -> host.openPorts.firstOrNull { it in listOf(8080, 5005, 80) } ?: host.openPorts.first()
                                                else -> 445
                                            }
                                            val endpoint = if (host.suggestedProtocol == SourceType.HTTP_WEBDAV) {
                                                "http://${host.ipAddress}:$port/"
                                            } else {
                                                host.ipAddress
                                            }
                                            dialogConfig = Triple(template, endpoint, port)
                                        },
                                        onQuickBrowse = { onQuickConnectHost(host) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (networkConnections.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Network Shares (${networkConnections.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    networkConnections.forEach { conn ->
                        val accent = Color(conn.accentHex)
                        var rowHasFocus by remember { mutableStateOf(false) }
                        val rowScale by animateFloatAsState(if (rowHasFocus) 1.015f else 1f, label = "netRowScale")
                        val rowBg by animateColorAsState(if (rowHasFocus) ObsidianCardFocused else ObsidianCard, label = "netRowBg")
                        val rowBorder by animateColorAsState(if (rowHasFocus) accent else ObsidianBorder, label = "netRowBorder")
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = rowBg,
                            border = BorderStroke(if (rowHasFocus) 2.5.dp else 1.dp, rowBorder),
                            shadowElevation = if (rowHasFocus) 8.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(rowScale)
                                .onFocusChanged { rowHasFocus = it.hasFocus }
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
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Lan, contentDescription = null, tint = accent)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conn.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${conn.sourceType.name} · ${conn.endpointUrl}:${conn.port}",
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
                                    Text("Open", style = MaterialTheme.typography.labelMedium)
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
                text = "Add Network Share",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            val cols = 3
            val rows = CloudAndNetworkCatalog.networkProviders.chunked(cols)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { template ->
                                val accent = Color(template.accentHex)
                                VaultFocusCard(
                                    onClick = {
                                        dialogConfig = Triple(template, template.defaultEndpoint, template.defaultPort)
                                    },
                                    accentColor = accent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_net_${template.id}")
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Lan, contentDescription = template.title, tint = accent, modifier = Modifier.size(30.dp))
                                            Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = accent, modifier = Modifier.size(20.dp))
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

    dialogConfig?.let { (template, host, port) ->
        ConnectionSetupDialog(
            template = template,
            initialHost = host,
            initialPort = port,
            onDismiss = { dialogConfig = null },
            onSaveAndTest = onSaveAndTestConnection,
            onSaveOnly = { entity ->
                onSaveConnectionOnly(entity)
                dialogConfig = null
            }
        )
    }
}

@Composable
private fun DiscoveredDeviceCard(
    host: DiscoveredLanHost,
    onConfigure: () -> Unit,
    onQuickBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (host.suggestedProtocol) {
        SourceType.FTP -> AmberVault
        SourceType.HTTP_WEBDAV -> ElectricCyan
        else -> EmeraldLive
    }
    val titleText = if (host.hostname != host.ipAddress) {
        "${host.hostname} (${host.ipAddress})"
    } else {
        host.ipAddress
    }
    val canQuickBrowse = host.supportsAnonymousFtp || host.suggestedProtocol == SourceType.HTTP_WEBDAV
    var cardHasFocus by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(if (cardHasFocus) 1.02f else 1f, label = "lanHostScale")
    val cardBg by animateColorAsState(if (cardHasFocus) ObsidianCardFocused else ObsidianCard, label = "lanHostBg")
    val cardBorder by animateColorAsState(
        if (cardHasFocus) accent else accent.copy(alpha = 0.42f),
        label = "lanHostBorder"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(if (cardHasFocus) 2.5.dp else 1.dp, cardBorder),
        shadowElevation = if (cardHasFocus) 8.dp else 0.dp,
        modifier = modifier
            .scale(cardScale)
            .onFocusChanged { cardHasFocus = it.hasFocus }
            .testTag("discovered_host_${host.ipAddress}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Computer, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = host.serviceBanner.ifBlank {
                            "Ports: ${host.openPorts.joinToString(", ")} · ${host.suggestedProtocol.name}"
                        },
                        style = MonoTextStyle,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (host.latencyMs > 0L) {
                    Text(
                        text = "${host.latencyMs}ms",
                        style = MonoTextStyle,
                        color = accent
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canQuickBrowse) {
                    VaultFocusPill(
                        onClick = onQuickBrowse,
                        selected = true,
                        accentColor = accent,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quick Browse", style = MaterialTheme.typography.labelMedium)
                    }
                }
                VaultFocusPill(
                    onClick = onConfigure,
                    selected = !canQuickBrowse,
                    accentColor = accent,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (canQuickBrowse) "Configure" else "Connect Share", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
