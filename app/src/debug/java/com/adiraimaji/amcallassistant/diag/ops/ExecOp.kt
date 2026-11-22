package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import com.adiraimaji.amcallassistant.adb.AdbTransport

internal object ExecOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd").orEmpty()
        report(
            appContext, "exec",
            AdbTransport.exec(cmd).fold({ it }, { "exec failed: ${it.message}" }),
        )
    }
}
