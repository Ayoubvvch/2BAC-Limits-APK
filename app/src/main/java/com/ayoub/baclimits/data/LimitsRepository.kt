package com.ayoub.baclimits.data

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

class LimitsRepository(
    context: Context,
    val db: StudentDatabase,
    val syncManager: ProgressSyncManager
) {
    companion object {
        private const val PREFS_SESSION = "bac_limits_native_session"
        private const val KEY_ACTIVE_USER = "active_username"
        private const val KEY_APP_LANG = "app_lang"
        private const val KEY_SHOW_IME = "show_system_keyboard"
    }

    private val prefs = context.getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
    private val localApi = LocalLimitsApi(db, syncManager)

    fun getInitialSession(): Pair<String?, String> {
        val savedUser = prefs.getString(KEY_ACTIVE_USER, null)?.trim()?.takeIf { it.isNotEmpty() }
        val savedLang = prefs.getString(KEY_APP_LANG, null)?.takeIf { it == "ar" || it == "fr" }

        if (savedUser != null) {
            return savedUser to (savedLang ?: "ar")
        }

        // Seamless upgrade from v1.0.1 / v1.0.2 WebView SQLite session
        val migrated = db.getLastActiveStudent()
        if (migrated != null) {
            val lang = savedLang ?: migrated.second
            saveSession(migrated.first, lang)
            return migrated.first to lang
        }

        return null to (savedLang ?: "ar")
    }

    fun saveSession(username: String, lang: String) {
        val cleanLang = if (lang == "fr") "fr" else "ar"
        prefs.edit()
            .putString(KEY_ACTIVE_USER, username.trim())
            .putString(KEY_APP_LANG, cleanLang)
            .apply()
    }

    fun saveLang(lang: String) {
        val cleanLang = if (lang == "fr") "fr" else "ar"
        prefs.edit().putString(KEY_APP_LANG, cleanLang).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_ACTIVE_USER).apply()
    }

    fun getShowSystemKeyboard(): Boolean = prefs.getBoolean(KEY_SHOW_IME, false)

    fun setShowSystemKeyboard(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_IME, show).apply()
    }

    fun getRecentUsernames(): List<String> = db.getRecentUsernames(limit = 6)

    fun login(username: String, lang: String): Result<StudentStats> {
        val payload = JSONObject()
            .put("username", username.trim())
            .put("lang", if (lang == "fr") "fr" else "ar")
        val res = localApi.handle("POST", "/api/login", payload.toString())
        return if (res.status in 200..299) {
            try {
                val stats = LimitsJsonParser.parseStats(JSONObject(res.bodyJson))
                Result.success(stats)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun stats(username: String, existingFallback: StudentStats? = null): Result<StudentStats> {
        val res = localApi.handle("GET", "/api/student/${encodeSegment(username)}/stats", null)
        return if (res.status in 200..299) {
            try {
                val stats = LimitsJsonParser.parseStats(JSONObject(res.bodyJson), existingFallback)
                Result.success(stats)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun resume(username: String): Result<ResumeState> {
        val res = localApi.handle("GET", "/api/student/${encodeSegment(username)}/resume", null)
        return if (res.status in 200..299) {
            try {
                Result.success(LimitsJsonParser.parseResume(JSONObject(res.bodyJson)))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun updateLang(username: String, lang: String): Result<Unit> {
        val cleanLang = if (lang == "fr") "fr" else "ar"
        val payload = JSONObject().put("lang", cleanLang)
        val res = localApi.handle("POST", "/api/student/${encodeSegment(username)}/lang", payload.toString())
        return if (res.status in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun nextExercise(
        username: String,
        technique: String,
        difficulty: Int,
        seed: String? = null
    ): Result<ExerciseData> {
        val payload = JSONObject()
            .put("username", username.trim())
            .put("technique", technique)
            .put("difficulty", difficulty)
            .put("seed", seed?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        val res = localApi.handle("POST", "/api/exercises/next", payload.toString())
        return if (res.status in 200..299) {
            try {
                Result.success(LimitsJsonParser.parseExercise(JSONObject(res.bodyJson)))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun requestHint(username: String, seed: String, hintIndex: Int): Result<LocalizedMessage> {
        val payload = JSONObject()
            .put("username", username.trim())
            .put("seed", seed.trim())
            .put("hint_index", hintIndex)
        val res = localApi.handle("POST", "/api/exercises/hint", payload.toString())
        return if (res.status in 200..299) {
            try {
                val obj = JSONObject(res.bodyJson)
                val hintObj = obj.optJSONObject("hint") ?: obj
                Result.success(LimitsJsonParser.parseLocalizedMessage(hintObj))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun submitAnswer(
        username: String,
        seed: String,
        answer: String,
        existingStats: StudentStats?
    ): Result<SubmitResult> {
        val payload = JSONObject()
            .put("username", username.trim())
            .put("seed", seed.trim())
            .put("answer", answer.trim())
        val res = localApi.handle("POST", "/api/exercises/submit", payload.toString())
        return if (res.status in 200..299) {
            try {
                Result.success(LimitsJsonParser.parseSubmit(JSONObject(res.bodyJson), existingStats))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    fun requestSolution(username: String, seed: String): Result<SolutionData> {
        val payload = JSONObject()
            .put("username", username.trim())
            .put("seed", seed.trim())
        val res = localApi.handle("POST", "/api/exercises/solution", payload.toString())
        return if (res.status in 200..299) {
            try {
                val sol = LimitsJsonParser.parseSolution(JSONObject(res.bodyJson))
                    ?: return Result.failure(IllegalStateException("Empty solution payload"))
                Result.success(sol)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException(LimitsJsonParser.parseApiError(res.bodyJson, res.status)))
        }
    }

    private fun encodeSegment(raw: String): String =
        URLEncoder.encode(raw.trim(), "UTF-8").replace("+", "%20")
}
