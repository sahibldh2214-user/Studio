package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.FileTypeClassifier
import com.example.transfer.TransferServerState
import com.example.ui.components.VaultFocusCard
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.AmberVault
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary

@Composable
fun PcTransferScreen(
    transferState: TransferServerState,
    qrBitmap: ImageBitmap?,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRegeneratePin: () -> Unit,
    onOpenTransfersFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { onStartServer() }
    )

    fun startWithOptionalNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        onStartServer()
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
                        text = "PC to TV Wireless File Transfer",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Built-in LAN web portal: send movies, music, PDFs, or APKs from any PC/Mac/Phone browser straight to your TV with zero cables.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                VaultFocusPill(
                    onClick = {
                        if (transferState.isRunning) onStopServer()
                        else startWithOptionalNotificationPermission()
                    },
                    selected = true,
                    accentColor = if (transferState.isRunning) CoralAlert else EmeraldLive,
                    modifier = Modifier.testTag("toggle_pc_transfer_server")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (transferState.isRunning) CoralAlert else EmeraldLive,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (transferState.isRunning) "Stop Server" else "Start Transfer Portal",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (transferState.isRunning) EmeraldLive.copy(alpha = 0.65f) else ObsidianBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QrCardBox(transferState, qrBitmap)
                    PortalDetailsColumn(
                        transferState = transferState,
                        onRegeneratePin = onRegeneratePin,
                        onOpenTransfersFolder = onOpenTransfersFolder,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (transferState.activeUploadName != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldLive.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLive)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = EmeraldLive)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Receiving file from PC: ${transferState.activeUploadName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldLive
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Wireless Transfers (${transferState.recentTransfers.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (transferState.recentTransfers.isEmpty()) {
            item {
                Text(
                    text = "No files transferred in this session yet. Start the portal and open the URL above on your PC browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            items(transferState.recentTransfers, key = { it.id }) { log ->
                VaultFocusCard(
                    onClick = onOpenTransfersFolder,
                    accentColor = ElectricCyan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = EmeraldLive)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(log.fileName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${log.direction} · ${FileTypeClassifier.formatSize(log.sizeBytes)}",
                                    style = MonoTextStyle,
                                    color = TextSecondary
                                )
                            }
                        }
                        Text("Open Folder", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCardBox(transferState: TransferServerState, qrBitmap: ImageBitmap?) {
    Box(
        modifier = Modifier
            .size(196.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (transferState.isRunning && qrBitmap != null) Color.White else Color(0xFF0E1422))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (transferState.isRunning && qrBitmap != null) {
            Image(
                bitmap = qrBitmap,
                contentDescription = "QR code to open PC transfer portal",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Start Portal\nfor QR Code", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun PortalDetailsColumn(
    transferState: TransferServerState,
    onRegeneratePin: () -> Unit,
    onOpenTransfersFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (transferState.isRunning) "Portal Online — Open on PC Browser:" else "Portal Offline",
            style = MaterialTheme.typography.labelLarge,
            color = if (transferState.isRunning) EmeraldLive else TextSecondary
        )
        Text(
            text = if (transferState.isRunning) transferState.serverUrl else "Press 'Start Transfer Portal' to bind LAN port ${transferState.port}",
            style = MaterialTheme.typography.titleLarge.merge(MonoTextStyle),
            color = ElectricCyan
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AmberVault.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "PIN: ${transferState.pin}",
                    style = MaterialTheme.typography.titleMedium.merge(MonoTextStyle),
                    color = AmberVault,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            VaultFocusPill(
                onClick = onRegeneratePin,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("regenerate_pin_button")
            ) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = AmberVault, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New PIN", style = MaterialTheme.typography.labelMedium)
            }
            VaultFocusPill(
                onClick = onOpenTransfersFolder,
                selected = true,
                accentColor = ElectricCyan,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("open_pc_transfers_folder")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Received Files", style = MaterialTheme.typography.labelMedium)
            }
        }
        transferState.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CoralAlert)
        }
    }
}
