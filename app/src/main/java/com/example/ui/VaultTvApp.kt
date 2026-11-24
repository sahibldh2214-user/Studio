package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FileCategoryFilter
import com.example.model.SortOrder
import com.example.model.SourceType
import com.example.model.ViewMode
import com.example.ui.components.VaultFocusPill
import com.example.ui.screens.CloudHubScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.NetworkHubScreen
import com.example.ui.screens.PcTransferScreen
import com.example.ui.screens.studio.AllInOneStudioScreen
import com.example.ui.screens.studio.VideoTheatrePane
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

@Composable
fun VaultTvApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentDest by viewModel.currentDestination.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val storageOverview by viewModel.storageOverview.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val recentItems by viewModel.recentItems.collectAsStateWithLifecycle()
    val uiPrefs by viewModel.uiPreferences.collectAsStateWithLifecycle()
    val studioPaneMode by viewModel.studioPaneMode.collectAsStateWithLifecycle()
    val studioMediaState by viewModel.studioMediaState.collectAsStateWithLifecycle()
    val pdfState by viewModel.pdfState.collectAsStateWithLifecycle()
    val networkScanState by viewModel.networkScanState.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()
    val activeImagePreview by viewModel.activeImagePreview.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusToast) {
        statusToast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissToast()
        }
    }

    // SAF Directory Picker (USB / Device Folder Mount)
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val docName = DocumentFile.fromTreeUri(context, it)?.name ?: "Mounted Folder"
                viewModel.mountSafTree(it, docName)
            }
        }
    )

    // SAF Document Importer (Videos, Audio, PDFs, Documents)
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.importLocalUri(it) }
        }
    )

    // Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.importLocalUri(it) }
        }
    )

    // Direct Video / Audio Pickers for Media3 ExoPlayer playback
    val directVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.openDirectMediaUri(it, isVideo = true) }
        }
    )

    val directAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.openDirectMediaUri(it, isVideo = false) }
        }
    )

    val homeRailFocusRequester = remember { FocusRequester() }
    var isVideoFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { homeRailFocusRequester.requestFocus() }
    }

    BackHandler(enabled = isVideoFullscreen) {
        isVideoFullscreen = false
    }

    BackHandler(enabled = !isVideoFullscreen && currentDest != AppDestination.HOME) {
        viewModel.navigateTo(AppDestination.HOME)
    }

    Scaffold(
        containerColor = ObsidianCanvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        if (isVideoFullscreen && currentDest == AppDestination.STUDIO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianCanvas)
                    .padding(innerPadding)
                    .padding(10.dp)
            ) {
                VideoTheatrePane(
                    controller = viewModel.mediaController,
                    mediaState = studioMediaState,
                    onPickVideoFromFiles = {
                        isVideoFullscreen = false
                        viewModel.openSource(
                            sourceType = SourceType.LOCAL_VAULT,
                            title = "Local TV Vault",
                            filter = FileCategoryFilter.VIDEO
                        )
                    },
                    onPickDeviceVideo = { directVideoLauncher.launch(arrayOf("video/*")) },
                    isFullscreen = true,
                    onToggleFullscreen = { isVideoFullscreen = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianCanvas)
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            TvNavigationSidebar(
                currentDest = currentDest,
                isTransferRunning = transferState.isRunning,
                activeAudioName = studioMediaState.activeAudio?.name,
                isAudioPlaying = studioMediaState.isAudioPlaying,
                homeFocusRequester = homeRailFocusRequester,
                onSelect = { viewModel.navigateTo(it) },
                onToggleAudio = { viewModel.mediaController.toggleAudioPlayPause() }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                    when (currentDest) {
                        AppDestination.HOME -> HomeDashboardScreen(
                            storage = storageOverview,
                            connections = connections,
                            recentItems = recentItems,
                            isTransferActive = transferState.isRunning,
                            onNavigate = { viewModel.navigateTo(it) },
                            onOpenLocalCategory = { filter ->
                                viewModel.openSource(
                                    sourceType = SourceType.LOCAL_VAULT,
                                    title = "Local TV Vault",
                                    filter = filter
                                )
                            },
                            onOpenConnection = { conn ->
                                viewModel.openSource(conn.sourceType, conn.id, conn.displayName)
                            },
                            onOpenRecent = { viewModel.openRecentInStudio(it) },
                            onMountSafFolder = { safTreeLauncher.launch(null) }
                        )

                        AppDestination.FILES -> FileBrowserScreen(
                            browserState = browserState,
                            prefs = uiPrefs,
                            connections = connections,
                            activeImagePreview = activeImagePreview,
                            onNavigateUp = { viewModel.navigateUp() },
                            onNavigateBreadcrumb = { viewModel.navigateToBreadcrumb(it) },
                            onSwitchToLocalVault = {
                                viewModel.openSource(SourceType.LOCAL_VAULT, title = "Local TV Vault")
                            },
                            onSwitchToConnection = { conn ->
                                viewModel.openSource(conn.sourceType, conn.id, conn.displayName)
                            },
                            onOpenEntry = { viewModel.openFile(it) },
                            onFilterChange = { viewModel.setCategoryFilter(it) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onToggleViewMode = {
                                val next = if (uiPrefs.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                                viewModel.setViewMode(next)
                            },
                            onCycleSortOrder = {
                                val all = SortOrder.entries
                                val next = all[(uiPrefs.sortOrder.ordinal + 1) % all.size]
                                viewModel.setSortOrder(next)
                            },
                            onRefresh = { viewModel.refreshBrowser() },
                            onCreateFolder = { viewModel.createLocalFolder(it) },
                            onRenameEntry = { entry, name -> viewModel.renameEntry(entry, name) },
                            onDeleteEntry = { viewModel.deleteEntry(it) },
                            onSetClipboard = { entry, isMove -> viewModel.setClipboard(entry, isMove) },
                            onPasteClipboard = { viewModel.pasteClipboard() },
                            onDownloadRemote = { viewModel.downloadRemoteEntry(it) },
                            onImportFile = {
                                openDocLauncher.launch(arrayOf("*/*"))
                            },
                            onImportPhotoVideo = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            onMountFolder = { safTreeLauncher.launch(null) },
                            onGoToPcTransfer = { viewModel.navigateTo(AppDestination.TRANSFER) },
                            onDismissImagePreview = { viewModel.dismissImagePreview() }
                        )

                        AppDestination.STUDIO -> AllInOneStudioScreen(
                            paneMode = studioPaneMode,
                            mediaController = viewModel.mediaController,
                            mediaState = studioMediaState,
                            pdfState = pdfState,
                            onChangePaneMode = { viewModel.setStudioPaneMode(it) },
                            onBrowseCategory = { filter ->
                                viewModel.openSource(
                                    sourceType = SourceType.LOCAL_VAULT,
                                    title = "Local TV Vault",
                                    filter = filter
                                )
                            },
                            onPickDeviceVideo = { directVideoLauncher.launch(arrayOf("video/*")) },
                            onPickDeviceAudio = { directAudioLauncher.launch(arrayOf("audio/*")) },
                            onOpenQueueFile = { viewModel.openFile(it) },
                            onPrevPdfPage = { viewModel.prevPdfPage() },
                            onNextPdfPage = { viewModel.nextPdfPage() },
                            onCyclePdfZoom = { viewModel.cyclePdfZoom() },
                            onToggleVideoFullscreen = { isVideoFullscreen = true }
                        )

                        AppDestination.CLOUDS -> CloudHubScreen(
                            connections = connections,
                            onOpenConnection = { conn ->
                                viewModel.openSource(conn.sourceType, conn.id, conn.displayName)
                            },
                            onSaveAndTestConnection = { entity, cb ->
                                viewModel.saveAndTestConnection(entity, cb)
                            },
                            onSaveConnectionOnly = { entity ->
                                viewModel.saveConnectionOnly(entity)
                            },
                            onDeleteConnection = { viewModel.deleteConnection(it) }
                        )

                        AppDestination.NETWORK -> NetworkHubScreen(
                            connections = connections,
                            scanState = networkScanState,
                            onScanLan = { viewModel.scanLan() },
                            onQuickConnectHost = { viewModel.quickConnectLanHost(it) },
                            onOpenConnection = { conn ->
                                viewModel.openSource(conn.sourceType, conn.id, conn.displayName)
                            },
                            onSaveAndTestConnection = { entity, cb ->
                                viewModel.saveAndTestConnection(entity, cb)
                            },
                            onSaveConnectionOnly = { entity ->
                                viewModel.saveConnectionOnly(entity)
                            },
                            onDeleteConnection = { viewModel.deleteConnection(it) }
                        )

                        AppDestination.TRANSFER -> PcTransferScreen(
                            transferState = transferState,
                            qrBitmap = qrBitmap,
                            onStartServer = { viewModel.startPcTransferServer() },
                            onStopServer = { viewModel.stopPcTransferServer() },
                            onRegeneratePin = { viewModel.regenerateTransferPin() },
                            onOpenTransfersFolder = {
                                viewModel.openSource(
                                    sourceType = SourceType.LOCAL_VAULT,
                                    title = "Local TV Vault",
                                    initialPath = "PC_Transfers"
                                )
                            }
                        )
                    }
                }
            }
        }
        }
}

