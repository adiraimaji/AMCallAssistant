package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent

internal object TestResultOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        report(
            appContext, "testresult",
            "${com.adiraimaji.amcallassistant.capture.CaptureDaemon.testResult(appContext)}",
        )
    }
}
