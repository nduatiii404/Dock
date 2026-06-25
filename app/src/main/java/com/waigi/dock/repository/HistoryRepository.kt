package com.waigi.dock.repository

import com.waigi.dock.database.DownloadedItem
import com.waigi.dock.database.HistoryDao
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for download history.
 * The UI layer only talks to this — never directly to the DAO.
 */
class HistoryRepository(private val dao: HistoryDao) {

    /** Reactive stream of all downloads, newest first. */
    val allItems: Flow<List<DownloadedItem>> = dao.observeAll()

    /** Reactive stream filtered by a search query. */
    fun search(query: String): Flow<List<DownloadedItem>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query)

    /** Save a completed download to history. */
    suspend fun save(item: DownloadedItem): Long = dao.insert(item)

    /** Remove a single item. */
    suspend fun delete(item: DownloadedItem) = dao.delete(item)

    /** Wipe all history. */
    suspend fun deleteAll() = dao.deleteAll()

    /** Total count — useful for empty-state checks. */
    suspend fun count(): Int = dao.count()
}
