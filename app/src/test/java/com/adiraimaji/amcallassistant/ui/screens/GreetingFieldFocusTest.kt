package com.adiraimaji.amcallassistant.ui.screens

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.adiraimaji.amcallassistant.ui.AppScaffold
import com.adiraimaji.amcallassistant.ui.Screen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36], qualifiers = ScreenTest.PHONE)
class GreetingFieldFocusTest : ScreenTest() {
    @Test fun focusStaysWhileTypingTextGreeting() {
        val state = Fixtures.ready().copy(screen = Screen.ANSWERING_MACHINE)
        val vm = viewModel(state)
        show { AppScaffold(state, vm) }
        compose.onNodeWithText("New profile", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Text").performScrollTo().performClick()
        val field = compose.onNode(hasSetTextAction() and hasText("What callers hear"))
        field.performScrollTo().performClick()
        field.performTextInput("H")
        // Let the delayed greeting validation run: it used to disable the
        // field and drop focus, closing the keyboard.
        Thread.sleep(900)
        compose.waitForIdle()
        field.assertIsFocused()
        field.performTextInput("i")
        Thread.sleep(900)
        compose.waitForIdle()
        field.assertIsFocused()
    }
}
