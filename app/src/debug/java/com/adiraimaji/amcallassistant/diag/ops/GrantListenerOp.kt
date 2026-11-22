package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent

// Notification access for the app's listener, as Settings'
// Show who called and Hide the debugging notification ask
// for it: through the recorder, or a session with none.
internal object GrantListenerOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        report(
            appContext, "grantlistener",
            com.adiraimaji.amcallassistant.setup.Setup.grantNotificationAccess(appContext)?.let { "not granted: $it" }
                ?: "granted",
        )
    }
}
