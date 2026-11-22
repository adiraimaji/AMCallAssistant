package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import com.adiraimaji.amcallassistant.adb.AdbTransport

internal object StatusOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        report(
            appContext, "status",
            if (AdbTransport.isConnected) "connected" else "not connected",
        )
    }
}
