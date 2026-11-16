package com.ayoub.baclimits.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ayoub.baclimits.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class UpdateCheckState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedLabel: String = "",
    val hasUpdate: Boolean = false,
    val remoteVersion: String? = null,
    val releaseNotes: String = "",
    val browserUrl: String? = null,
    val apiAssetUrl: String? = null,
    val otherEditionBrowserUrl: String? = null,
    val currentEdition: String = "lite",
    val engineLabel: String = "",
    val error: String? = null,
    val lastCheckedAt: Long = 0L
)

class AppUpdaterManager(
    private val context: Context,
    private val onStateChanged: (UpdateCheckState) -> Unit,
    private val onRequestInstallPermission: (File) -> Unit
) {
    companion object {
        private const val REPO_OWNER = "Ayoubvvch"
        private const val REPO_NAME = "2BAC-Limits-APK"
        private const val PREFS_UPDATER = "bac_limits_updater_prefs"
        private const val KEY_GITHUB_PAT = "github_private_pat"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences(PREFS_UPDATER, Context.MODE_PRIVATE)

    @Volatile
    private var state = UpdateCheckState()

    fun getGitHubPat(): String = prefs.getString(KEY_GITHUB_PAT, "")?.trim().orEmpty()

    fun saveGitHubPat(pat: String) {
        prefs.edit().putString(KEY_GITHUB_PAT, pat.trim()).apply()
    }

    fun getCurrentState(): UpdateCheckState = state

    private fun updateState(transform: (UpdateCheckState) -> UpdateCheckState) {
        val next = transform(state)
        state = next
        mainHandler.post { onStateChanged(next) }
    }

    fun checkLatestRelease(silent: Boolean = false) {
        if (state.checking || state.downloading) return
        updateState {
            it.copy(checking = true, error = if (silent) it.error else null)
        }

        executor.execute {
            try {
                val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
                val pat = getGitHubPat()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "2BAC-Limits-Android/${BuildConfig.VERSION_NAME}")
                    if (pat.isNotEmpty()) {
                        setRequestProperty("Authorization", "Bearer $pat")
                    }
                }
                val code = conn.responseCode
                if (code == 404) {
                    conn.disconnect()
                    updateState {
                        it.copy(
                            checking = false,
                            hasUpdate = false,
                            remoteVersion = null,
                            releaseNotes = "",
                            error = null,
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    }
                    return@execute
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    updateState {
                        it.copy(
                            checking = false,
                            error = if (silent) null else "GitHub API HTTP $code",
                            lastCheckedAt = System.currentTimeMillis()
                        )
                    }
                    return@execute
                }

                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v").trim()
                val notes = json.optString("body", "").take(320)
                val currentEdition = OfflineLimitsEngineHolder.get().edition.lowercase()
                val currentEngineName = OfflineLimitsEngineHolder.get().engineName
                val otherEdition = if (currentEdition == "full") "lite" else "full"

                val assets = json.optJSONArray("assets")
                var browserUrl: String? = null
                var apiAssetUrl: String? = null
                var otherEditionBrowserUrl: String? = null

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name", "").lowercase()
                        if (name.endsWith(".apk")) {
                            if (name.contains(currentEdition)) {
                                browserUrl = a.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                                apiAssetUrl = a.optString("url", "").takeIf { it.isNotBlank() }
                            } else if (name.contains(otherEdition)) {
                                otherEditionBrowserUrl = a.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                            }
                        }
                    }

                    // Fallback to any apk if exact edition match was not found
                    if (browserUrl == null && apiAssetUrl == null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                                browserUrl = a.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                                apiAssetUrl = a.optString("url", "").takeIf { it.isNotBlank() }
                                break
                            }
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tag, BuildConfig.VERSION_NAME) &&
                    (browserUrl != null || apiAssetUrl != null)
                updateState {
                    it.copy(
                        checking = false,
                        hasUpdate = hasUpdate,
                        remoteVersion = tag.ifBlank { null },
                        releaseNotes = notes,
                        browserUrl = browserUrl,
                        apiAssetUrl = apiAssetUrl,
                        otherEditionBrowserUrl = otherEditionBrowserUrl,
                        currentEdition = currentEdition,
                        engineLabel = currentEngineName,
                        error = null,
                        lastCheckedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        checking = false,
                        error = if (silent) null else (e.message ?: "Offline / network error"),
                        lastCheckedAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun downloadAndInstall() {
        val snapshot = state
        if (snapshot.downloading) return
        val pat = getGitHubPat()
        val targetUrl = if (pat.isNotEmpty() && !snapshot.apiAssetUrl.isNullOrBlank()) {
            snapshot.apiAssetUrl
        } else {
            snapshot.browserUrl
        } ?: return

        val ver = snapshot.remoteVersion ?: "latest"
        updateState {
            it.copy(
                downloading = true,
                downloadProgress = 0,
                downloadedLabel = "0.0 MB / ...",
                error = null
            )
        }

        executor.execute {
            try {
                var currentUrl = targetUrl
                var redirects = 0
                var conn: HttpURLConnection
                var sendAuth = pat.isNotEmpty() && currentUrl.contains("api.github.com")

                while (true) {
                    val u = URL(currentUrl)
                    conn = (u.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "2BAC-Limits-Android/${BuildConfig.VERSION_NAME}")
                        if (sendAuth) {
                            setRequestProperty("Authorization", "Bearer $pat")
                            setRequestProperty("Accept", "application/octet-stream")
                        }
                    }
                    conn.connect()
                    val code = conn.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val loc = conn.getHeaderField("Location") ?: throw Exception("Missing redirect target")
                        conn.disconnect()
                        currentUrl = loc
                        sendAuth = false
                        redirects++
                        if (redirects > 8) throw Exception("Too many redirects")
                        continue
                    }
                    if (code !in 200..299) {
                        conn.disconnect()
                        throw Exception("HTTP $code")
                    }
                    break
                }

                val totalBytes = conn.contentLengthLong
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val outFile = File(downloadDir, "2BAC-Limits-v$ver.apk")
                if (outFile.exists()) outFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        var lastUi = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUi >= 90 || downloaded == totalBytes) {
                                lastUi = now
                                val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                val loadedStr = formatMb(downloaded)
                                val totalStr = if (totalBytes > 0) formatMb(totalBytes) else "..."
                                updateState {
                                    it.copy(
                                        downloadProgress = pct,
                                        downloadedLabel = "$pct% • $loadedStr / $totalStr"
                                    )
                                }
                            }
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                updateState {
                    it.copy(downloading = false, downloadProgress = 100)
                }
                mainHandler.post { installApkFile(outFile) }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        downloading = false,
                        error = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    fun installApkFile(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onRequestInstallPermission(apkFile)
            return
        }
        launchInstaller(apkFile)
    }

    fun launchInstaller(apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Installer error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        val r = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(r.size, l.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private fun formatMb(bytes: Long): String =
        String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
}
