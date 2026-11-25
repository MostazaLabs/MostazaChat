package com.example

import com.example.ui.components.SheetBackDecision
import com.example.ui.components.SheetBackNavigator
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetBackNavigatorTest {

    @Test
    fun backWithVisibleKeyboard_hidesKeyboardWithoutDismissingSheet() {
        val decision = SheetBackNavigator.resolveBackAction(
            isImeVisible = true,
            nowElapsedMs = 5_000L,
            lastImeVisibleElapsedMs = 5_000L
        )
        assertEquals(SheetBackDecision.HIDE_KEYBOARD_ONLY, decision)
    }

    @Test
    fun backImmediatelyAfterImeStartedHiding_absorbsSamePressWithoutDismissingSheet() {
        val decision = SheetBackNavigator.resolveBackAction(
            isImeVisible = false,
            nowElapsedMs = 5_100L,
            lastImeVisibleElapsedMs = 5_000L,
            graceWindowMs = 200L
        )
        assertEquals(SheetBackDecision.HIDE_KEYBOARD_ONLY, decision)
    }

    @Test
    fun secondBackAfterKeyboardHidden_dismissesSheet() {
        val decision = SheetBackNavigator.resolveBackAction(
            isImeVisible = false,
            nowElapsedMs = 5_450L,
            lastImeVisibleElapsedMs = 5_000L,
            graceWindowMs = 200L
        )
        assertEquals(SheetBackDecision.DISMISS_SHEET, decision)
    }

    @Test
    fun backWhenKeyboardWasNeverOpened_dismissesSheetImmediately() {
        val decision = SheetBackNavigator.resolveBackAction(
            isImeVisible = false,
            nowElapsedMs = 2_000L,
            lastImeVisibleElapsedMs = 0L
        )
        assertEquals(SheetBackDecision.DISMISS_SHEET, decision)
    }
}
