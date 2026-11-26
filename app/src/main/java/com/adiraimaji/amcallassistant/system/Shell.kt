package com.adiraimaji.amcallassistant.system

import android.content.Context
import com.adiraimaji.amcallassistant.adb.AdbTransport
import com.adiraimaji.amcallassistant.setup.Setup

/** One shell command, over whichever bootstrap the user chose in setup. */
object Shell {
    /** Set at process start; lets low-level daemon helpers use the chosen
     *  bootstrap without threading a Context through every shell call. */
    @Volatile
    var appContext: Context? = null

    suspend fun exec(context: Context, command: String): Result<String> =
        if (Setup.method(context) == Setup.METHOD_SHIZUKU && ShizukuShell.granted()) {
            ShizukuShell.exec(command)
        } else {
            AdbTransport.exec(command)
        }
}
