package com.waigi.dock

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.waigi.dock.player.VideoSwipeViewerScreen
import com.waigi.dock.ui.theme.DockTheme
import java.io.File

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra("FILE_PATH")
        val uri = if (filePath != null) {
            Uri.fromFile(File(filePath))
        } else {
            intent.data
        }

        if (uri == null) {
            finish()
            return
        }

        setContent {
            DockTheme {
                VideoSwipeViewerScreen(
                    videos = listOf(uri),
                    initialIndex = 0,
                    onClose = { finish() }
                )
            }
        }
    }
}
