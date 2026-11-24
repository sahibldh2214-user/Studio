package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.model.FileCategoryFilter
import com.example.model.FileEntry
import com.example.model.FileTypeClassifier
import com.example.model.MediaKind
import com.example.model.SortOrder
import com.example.model.SourceType
import com.example.model.StorageOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class LocalFileEngine(private val context: Context) {

    val vaultRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "VaultTV").apply {
            if (!exists()) mkdirs()
            listOf("Videos", "Music", "Documents", "PC_Transfers", "Downloads").forEach { sub ->
                File(this, sub).apply { if (!exists()) mkdirs() }
            }
        }
    }

    val pcTransfersDir: File
        get() = File(vaultRoot, "PC_Transfers").apply { if (!exists()) mkdirs() }

    suspend fun listLocalDirectory(
        relativePath: String,
        sortOrder: SortOrder,
        categoryFilter: FileCategoryFilter,
        searchQuery: String
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val targetDir = resolveSafeVaultFile(relativePath)
        if (!targetDir.exists() || !targetDir.isDirectory) return@withContext emptyList()

        val rawFiles = if (categoryFilter != FileCategoryFilter.ALL || searchQuery.isNotBlank()) {
            collectFilteredRecursive(targetDir, categoryFilter, searchQuery)
        } else {
            targetDir.listFiles()?.toList().orEmpty()
        }

        val entries = rawFiles.map { file ->
            val rel = file.absolutePath.removePrefix(vaultRoot.absolutePath).trimStart('/')
            val isDir = file.isDirectory
            val kind = FileTypeClassifier.classify(file.name, isDir)
            FileEntry(
                id = "local:$rel",
                name = file.name,
                path = rel,
                uriString = Uri.fromFile(file).toString(),
                isDirectory = isDir,
                sizeBytes = if (isDir) (file.listFiles()?.size?.toLong() ?: 0L) else file.length(),
                lastModifiedMs = file.lastModified(),
                mimeType = if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(file.name),
                mediaKind = kind,
                sourceType = SourceType.LOCAL_VAULT
            )
        }

        sortEntries(entries, sortOrder)
    }

    private fun collectFilteredRecursive(
        dir: File,
        filter: FileCategoryFilter,
        query: String
    ): List<File> {
        val results = mutableListOf<File>()
        val trimmedQuery = query.trim().lowercase()
        dir.walkTopDown().maxDepth(5).forEach { file ->
            if (file == dir) return@forEach
            val kind = FileTypeClassifier.classify(file.name, file.isDirectory)
            val matchesFilter = when (filter) {
                FileCategoryFilter.ALL -> true
                FileCategoryFilter.VIDEO -> kind == MediaKind.VIDEO
                FileCategoryFilter.AUDIO -> kind == MediaKind.AUDIO
                FileCategoryFilter.PDF -> kind == MediaKind.PDF
                FileCategoryFilter.IMAGE -> kind == MediaKind.IMAGE
            }
            val matchesQuery = trimmedQuery.isEmpty() || file.name.lowercase().contains(trimmedQuery)
            if (matchesFilter && matchesQuery) {
                if (filter == FileCategoryFilter.ALL || !file.isDirectory) {
                    results.add(file)
                }
            }
        }
        return results
    }

    suspend fun listSafTree(
        treeUriString: String,
        subDocumentUriString: String?,
        connectionId: Long,
        sortOrder: SortOrder,
        categoryFilter: FileCategoryFilter,
        searchQuery: String
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val targetUri = Uri.parse(subDocumentUriString ?: treeUriString)
        val docDir = DocumentFile.fromTreeUri(context, targetUri)
            ?: DocumentFile.fromSingleUri(context, targetUri)
            ?: return@withContext emptyList()

        val query = searchQuery.trim().lowercase()
        val entries = docDir.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            val isDir = doc.isDirectory
            val mime = doc.type ?: if (isDir) "inode/directory" else FileTypeClassifier.guessMimeType(name)
            val kind = FileTypeClassifier.classify(name, isDir, mime)
            val matchesFilter = when (categoryFilter) {
                FileCategoryFilter.ALL -> true
                FileCategoryFilter.VIDEO -> kind == MediaKind.VIDEO
                FileCategoryFilter.AUDIO -> kind == MediaKind.AUDIO
                FileCategoryFilter.PDF -> kind == MediaKind.PDF
                FileCategoryFilter.IMAGE -> kind == MediaKind.IMAGE
            }
            val matchesQuery = query.isEmpty() || name.lowercase().contains(query)
            if (!matchesFilter || !matchesQuery) return@mapNotNull null
            if (categoryFilter != FileCategoryFilter.ALL && isDir) return@mapNotNull null

            FileEntry(
                id = "saf:${doc.uri}",
                name = name,
                path = doc.uri.toString(),
                uriString = doc.uri.toString(),
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else doc.length(),
                lastModifiedMs = doc.lastModified(),
                mimeType = mime,
                mediaKind = kind,
                sourceType = SourceType.SAF_MOUNT,
                connectionId = connectionId
            )
        }
        sortEntries(entries, sortOrder)
    }

    suspend fun getStorageOverview(): StorageOverview = withContext(Dispatchers.IO) {
        var vaultBytes = 0L
        var videos = 0
        var audio = 0
        var pdfs = 0
        var images = 0
        var total = 0

        vaultRoot.walkTopDown().forEach { file ->
            if (file.isFile) {
                vaultBytes += file.length()
                total++
                when (FileTypeClassifier.classify(file.name, false)) {
                    MediaKind.VIDEO -> videos++
                    MediaKind.AUDIO -> audio++
                    MediaKind.PDF -> pdfs++
                    MediaKind.IMAGE -> images++
                    else -> Unit
                }
            }
        }

        val stat = StatFs(vaultRoot.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        StorageOverview(
            vaultUsedBytes = vaultBytes,
            deviceFreeBytes = freeBytes,
            deviceTotalBytes = totalBytes,
            videoCount = videos,
            audioCount = audio,
            pdfCount = pdfs,
            imageCount = images,
            totalFilesCount = total
        )
    }

    suspend fun createFolder(parentRelativePath: String, folderName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = sanitizeFileName(folderName)
                require(clean.isNotBlank()) { "Folder name cannot be empty" }
                val parent = resolveSafeVaultFile(parentRelativePath)
                val newDir = File(parent, clean)
                if (!newDir.exists() && !newDir.mkdirs()) {
                    error("Could not create folder $clean")
                }
                newDir
            }
        }

    suspend fun renameLocalEntry(relativePath: String, newName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = sanitizeFileName(newName)
                require(clean.isNotBlank()) { "Name cannot be empty" }
                val target = resolveSafeVaultFile(relativePath)
                require(target.exists()) { "File not found" }
                val dest = File(target.parentFile, clean)
                require(!dest.exists()) { "A file or folder named '$clean' already exists" }
                if (!target.renameTo(dest)) error("Rename failed")
                dest
            }
        }

    suspend fun deleteLocalEntry(relativePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = resolveSafeVaultFile(relativePath)
                if (target == vaultRoot) error("Cannot delete root vault")
                val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
                if (!deleted) error("Could not delete ${target.name}")
            }
        }

    suspend fun copyOrMoveLocalEntry(
        sourceRelativePath: String,
        targetDirRelativePath: String,
        move: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val src = resolveSafeVaultFile(sourceRelativePath)
            val destDir = resolveSafeVaultFile(targetDirRelativePath)
            require(src.exists() && destDir.isDirectory) { "Invalid source or destination" }
            val dest = uniqueDestination(destDir, src.name)
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false)
                if (move) src.deleteRecursively()
            } else {
                FileInputStream(src).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                if (move) src.delete()
            }
            dest
        }
    }

    suspend fun importContentUri(
        uri: Uri,
        targetRelativeDir: String
    ): Result<FileEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
            val destDir = resolveSafeVaultFile(targetRelativeDir)
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = uniqueDestination(destDir, sanitizeFileName(displayName))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            } ?: error("Unable to open selected file stream")

            val rel = destFile.absolutePath.removePrefix(vaultRoot.absolutePath).trimStart('/')
            FileEntry(
                id = "local:$rel",
                name = destFile.name,
                path = rel,
                uriString = Uri.fromFile(destFile).toString(),
                isDirectory = false,
                sizeBytes = destFile.length(),
                lastModifiedMs = destFile.lastModified(),
                mimeType = FileTypeClassifier.guessMimeType(destFile.name),
                mediaKind = FileTypeClassifier.classify(destFile.name, false),
                sourceType = SourceType.LOCAL_VAULT
            )
        }
    }

    fun resolveSafeVaultFile(relativePath: String): File {
        val clean = relativePath.trim('/').replace("..", "")
        val target = if (clean.isEmpty()) vaultRoot else File(vaultRoot, clean)
        val canonicalRoot = vaultRoot.canonicalPath
        val canonicalTarget = target.canonicalPath
        require(canonicalTarget == canonicalRoot || canonicalTarget.startsWith("$canonicalRoot/")) {
            "Access outside Vault root is prohibited"
        }
        return target
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun uniqueDestination(dir: File, desiredName: String): File {
        val candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var idx = 1
        while (true) {
            val nextName = if (ext.isNotEmpty()) "${base}_($idx).$ext" else "${base}_($idx)"
            val nextFile = File(dir, nextName)
            if (!nextFile.exists()) return nextFile
            idx++
        }
    }

    fun sortEntries(entries: List<FileEntry>, sortOrder: SortOrder): List<FileEntry> {
        val comparator = when (sortOrder) {
            SortOrder.NAME_ASC -> compareBy<FileEntry>({ !it.isDirectory }, { it.name.lowercase() })
            SortOrder.NAME_DESC -> compareBy<FileEntry>({ !it.isDirectory })
                .thenByDescending { it.name.lowercase() }
            SortOrder.DATE_DESC -> compareBy<FileEntry>({ !it.isDirectory })
                .thenByDescending { it.lastModifiedMs }
            SortOrder.SIZE_DESC -> compareBy<FileEntry>({ !it.isDirectory })
                .thenByDescending { it.sizeBytes }
            SortOrder.TYPE -> compareBy<FileEntry>({ !it.isDirectory }, { it.mediaKind.name }, { it.name.lowercase() })
        }
        return entries.sortedWith(comparator)
    }
}
