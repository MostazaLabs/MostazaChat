package com.example.ui.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID

enum class SheetBackDecision {
    HIDE_KEYBOARD_ONLY,
    DISMISS_SHEET
}

internal object SheetBackNavigator {
    const val DEFAULT_IME_GRACE_MS = 200L

    fun resolveBackAction(
        isImeVisible: Boolean,
        nowElapsedMs: Long,
        lastImeVisibleElapsedMs: Long,
        graceWindowMs: Long = DEFAULT_IME_GRACE_MS
    ): SheetBackDecision {
        val imeRecentlyVisible = lastImeVisibleElapsedMs > 0L &&
            (nowElapsedMs - lastImeVisibleElapsedMs) in 0L..graceWindowMs
        return if (isImeVisible || imeRecentlyVisible) {
            SheetBackDecision.HIDE_KEYBOARD_ONLY
        } else {
            SheetBackDecision.DISMISS_SHEET
        }
    }
}

class InWindowSheetEntry(
    val id: String,
    initialContent: @Composable () -> Unit
) {
    var content by mutableStateOf(initialContent)
}

class InWindowSheetHost {
    val entries = mutableStateListOf<InWindowSheetEntry>()

    fun add(entry: InWindowSheetEntry) {
        if (entries.none { it.id == entry.id }) {
            entries.add(entry)
        }
    }

    fun remove(entry: InWindowSheetEntry) {
        entries.removeAll { it.id == entry.id }
    }
}

val LocalInWindowSheetHost = staticCompositionLocalOf<InWindowSheetHost?> { null }

@Composable
fun InWindowSheetHostContainer(
    host: InWindowSheetHost,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        host.entries.forEach { entry ->
            key(entry.id) {
                entry.content()
            }
        }
    }
}

@Composable
fun rememberIsImeVisible(): Boolean {
    val view = LocalView.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val rootImeVisible = ViewCompat.getRootWindowInsets(view)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    return imeBottomPx > 0 || rootImeVisible
}

@Composable
fun InWindowModalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val host = LocalInWindowSheetHost.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)

    if (host == null) {
        InWindowSheetSurface(
            onDismiss = { currentOnDismiss() },
            modifier = modifier,
            content = { currentContent() }
        )
        return
    }

    val entryId = remember { UUID.randomUUID().toString() }
    val entry = remember(entryId) {
        InWindowSheetEntry(entryId) {
            InWindowSheetSurface(
                onDismiss = { currentOnDismiss() },
                modifier = modifier,
                content = { currentContent() }
            )
        }
    }

    DisposableEffect(host, entry) {
        host.add(entry)
        onDispose {
            host.remove(entry)
        }
    }
}

@Composable
private fun InWindowSheetSurface(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isImeVisible = rememberIsImeVisible()
    var lastImeVisibleElapsedMs by remember { mutableLongStateOf(0L) }

    SideEffect {
        if (isImeVisible) {
            lastImeVisibleElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    val handleKeyboardOrDismiss = {
        val decision = SheetBackNavigator.resolveBackAction(
            isImeVisible = isImeVisible,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            lastImeVisibleElapsedMs = lastImeVisibleElapsedMs
        )
        when (decision) {
            SheetBackDecision.HIDE_KEYBOARD_ONLY -> {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            SheetBackDecision.DISMISS_SHEET -> {
                onDismiss()
            }
        }
    }

    BackHandler(enabled = true) {
        handleKeyboardOrDismiss()
    }

    val dismissThresholdPx = with(density) { 72.dp.toPx() }
    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { handleKeyboardOrDismiss() }
                )
                .testTag("in_window_sheet_scrim")
        )

        Surface(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("in_window_sheet_surface"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(isImeVisible, dismissThresholdPx) {
                            detectVerticalDragGestures(
                                onDragStart = { dragAccumulatedPx = 0f },
                                onDragCancel = { dragAccumulatedPx = 0f },
                                onDragEnd = {
                                    if (dragAccumulatedPx >= dismissThresholdPx) {
                                        handleKeyboardOrDismiss()
                                    }
                                    dragAccumulatedPx = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0f) {
                                        dragAccumulatedPx += dragAmount
                                    }
                                }
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(width = 32.dp, height = 4.dp)
                    ) {}
                }

                content()
            }
        }
    }
}
