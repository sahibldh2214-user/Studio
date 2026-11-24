package com.example.model

import java.util.Locale

enum class MediaKind {
    FOLDER,
    VIDEO,
    AUDIO,
    PDF,
    IMAGE,
    DOCUMENT,
    ARCHIVE,
    APK,
    OTHER
}

enum class SourceType {
    LOCAL_VAULT,
    SAF_MOUNT,
    GOOGLE_DRIVE,
    ONEDRIVE,
    BOX,
    DROPBOX,
    NEXTCLOUD_WEBDAV,
    CUSTOM_CLOUD,
    SMB,
    FTP,
    HTTP_WEBDAV
}

enum class SortOrder(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    DATE_DESC("Newest First"),
    SIZE_DESC("Largest First"),
    TYPE("By Type")
}

enum class ViewMode {
    GRID,
    LIST
}

enum class FileCategoryFilter(val label: String) {
    ALL("All"),
    VIDEO("Videos"),
    AUDIO("Music"),
    PDF("PDFs"),
    IMAGE("Photos")
}

data class FileEntry(
    val id: String,
    val name: String,
    val path: String,
    val uriString: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val mimeType: String,
    val mediaKind: MediaKind,
    val sourceType: SourceType,
    val connectionId: Long = 0L,
    val authHeader: String? = null
)

data class StorageOverview(
    val vaultUsedBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val deviceTotalBytes: Long = 0L,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val pdfCount: Int = 0,
    val imageCount: Int = 0,
    val totalFilesCount: Int = 0
)

data class BreadcrumbNode(
    val label: String,
    val path: String
)

object FileTypeClassifier {
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "flv", "3gp")
    private val audioExts = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma", "alac")
    private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif")
    private val docExts = setOf("txt", "md", "json", "xml", "csv", "log", "doc", "docx", "rtf")
    private val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "bz2")

    fun classify(name: String, isDirectory: Boolean, mimeType: String? = null): MediaKind {
        if (isDirectory) return MediaKind.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        val mime = mimeType?.lowercase(Locale.US).orEmpty()
        return when {
            ext == "pdf" || mime == "application/pdf" -> MediaKind.PDF
            ext in videoExts || mime.startsWith("video/") -> MediaKind.VIDEO
            ext in audioExts || mime.startsWith("audio/") -> MediaKind.AUDIO
            ext in imageExts || mime.startsWith("image/") -> MediaKind.IMAGE
            ext == "apk" || mime == "application/vnd.android.package-archive" -> MediaKind.APK
            ext in archiveExts -> MediaKind.ARCHIVE
            ext in docExts || mime.startsWith("text/") -> MediaKind.DOCUMENT
            else -> MediaKind.OTHER
        }
    }

    fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac", "m4a" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
