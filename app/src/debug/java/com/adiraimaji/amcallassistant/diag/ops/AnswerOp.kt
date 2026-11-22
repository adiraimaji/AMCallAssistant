package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent

// The answering machine, for tools/emulator_checks.py: a
// greeting from a raw 48 kHz mono PCM file the shell put
// where this app can read it, the switch on, and both
// handed to the recorder.
//   --es op answer --es greeting /data/local/tmp/amcallassistant-test-greeting.pcm
internal object AnswerOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val path = intent.getStringExtra("greeting")
        val text = runCatching {
            if (path != null) {
                com.adiraimaji.amcallassistant.answering.Greeting.save(appContext, java.io.File(path).readBytes())
            }
            com.adiraimaji.amcallassistant.answering.AnsweringMachine.setEnabled(appContext, intent.getBooleanExtra("on", true))
            // All of it: the recording mode a check set too.
            com.adiraimaji.amcallassistant.capture.CaptureDaemon.pushMode(appContext)
            val set = com.adiraimaji.amcallassistant.capture.CaptureDaemon.setAnswering(appContext)
            "greeting ${com.adiraimaji.amcallassistant.answering.Greeting.seconds(appContext)} s, " +
                "wire ${com.adiraimaji.amcallassistant.answering.AnsweringMachine.wire(appContext)}, recorder took it: $set, " +
                "recorder has ${com.adiraimaji.amcallassistant.capture.CaptureDaemon.greetingBytes(appContext)} bytes"
        }.getOrElse { "failed: $it" }
        report(appContext, "answer", text)
    }
}