@Composable
private fun TvNavigationSidebar(
    currentDest: AppDestination,
    isTransferRunning: Boolean,
    activeAudioName: String?,
    isAudioPlaying: Boolean,
    homeFocusRequester: FocusRequester,
    onSelect: (AppDestination) -> Unit,
    onToggleAudio: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(198.dp)
            .fillMaxHeight()
            .padding(end = 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "VaultTV",
                style = MaterialTheme.typography.headlineSmall,
                color = ElectricCyan,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Text(
                text = "GOOGLE TV 14 · D-PAD",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            AppDestination.entries.forEach { dest ->
                val selected = currentDest == dest
                val icon = iconForDestination(dest, selected)
                val accent = if (dest == AppDestination.TRANSFER && isTransferRunning) EmeraldLive else ElectricCyan
                VaultFocusPill(
                    onClick = { onSelect(dest) },
                    selected = selected,
                    accentColor = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (dest == AppDestination.HOME) Modifier.focusRequester(homeFocusRequester)
                            else Modifier
                        )
                        .testTag("nav_rail_${dest.route}"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = dest.label,
                        tint = if (selected) accent else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeAudioName != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianCanvas.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeAudioName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VaultFocusPill(
                                onClick = onToggleAudio,
                                selected = true,
                                accentColor = IndigoAccent,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isAudioPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isAudioPlaying) "Pause music" else "Play music",
                                    tint = IndigoAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isAudioPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelSmall)
                            }
                            VaultFocusPill(
                                onClick = { onSelect(AppDestination.STUDIO) },
                                accentColor = ElectricCyan,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Deck", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCanvas.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "D-Pad: Move · Center: Open\nMenu/Hold: Options · Back: Up",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun iconForDestination(dest: AppDestination, selected: Boolean): ImageVector = when (dest) {
    AppDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    AppDestination.FILES -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    AppDestination.STUDIO -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    AppDestination.CLOUDS -> if (selected) Icons.Filled.CloudQueue else Icons.Outlined.CloudQueue
    AppDestination.NETWORK -> if (selected) Icons.Filled.Lan else Icons.Outlined.Lan
    AppDestination.TRANSFER -> if (selected) Icons.Filled.WifiTethering else Icons.Outlined.WifiTethering
}
