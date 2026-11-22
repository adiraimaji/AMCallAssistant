package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent

// Setup's last step - the grant, then the recorder - over
// the session `connect` opened: what Try again runs.
internal object FinishOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        report(
            appContext, "finish",
            com.adiraimaji.amcallassistant.setup.Setup.finish(appContext)
                .fold({ "finished" }, { "failed: ${it.message}" }),
        )
    }
}
