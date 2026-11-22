package com.adiraimaji.amcallassistant.diag.ops

import android.content.Context
import android.content.Intent
import android.provider.Settings

// What THIS process reads for the settings setup decides
// by, to set against `settings get global` from a shell.
// Android 17's docs say adb_enabled and
// development_settings_enabled "always return 0 for all
// third-party apps"; this is how to see it, and whether
// adb_wifi_enabled went the same way. With --es write <name>
// --ei value <0|1> it writes first, to see whether writes
// still land.
internal object SettingsOp : DiagOp {
    override suspend fun run(appContext: Context, intent: Intent) {
        val resolver = appContext.contentResolver
        val write = intent.getStringExtra("write")
        val wrote = write?.let { name ->
            val value = intent.getIntExtra("value", 1)
            runCatching { Settings.Global.putInt(resolver, name, value) }
                .fold({ "putInt $name=$value accepted=$it" }, { "putInt $name FAILED: ${it.javaClass.simpleName}: ${it.message}" })
        }
        val reads = listOf(
            "adb_enabled", "adb_wifi_enabled", "development_settings_enabled",
            // A control nobody would hide.
            "airplane_mode_on",
        ).joinToString("\n") { name ->
            val raw = runCatching { Settings.Global.getString(resolver, name) }
                .getOrElse { "threw ${it.javaClass.simpleName}" }
            "$name=$raw"
        }
        val about = "sdk=${android.os.Build.VERSION.SDK_INT}" +
            (if (android.os.Build.VERSION.SDK_INT >= 36) " sdkFull=${android.os.Build.VERSION.SDK_INT_FULL}" else "") +
            " target=${appContext.applicationInfo.targetSdkVersion}" +
            " secureSettings=${com.adiraimaji.amcallassistant.setup.Setup.hasSecureSettings(appContext)}"
        val concluded = "app concludes: usb=${com.adiraimaji.amcallassistant.setup.Setup.usbDebugging(appContext)}" +
            " wireless=${com.adiraimaji.amcallassistant.setup.Setup.wirelessDebuggingOn(appContext)}" +
            " devOptions=${com.adiraimaji.amcallassistant.setup.Setup.developerOptions(appContext)}" +
            " actAsHidden=${com.adiraimaji.amcallassistant.system.GlobalSettings.actAsHidden}"
        report(appContext, "settings", listOfNotNull(about, wrote, reads, concluded).joinToString("\n"))
    }
}
