package com.ayoub.baclimits.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class SyncStatusSnapshot(
    val label: String,
    val colorHex: String,
    val isNetworkConnected: Boolean,
    val isServerReachable: Boolean,
    val pendingCount: Int,
    val serverUrl: String,
    val lastSyncAt: Long,
    val lastError: String?
)

class ProgressSyncManager(
    private val context: Context,
    private val db: StudentDatabase,
    private val onStatusChanged: (SyncStatusSnapshot) -> Unit
) : RemoteApiGateway {

    companion object {
        private const val PREFS_NAME = "bac_limits_sync_prefs"
        private const val KEY_SYNC_URL = "remote_sync_url"
        private const val KEY_SYNC_TOKEN = "remote_sync_token"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val syncLock = Any()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var serverReachable: Boolean = false

    @Volatile
    private var hasCheckedHealth: Boolean = false

    @Volatile
    private var isSyncingNow: Boolean = false

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastProbeTimestamp: Long = 0L

    fun getSyncUrl(): String {
        val raw = prefs.getString(KEY_SYNC_URL, "")?.trim().orEmpty()
        return RemoteSyncProtocol.normalizeBaseUrl(raw)
    }

    fun getRawConfiguredSyncUrl(): String =
        prefs.getString(KEY_SYNC_URL, "")?.trim().orEmpty()

    fun getSyncToken(): String = prefs.getString(KEY_SYNC_TOKEN, "")?.trim().orEmpty()

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    fun getPendingCount(): Int = db.getPendingSyncCount()

    override fun isServerConfigured(): Boolean = getSyncUrl().isNotBlank()

    fun saveSyncConfig(url: String, token: String, triggerSync: Boolean = true) {
        val cleaned = url.trim()
        val toStore = if (cleaned.isEmpty()) "" else RemoteSyncProtocol.normalizeBaseUrl(cleaned)
        prefs.edit()
            .putString(KEY_SYNC_URL, toStore)
            .putString(KEY_SYNC_TOKEN, token.trim())
            .apply()
        hasCheckedHealth = false
        lastError = null
        notifyCurrentStatus()
        if (triggerSync) {
            triggerSyncNow(forceHealth = true)
        }
    }

    fun resetToDefaultServer(triggerSync: Boolean = true) {
        prefs.edit().remove(KEY_SYNC_URL).apply()
        hasCheckedHealth = false
        lastError = null
        notifyCurrentStatus()
        if (triggerSync) {
            triggerSyncNow(forceHealth = true)
        }
    }

    fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun startMonitoring() {
        notifyCurrentStatus()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                lastError = null
                notifyCurrentStatus()
                triggerSyncNow(forceHealth = true)
            }

            override fun onLost(network: Network) {
                serverReachable = false
                notifyCurrentStatus()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    val now = System.currentTimeMillis()
                    if (!serverReachable && now - lastProbeTimestamp > 5000L) {
                        triggerSyncNow(forceHealth = true)
                    }
                }
            }
        }
        networkCallback = cb
        try {
            connectivityManager.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
            try {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(req, cb)
            } catch (_: Exception) {
            }
        }
        triggerSyncNow(forceHealth = true)
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    fun getCurrentSnapshot(): SyncStatusSnapshot {
        val online = isNetworkConnected()
        val pending = db.getPendingSyncCount()
        val url = getSyncUrl()

        val (label, color) = when {
            isSyncingNow -> "↻ Syncing..." to "#60A5FA"
            !online && pending > 0 -> "● Offline • $pending queued" to "#FBBF24"
            !online -> "● Offline Mode" to "#9CA3AF"
            !hasCheckedHealth -> "↻ Connecting..." to "#60A5FA"
            serverReachable && pending > 0 -> "● Online • $pending pending" to "#60A5FA"
            serverReachable -> "● Online • Synced" to "#4ADE80"
            pending > 0 -> "⚠ Server Offline • $pending queued" to "#FBBF24"
            else -> "⚠ Server Offline • Local" to "#FBBF24"
        }

        return SyncStatusSnapshot(
            label = label,
            colorHex = color,
            isNetworkConnected = online,
            isServerReachable = serverReachable,
            pendingCount = pending,
            serverUrl = url,
            lastSyncAt = getLastSyncTime(),
            lastError = lastError
        )
    }

    fun notifyCurrentStatus() {
        onStatusChanged(getCurrentSnapshot())
    }

    override fun onRemoteSuccess() {
        serverReachable = true
        hasCheckedHealth = true
        lastError = null
        prefs.edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()
        notifyCurrentStatus()
    }

    override fun enqueue(username: String, eventType: String, apiPath: String, payload: JSONObject) {
        db.enqueueRemoteEvent(username, eventType, apiPath, payload)
        notifyCurrentStatus()
    }

    override fun execute(method: String, apiPath: String, bodyJson: String?): ApiResponse? {
        if (!isNetworkConnected()) {
            serverReachable = false
            lastError = "No active internet connection"
            notifyCurrentStatus()
            return null
        }

        return performHttp(
            method = method,
            apiPath = apiPath,
            bodyJson = bodyJson,
            connectTimeoutMs = 5000,
            readTimeoutMs = 12000
        )
    }

    override fun flushPendingSync() {
        if (!isNetworkConnected() || db.getPendingSyncCount() == 0) return
        synchronized(syncLock) {
            val pending = db.getPendingRemoteEvents()
            if (pending.isEmpty()) return

            for (item in pending) {
                val res = performHttp(
                    method = "POST",
                    apiPath = item.apiPath,
                    bodyJson = item.bodyJson,
                    connectTimeoutMs = 5000,
                    readTimeoutMs = 12000
                ) ?: break

                if (res.status in 200..299) {
                    db.markSynced(listOf(item.id))
                    try {
                        val json = JSONObject(res.bodyJson)
                        val statsObj = json.optJSONObject("stats")
                        if (statsObj != null) {
                            db.cacheRemoteStats(statsObj, fallbackUsername = item.username)
                        }
                    } catch (_: Exception) {
                    }
                } else if (!RemoteSyncProtocol.isRetryableQueueFailure(res.status)) {
                    // Non-retryable 4xx (e.g. bad payload / missing item) — clear from queue to prevent blocking future syncs
                    db.markSynced(listOf(item.id))
                } else {
                    break
                }
            }
        }
        notifyCurrentStatus()
    }

    fun triggerSyncNow(forceHealth: Boolean = false, onFinished: ((SyncStatusSnapshot) -> Unit)? = null) {
        executor.execute {
            runSyncCycle(forceHealth)
            onFinished?.invoke(getCurrentSnapshot())
        }
    }

    private fun runSyncCycle(forceHealth: Boolean) {
        synchronized(syncLock) {
            val online = isNetworkConnected()
            if (!online) {
                serverReachable = false
                hasCheckedHealth = true
                lastError = "No active internet connection"
                notifyCurrentStatus()
                return
            }

            val now = System.currentTimeMillis()
            if (!forceHealth && hasCheckedHealth && (now - lastProbeTimestamp) < 4000L && db.getPendingSyncCount() == 0) {
                notifyCurrentStatus()
                return
            }

            isSyncingNow = true
            notifyCurrentStatus()
            try {
                lastProbeTimestamp = now
                val healthRes = performHttp(
                    method = "GET",
                    apiPath = "/api/health",
                    bodyJson = null,
                    connectTimeoutMs = 4000,
                    readTimeoutMs = 4000
                )
                hasCheckedHealth = true
                if (healthRes != null && healthRes.status in 200..299) {
                    serverReachable = true
                    lastError = null
                    prefs.edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()

                    val hadPending = db.getPendingSyncCount() > 0
                    flushPendingSync()
                    if (hadPending) {
                        refreshActiveUserSnapshot()
                    }
                } else {
                    serverReachable = false
                    if (healthRes != null) {
                        lastError = "Server returned HTTP ${healthRes.status}"
                    }
                }
            } finally {
                isSyncingNow = false
                notifyCurrentStatus()
            }
        }
    }

    private fun refreshActiveUserSnapshot() {
        val activeUser = db.getLastActiveUsername()?.trim().orEmpty()
        if (activeUser.isEmpty()) return
        val encoded = URLEncoder.encode(activeUser, "UTF-8").replace("+", "%20")

        val statsRes = performHttp("GET", "/api/student/$encoded/stats", null, 5000, 10000)
        if (statsRes != null && statsRes.status in 200..299) {
            try {
                db.cacheRemoteStats(JSONObject(statsRes.bodyJson), fallbackUsername = activeUser)
            } catch (_: Exception) {
            }
        }

        val resumeRes = performHttp("GET", "/api/student/$encoded/resume", null, 5000, 10000)
        if (resumeRes != null && resumeRes.status in 200..299) {
            try {
                db.cacheRemoteResume(activeUser, JSONObject(resumeRes.bodyJson))
            } catch (_: Exception) {
            }
        }
    }

    private fun performHttp(
        method: String,
        apiPath: String,
        bodyJson: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): ApiResponse? {
        val fullUrl = RemoteSyncProtocol.buildEndpointUrl(getSyncUrl(), apiPath)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "2BAC-Limits-Android")
                val token = getSyncToken()
                if (token.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (method != "GET" && method != "HEAD") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
            }

            if (method != "GET" && method != "HEAD") {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(bodyJson ?: "{}")
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }

            if (RemoteSyncProtocol.isServerDownStatus(code)) {
                serverReachable = false
                hasCheckedHealth = true
                lastError = "Server HTTP $code"
                notifyCurrentStatus()
                return null
            }

            serverReachable = true
            hasCheckedHealth = true
            lastError = null
            prefs.edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()
            ApiResponse(code, text.ifBlank { "{}" })
        } catch (e: Exception) {
            serverReachable = false
            hasCheckedHealth = true
            lastError = e.message ?: e.javaClass.simpleName
            notifyCurrentStatus()
            null
        } finally {
            conn?.disconnect()
        }
    }
}
