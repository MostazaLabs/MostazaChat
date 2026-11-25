package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.media.MediaImportHelper
import com.example.ui.navigation.ChatMockApp
import com.example.ui.theme.ChatMockTheme

class MainActivity : ComponentActivity() {

    private var incomingSharedIntent by mutableStateOf<Intent?>(null)
    private var screenCaptureSignal by mutableIntStateOf(0)
    private var registeredScreenCallback: Any? = null

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
                    onConsumeSharedIntent = { incomingSharedIntent = null },
                    screenCaptureSignal = screenCaptureSignal
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registeredScreenCallback = Api34ScreenCaptureHelper.register(this) {
                screenCaptureSignal++
            }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34ScreenCaptureHelper.unregister(this, registeredScreenCallback)
            registeredScreenCallback = null
        }
        super.onStop()
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

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object Api34ScreenCaptureHelper {
    fun register(activity: Activity, onCaptured: () -> Unit): Any? {
        val callback = Activity.ScreenCaptureCallback { onCaptured() }
        return runCatching {
            activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
            callback
        }.getOrNull()
    }

    fun unregister(activity: Activity, callbackToken: Any?) {
        val cb = callbackToken as? Activity.ScreenCaptureCallback ?: return
        runCatching { activity.unregisterScreenCaptureCallback(cb) }
    }
}
