package com.adiraimaji.amcallassistant.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.FileInputStream

/**
 * Optional shell bootstrap through Shizuku. Nothing here is used unless the
 * user picks Shizuku during setup; the native ADB path stays the default.
 */
object ShizukuShell {
    const val REQUEST_CODE = 4711

    private val _binderEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when a compatible backend's binder arrives or dies. The setup
     *  screen rechecks on it, so no package name or activity resume is needed. */
    val binderEvents: SharedFlow<Unit> = _binderEvents

    fun notifyBinderChanged() {
        _binderEvents.tryEmit(Unit)
    }

    /** Any Shizuku-compatible backend (Shizuku, Shizuku-Next, Sui) exposes the
     *  same API binder, so no package-name check is needed. */
    fun installed(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        context.packageManager.getApplicationInfo("rikka.shizuku", 0)
        true
    }.getOrDefault(false)

    fun binderAvailable(): Boolean = runCatching { Shizuku.getBinder() != null }.getOrDefault(false)

    fun granted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun ready(context: Context): Boolean = binderAvailable() && granted()

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** Run a shell command as Shizuku's caller, returning its combined output. */
    fun exec(command: String): Result<String> = runCatching {
        val binder = Shizuku.getBinder() ?: error("Shizuku binder is not available")
        val service = IShizukuService.Stub.asInterface(binder)
        val remote = service.newProcess(arrayOf("sh", "-c", command), null, null)
        val output = FileInputStream(remote.inputStream.fileDescriptor).bufferedReader().use { it.readText() } +
            FileInputStream(remote.errorStream.fileDescriptor).bufferedReader().use { it.readText() }
        remote.waitFor()
        output
    }
}
