package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.MediaKind
import com.example.model.SourceType

@Entity(tableName = "recent_items")
data class RecentItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val uriString: String,
    val mimeType: String,
    val mediaKindName: String,
    val sourceTypeName: String,
    val connectionId: Long,
    val sizeBytes: Long,
    val lastOpenedMs: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) {
    val mediaKind: MediaKind
        get() = runCatching { MediaKind.valueOf(mediaKindName) }.getOrDefault(MediaKind.OTHER)

    val sourceType: SourceType
        get() = runCatching { SourceType.valueOf(sourceTypeName) }.getOrDefault(SourceType.LOCAL_VAULT)
}
