package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import com.adiraimaji.amcallassistant.diag.TurnOnLater

// AM Call Assistant off or on, as the record control and the tile turn
// it, with When AM Call Assistant is off as the prefs set it - and on
// again by itself after a delay, for the emulator check of
// Turn all debugging off, whose off takes adb away with it
// (tools/emulator_checks.py, debugging_off):
//   --es op switch --ez on false [--ei back_after_s 30]
internal object SwitchOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val on = intent.getBooleanExtra("on", true)
        val backAfter = intent.getIntExtra("back_after_s", 0)
        if (!on && backAfter > 0) TurnOnLater.schedule(appContext, backAfter)
        report(
            appContext, "switch",
            "turning ${if (on) "on" else "off"}" +
                (if (!on && backAfter > 0) ", on again in $backAfter s" else "") +
                ", with ${com.adiraimaji.amcallassistant.recorder.DebugSwitchesOff.mode(appContext)}",
        )
        com.adiraimaji.amcallassistant.recorder.RecorderSwitch.set(appContext, on)
        com.adiraimaji.amcallassistant.recorder.RecorderSwitch.turn(appContext, on)
    }
}
