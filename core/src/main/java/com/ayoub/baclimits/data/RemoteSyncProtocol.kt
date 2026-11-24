package com.ayoub.baclimits.data

import org.json.JSONObject
import kotlin.math.roundToInt

object RemoteSyncProtocol {
    const val DEFAULT_SERVER_URL = "http://51.170.138.59"

    private val SERVER_SEED_REGEX = Regex("^2BAC-[A-Z0-9]{4}-L[123]-[0-9A-F]{10,}$")
    private val LOCAL_SEED_REGEX = Regex("^2BAC-[A-Z0-9]{4}-L[123]-[0-9A-F]{8}$")

    fun inferTechniqueFromSeed(seed: String): String {
        val code = seed.trim().uppercase().split("-").getOrNull(1).orEmpty()
        return when (code) {
            "POLY" -> "poly_factor"
            "SQRT" -> "sqrt_conjugate"
            "NTHR", "NROT" -> "nth_root"
            "LNLM" -> "ln_limits"
            "EXPL" -> "exp_limits"
            "ADD0" -> "add_sub_zero"
            "TRIG" -> "trig"
            "HYBR" -> "hybrid"
            "SQLM" -> "seq_limits"
            "SQAG" -> "seq_arith_geo"
            "SQFP" -> "seq_fixed_point"
            "SQHM" -> "seq_homographic"
            else -> "adaptive"
        }
    }

    fun normalizeBaseUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_SERVER_URL

        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }

        var clean = withScheme.trimEnd('/')
        for (suffix in listOf("/docs", "/redoc", "/openapi.json")) {
            if (clean.endsWith(suffix, ignoreCase = true)) {
                clean = clean.dropLast(suffix.length).trimEnd('/')
            }
        }
        return clean.ifEmpty { DEFAULT_SERVER_URL }
    }

    fun buildEndpointUrl(baseUrl: String, apiPath: String): String {
        val base = normalizeBaseUrl(baseUrl)
        val path = if (apiPath.startsWith("/")) apiPath else "/$apiPath"

        return when {
            base.endsWith("/api/bac-limits", ignoreCase = true) && path.startsWith("/api/") ->
                base + path.removePrefix("/api")
            base.endsWith("/api/bac-limits", ignoreCase = true) && path == "/api" ->
                base
            base.endsWith("/api", ignoreCase = true) && path.startsWith("/api/") ->
                base + path.removePrefix("/api")
            base.endsWith("/api", ignoreCase = true) && path == "/api" ->
                base
            else -> base + path
        }
    }

    fun isServerSeed(seed: String?): Boolean {
        val s = seed?.trim()?.uppercase().orEmpty()
        return SERVER_SEED_REGEX.matches(s)
    }

    fun isLocalKotlinSeed(seed: String?): Boolean {
        val s = seed?.trim()?.uppercase().orEmpty()
        return LOCAL_SEED_REGEX.matches(s)
    }

    fun isServerDownStatus(httpCode: Int): Boolean =
        httpCode in listOf(502, 503, 504)

    fun isRetryableQueueFailure(httpCode: Int): Boolean =
        httpCode >= 500 || httpCode == 408 || httpCode == 429

    /**
     * Converts remote stats accuracy field (either 0..1 ratio like 0.9 or 0..100 percent like 90)
     * into an integer percentage 0..100 for local SQLite fallback calculations.
     */
    fun extractAccuracyPercent(statsJson: JSONObject, totalSolved: Int, totalAttempts: Int): Int {
        if (statsJson.has("accuracy") && !statsJson.isNull("accuracy")) {
            val raw = statsJson.optDouble("accuracy", -1.0)
            if (!raw.isNaN() && raw >= 0.0) {
                return when {
                    raw == 0.0 -> 0
                    raw <= 1.0 -> {
                        if (raw == 1.0 && totalAttempts > 0 && (totalSolved.toDouble() / totalAttempts) < 0.1) {
                            1
                        } else {
                            (raw * 100.0).roundToInt().coerceIn(0, 100)
                        }
                    }
                    else -> raw.roundToInt().coerceIn(0, 100)
                }
            }
        }
        return if (totalAttempts <= 0) 0 else ((totalSolved * 100.0) / totalAttempts).roundToInt().coerceIn(0, 100)
    }
}
