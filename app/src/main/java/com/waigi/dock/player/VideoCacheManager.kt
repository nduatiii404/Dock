package com.waigi.dock.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object VideoCacheManager {
    @Volatile
    private var simpleCache: SimpleCache? = null
    private val lock = Any()

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(lock) {
            simpleCache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "media3_video_cache")
                SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(150L * 1024 * 1024), // 150 MB LRU
                    StandaloneDatabaseProvider(context.applicationContext)
                ).also { simpleCache = it }
            }
        }
    }

    fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun evict(context: Context, uri: Uri) {
        try {
            getCache(context).removeResource(uri.toString())
        } catch (_: Exception) {}
    }
}
