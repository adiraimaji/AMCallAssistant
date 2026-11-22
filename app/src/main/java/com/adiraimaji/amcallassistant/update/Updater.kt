package com.adiraimaji.amcallassistant.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one outgoing request AM Call Assistant makes: GitHub's latest release.
 *
 * Everything the recorder does stays on the phone. This is only the update
 * check, and it says so in the privacy policy and the listing. The release
 * asset has one fixed name, so a stable URL would work too; the API is used
 * instead because the release notes are shown with the offer.
 */
object Updater {
    private const val TAG = "AMCallAssistant"

    // Android's onResume is where "the user opened the app" is knowable, but
    // the check belongs to the view model. The activity posts here; the screen
    // collects. One slot keeps the first opening's check until the screen is up.
    private val _opens = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val opens = _opens.asSharedFlow()
    fun appOpened() { _opens.tryEmit(Unit) }

    private const val RELEASES = "https://api.github.com/repos/adiraimaji/AMCallAssistant/releases/latest"
    private const val ASSET = "AM-Call-Assistant.apk"
    private const val TIMEOUT_MS = 10_000

    data class Release(
        val version: String,
        val notes: String,
        val downloadUrl: String,
    )

    sealed interface Result {
        data class Available(val release: Release) : Result
        data object Current : Result
        data object Unavailable : Result
    }

    /** `v1.10` is newer than `1.9`, and a missing part is zero. */
    internal fun newer(remote: String, local: String): Boolean {
        val a = remote.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = local.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Parse a GitHub release body against the version installed now. */
    internal fun read(json: String, current: String): Result = runCatching {
        val release = JSONObject(json)
        val tag = release.optString("tag_name").trim()
        val assets = release.optJSONArray("assets")
        var url: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == ASSET) {
                    url = asset.optString("browser_download_url")
                    break
                }
            }
        }
        if (tag.isEmpty() || url.isNullOrEmpty()) {
            Result.Unavailable
        } else if (newer(tag, current)) {
            Result.Available(
                Release(
                    version = tag.removePrefix("v"),
                    notes = release.optString("body").trim(),
                    downloadUrl = url,
                ),
            )
        } else {
            Result.Current
        }
    }.getOrElse {
        Log.w(TAG, "update: could not read the latest release", it)
        Result.Unavailable
    }

    /** Tests replace the network with a fixed answer; the app leaves it null. */
    internal var checkOverride: (suspend (Context) -> Result)? = null

    suspend fun check(context: Context): Result =
        checkOverride?.invoke(context) ?: withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(RELEASES).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "AMCallAssistant")
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.inputStream.use { stream ->
                        val current = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
                        } catch (e: PackageManager.NameNotFoundException) {
                            "0"
                        }
                        read(stream.bufferedReader().readText(), current)
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse {
                Log.w(TAG, "update: check failed", it)
                Result.Unavailable
            }
        }

    /** Download to app-private cache. The file is shared only by the install URI. */
    suspend fun download(
        context: Context,
        release: Release,
        progress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, ASSET)
            if (file.exists()) file.delete()
            val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val total = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            done += read
                            if (total > 0) progress((done * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            file.takeIf { it.length() > 0 }
        }.getOrElse {
            Log.w(TAG, "update: download failed", it)
            null
        }
    }

    /** Android 8+ needs the install-unknown-apps grant for this package. */
    internal fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    internal fun askToInstall(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.recordings", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
