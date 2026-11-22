package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import com.adiraimaji.amcallassistant.capture.CaptureDaemon
import com.adiraimaji.amcallassistant.capture.LastCallReport
import com.adiraimaji.amcallassistant.ui.appVersion

// What Copy a report on Diagnostics copies (LastCallReport).
internal object CallReportOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val recorder = CaptureDaemon.callReport(appContext)
        report(appContext, "callreport", LastCallReport.text(appContext, appVersion(appContext), recorder))
    }
}
