package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent

// A keep-alive retry left by the check before, ended: a
// force-stop does not end one, WorkManager schedules it
// again at the next start, and it ran a revive in the
// middle of the next check (tools/emulator_checks.py).
// The unique names, as the tests' HeldWork has them.
internal object ForgetKicksOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val work = androidx.work.WorkManager.getInstance(appContext)
        work.cancelUniqueWork("amcallassistant.keepalive.now")
        work.cancelUniqueWork("amcallassistant.keepalive.aftercall")
        work.cancelUniqueWork("amcallassistant.keepalive.wifi")
        report(appContext, "forgetkicks", "ended")
    }
}
