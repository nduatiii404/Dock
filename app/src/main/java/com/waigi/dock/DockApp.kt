package com.waigi.dock

import android.app.Application
import com.tencent.mmkv.MMKV
import com.waigi.dock.di.appModule
import com.waigi.dock.util.NotificationUtil
import com.waigi.dock.util.YoutubeDLUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DockApp : Application() {

    /** Application-wide coroutine scope — survives across screen rotations. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize MMKV (fast key-value storage for preferences)
        MMKV.initialize(this)

        // 2. Create notification channels (required before posting any notification)
        NotificationUtil.createChannels(this)

        // 3. Start Koin dependency injection
        startKoin {
            androidContext(this@DockApp)
            modules(appModule)
        }

        // 3. Initialize yt-dlp, ffmpeg, aria2c (must run on main thread first call)
        YoutubeDLUpdater.init(this)

        // 5. Give Downloader an app context for MediaStore scanning
        com.waigi.dock.download.Downloader.init(this)

        // 4. Auto-update yt-dlp in background if enough time has passed
        if (YoutubeDLUpdater.shouldAutoUpdate()) {
            applicationScope.launch(Dispatchers.IO) {
                YoutubeDLUpdater.updateYtDlp(this@DockApp)
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
