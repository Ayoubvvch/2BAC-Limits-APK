package com.ayoub.baclimits.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ProgressSyncManager(
    private val context: Context,
    private val db: StudentDatabase,
    private val onStatusChanged: (statusText: String, isOnline: Boolean, pendingCount: Int) -> Unit
) {
    companion object {
        private const val PREFS_NAME = "bac_limits_sync_prefs"
        private const val KEY_SYNC_URL = "remote_sync_url"
        private const val KEY_SYNC_TOKEN = "remote_sync_token"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun getSyncUrl(): String = prefs.getString(KEY_SYNC_URL, "")?.trim() ?: ""

    fun getSyncToken(): String = prefs.getString(KEY_SYNC_TOKEN, "")?.trim() ?: ""

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    fun getPendingCount(): Int = db.getPendingSyncCount()

    fun saveSyncConfig(url: String, token: String) {
        prefs.edit()
            .putString(KEY_SYNC_URL, url.trim())
            .putString(KEY_SYNC_TOKEN, token.trim())
            .apply()
        notifyCurrentStatus()
        triggerSyncNow()
    }

    fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun startMonitoring() {
        notifyCurrentStatus()
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyCurrentStatus()
                triggerSyncNow()
            }

            override fun onLost(network: Network) {
                notifyCurrentStatus()
            }
        }
        networkCallback = cb
        try {
            connectivityManager.registerNetworkCallback(req, cb)
        } catch (_: Exception) {
        }
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

    fun notifyCurrentStatus() {
        val online = isNetworkConnected()
        val pending = db.getPendingSyncCount()
        val remoteUrl = getSyncUrl()
        val label = when {
            !online && pending > 0 -> "Offline • $pending queued"
            !online -> "100% Offline"
            remoteUrl.isNotEmpty() && pending > 0 -> "Syncing $pending..."
            remoteUrl.isNotEmpty() -> "Online • Synced"
            else -> "Online • Local Ready"
        }
        onStatusChanged(label, online, pending)
    }

    fun triggerSyncNow() {
        executor.execute {
            val pending = db.getPendingSyncItems()
            val remoteUrl = getSyncUrl()
            if (remoteUrl.isEmpty() || !isNetworkConnected() || pending.isEmpty()) {
                notifyCurrentStatus()
                return@execute
            }

            try {
                val eventsArray = JSONArray()
                val syncedIds = mutableListOf<Long>()
                for ((id, payloadStr) in pending) {
                    eventsArray.put(JSONObject(payloadStr))
                    syncedIds.add(id)
                }

                val body = JSONObject().apply {
                    put("app", "2BAC-Limits-APK")
                    put("events", eventsArray)
                }

                val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    val token = getSyncToken()
                    if (token.isNotEmpty()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    db.markSynced(syncedIds)
                    prefs.edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()
                }
            } catch (_: Exception) {
                // Keep queued locally for automatic retry on next connection
            } finally {
                notifyCurrentStatus()
            }
        }
    }
}
