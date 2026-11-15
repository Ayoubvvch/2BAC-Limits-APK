package com.ayoub.baclimits

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ayoub.baclimits.data.LocalLimitsApi
import com.ayoub.baclimits.data.ProgressSyncManager
import com.ayoub.baclimits.data.StudentDatabase
import com.ayoub.baclimits.updater.InAppUpdaterDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var syncBadgeText: TextView
    private lateinit var db: StudentDatabase
    private lateinit var localApi: LocalLimitsApi
    private lateinit var syncManager: ProgressSyncManager
    private lateinit var updaterDialog: InAppUpdaterDialog

    private val apiExecutor = Executors.newFixedThreadPool(2)
    private var pendingApkFile: File? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            pendingApkFile?.let { file ->
                updaterDialog.launchInstaller(file)
                pendingApkFile = null
            }
        } else {
            Toast.makeText(this, "Install permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupOledWindow()

        db = StudentDatabase(this)
        syncManager = ProgressSyncManager(this, db) { statusText, _, _ ->
            runOnUiThread {
                if (::syncBadgeText.isInitialized) {
                    syncBadgeText.text = statusText
                }
            }
        }
        localApi = LocalLimitsApi(db) {
            syncManager.triggerSyncNow()
        }
        updaterDialog = InAppUpdaterDialog(this, syncManager) { apkFile ->
            pendingApkFile = apkFile
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Toast.makeText(this, "Allow 2BAC Limits to install app updates", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                installPermissionLauncher.launch(intent)
            }
        }

        buildRootLayout()
        setupWebView()
        setupBackNavigation()
        syncManager.startMonitoring()
    }

    private fun setupOledWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    private fun buildRootLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Ultra-slim native OLED utility bar (never overlaps page buttons)
        val utilityBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val versionLabel = TextView(this).apply {
            text = "2BAC Limits • v${BuildConfig.VERSION_NAME}"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        }

        syncBadgeText = TextView(this).apply {
            text = "100% Offline"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 11f
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }

        val updateBtn = TextView(this).apply {
            text = "↻ Update"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#09090B"))
                cornerRadius = dp(99).toFloat()
                setStroke(dp(1), Color.parseColor("#24242C"))
            }
            setOnClickListener { updaterDialog.show() }
        }

        utilityBar.addView(versionLabel)
        utilityBar.addView(syncBadgeText)
        utilityBar.addView(updateBtn)

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#181820"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        root.addView(utilityBar)
        root.addView(divider)
        root.addView(webView)
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.addJavascriptInterface(AndroidApiBridge(), "AndroidApiBridge")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                FETCH_BRIDGE_JS,
                setOf("https://appassets.androidplatform.net")
            )
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val uri = request.url
                val host = uri.host.orEmpty()
                val path = uri.path.orEmpty()

                // 1. Offline bundled KaTeX 0.16.11 assets
                if (host == "cdn.jsdelivr.net" && path.startsWith("/npm/katex@0.16.11/dist/")) {
                    val rel = path.removePrefix("/npm/katex@0.16.11/dist/")
                    return serveAssetFile("vendor/katex/$rel")
                }

                // 2. Fast-fail Google Fonts when offline so boot never hangs
                if (host == "fonts.googleapis.com" && !syncManager.isNetworkConnected()) {
                    return WebResourceResponse(
                        "text/css",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                // 3. Security shield stub paths referenced by untouched index.html
                if (host == "appassets.androidplatform.net") {
                    if (path == "/security-shield.css") {
                        return WebResourceResponse(
                            "text/css",
                            "UTF-8",
                            ByteArrayInputStream("/* AcMods Shield */".toByteArray())
                        )
                    }
                    if (path == "/security-shield.js") {
                        return WebResourceResponse(
                            "application/javascript",
                            "UTF-8",
                            ByteArrayInputStream("/* AcMods Shield */".toByteArray())
                        )
                    }
                    return assetLoader.shouldInterceptRequest(uri)
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Fallback bridge injection for older WebView implementations
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view?.evaluateJavascript(FETCH_BRIDGE_JS, null)
                }
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun serveAssetFile(assetPath: String): WebResourceResponse? {
        return try {
            val clean = assetPath.trimStart('/')
            val mime = when {
                clean.endsWith(".css") -> "text/css"
                clean.endsWith(".js") -> "application/javascript"
                clean.endsWith(".woff2") -> "font/woff2"
                clean.endsWith(".woff") -> "font/woff"
                clean.endsWith(".ttf") -> "font/ttf"
                else -> "application/octet-stream"
            }
            val stream = assets.open(clean)
            val enc = if (mime.startsWith("text/") || mime == "application/javascript") "UTF-8" else null
            WebResourceResponse(
                mime,
                enc,
                200,
                "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "max-age=31536000"
                ),
                stream
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    inner class AndroidApiBridge {
        @JavascriptInterface
        fun request(reqId: String, method: String, path: String, body: String?) {
            apiExecutor.execute {
                val res = localApi.handle(method, path, body)
                val b64 = Base64.encodeToString(res.bodyJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                runOnUiThread {
                    webView.evaluateJavascript(
                        "window.__bacApiResolve('$reqId', ${res.status}, '$b64');",
                        null
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        syncManager.triggerSyncNow()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        syncManager.stopMonitoring()
        apiExecutor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val FETCH_BRIDGE_JS = """
            (() => {
              if (window.__bacFetchInstalled) return;
              window.__bacFetchInstalled = true;
              const pending = new Map();
              let seq = 0;

              window.__bacApiResolve = function(id, status, b64) {
                const resolver = pending.get(id);
                if (!resolver) return;
                pending.delete(id);
                try {
                  const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
                  const text = new TextDecoder('utf-8').decode(bytes);
                  resolver.resolve(new Response(text, {
                    status: status,
                    headers: { 'Content-Type': 'application/json; charset=utf-8' }
                  }));
                } catch (err) {
                  resolver.reject(err);
                }
              };

              const origFetch = window.fetch ? window.fetch.bind(window) : null;
              window.fetch = function(input, init) {
                const rawUrl = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                let path = rawUrl;
                try {
                  const u = new URL(rawUrl, window.location.href);
                  if (u.origin === window.location.origin) {
                    path = u.pathname + u.search;
                  }
                } catch (_) {}

                if (path.startsWith('/api/') || path === '/api') {
                  const method = (init && init.method) ? String(init.method).toUpperCase() : 'GET';
                  const body = (init && init.body != null) ? String(init.body) : '';
                  const reqId = 'r_' + (++seq);
                  return new Promise((resolve, reject) => {
                    pending.set(reqId, { resolve, reject });
                    try {
                      window.AndroidApiBridge.request(reqId, method, path, body);
                    } catch (e) {
                      pending.delete(reqId);
                      reject(e);
                    }
                  });
                }
                return origFetch ? origFetch(input, init) : Promise.reject(new Error('fetch unavailable'));
              };
            })();
        """.trimIndent()
    }
}
