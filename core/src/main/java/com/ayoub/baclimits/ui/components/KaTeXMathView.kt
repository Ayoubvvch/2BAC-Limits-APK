package com.ayoub.baclimits.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import com.ayoub.baclimits.data.LocalizedMessage
import com.ayoub.baclimits.data.SolutionStepData
import com.ayoub.baclimits.ui.i18n.AppStrings
import com.ayoub.baclimits.ui.theme.OledColors
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

class AndroidMathBridge(
    private val onMetricsUpdate: (heightCssPx: Int, overflows: Boolean) -> Unit
) {
    @JavascriptInterface
    fun onMetrics(heightCssPx: Int, overflows: Boolean) {
        onMetricsUpdate(heightCssPx, overflows)
    }
}

@SuppressLint("ViewConstructor")
private class MathCanvasWebView(context: Context) : WebView(context) {
    var allowHorizontalPan: Boolean = true
    var onTapFormula: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
                if (allowHorizontalPan) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true
                    if (dy > dx * 1.15f) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (allowHorizontalPan) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!isDragging) {
                    onTapFormula?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredMathWebView(
    context: Context,
    bridge: AndroidMathBridge,
    onPageReady: (WebView) -> Unit
): MathCanvasWebView {
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    return MathCanvasWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = false
            loadWithOverviewMode = false
        }

        addJavascriptInterface(bridge, "AndroidMathBridge")

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val uri = request.url
                if (uri.host == "appassets.androidplatform.net") {
                    return assetLoader.shouldInterceptRequest(uri)
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null) {
                    onPageReady(view)
                }
            }
        }

        loadUrl("https://appassets.androidplatform.net/assets/katex_stage.html")
    }
}

@Composable
fun KaTeXFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    displayMode: Boolean = true,
    fontPx: Float = 20f,
    minScale: Float = 0.72f,
    centerWhenFits: Boolean = true,
    minHeightDp: Int = 56,
    onOverflowChanged: ((Boolean) -> Unit)? = null,
    onTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var measuredHeightDp by remember { mutableIntStateOf(minHeightDp) }
    var overflows by remember { mutableStateOf(false) }
    var pageReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<MathCanvasWebView?>(null) }

    val latestLatex by rememberUpdatedState(latex)
    val latestDisplay by rememberUpdatedState(displayMode)
    val latestFontPx by rememberUpdatedState(fontPx)
    val latestMinScale by rememberUpdatedState(minScale)
    val latestCenter by rememberUpdatedState(centerWhenFits)
    val latestOnOverflow by rememberUpdatedState(onOverflowChanged)
    val latestOnTap by rememberUpdatedState(onTap)

    fun pushRender(wv: WebView) {
        val quoted = JSONObject.quote(latestLatex)
        val js = "window.renderFormula($quoted, $latestDisplay, $latestFontPx, $latestMinScale, $latestCenter);"
        wv.evaluateJavascript(js, null)
    }

    val bridge = remember {
        AndroidMathBridge { heightCssPx, isOverflow ->
            val clampedH = heightCssPx.coerceIn(minHeightDp, 460)
            webViewRef?.post {
                measuredHeightDp = clampedH
                overflows = isOverflow
                webViewRef?.allowHorizontalPan = isOverflow
                latestOnOverflow?.invoke(isOverflow)
            }
        }
    }

    val webView = remember {
        createConfiguredMathWebView(context, bridge) { wv ->
            pageReady = true
            pushRender(wv)
        }.also { webViewRef = it }
    }

    LaunchedEffect(latestOnTap) {
        webView.onTapFormula = latestOnTap
    }

    LaunchedEffect(pageReady, latex, displayMode, fontPx, minScale, centerWhenFits) {
        if (pageReady) {
            pushRender(webView)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            view.onTapFormula = latestOnTap
            view.allowHorizontalPan = overflows
        },
        modifier = modifier
            .fillMaxWidth()
            .height(measuredHeightDp.coerceAtLeast(minHeightDp).dp)
    )
}

