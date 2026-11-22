package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import com.adiraimaji.amcallassistant.adb.ResultFile
import com.adiraimaji.amcallassistant.recorder.SelfTest

// The same check the app's own Self-test button runs.
// It used to be the transport's acceptance check, which
// asked whether an ADB shell session existed - a question
// that became meaningless once the app started closing that
// session on purpose the moment the recorder was up.
internal object SelfTestOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val text = SelfTest.run(appContext).report
        ResultFile.write(appContext, text)
        report(appContext, "selftest", text)
    }
}
