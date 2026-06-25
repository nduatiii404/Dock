package com.waigi.dock.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /** Observe all items, newest first. */
    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedItem>>

    /** Observe items whose title or URL contains [query], newest first. */
    @Query("""
        SELECT * FROM download_history
        WHERE title LIKE '%' || :query || '%'
           OR url   LIKE '%' || :query || '%'
        ORDER BY downloadedAt DESC
    """)
    fun search(query: String): Flow<List<DownloadedItem>>

    /** Insert or replace a completed download record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadedItem): Long

    /** Delete a specific item. */
    @Delete
    suspend fun delete(item: DownloadedItem)

    /** Delete all history. */
    @Query("DELETE FROM download_history")
    suspend fun deleteAll()

    /** Get a single item by ID. */
    @Query("SELECT * FROM download_history WHERE id = :id")
    suspend fun getById(id: Long): DownloadedItem?

    /** Total number of items saved. */
    @Query("SELECT COUNT(*) FROM download_history")
    suspend fun count(): Int
}
