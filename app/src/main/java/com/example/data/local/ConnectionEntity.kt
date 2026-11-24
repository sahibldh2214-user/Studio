package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ConnectionCategory
import com.example.model.SourceType

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val sourceTypeName: String,
    val categoryName: String,
    val endpointUrl: String,
    val port: Int = 443,
    val shareOrRootPath: String = "",
    val username: String = "",
    val secretTokenOrPassword: String = "",
    val accentHex: Long = 0xFF22D3EE,
    val lastSyncedMs: Long = 0L,
    val isPinned: Boolean = true
) {
    val sourceType: SourceType
        get() = runCatching { SourceType.valueOf(sourceTypeName) }.getOrDefault(SourceType.CUSTOM_CLOUD)

    val category: ConnectionCategory
        get() = runCatching { ConnectionCategory.valueOf(categoryName) }.getOrDefault(ConnectionCategory.CLOUD)
}
