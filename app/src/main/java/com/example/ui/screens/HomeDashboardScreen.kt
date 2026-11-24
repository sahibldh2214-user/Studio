package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConnectionEntity
import com.example.data.local.RecentItemEntity
import com.example.model.FileCategoryFilter
import com.example.model.FileTypeClassifier
import com.example.model.SourceType
import com.example.model.StorageOverview
import com.example.ui.AppDestination
import com.example.ui.components.VaultFocusCard
import com.example.ui.components.VaultFocusPill
import com.example.ui.components.styleForMediaKind
import com.example.ui.theme.AmberVault
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.TextSecondary

@Composable
fun HomeDashboardScreen(
    storage: StorageOverview,
    connections: List<ConnectionEntity>,
    recentItems: List<RecentItemEntity>,
    isTransferActive: Boolean,
    onNavigate: (AppDestination) -> Unit,
    onOpenLocalCategory: (FileCategoryFilter) -> Unit,
    onOpenConnection: (ConnectionEntity) -> Unit,
    onOpenRecent: (RecentItemEntity) -> Unit,
    onMountSafFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Cinema Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(158.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_tv_banner_1795241401818),
                    contentDescription = "VaultTV Cinema Backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ObsidianCanvas.copy(alpha = 0.92f),
                                    ObsidianCanvas.copy(alpha = 0.65f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "VaultTV Explorer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Google TV 14 File Manager · Video, Music & PDF Studio · Multi-Cloud, LAN & PC Transfer",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VaultFocusPill(
                            onClick = { onNavigate(AppDestination.STUDIO) },
                            selected = true,
                            accentColor = ElectricCyan,
                            modifier = Modifier.testTag("hero_open_studio")
                        ) {
                            Icon(Icons.Filled.Dashboard, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open All-in-One Studio", style = MaterialTheme.typography.labelLarge)
                        }
                        VaultFocusPill(
                            onClick = { onNavigate(AppDestination.TRANSFER) },
                            selected = isTransferActive,
                            accentColor = EmeraldLive,
                            modifier = Modifier.testTag("hero_open_transfer")
                        ) {
                            Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTransferActive) "PC Transfer Live" else "PC → TV Transfer",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Storage & Quick Hub Cards
        item {
            Text(
                text = "Command Center",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    HomeHubTile(
                        title = "Local TV Vault",
                        subtitle = "${storage.totalFilesCount} files · ${FileTypeClassifier.formatSize(storage.vaultUsedBytes)}",
                        icon = Icons.Filled.Storage,
                        accent = ElectricCyan,
                        tag = "hub_local_vault",
                        onClick = { onOpenLocalCategory(FileCategoryFilter.ALL) }
                    )
                }
                item {
                    HomeHubTile(
                        title = "Cloud Drives",
                        subtitle = "Drive, OneDrive, Box & more",
                        icon = Icons.Filled.CloudQueue,
                        accent = Color(0xFF38BDF8),
                        tag = "hub_clouds",
                        onClick = { onNavigate(AppDestination.CLOUDS) }
                    )
                }
                item {
                    HomeHubTile(
                        title = "Network & LAN",
                        subtitle = "SMB, FTP, WebDAV Scanner",
                        icon = Icons.Filled.Lan,
                        accent = EmeraldLive,
                        tag = "hub_network",
                        onClick = { onNavigate(AppDestination.NETWORK) }
                    )
                }
                item {
                    HomeHubTile(
                        title = "PC → TV Transfer",
                        subtitle = if (isTransferActive) "Portal Running" else "Wireless QR Upload",
                        icon = Icons.Filled.WifiTethering,
                        accent = AmberVault,
                        tag = "hub_pc_transfer",
                        onClick = { onNavigate(AppDestination.TRANSFER) }
                    )
                }
                item {
                    HomeHubTile(
                        title = "Mount Folder / USB",
                        subtitle = "Grant SAF drive access",
                        icon = Icons.Filled.CreateNewFolder,
                        accent = IndigoAccent,
                        tag = "hub_mount_usb",
                        onClick = onMountSafFolder
                    )
                }
            }
        }

        // Media & Document Category Shelf
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Media & Document Shelves",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${FileTypeClassifier.formatSize(storage.deviceFreeBytes)} free on device",
                    style = MonoTextStyle,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val usedFraction = if (storage.deviceTotalBytes > 0) {
                1f - (storage.deviceFreeBytes.toFloat() / storage.deviceTotalBytes.toFloat())
            } else 0.1f
            LinearProgressIndicator(
                progress = { usedFraction.coerceIn(0.02f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = ElectricCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    CategoryChipCard("Videos", storage.videoCount, Icons.Filled.Movie, Color(0xFF38BDF8)) {
                        onOpenLocalCategory(FileCategoryFilter.VIDEO)
                    }
                }
                item {
                    CategoryChipCard("Music", storage.audioCount, Icons.Filled.MusicNote, IndigoAccent) {
                        onOpenLocalCategory(FileCategoryFilter.AUDIO)
                    }
                }
                item {
                    CategoryChipCard("PDF Docs", storage.pdfCount, Icons.Filled.PictureAsPdf, AmberVault) {
                        onOpenLocalCategory(FileCategoryFilter.PDF)
                    }
                }
                item {
                    CategoryChipCard("Photos", storage.imageCount, Icons.Filled.Image, Color(0xFFA855F7)) {
                        onOpenLocalCategory(FileCategoryFilter.IMAGE)
                    }
                }
            }
        }

        // Connected Clouds & Mounted Drives
        if (connections.isNotEmpty()) {
            item {
                Text(
                    text = "Connected Drives & Shares",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(connections, key = { it.id }) { conn ->
                        val accent = Color(conn.accentHex)
                        val icon = when (conn.sourceType) {
                            SourceType.SAF_MOUNT -> Icons.Filled.Folder
                            SourceType.SMB, SourceType.FTP, SourceType.HTTP_WEBDAV -> Icons.Filled.Lan
                            else -> Icons.Filled.CloudQueue
                        }
                        HomeHubTile(
                            title = conn.displayName,
                            subtitle = conn.sourceType.name.replace('_', ' '),
                            icon = icon,
                            accent = accent,
                            tag = "home_conn_${conn.id}",
                            onClick = { onOpenConnection(conn) }
                        )
                    }
                }
            }
        }

        // Continue Watching / Listening / Reading
        if (recentItems.isNotEmpty()) {
            item {
                Text(
                    text = "Continue in Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentItems, key = { it.id }) { recent ->
                        val style = styleForMediaKind(recent.mediaKind)
                        VaultFocusCard(
                            onClick = { onOpenRecent(recent) },
                            accentColor = style.accent,
                            modifier = Modifier
                                .width(220.dp)
                                .testTag("recent_card_${recent.name}")
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(style.icon, contentDescription = null, tint = style.accent, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(style.badgeLabel, style = MaterialTheme.typography.labelSmall, color = style.accent)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = recent.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FileTypeClassifier.formatSize(recent.sizeBytes),
                                    style = MonoTextStyle,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHubTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    VaultFocusCard(
        onClick = onClick,
        accentColor = accent,
        modifier = Modifier
            .width(220.dp)
            .testTag(tag)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryChipCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    VaultFocusCard(
        onClick = onClick,
        accentColor = accent,
        modifier = Modifier
            .width(165.dp)
            .testTag("category_card_$title"),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("$count items", style = MonoTextStyle, color = TextSecondary)
            }
        }
    }
}
