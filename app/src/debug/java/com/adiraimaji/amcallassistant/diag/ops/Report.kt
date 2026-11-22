package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import android.util.Log
import com.adiraimaji.amcallassistant.adb.ResultFile

/** One of SpikeReceiver's ops: what it does with the broadcast's extras.
 *  Each reports its answer with [report]; one that throws is reported by
 *  the receiver. */
internal interface DiagOp {
    suspend fun run(appContext: Context, intent: Intent)
}

internal const val TAG = "AMCallAssistant"

/** Logged line by line: logcat truncates very long single messages. */
internal fun report(context: Context, op: String, text: String) {
    Log.i(TAG, "diag[$op] >>>")
    text.lineSequence().forEach { Log.i(TAG, "diag[$op] $it") }
    Log.i(TAG, "diag[$op] <<<")
    ResultFile.write(context, text)
}
