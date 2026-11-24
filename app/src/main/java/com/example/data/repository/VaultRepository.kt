package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.ConnectionEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.RecentItemEntity
import com.example.data.local.VaultDao
import com.example.data.local.VaultDatabase
import com.example.data.local.VaultUiPreferences
import com.example.data.remote.CloudAndNetworkClient
import com.example.data.remote.LocalFileEngine
import com.example.model.DiscoveredLanHost
import com.example.model.FileCategoryFilter
import com.example.model.FileEntry
import com.example.model.LocalNetworkSummary
import com.example.model.SortOrder
import com.example.model.SourceType
import com.example.model.StorageOverview
import kotlinx.coroutines.flow.Flow
import java.io.File

class VaultRepository(context: Context) {
    private val dao: VaultDao = VaultDatabase.getInstance(context).vaultDao()
    val prefs: PreferencesRepository = PreferencesRepository(context)
    val localEngine: LocalFileEngine = LocalFileEngine(context)
    val remoteClient: CloudAndNetworkClient = CloudAndNetworkClient(context)

    val connectionsFlow: Flow<List<ConnectionEntity>> = dao.observeConnections()
    val recentItemsFlow: Flow<List<RecentItemEntity>> = dao.observeRecentItems()
    val favoritesFlow: Flow<List<RecentItemEntity>> = dao.observeFavorites()
    val uiPreferencesFlow: Flow<VaultUiPreferences> = prefs.preferencesFlow

    suspend fun getConnection(id: Long): ConnectionEntity? = dao.getConnectionById(id)

    suspend fun saveConnection(entity: ConnectionEntity): Long = dao.upsertConnection(entity)

    suspend fun deleteConnection(id: Long) = dao.deleteConnection(id)

    suspend fun recordRecent(entry: FileEntry, favorite: Boolean = false) {
        dao.upsertRecentItem(
            RecentItemEntity(
                id = entry.id,
                name = entry.name,
                path = entry.path,
                uriString = entry.uriString,
                mimeType = entry.mimeType,
                mediaKindName = entry.mediaKind.name,
                sourceTypeName = entry.sourceType.name,
                connectionId = entry.connectionId,
                sizeBytes = entry.sizeBytes,
                lastOpenedMs = System.currentTimeMillis(),
                isFavorite = favorite
            )
        )
    }

    suspend fun toggleFavorite(entry: FileEntry, currentlyFavorite: Boolean) {
        val existing = dao.setFavorite(entry.id, !currentlyFavorite)
        recordRecent(entry, favorite = !currentlyFavorite)
    }

    suspend fun getStorageOverview(): StorageOverview = localEngine.getStorageOverview()

    suspend fun browseEntries(
        sourceType: SourceType,
        connectionId: Long,
        path: String,
        sortOrder: SortOrder,
        filter: FileCategoryFilter,
        query: String
    ): Result<List<FileEntry>> {
        return when (sourceType) {
            SourceType.LOCAL_VAULT -> {
                Result.success(localEngine.listLocalDirectory(path, sortOrder, filter, query))
            }
            SourceType.SAF_MOUNT -> {
                val conn = dao.getConnectionById(connectionId)
                    ?: return Result.failure(IllegalStateException("Mounted folder not found"))
                val items = localEngine.listSafTree(
                    treeUriString = conn.endpointUrl,
                    subDocumentUriString = path.ifBlank { null },
                    connectionId = connectionId,
                    sortOrder = sortOrder,
                    categoryFilter = filter,
                    searchQuery = query
                )
                Result.success(items)
            }
            else -> {
                val conn = dao.getConnectionById(connectionId)
                    ?: return Result.failure(IllegalStateException("Connection profile not found"))
                remoteClient.listRemoteDirectory(conn, path).map { items ->
                    val q = query.trim().lowercase()
                    val filtered = items.filter { item ->
                        val matchesQuery = q.isEmpty() || item.name.lowercase().contains(q)
                        val matchesFilter = when (filter) {
                            FileCategoryFilter.ALL -> true
                            FileCategoryFilter.VIDEO -> item.mediaKind == com.example.model.MediaKind.VIDEO
                            FileCategoryFilter.AUDIO -> item.mediaKind == com.example.model.MediaKind.AUDIO
                            FileCategoryFilter.PDF -> item.mediaKind == com.example.model.MediaKind.PDF
                            FileCategoryFilter.IMAGE -> item.mediaKind == com.example.model.MediaKind.IMAGE
                        }
                        matchesQuery && matchesFilter
                    }
                    localEngine.sortEntries(filtered, sortOrder)
                }
            }
        }
    }

    suspend fun resolvePlayableOrReadableUri(
        entry: FileEntry,
        forceLocalCache: Boolean = false
    ): Result<Uri> {
        if (entry.sourceType == SourceType.LOCAL_VAULT || entry.sourceType == SourceType.SAF_MOUNT) {
            return Result.success(Uri.parse(entry.uriString))
        }
        val conn = dao.getConnectionById(entry.connectionId)
            ?: return Result.failure(IllegalStateException("Connection not found"))
        return remoteClient.resolveForPlaybackOrPdf(conn, entry, forceDownloadToLocal = forceLocalCache)
    }

    suspend fun downloadRemoteToLocalVault(entry: FileEntry): Result<FileEntry> {
        val conn = dao.getConnectionById(entry.connectionId)
            ?: return Result.failure(IllegalStateException("Connection not found"))
        val cached = remoteClient.resolveForPlaybackOrPdf(conn, entry, forceDownloadToLocal = true)
        return cached.mapCatching { uri ->
            val srcFile = File(uri.path ?: error("Invalid cached path"))
            val destDir = File(localEngine.vaultRoot, "Downloads").apply { mkdirs() }
            val destFile = File(destDir, entry.name)
            srcFile.copyTo(destFile, overwrite = true)
            val rel = destFile.absolutePath.removePrefix(localEngine.vaultRoot.absolutePath).trimStart('/')
            entry.copy(
                id = "local:$rel",
                path = rel,
                uriString = Uri.fromFile(destFile).toString(),
                sourceType = SourceType.LOCAL_VAULT,
                connectionId = 0L
            )
        }
    }

    suspend fun testConnection(connection: ConnectionEntity): Result<String> =
        remoteClient.testConnection(connection)

    suspend fun scanLanShares(): List<DiscoveredLanHost> =
        remoteClient.scanLanForShares()

    suspend fun scanLanWithSummary(): Pair<LocalNetworkSummary, List<DiscoveredLanHost>> =
        remoteClient.scanLanWithSummary()

    fun getLocalNetworkSummary(): LocalNetworkSummary =
        remoteClient.getLocalNetworkSummary()
}
