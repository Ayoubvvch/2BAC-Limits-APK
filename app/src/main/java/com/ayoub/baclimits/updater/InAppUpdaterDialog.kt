package com.ayoub.baclimits.updater

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ayoub.baclimits.BuildConfig
import com.ayoub.baclimits.data.ProgressSyncManager
import com.ayoub.baclimits.data.RemoteSyncProtocol
import com.ayoub.baclimits.data.SyncStatusSnapshot
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class InAppUpdaterDialog(
    private val context: Context,
    private val syncManager: ProgressSyncManager,
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
    private var latestDownloadUrl: String? = null
    @Volatile
    private var latestApiAssetUrl: String? = null
    @Volatile
    private var latestVersionName: String? = null
    @Volatile
    private var isDownloading = false

    fun show() {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.parseColor("#000000"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.parseColor("#24242C"))
        })

        val scroll = ScrollView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // Title Row
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(context).apply {
            text = "System & OTA Updates"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val versionBadge = TextView(context).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = pillBg("#0D1B3E", "#2563EB", 99)
        }
        titleRow.addView(titleView)
        titleRow.addView(versionBadge)
        root.addView(titleRow)

        val statusTitle = TextView(context).apply {
            text = "Checking GitHub Releases..."
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        }
        val statusDesc = TextView(context).apply {
            text = "Repository: $REPO_OWNER/$REPO_NAME"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 13f
        }
        root.addView(statusTitle)
        root.addView(statusDesc)

        // Progress Section (hidden until download starts)
        val progressBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(14), 0, dp(6))
        }
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setTint(Color.parseColor("#3B82F6"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        }
        val progressMeta = TextView(context).apply {
            text = "0% • 0.0 MB / ..."
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        progressBox.addView(progressBar)
        progressBox.addView(progressMeta)
        root.addView(progressBox)

        // Action Buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }

        val actionBtn = Button(context).apply {
            text = "Recheck"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = pillBg("#2563EB", "#3B82F6", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
        }
        val closeBtn = Button(context).apply {
            text = "Close"
            isAllCaps = false
            setTextColor(Color.parseColor("#E5E7EB"))
            background = pillBg("#121216", "#32323E", 10)
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(42))
        }
        btnRow.addView(actionBtn)
        btnRow.addView(closeBtn)
        root.addView(btnRow)

        // Divider + Optional Private Repo Auth & Progress Sync Settings
        val settingsToggle = TextView(context).apply {
            text = "⚙ Backend Server, Sync & Private Repo Auth"
            setTextColor(Color.parseColor("#2DD4BF"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(6))
        }
        root.addView(settingsToggle)

        val settingsBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = pillBg("#09090B", "#24242C", 12)
        }

        val syncInfo = TextView(context).apply {
            val snap = syncManager.getCurrentSnapshot()
            text = "${snap.label} • Pending queue: ${snap.pendingCount}"
            setTextColor(Color.parseColor(snap.colorHex))
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        settingsBox.addView(syncInfo)

        val syncUrlInput = styledInput(
            "Backend URL (Default: ${RemoteSyncProtocol.DEFAULT_SERVER_URL})",
            syncManager.getSyncUrl()
        )
        val syncTokenInput = styledInput("Optional Sync Bearer Token", syncManager.getSyncToken(), isSecret = true)
        val ghPatInput = styledInput(
            "Optional GitHub Token (only if repo becomes Private)",
            prefs.getString(KEY_GITHUB_PAT, "") ?: "",
            isSecret = true
        )

        settingsBox.addView(label("Backend / Sync Server URL (Default: ${RemoteSyncProtocol.DEFAULT_SERVER_URL})"))
        settingsBox.addView(syncUrlInput)
        settingsBox.addView(label("Sync Bearer Token (Optional)"))
        settingsBox.addView(syncTokenInput)
        settingsBox.addView(label("GitHub Release Token (for Private Repos)"))
        settingsBox.addView(ghPatInput)

        val syncSettingsBtnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        val saveSettingsBtn = Button(context).apply {
            text = "Save & Sync Now"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = pillBg("#0F766E", "#2DD4BF", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(8)
            }
        }

        val resetDefaultBtn = Button(context).apply {
            text = "Reset Default"
            isAllCaps = false
            setTextColor(Color.parseColor("#E5E7EB"))
            background = pillBg("#121216", "#32323E", 10)
            layoutParams = LinearLayout.LayoutParams(dp(116), dp(40))
        }

        syncSettingsBtnRow.addView(saveSettingsBtn)
        syncSettingsBtnRow.addView(resetDefaultBtn)
        settingsBox.addView(syncSettingsBtnRow)
        root.addView(settingsBox)

        settingsToggle.setOnClickListener {
            settingsBox.visibility = if (settingsBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        saveSettingsBtn.setOnClickListener {
            prefs.edit().putString(KEY_GITHUB_PAT, ghPatInput.text.toString().trim()).apply()
            syncManager.saveSyncConfig(
                syncUrlInput.text.toString(),
                syncTokenInput.text.toString(),
                triggerSync = false
            )
            syncUrlInput.setText(syncManager.getSyncUrl())
            Toast.makeText(context, "Configuration saved", Toast.LENGTH_SHORT).show()
            syncManager.triggerSyncNow(forceHealth = true) { snap ->
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    syncInfo.text = "${snap.label} • Pending queue: ${snap.pendingCount}"
                    try { syncInfo.setTextColor(Color.parseColor(snap.colorHex)) } catch (_: Exception) {}
                }
            }
        }

        resetDefaultBtn.setOnClickListener {
            syncManager.resetToDefaultServer(triggerSync = false)
            syncUrlInput.setText(RemoteSyncProtocol.DEFAULT_SERVER_URL)
            Toast.makeText(context, "Reset to ${RemoteSyncProtocol.DEFAULT_SERVER_URL}", Toast.LENGTH_SHORT).show()
            syncManager.triggerSyncNow(forceHealth = true) { snap ->
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    syncInfo.text = "${snap.label} • Pending queue: ${snap.pendingCount}"
                    try { syncInfo.setTextColor(Color.parseColor(snap.colorHex)) } catch (_: Exception) {}
                }
            }
        }

        closeBtn.setOnClickListener {
            if (!isDownloading) dialog.dismiss()
            else dialog.hide()
        }

        val performCheck = {
            statusTitle.text = "Checking GitHub Releases..."
            statusDesc.text = "Contacting api.github.com/repos/$REPO_OWNER/$REPO_NAME..."
            actionBtn.isEnabled = false
            checkLatestRelease(
                onResult = { hasUpdate, remoteVer, notes, browserUrl, apiAssetUrl ->
                    actionBtn.isEnabled = true
                    if (hasUpdate && (browserUrl != null || apiAssetUrl != null)) {
                        latestVersionName = remoteVer
                        latestDownloadUrl = browserUrl
                        latestApiAssetUrl = apiAssetUrl
                        statusTitle.text = "Update Available: v$remoteVer"
                        statusTitle.setTextColor(Color.parseColor("#4ADE80"))
                        statusDesc.text = notes.ifBlank { "A new signed release is ready to install." }
                        actionBtn.text = "Download & Install v$remoteVer"
                        actionBtn.setOnClickListener {
                            startDownload(
                                dialog = dialog,
                                statusTitle = statusTitle,
                                statusDesc = statusDesc,
                                progressBox = progressBox,
                                progressBar = progressBar,
                                progressMeta = progressMeta,
                                actionBtn = actionBtn
                            )
                        }
                    } else {
                        statusTitle.text = "Up to Date (v${BuildConfig.VERSION_NAME})"
                        statusTitle.setTextColor(Color.parseColor("#60A5FA"))
                        statusDesc.text = if (remoteVer != null) {
                            "Latest GitHub release is v$remoteVer."
                        } else {
                            "No releases published yet on $REPO_OWNER/$REPO_NAME."
                        }
                        actionBtn.text = "Recheck"
                    }
                },
                onError = { err ->
                    actionBtn.isEnabled = true
                    statusTitle.text = "Offline / Check Failed"
                    statusTitle.setTextColor(Color.parseColor("#FBBF24"))
                    statusDesc.text = err
                    actionBtn.text = "Retry"
                }
            )
        }

        actionBtn.setOnClickListener { performCheck() }
        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
        performCheck()
    }

    fun showSyncDialog(onRefreshAccount: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.parseColor("#000000"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.parseColor("#24242C"))
        })

        val scroll = ScrollView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(context).apply {
            text = "Server & Account Sync"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val statusPill = TextView(context).apply {
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = pillBg("#09090B", "#24242C", 99)
        }
        titleRow.addView(titleView)
        titleRow.addView(statusPill)
        root.addView(titleRow)

        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = pillBg("#09090B", "#24242C", 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }

        val networkStatusText = TextView(context).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#E5E7EB"))
        }
        val serverStatusText = TextView(context).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#E5E7EB"))
            setPadding(0, dp(4), 0, 0)
        }
        val pendingText = TextView(context).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, dp(4), 0, 0)
        }
        val lastSyncText = TextView(context).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, dp(4), 0, 0)
        }
        val lastErrorText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#FBBF24"))
            setPadding(0, dp(6), 0, 0)
            visibility = View.GONE
        }

        statusCard.addView(networkStatusText)
        statusCard.addView(serverStatusText)
        statusCard.addView(pendingText)
        statusCard.addView(lastSyncText)
        statusCard.addView(lastErrorText)
        root.addView(statusCard)

        val urlInput = styledInput(
            "Backend URL (Default: ${RemoteSyncProtocol.DEFAULT_SERVER_URL})",
            syncManager.getSyncUrl()
        )
        val tokenInput = styledInput("Optional Bearer Token", syncManager.getSyncToken(), isSecret = true)

        root.addView(label("Backend / Sync Server URL (Default: ${RemoteSyncProtocol.DEFAULT_SERVER_URL})"))
        root.addView(urlInput)
        root.addView(label("Sync Bearer Token (Optional)"))
        root.addView(tokenInput)

        val renderSnapshot: (SyncStatusSnapshot) -> Unit = { snap ->
            statusPill.text = snap.label
            try {
                statusPill.setTextColor(Color.parseColor(snap.colorHex))
                statusPill.background = pillBg("#09090B", snap.colorHex, 99)
            } catch (_: Exception) {
            }
            networkStatusText.text = "Device Network: ${if (snap.isNetworkConnected) "Connected" else "Offline"}"
            serverStatusText.text = "Server (${snap.serverUrl}): ${if (snap.isServerReachable) "Reachable ✓" else "Unreachable"}"
            pendingText.text = "Pending Offline Events: ${snap.pendingCount}"
            lastSyncText.text = "Last Successful Sync: ${formatSyncTimestamp(snap.lastSyncAt)}"
            if (!snap.lastError.isNullOrBlank() && !snap.isServerReachable) {
                lastErrorText.text = "Last Error: ${snap.lastError}"
                lastErrorText.visibility = View.VISIBLE
            } else {
                lastErrorText.visibility = View.GONE
            }
        }

        val primaryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        val syncRefreshBtn = Button(context).apply {
            text = "Sync & Refresh Account"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = pillBg("#2563EB", "#3B82F6", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(8)
            }
        }
        val saveUrlBtn = Button(context).apply {
            text = "Save URL"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = pillBg("#0F766E", "#2DD4BF", 10)
            layoutParams = LinearLayout.LayoutParams(dp(106), dp(42))
        }
        primaryRow.addView(syncRefreshBtn)
        primaryRow.addView(saveUrlBtn)
        root.addView(primaryRow)

        val secondaryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val resetBtn = Button(context).apply {
            text = "Reset Default"
            isAllCaps = false
            setTextColor(Color.parseColor("#E5E7EB"))
            background = pillBg("#121216", "#32323E", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = dp(8)
            }
        }
        val closeBtn = Button(context).apply {
            text = "Close"
            isAllCaps = false
            setTextColor(Color.parseColor("#E5E7EB"))
            background = pillBg("#121216", "#32323E", 10)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(38))
        }
        secondaryRow.addView(resetBtn)
        secondaryRow.addView(closeBtn)
        root.addView(secondaryRow)

        renderSnapshot(syncManager.getCurrentSnapshot())

        syncRefreshBtn.setOnClickListener {
            syncRefreshBtn.isEnabled = false
            syncRefreshBtn.text = "Syncing..."
            syncManager.saveSyncConfig(urlInput.text.toString(), tokenInput.text.toString(), triggerSync = false)
            urlInput.setText(syncManager.getSyncUrl())
            syncManager.triggerSyncNow(forceHealth = true) { snap ->
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    syncRefreshBtn.isEnabled = true
                    syncRefreshBtn.text = "Sync & Refresh Account"
                    renderSnapshot(snap)
                    onRefreshAccount()
                    if (snap.isServerReachable) {
                        Toast.makeText(context, "Synced with ${snap.serverUrl}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        saveUrlBtn.setOnClickListener {
            saveUrlBtn.isEnabled = false
            syncManager.saveSyncConfig(urlInput.text.toString(), tokenInput.text.toString(), triggerSync = false)
            urlInput.setText(syncManager.getSyncUrl())
            syncManager.triggerSyncNow(forceHealth = true) { snap ->
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    saveUrlBtn.isEnabled = true
                    renderSnapshot(snap)
                    Toast.makeText(
                        context,
                        if (snap.isServerReachable) "Connected to ${snap.serverUrl}" else "Saved ${snap.serverUrl} (currently unreachable)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        resetBtn.setOnClickListener {
            syncManager.resetToDefaultServer(triggerSync = false)
            urlInput.setText(RemoteSyncProtocol.DEFAULT_SERVER_URL)
            syncManager.triggerSyncNow(forceHealth = true) { snap ->
                mainHandler.post {
                    if (!dialog.isShowing) return@post
                    renderSnapshot(snap)
                    Toast.makeText(context, "Reset to ${RemoteSyncProtocol.DEFAULT_SERVER_URL}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        closeBtn.setOnClickListener { dialog.dismiss() }

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()

        syncManager.triggerSyncNow(forceHealth = true) { snap ->
            mainHandler.post {
                if (dialog.isShowing) renderSnapshot(snap)
            }
        }
    }

    private fun formatSyncTimestamp(millis: Long): String {
        if (millis <= 0L) return "Never"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            millis.toString()
        }
    }

    private fun checkLatestRelease(
        onResult: (hasUpdate: Boolean, remoteVer: String?, notes: String, browserUrl: String?, apiAssetUrl: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "2BAC-Limits-Android/${BuildConfig.VERSION_NAME}")
                    val pat = prefs.getString(KEY_GITHUB_PAT, "")?.trim().orEmpty()
                    if (pat.isNotEmpty()) {
                        setRequestProperty("Authorization", "Bearer $pat")
                    }
                }
                val code = conn.responseCode
                if (code == 404) {
                    conn.disconnect()
                    mainHandler.post { onResult(false, null, "", null, null) }
                    return@execute
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    mainHandler.post { onError("GitHub API returned HTTP $code") }
                    return@execute
                }

                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v").trim()
                val notes = json.optString("body", "").take(220)
                val assets = json.optJSONArray("assets")
                var browserUrl: String? = null
                var apiAssetUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                            browserUrl = a.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                            apiAssetUrl = a.optString("url", "").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tag, BuildConfig.VERSION_NAME)
                mainHandler.post { onResult(hasUpdate, tag, notes, browserUrl, apiAssetUrl) }
            } catch (e: Exception) {
                mainHandler.post {
                    onError(e.message ?: "Could not connect to GitHub Releases.")
                }
            }
        }
    }

    private fun startDownload(
        dialog: Dialog,
        statusTitle: TextView,
        statusDesc: TextView,
        progressBox: LinearLayout,
        progressBar: ProgressBar,
        progressMeta: TextView,
        actionBtn: Button
    ) {
        if (isDownloading) return
        val pat = prefs.getString(KEY_GITHUB_PAT, "")?.trim().orEmpty()
        val targetUrl = if (pat.isNotEmpty() && !latestApiAssetUrl.isNullOrBlank()) {
            latestApiAssetUrl!!
        } else {
            latestDownloadUrl ?: return
        }
        val ver = latestVersionName ?: "latest"

        isDownloading = true
        actionBtn.isEnabled = false
        progressBox.visibility = View.VISIBLE
        statusTitle.text = "Downloading Update v$ver..."
        statusDesc.text = "Downloading signed APK package..."

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
                        sendAuth = false // Never forward GitHub PAT to S3 CDN redirect
                        redirects++
                        if (redirects > 8) throw Exception("Too many redirects")
                        continue
                    }
                    if (code !in 200..299) {
                        conn.disconnect()
                        throw Exception("Download failed (HTTP $code)")
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
                        var lastUiUpdate = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate >= 80 || downloaded == totalBytes) {
                                lastUiUpdate = now
                                val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                                val loadedStr = formatMb(downloaded)
                                val totalStr = if (totalBytes > 0) formatMb(totalBytes) else "..."
                                mainHandler.post {
                                    progressBar.progress = pct
                                    progressMeta.text = "$pct% • $loadedStr / $totalStr"
                                }
                            }
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                mainHandler.post {
                    isDownloading = false
                    progressBar.progress = 100
                    statusTitle.text = "Ready to Install"
                    statusDesc.text = "Launching Android package installer..."
                    dialog.dismiss()
                    installApkFile(outFile)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isDownloading = false
                    actionBtn.isEnabled = true
                    statusTitle.text = "Download Failed"
                    statusTitle.setTextColor(Color.parseColor("#F87171"))
                    statusDesc.text = e.message ?: "Unknown download error"
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

    private fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun pillBg(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun label(textStr: String): TextView = TextView(context).apply {
        text = textStr
        setTextColor(Color.parseColor("#9CA3AF"))
        textSize = 11f
        setPadding(0, dp(6), 0, dp(3))
    }

    private fun styledInput(hintStr: String, initialValue: String, isSecret: Boolean = false): EditText {
        return EditText(context).apply {
            hint = hintStr
            setText(initialValue)
            setTextColor(Color.parseColor("#F8FAFC"))
            setHintTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = pillBg("#000000", "#32323E", 8)
            inputType = if (isSecret) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
        }
    }
}
