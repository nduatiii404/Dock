package com.waigi.dock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.waigi.dock.ui.DockNavHost

class MainActivity : ComponentActivity() {

    private val sharedUrlState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val navigateToState = androidx.compose.runtime.mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — downloads still work; user just won't see notifications if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            // DockNavHost owns the DockTheme — no double-wrap here
            DockNavHost(
                sharedUrl = sharedUrlState.value,
                navigateTo = navigateToState.value,
                onNavigationHandled = { navigateToState.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle URLs shared while the app is already open
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        sharedUrlState.value = intent?.getSharedUrl()
        navigateToState.value = intent?.getStringExtra("navigate_to")
    }
}

/** Extract a plain-text URL from a share or VIEW intent. */
fun Intent.getSharedUrl(): String? = when (action) {
    Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)?.trim()
    Intent.ACTION_VIEW -> dataString?.trim()
    else               -> null
}
