package com.waigi.dock

import android.app.Application
import android.content.Intent
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tencent.mmkv.MMKV
import com.waigi.dock.di.appModule
import com.waigi.dock.util.FileUtil
import com.waigi.dock.util.NotificationUtil
import com.waigi.dock.util.YoutubeDLUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DockApp : Application(), ImageLoaderFactory {

    /** Application-wide coroutine scope — survives across screen rotations. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // 0. Set up global crash handler so unexpected runtime exceptions launch CrashActivity
        setupCrashHandler()

        // 1. Initialize MMKV (fast key-value storage for preferences)
        try {
            MMKV.initialize(this)
        } catch (t: Throwable) {
            Log.e("DockApp", "Failed to initialize MMKV", t)
        }

        // 2. Create notification channels (required before posting any notification)
        try {
            NotificationUtil.createChannels(this)
        } catch (t: Throwable) {
            Log.e("DockApp", "Failed to create notification channels", t)
        }

        // 3. Start Koin dependency injection
        try {
            startKoin {
                androidContext(this@DockApp)
                modules(appModule)
            }
        } catch (t: Throwable) {
            Log.e("DockApp", "Failed to start Koin", t)
        }

        // 4. Initialize yt-dlp, ffmpeg, aria2c (safe against linkage errors)
        try {
            YoutubeDLUpdater.init(this)
        } catch (t: Throwable) {
            Log.e("DockApp", "Failed to initialize YoutubeDLUpdater", t)
        }

        // 5. Give Downloader an app context for MediaStore scanning
        try {
            com.waigi.dock.download.Downloader.init(this)
        } catch (t: Throwable) {
            Log.e("DockApp", "Failed to initialize Downloader", t)
        }

        // 6. Auto-update yt-dlp in background if enough time has passed
        try {
            if (YoutubeDLUpdater.shouldAutoUpdate()) {
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        YoutubeDLUpdater.updateYtDlp(this@DockApp)
                    } catch (t: Throwable) {
                        Log.e("DockApp", "Background yt-dlp auto update error", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("DockApp", "Failed during auto-update check", t)
        }

        // 7. Eagerly create the dedicated Dock folder structure
        applicationScope.launch(Dispatchers.IO) {
            try {
                FileUtil.ensureDockFoldersExist()
            } catch (t: Throwable) {
                Log.e("DockApp", "Failed to create Dock directories", t)
            }
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val crashReport = "=== CRASH AT $timeStamp on thread ${thread.name} ===\n$stackTrace\n"

                Log.e("DockApp", crashReport)

                // Write to external files dir (accessible via file manager)
                getExternalFilesDir(null)?.let { dir ->
                    File(dir, "crash_log.txt").appendText(crashReport)
                }
                // Also write to internal files dir as fallback
                File(filesDir, "crash_log.txt").appendText(crashReport)

                // Launch CrashActivity in its separate process to display the error cleanly to the user
                val intent = Intent(this@DockApp, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_CRASH_REPORT, crashReport)
                    putExtra(CrashActivity.EXTRA_ERROR_MESSAGE, throwable.localizedMessage ?: throwable.javaClass.simpleName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)

                // Terminate crashed process cleanly
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (e: Throwable) {
                Log.e("DockApp", "Failed to launch crash activity", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        lateinit var instance: DockApp
            private set
    }

    init {
        instance = this
    }
}
