package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import com.adiraimaji.amcallassistant.capture.CallMonitorService

internal object MonitorStopOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        CallMonitorService.stop(appContext)
        kotlinx.coroutines.delay(1_000)
        report(appContext, "monitorstop", "monitor stop requested")
    }
}
