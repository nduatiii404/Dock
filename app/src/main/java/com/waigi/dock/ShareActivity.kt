package com.waigi.dock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.waigi.dock.service.DownloadService
import com.waigi.dock.ui.components.DownloadOptionsDialog
import com.waigi.dock.ui.theme.DockTheme

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val sharedUrl = intent?.getSharedUrl()
        if (sharedUrl.isNullOrBlank()) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            DockTheme {
                DownloadOptionsDialog(
                    url = sharedUrl,
                    videoInfo = null,
                    onDismiss = { finish() },
                    onDownload = { url, prefs ->
                        DownloadService.startDownload(this@ShareActivity, url, prefs)
                        Toast.makeText(this@ShareActivity, "Fetching video details\u2026", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}
