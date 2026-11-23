package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.media.MediaImportHelper
import com.example.ui.navigation.ChatMockApp
import com.example.ui.theme.ChatMockTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            MediaImportHelper.ensureAttachedScreenshotSavedToGallery(applicationContext)
            MediaImportHelper.ensureAttachedWhatsAppTxtSavedToDownloads(applicationContext)
        }
        setContent {
            ChatMockTheme {
                ChatMockApp()
            }
        }
    }
}
