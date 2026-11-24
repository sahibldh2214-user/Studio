package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM connections ORDER BY isPinned DESC, id DESC")
    fun observeConnections(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id LIMIT 1")
    suspend fun getConnectionById(id: Long): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConnection(connection: ConnectionEntity): Long

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun deleteConnection(id: Long)

    @Query("SELECT * FROM recent_items ORDER BY lastOpenedMs DESC LIMIT 24")
    fun observeRecentItems(): Flow<List<RecentItemEntity>>

    @Query("SELECT * FROM recent_items WHERE isFavorite = 1 ORDER BY lastOpenedMs DESC")
    fun observeFavorites(): Flow<List<RecentItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentItem(item: RecentItemEntity)

    @Query("UPDATE recent_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM recent_items WHERE id = :id")
    suspend fun deleteRecentItem(id: String)
}
