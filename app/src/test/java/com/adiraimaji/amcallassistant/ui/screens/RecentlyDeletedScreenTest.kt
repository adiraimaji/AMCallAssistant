package com.adiraimaji.amcallassistant.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.net.toUri
import com.adiraimaji.amcallassistant.recordings.Recordings
import com.adiraimaji.amcallassistant.ui.Screen
import com.adiraimaji.amcallassistant.ui.TrashScreen
import com.adiraimaji.amcallassistant.ui.UiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Recently deleted with two copies of one name: a row each, as a lazy list
 *  throws on a key used twice. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class RecentlyDeletedScreenTest : ScreenTest() {

    @Test fun twoCopiesOfOneNameAreTwoRows() {
        val copies = listOf("content://media/external/audio/media/5", "content://folder/document/5").map {
            Recordings.describe("20261020_101010_in.ogg", it.toUri(), 1024, 61_000)
        }
        val state = UiState(screen = Screen.TRASH, trashed = copies)
        val vm = viewModel(state)
        show { TrashScreen(state, vm) }
        compose.onAllNodesWithText("Restore").assertCountEquals(2)
    }
}
