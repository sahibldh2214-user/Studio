package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConnectionEntity
import com.example.data.local.RecentItemEntity
import com.example.data.local.VaultUiPreferences
import com.example.data.repository.VaultRepository
import com.example.media.MediaPlaybackController
import com.example.media.PdfDocumentSession
import com.example.media.StudioMediaState
import com.example.model.BreadcrumbNode
import com.example.model.ConnectionCategory
import com.example.model.DiscoveredLanHost
import com.example.model.FileCategoryFilter
import com.example.model.FileEntry
import com.example.model.FileTypeClassifier
import com.example.model.LocalNetworkSummary
import com.example.model.MediaKind
import com.example.model.SortOrder
import com.example.model.SourceType
import com.example.model.StorageOverview
import com.example.model.ViewMode
import com.example.transfer.QrCodeGenerator
import com.example.transfer.TransferServerState
import com.example.transfer.WifiTransferForegroundService
import com.example.transfer.WifiTransferServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    FILES("files", "Files"),
    STUDIO("studio", "Studio"),
    CLOUDS("clouds", "Clouds"),
    NETWORK("network", "Network"),
    TRANSFER("transfer", "PC Transfer")
}

enum class StudioPaneMode(val label: String) {
    ALL_IN_ONE("All-in-One"),
    VIDEO("Video Theatre"),
    MUSIC("Music Deck"),
    PDF("PDF Reader")
}

data class ClipboardAction(
    val entry: FileEntry,
    val isMove: Boolean
)

data class BrowserUiState(
    val sourceType: SourceType = SourceType.LOCAL_VAULT,
    val connectionId: Long = 0L,
    val sourceTitle: String = "Local TV Vault",
    val currentPath: String = "",
    val breadcrumbs: List<BreadcrumbNode> = listOf(BreadcrumbNode("Vault", "")),
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categoryFilter: FileCategoryFilter = FileCategoryFilter.ALL,
    val searchQuery: String = "",
    val clipboard: ClipboardAction? = null
)

