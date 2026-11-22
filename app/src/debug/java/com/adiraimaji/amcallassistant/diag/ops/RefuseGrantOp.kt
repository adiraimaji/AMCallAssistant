package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent

// Act like a OnePlus with Disable system optimization off,
// whose shell is refused setup's grant: --ez on true|false.
// For this process only.
internal object RefuseGrantOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val on = intent.getBooleanExtra("on", true)
        com.adiraimaji.amcallassistant.system.ShellGrant.actAsRefused = on
        report(appContext, "refusegrant", "acting as if the grant is refused: $on")
    }
}