@Composable
fun KaTeXStepsView(
    lang: String,
    hints: List<LocalizedMessage>,
    steps: List<SolutionStepData>,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 120
) {
    val context = LocalContext.current
    var measuredHeightDp by remember { mutableIntStateOf(minHeightDp) }
    var pageReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<MathCanvasWebView?>(null) }

    val payloadJson = remember(lang, hints, steps) {
        val hintsArr = JSONArray().apply {
            hints.forEach { h ->
                put(JSONObject().put("ar", h.ar).put("fr", h.fr))
            }
        }
        val stepsArr = JSONArray().apply {
            steps.forEach { s ->
                put(
                    JSONObject()
                        .put("title", JSONObject().put("ar", s.title.ar).put("fr", s.title.fr))
                        .put("explanation", JSONObject().put("ar", s.explanation.ar).put("fr", s.explanation.fr))
                        .put("latex", s.latex ?: JSONObject.NULL)
                )
            }
        }
        JSONObject()
            .put("lang", if (lang == "fr") "fr" else "ar")
            .put("hints", hintsArr)
            .put("steps", stepsArr)
            .toString()
    }

    val latestPayload by rememberUpdatedState(payloadJson)

    fun pushDoc(wv: WebView) {
        wv.evaluateJavascript("window.renderStepsDocument($latestPayload);", null)
    }

    val bridge = remember {
        AndroidMathBridge { heightCssPx, _ ->
            val clampedH = heightCssPx.coerceIn(minHeightDp, 4200)
            webViewRef?.post {
                measuredHeightDp = clampedH
            }
        }
    }

    val webView = remember {
        createConfiguredMathWebView(context, bridge) { wv ->
            pageReady = true
            pushDoc(wv)
        }.also {
            it.allowHorizontalPan = true
            webViewRef = it
        }
    }

    LaunchedEffect(pageReady, payloadJson) {
        if (pageReady) {
            pushDoc(webView)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxWidth()
            .height(measuredHeightDp.dp)
    )
}

@Composable
fun FullscreenFormulaDialog(
    latex: String,
    seed: String?,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    var zoomScale by remember { mutableFloatStateOf(1.1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = OledColors.Surface,
            border = BorderStroke(1.dp, OledColors.BorderStrong)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomOutMap,
                            contentDescription = null,
                            tint = OledColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.expandFormula,
                            style = MaterialTheme.typography.titleMedium,
                            color = OledColors.TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.close,
                            tint = OledColors.TextSecondary
                        )
                    }
                }

                if (!seed.isNullOrBlank()) {
                    Surface(
                        color = OledColors.SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OledColors.Border)
                    ) {
                        Text(
                            text = seed,
                            style = MaterialTheme.typography.labelSmall,
                            color = OledColors.Teal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OledColors.Background, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KaTeXFormulaView(
                        latex = latex,
                        displayMode = true,
                        fontPx = 22f * zoomScale,
                        minScale = if (zoomScale > 1.05f) 1.0f else 0.72f,
                        centerWhenFits = true,
                        minHeightDp = 120
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${strings.zoomLabel}: ${(zoomScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = OledColors.TextSecondary
                    )
                    TextButton(onClick = { zoomScale = 1.0f }) {
                        Text("100%", color = OledColors.Primary, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.75f) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = OledColors.SurfaceElevated,
                            contentColor = OledColors.TextPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = null)
                    }

                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 0.75f..2.2f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = OledColors.Primary,
                            activeTrackColor = OledColors.Primary,
                            inactiveTrackColor = OledColors.SurfaceHighlight
                        )
                    )

                    FilledTonalIconButton(
                        onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.2f) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = OledColors.SurfaceElevated,
                            contentColor = OledColors.TextPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                }
            }
        }
    }
}
