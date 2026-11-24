package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.ConnectionEntity
import com.example.data.local.VaultUiPreferences
import com.example.model.FileCategoryFilter
import com.example.model.FileEntry
import com.example.model.FileTypeClassifier
import com.example.model.SortOrder
import com.example.model.SourceType
import com.example.model.ViewMode
import com.example.ui.BrowserUiState
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListRow
import com.example.ui.components.VaultFocusPill
import com.example.ui.theme.AmberVault
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary

@Composable
fun FileBrowserScreen(
    browserState: BrowserUiState,
    prefs: VaultUiPreferences,
    connections: List<ConnectionEntity>,
    activeImagePreview: FileEntry?,
    onNavigateUp: () -> Boolean,
    onNavigateBreadcrumb: (Int) -> Unit,
    onSwitchToLocalVault: () -> Unit,
    onSwitchToConnection: (ConnectionEntity) -> Unit,
    onOpenEntry: (FileEntry) -> Unit,
    onFilterChange: (FileCategoryFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onCycleSortOrder: () -> Unit,
    onRefresh: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameEntry: (FileEntry, String) -> Unit,
    onDeleteEntry: (FileEntry) -> Unit,
    onSetClipboard: (FileEntry, Boolean) -> Unit,
    onPasteClipboard: () -> Unit,
    onDownloadRemote: (FileEntry) -> Unit,
    onImportFile: () -> Unit,
    onImportPhotoVideo: () -> Unit,
    onMountFolder: () -> Unit,
    onGoToPcTransfer: () -> Unit,
    onDismissImagePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<FileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }

    BackHandler(enabled = browserState.breadcrumbs.size > 1) {
        onNavigateUp()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Source Selector Bar (Local Vault + Connected Clouds/LAN/SAF)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                VaultFocusPill(
                    onClick = onSwitchToLocalVault,
                    selected = browserState.sourceType == SourceType.LOCAL_VAULT,
                    accentColor = ElectricCyan,
                    modifier = Modifier.testTag("source_pill_local")
                ) {
                    Text("Local TV Vault", style = MaterialTheme.typography.labelLarge)
                }
            }
            items(connections, key = { it.id }) { conn ->
                VaultFocusPill(
                    onClick = { onSwitchToConnection(conn) },
                    selected = browserState.connectionId == conn.id && browserState.sourceType != SourceType.LOCAL_VAULT,
                    accentColor = androidx.compose.ui.graphics.Color(conn.accentHex)
                ) {
                    Text(conn.displayName, style = MaterialTheme.typography.labelLarge)
                }
            }
            item {
                VaultFocusPill(
                    onClick = onMountFolder,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("mount_folder_button")
                ) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Mount Folder", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Mount USB / Folder", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Breadcrumbs + Toolbar Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (browserState.breadcrumbs.size > 1) {
                VaultFocusPill(
                    onClick = { onNavigateUp() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("browser_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent folder", modifier = Modifier.size(18.dp))
                }
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(browserState.breadcrumbs) { idx, node ->
                    VaultFocusPill(
                        onClick = { onNavigateBreadcrumb(idx) },
                        selected = idx == browserState.breadcrumbs.lastIndex,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(node.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            VaultFocusPill(
                onClick = onToggleViewMode,
                contentPadding = PaddingValues(10.dp),
                modifier = Modifier.testTag("toggle_view_mode")
            ) {
                Icon(
                    imageVector = if (prefs.viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = "Switch Grid or List View",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            VaultFocusPill(
                onClick = onCycleSortOrder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("cycle_sort_order")
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort order", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(prefs.sortOrder.label, style = MaterialTheme.typography.labelMedium)
            }
            VaultFocusPill(
                onClick = onRefresh,
                contentPadding = PaddingValues(10.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category Pills + D-pad Friendly Search & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FileCategoryFilter.entries) { filter ->
                    VaultFocusPill(
                        onClick = { onFilterChange(filter) },
                        selected = browserState.categoryFilter == filter,
                        accentColor = ElectricCyan,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("filter_pill_${filter.name}")
                    ) {
                        Text(filter.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            VaultFocusPill(
                onClick = { showSearchDialog = true },
                selected = browserState.searchQuery.isNotBlank(),
                accentColor = ElectricCyan,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("browser_search_button")
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search files", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (browserState.searchQuery.isBlank()) "Search" else "\"${browserState.searchQuery}\"",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (browserState.searchQuery.isNotBlank()) {
                VaultFocusPill(
                    onClick = { onSearchChange("") },
                    accentColor = CoralAlert,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelMedium, color = CoralAlert)
                }
            }

            if (browserState.sourceType == SourceType.LOCAL_VAULT) {
                VaultFocusPill(
                    onClick = { showNewFolderDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("new_folder_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder", style = MaterialTheme.typography.labelMedium)
                }
                VaultFocusPill(
                    onClick = onImportFile,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("import_file_button")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (browserState.clipboard != null && browserState.sourceType == SourceType.LOCAL_VAULT) {
                VaultFocusPill(
                    onClick = onPasteClipboard,
                    selected = true,
                    accentColor = AmberVault,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("paste_clipboard_button")
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = AmberVault, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main File Grid / List Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                browserState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricCyan)
                    }
                }
                browserState.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = browserState.errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = CoralAlert
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            VaultFocusPill(onClick = onRefresh, selected = true) {
                                Text("Retry Connection")
                            }
                        }
                    }
                }
                browserState.entries.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "This folder is empty",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Send Videos, Music, or PDFs wirelessly from PC or import from device storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                VaultFocusPill(onClick = onGoToPcTransfer, selected = true, accentColor = EmeraldLive) {
                                    Text("PC → TV Transfer")
                                }
                                VaultFocusPill(onClick = onImportPhotoVideo, selected = true, accentColor = ElectricCyan) {
                                    Text("Pick Photo / Video")
                                }
                            }
                        }
                    }
                }
                prefs.viewMode == ViewMode.GRID -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val cols = when {
                            maxWidth >= 1000.dp -> 5
                            maxWidth >= 700.dp -> 4
                            maxWidth >= 480.dp -> 3
                            else -> 2
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(browserState.entries, key = { it.id }) { entry ->
                                FileGridCard(
                                    entry = entry,
                                    onOpen = { onOpenEntry(entry) },
                                    onMoreActions = { actionTarget = entry }
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(browserState.entries, key = { it.id }) { entry ->
                            FileListRow(
                                entry = entry,
                                onOpen = { onOpenEntry(entry) },
                                onMoreActions = { actionTarget = entry }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        TextInputDialog(
            title = "Create New Folder",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                onCreateFolder(name)
            }
        )
    }

    if (showSearchDialog) {
        TextInputDialog(
            title = "Search Files",
            initialValue = browserState.searchQuery,
            confirmLabel = "Search",
            onDismiss = { showSearchDialog = false },
            onConfirm = { query ->
                showSearchDialog = false
                onSearchChange(query)
            }
        )
    }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            containerColor = ObsidianCard,
            title = { Text(target.name, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${target.mediaKind.name} · ${FileTypeClassifier.formatSize(target.sizeBytes)} · ${target.sourceType.name}",
                        style = MonoTextStyle,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VaultFocusPill(
                        onClick = { actionTarget = null; onOpenEntry(target) },
                        selected = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (target.isDirectory) "Open Folder" else "Open in Studio")
                    }
                    if (!target.isDirectory && target.sourceType != SourceType.LOCAL_VAULT) {
                        VaultFocusPill(
                            onClick = { actionTarget = null; onDownloadRemote(target) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Local Downloads")
                        }
                    }
                    if (target.sourceType == SourceType.LOCAL_VAULT) {
                        VaultFocusPill(
                            onClick = { actionTarget = null; onSetClipboard(target, false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy")
                        }
                        VaultFocusPill(
                            onClick = { actionTarget = null; onSetClipboard(target, true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Move / Cut")
                        }
                        VaultFocusPill(
                            onClick = { actionTarget = null; renameTarget = target },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rename")
                        }
                        VaultFocusPill(
                            onClick = { actionTarget = null; onDeleteEntry(target) },
                            accentColor = CoralAlert,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete", color = CoralAlert)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("Close") }
            }
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename '${target.name}'",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                onRenameEntry(target, newName)
            }
        )
    }

    activeImagePreview?.let { img ->
        Dialog(onDismissRequest = onDismissImagePreview) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = img.uriString,
                    contentDescription = img.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                VaultFocusPill(onClick = onDismissImagePreview, selected = true) {
                    Text("Close Image (${img.name})")
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
