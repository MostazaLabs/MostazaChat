package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.media.MediaImportHelper
import com.example.ui.navigation.ChatMockApp
import com.example.ui.theme.ChatMockTheme

class MainActivity : ComponentActivity() {

    private var incomingSharedIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && isShareIntent(intent)) {
            incomingSharedIntent = intent
        }
        setContent {
            ChatMockTheme {
                LaunchedEffect(Unit) {
                    if (BuildConfig.DEBUG) {
                        MediaImportHelper.syncDebugTestGalleryImagesIfNeeded(applicationContext)
                    }
                }
                ChatMockApp(
                    sharedIntent = incomingSharedIntent,
                    onConsumeSharedIntent = { incomingSharedIntent = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isShareIntent(intent)) {
            incomingSharedIntent = intent
        }
    }

    private fun isShareIntent(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        return action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
    }
}
