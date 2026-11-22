package com.adiraimaji.amcallassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.adiraimaji.amcallassistant.recorder.RecorderSwitch
import com.adiraimaji.amcallassistant.ui.TurnOffAwayFromWifiDialog
import com.adiraimaji.amcallassistant.ui.theme.AmCallAssistantTheme
import com.adiraimaji.amcallassistant.ui.theme.Looks

/**
 * The Quick Settings tile's way to ask TurnOffAwayFromWifiDialog: a tile has
 * no screen of its own, so this see-through one holds the same dialog the
 * header's button shows, over whatever was open. A question, not a place, so
 * it keeps no history and stays out of Recents.
 */
class TurnOffQuestionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmCallAssistantTheme(Looks.choice(this)) {
                TurnOffAwayFromWifiDialog(
                    onTurnOff = {
                        RecorderSwitch.turnInBackground(applicationContext, false)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