data class PdfViewerState(
    val activePdf: FileEntry? = null,
    val currentPage: Int = 0,
    val pageCount: Int = 0,
    val renderedBitmap: Bitmap? = null,
    val zoomScale: Float = 1.0f,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class NetworkScanState(
    val isScanning: Boolean = false,
    val discoveredHosts: List<DiscoveredLanHost> = emptyList(),
    val hasScannedOnce: Boolean = false,
    val networkSummary: LocalNetworkSummary = LocalNetworkSummary(),
    val scanError: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)
    val mediaController = MediaPlaybackController(application)
    private val pdfSession = PdfDocumentSession(application)
    private val transferServer = WifiTransferServer(repository.localEngine)

    val connections: StateFlow<List<ConnectionEntity>> = repository.connectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentItems: StateFlow<List<RecentItemEntity>> = repository.recentItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<RecentItemEntity>> = repository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiPreferences: StateFlow<VaultUiPreferences> = repository.uiPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultUiPreferences())

    val studioMediaState: StateFlow<StudioMediaState> = mediaController.state
    val transferState: StateFlow<TransferServerState> = transferServer.state

    private val _currentDestination = MutableStateFlow(AppDestination.HOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _browserState = MutableStateFlow(BrowserUiState())
    val browserState: StateFlow<BrowserUiState> = _browserState.asStateFlow()

    private val _storageOverview = MutableStateFlow(StorageOverview())
    val storageOverview: StateFlow<StorageOverview> = _storageOverview.asStateFlow()

    private val _studioPaneMode = MutableStateFlow(StudioPaneMode.ALL_IN_ONE)
    val studioPaneMode: StateFlow<StudioPaneMode> = _studioPaneMode.asStateFlow()

    private val _pdfState = MutableStateFlow(PdfViewerState())
    val pdfState: StateFlow<PdfViewerState> = _pdfState.asStateFlow()

    private val _networkScanState = MutableStateFlow(NetworkScanState())
    val networkScanState: StateFlow<NetworkScanState> = _networkScanState.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _qrBitmap = MutableStateFlow<androidx.compose.ui.graphics.ImageBitmap?>(null)
    val qrBitmap: StateFlow<androidx.compose.ui.graphics.ImageBitmap?> = _qrBitmap.asStateFlow()

    private val _activeImagePreview = MutableStateFlow<FileEntry?>(null)
    val activeImagePreview: StateFlow<FileEntry?> = _activeImagePreview.asStateFlow()

    init {
        transferServer.onFileReceivedCallback = {
            viewModelScope.launch {
                refreshStorageOverview()
                if (_browserState.value.sourceType == SourceType.LOCAL_VAULT) {
                    refreshBrowser()
                }
            }
        }
        refreshStorageOverview()
        refreshBrowser()
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun dismissToast() {
        _statusToast.value = null
    }

    fun showToast(message: String) {
        _statusToast.value = message
    }

    fun refreshStorageOverview() {
        viewModelScope.launch {
            _storageOverview.value = repository.getStorageOverview()
        }
    }

    fun openSource(
        sourceType: SourceType,
        connectionId: Long = 0L,
        title: String = "Local TV Vault",
        initialPath: String = "",
        filter: FileCategoryFilter = FileCategoryFilter.ALL
    ) {
        _browserState.update {
            it.copy(
                sourceType = sourceType,
                connectionId = connectionId,
                sourceTitle = title,
                currentPath = initialPath,
                breadcrumbs = listOf(BreadcrumbNode(title, initialPath)),
                categoryFilter = filter,
                searchQuery = "",
                errorMessage = null
            )
        }
        _currentDestination.value = AppDestination.FILES
        refreshBrowser()
    }

    fun navigateIntoFolder(folder: FileEntry) {
        val current = _browserState.value
        val nextCrumbs = current.breadcrumbs + BreadcrumbNode(folder.name, folder.path)
        _browserState.update {
            it.copy(
                currentPath = folder.path,
                breadcrumbs = nextCrumbs,
                searchQuery = "",
                categoryFilter = FileCategoryFilter.ALL,
                errorMessage = null
            )
        }
        refreshBrowser()
    }

    fun navigateUp(): Boolean {
        val current = _browserState.value
        if (current.breadcrumbs.size <= 1) return false
        val trimmed = current.breadcrumbs.dropLast(1)
        _browserState.update {
            it.copy(
                currentPath = trimmed.last().path,
                breadcrumbs = trimmed,
                errorMessage = null
            )
        }
        refreshBrowser()
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        val current = _browserState.value
        if (index !in current.breadcrumbs.indices) return
        val trimmed = current.breadcrumbs.take(index + 1)
        _browserState.update {
            it.copy(
                currentPath = trimmed.last().path,
                breadcrumbs = trimmed,
                errorMessage = null
            )
        }
        refreshBrowser()
    }

    fun setCategoryFilter(filter: FileCategoryFilter) {
        _browserState.update { it.copy(categoryFilter = filter) }
        refreshBrowser()
    }

    fun setSearchQuery(query: String) {
        _browserState.update { it.copy(searchQuery = query) }
        refreshBrowser()
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { repository.prefs.setViewMode(mode) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            repository.prefs.setSortOrder(order)
            refreshBrowser()
        }
    }

    fun setStudioSplitPane(enabled: Boolean) {
        viewModelScope.launch { repository.prefs.setStudioSplitPane(enabled) }
    }

    fun setStudioPaneMode(mode: StudioPaneMode) {
        _studioPaneMode.value = mode
    }

    fun refreshBrowser() {
        val state = _browserState.value
        val sort = uiPreferences.value.sortOrder
        viewModelScope.launch {
            _browserState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.browseEntries(
                sourceType = state.sourceType,
                connectionId = state.connectionId,
                path = state.currentPath,
                sortOrder = sort,
                filter = state.categoryFilter,
                query = state.searchQuery
            )
            result.fold(
                onSuccess = { list ->
                    _browserState.update { it.copy(entries = list, isLoading = false) }
                },
                onFailure = { err ->
                    _browserState.update {
                        it.copy(
                            entries = emptyList(),
                            isLoading = false,
                            errorMessage = err.message ?: "Unable to list files"
                        )
                    }
                }
            )
        }
    }

    fun openFile(entry: FileEntry) {
        if (entry.isDirectory) {
            navigateIntoFolder(entry)
            return
        }
        viewModelScope.launch {
            repository.recordRecent(entry)
            when (entry.mediaKind) {
                MediaKind.VIDEO -> openVideoInStudio(entry)
                MediaKind.AUDIO -> openAudioInStudio(entry)
                MediaKind.PDF -> openPdfInStudio(entry)
                MediaKind.IMAGE -> _activeImagePreview.value = entry
                else -> showToast("File ready: ${entry.name} (${FileTypeClassifier.formatSize(entry.sizeBytes)})")
            }
        }
    }

    fun dismissImagePreview() {
        _activeImagePreview.value = null
    }

    fun openDirectMediaUri(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val displayName = androidx.documentfile.provider.DocumentFile.fromSingleUri(ctx, uri)?.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: if (isVideo) "Video Stream" else "Audio Track"
            val entry = FileEntry(
                id = "direct:$uri",
                name = displayName,
                path = uri.toString(),
                uriString = uri.toString(),
                isDirectory = false,
                sizeBytes = 0L,
                lastModifiedMs = System.currentTimeMillis(),
                mimeType = ctx.contentResolver.getType(uri)
                    ?: if (isVideo) "video/*" else "audio/*",
                mediaKind = if (isVideo) MediaKind.VIDEO else MediaKind.AUDIO,
                sourceType = SourceType.SAF_MOUNT
            )
            repository.recordRecent(entry)
            if (isVideo) {
                mediaController.playVideo(entry, uri, listOf(entry to uri))
                _studioPaneMode.value = StudioPaneMode.VIDEO
            } else {
                mediaController.playAudio(entry, uri, listOf(entry to uri))
                _studioPaneMode.value = StudioPaneMode.MUSIC
            }
            _currentDestination.value = AppDestination.STUDIO
        }
    }

    private suspend fun openVideoInStudio(entry: FileEntry) {
        val videoNeighbors = _browserState.value.entries.filter { it.mediaKind == MediaKind.VIDEO }
            .ifEmpty { listOf(entry) }
        val resolvedUri = repository.resolvePlayableOrReadableUri(entry, forceLocalCache = false)
        resolvedUri.fold(
            onSuccess = { uri ->
                val queuePairs = videoNeighbors.mapNotNull { item ->
                    if (item.id == entry.id) item to uri
                    else if (item.sourceType == SourceType.LOCAL_VAULT || item.sourceType == SourceType.SAF_MOUNT) {
                        item to Uri.parse(item.uriString)
                    } else null
                }
                mediaController.playVideo(entry, uri, queuePairs)
                if (_studioPaneMode.value == StudioPaneMode.MUSIC || _studioPaneMode.value == StudioPaneMode.PDF) {
                    _studioPaneMode.value = StudioPaneMode.ALL_IN_ONE
                }
                _currentDestination.value = AppDestination.STUDIO
            },
            onFailure = { showToast("Could not play video: ${it.message}") }
        )
    }

    private suspend fun openAudioInStudio(entry: FileEntry) {
        val audioNeighbors = _browserState.value.entries.filter { it.mediaKind == MediaKind.AUDIO }
            .ifEmpty { listOf(entry) }
        val resolvedUri = repository.resolvePlayableOrReadableUri(entry, forceLocalCache = false)
        resolvedUri.fold(
            onSuccess = { uri ->
                val queuePairs = audioNeighbors.mapNotNull { item ->
                    if (item.id == entry.id) item to uri
                    else if (item.sourceType == SourceType.LOCAL_VAULT || item.sourceType == SourceType.SAF_MOUNT) {
                        item to Uri.parse(item.uriString)
                    } else null
                }
                mediaController.playAudio(entry, uri, queuePairs)
                if (_studioPaneMode.value == StudioPaneMode.VIDEO) {
                    _studioPaneMode.value = StudioPaneMode.ALL_IN_ONE
                }
                _currentDestination.value = AppDestination.STUDIO
            },
            onFailure = { showToast("Could not play audio: ${it.message}") }
        )
    }

    private suspend fun openPdfInStudio(entry: FileEntry) {
        _pdfState.update {
            it.copy(
                activePdf = entry,
                isLoading = true,
                errorMessage = null,
                currentPage = 0,
                zoomScale = 1.0f
            )
        }
        _currentDestination.value = AppDestination.STUDIO
        if (_studioPaneMode.value == StudioPaneMode.VIDEO || _studioPaneMode.value == StudioPaneMode.MUSIC) {
            _studioPaneMode.value = StudioPaneMode.ALL_IN_ONE
        }
        val uriResult = repository.resolvePlayableOrReadableUri(entry, forceLocalCache = true)
        uriResult.fold(
            onSuccess = { uri ->
                pdfSession.open(uri).fold(
                    onSuccess = { count ->
                        _pdfState.update { it.copy(pageCount = count, currentPage = 0) }
                        renderPdfPage(0)
                    },
                    onFailure = { err ->
                        _pdfState.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to open PDF")
                        }
                    }
                )
            },
            onFailure = { err ->
                _pdfState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "Failed to fetch PDF")
                }
            }
        )
    }

    fun renderPdfPage(pageIndex: Int) {
        val total = _pdfState.value.pageCount
        if (total <= 0) return
        val clamped = pageIndex.coerceIn(0, total - 1)
        viewModelScope.launch {
            _pdfState.update { it.copy(isLoading = true, currentPage = clamped) }
            val bmp = pdfSession.renderPage(clamped)
            _pdfState.update {
                it.copy(
                    renderedBitmap = bmp,
                    isLoading = false,
                    errorMessage = if (bmp == null) "Could not render page ${clamped + 1}" else null
                )
            }
        }
    }

    fun nextPdfPage() = renderPdfPage(_pdfState.value.currentPage + 1)
    fun prevPdfPage() = renderPdfPage(_pdfState.value.currentPage - 1)

    fun cyclePdfZoom() {
        val levels = listOf(1.0f, 1.4f, 1.85f)
        val idx = levels.indexOf(_pdfState.value.zoomScale)
        val next = levels[(idx + 1) % levels.size]
        _pdfState.update { it.copy(zoomScale = next) }
    }

    fun openRecentInStudio(recent: RecentItemEntity) {
        val entry = FileEntry(
            id = recent.id,
            name = recent.name,
            path = recent.path,
            uriString = recent.uriString,
            isDirectory = false,
            sizeBytes = recent.sizeBytes,
            lastModifiedMs = recent.lastOpenedMs,
            mimeType = recent.mimeType,
            mediaKind = recent.mediaKind,
            sourceType = recent.sourceType,
            connectionId = recent.connectionId
        )
        openFile(entry)
    }

    fun createLocalFolder(folderName: String) {
        if (_browserState.value.sourceType != SourceType.LOCAL_VAULT) {
            showToast("Switch to Local TV Vault to create folders")
            return
        }
        viewModelScope.launch {
            repository.localEngine.createFolder(_browserState.value.currentPath, folderName).fold(
                onSuccess = {
                    showToast("Created folder '$folderName'")
                    refreshBrowser()
                },
                onFailure = { showToast(it.message ?: "Folder creation failed") }
            )
        }
    }

    fun renameEntry(entry: FileEntry, newName: String) {
        if (entry.sourceType != SourceType.LOCAL_VAULT) {
            showToast("Renaming is supported for Local TV Vault files")
            return
        }
        viewModelScope.launch {
            repository.localEngine.renameLocalEntry(entry.path, newName).fold(
                onSuccess = {
                    showToast("Renamed to $newName")
                    refreshBrowser()
                },
                onFailure = { showToast(it.message ?: "Rename failed") }
            )
        }
    }

    fun deleteEntry(entry: FileEntry) {
        if (entry.sourceType != SourceType.LOCAL_VAULT) {
            showToast("Deleting remote files is disabled for safety")
            return
        }
        viewModelScope.launch {
            repository.localEngine.deleteLocalEntry(entry.path).fold(
                onSuccess = {
                    showToast("Deleted ${entry.name}")
                    refreshStorageOverview()
                    refreshBrowser()
                },
                onFailure = { showToast(it.message ?: "Delete failed") }
            )
        }
    }

    fun setClipboard(entry: FileEntry, isMove: Boolean) {
        _browserState.update { it.copy(clipboard = ClipboardAction(entry, isMove)) }
        showToast("${if (isMove) "Cut" else "Copied"} '${entry.name}'. Navigate to destination and Paste.")
    }

    fun pasteClipboard() {
        val clip = _browserState.value.clipboard ?: return
        if (_browserState.value.sourceType != SourceType.LOCAL_VAULT) {
            showToast("Paste target must be inside Local TV Vault")
            return
        }
        viewModelScope.launch {
            if (clip.entry.sourceType == SourceType.LOCAL_VAULT) {
                repository.localEngine.copyOrMoveLocalEntry(
                    sourceRelativePath = clip.entry.path,
                    targetDirRelativePath = _browserState.value.currentPath,
                    move = clip.isMove
                ).fold(
                    onSuccess = {
                        _browserState.update { it.copy(clipboard = null) }
                        showToast("Pasted ${clip.entry.name}")
                        refreshStorageOverview()
                        refreshBrowser()
                    },
                    onFailure = { showToast(it.message ?: "Paste failed") }
                )
            } else {
                repository.downloadRemoteToLocalVault(clip.entry).fold(
                    onSuccess = {
                        _browserState.update { it.copy(clipboard = null) }
                        showToast("Saved ${clip.entry.name} to Local Downloads")
                        refreshStorageOverview()
                        refreshBrowser()
                    },
                    onFailure = { showToast(it.message ?: "Download failed") }
                )
            }
        }
    }

    fun downloadRemoteEntry(entry: FileEntry) {
        viewModelScope.launch {
            showToast("Downloading ${entry.name} to Local Vault...")
            repository.downloadRemoteToLocalVault(entry).fold(
                onSuccess = {
                    showToast("Saved '${entry.name}' to Downloads")
                    refreshStorageOverview()
                },
                onFailure = { showToast("Download failed: ${it.message}") }
            )
        }
    }

    fun importLocalUri(uri: Uri) {
        viewModelScope.launch {
            val targetDir = if (_browserState.value.sourceType == SourceType.LOCAL_VAULT) {
                _browserState.value.currentPath
            } else "Downloads"
            repository.localEngine.importContentUri(uri, targetDir).fold(
                onSuccess = { entry ->
                    showToast("Imported ${entry.name}")
                    refreshStorageOverview()
                    if (_browserState.value.sourceType == SourceType.LOCAL_VAULT) refreshBrowser()
                },
                onFailure = { showToast("Import failed: ${it.message}") }
            )
        }
    }

    fun mountSafTree(uri: Uri, label: String) {
        viewModelScope.launch {
            val conn = ConnectionEntity(
                displayName = label.ifBlank { "Device / USB Folder" },
                sourceTypeName = SourceType.SAF_MOUNT.name,
                categoryName = ConnectionCategory.LOCAL_MOUNT.name,
                endpointUrl = uri.toString(),
                accentHex = 0xFF22D3EE
            )
            val id = repository.saveConnection(conn)
            showToast("Mounted '${conn.displayName}'")
            openSource(SourceType.SAF_MOUNT, id, conn.displayName, "")
        }
    }

    fun saveAndTestConnection(entity: ConnectionEntity, onTested: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val savedId = repository.saveConnection(entity)
            val persisted = entity.copy(id = savedId)
            val result = repository.testConnection(persisted)
            onTested(result)
        }
    }

    fun saveConnectionOnly(entity: ConnectionEntity) {
        viewModelScope.launch {
            repository.saveConnection(entity)
            showToast("Saved '${entity.displayName}'")
        }
    }

    fun deleteConnection(id: Long) {
        viewModelScope.launch {
            repository.deleteConnection(id)
            showToast("Removed connection")
        }
    }

    fun scanLan() {
        if (_networkScanState.value.isScanning) return
        viewModelScope.launch {
            val preSummary = runCatching { repository.getLocalNetworkSummary() }
                .getOrDefault(LocalNetworkSummary())
            _networkScanState.update {
                it.copy(isScanning = true, networkSummary = preSummary, scanError = null)
            }
            runCatching {
                repository.scanLanWithSummary()
            }.fold(
                onSuccess = { (summary, hosts) ->
                    val err = if (summary.localIp.isBlank()) {
                        "No active Wi-Fi or Ethernet IPv4 network interface detected."
                    } else null
                    _networkScanState.update {
                        it.copy(
                            isScanning = false,
                            discoveredHosts = hosts,
                            hasScannedOnce = true,
                            networkSummary = summary,
                            scanError = err
                        )
                    }
                },
                onFailure = { throwable ->
                    _networkScanState.update {
                        it.copy(
                            isScanning = false,
                            hasScannedOnce = true,
                            scanError = throwable.message ?: "LAN scan failed"
                        )
                    }
                }
            )
        }
    }

    fun quickConnectLanHost(host: DiscoveredLanHost) {
        viewModelScope.launch {
            val label = if (host.hostname != host.ipAddress) host.hostname else "LAN ${host.ipAddress}"
            val port = when (host.suggestedProtocol) {
                SourceType.FTP -> host.openPorts.firstOrNull { it == 21 || it == 2121 } ?: 21
                SourceType.HTTP_WEBDAV -> host.openPorts.firstOrNull { it in listOf(8080, 5005, 80) } ?: 8080
                else -> 445
            }
            val endpoint = if (host.suggestedProtocol == SourceType.HTTP_WEBDAV) {
                "http://${host.ipAddress}:$port/"
            } else {
                host.ipAddress
            }
            val accent = when (host.suggestedProtocol) {
                SourceType.FTP -> 0xFFF59E0B
                SourceType.HTTP_WEBDAV -> 0xFF22D3EE
                else -> 0xFF10B981
            }
            val entity = ConnectionEntity(
                displayName = label,
                sourceTypeName = host.suggestedProtocol.name,
                categoryName = ConnectionCategory.NETWORK.name,
                endpointUrl = endpoint,
                port = port,
                username = if (host.supportsAnonymousFtp) "anonymous" else "",
                secretTokenOrPassword = if (host.supportsAnonymousFtp) "vaulttv@local" else "",
                accentHex = accent
            )
            val savedId = repository.saveConnection(entity)
            openSource(host.suggestedProtocol, savedId, label, "")
        }
    }

    fun startPcTransferServer() {
        val prefs = uiPreferences.value
        transferServer.start(preferredPort = prefs.transferPort, pin = prefs.transferPin)
        viewModelScope.launch {
            // Wait briefly for socket bind to publish URL & generate QR
            kotlinx.coroutines.delay(200)
            val state = transferServer.state.value
            if (state.serverUrl.isNotBlank()) {
                _qrBitmap.value = QrCodeGenerator.generateQrBitmap(state.serverUrl, 480)
                val ctx = getApplication<Application>()
                val intent = Intent(ctx, WifiTransferForegroundService::class.java).apply {
                    putExtra(WifiTransferForegroundService.EXTRA_URL, state.serverUrl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { ctx.startForegroundService(intent) }
                } else {
                    runCatching { ctx.startService(intent) }
                }
            }
        }
    }

    fun stopPcTransferServer() {
        transferServer.stop()
        val ctx = getApplication<Application>()
        val stopIntent = Intent(ctx, WifiTransferForegroundService::class.java).apply {
            action = WifiTransferForegroundService.ACTION_STOP
        }
        runCatching { ctx.startService(stopIntent) }
    }

    fun regenerateTransferPin() {
        val newPin = (1000..9999).random().toString()
        viewModelScope.launch {
            repository.prefs.setTransferPin(newPin)
            if (transferServer.state.value.isRunning) {
                transferServer.stop()
                transferServer.start(uiPreferences.value.transferPort, newPin)
                kotlinx.coroutines.delay(200)
                val url = transferServer.state.value.serverUrl
                if (url.isNotBlank()) {
                    _qrBitmap.value = QrCodeGenerator.generateQrBitmap(url, 480)
                }
            }
            showToast("Updated transfer PIN to $newPin")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPcTransferServer()
        mediaController.release()
        viewModelScope.launch { pdfSession.close() }
    }
}
