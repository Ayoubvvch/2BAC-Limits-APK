package com.ayoub.baclimits.data

import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.MathExprEvaluator
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

data class ApiResponse(val status: Int, val bodyJson: String)

interface RemoteApiGateway {
    fun isServerConfigured(): Boolean
    fun execute(method: String, apiPath: String, bodyJson: String?): ApiResponse?
    fun flushPendingSync()
    fun enqueue(username: String, eventType: String, apiPath: String, payload: JSONObject)
    fun onRemoteSuccess()
}

class LocalLimitsApi(
    private val db: StudentDatabase,
    private val gateway: RemoteApiGateway? = null
) {

    fun handle(methodRaw: String, pathRaw: String, bodyRaw: String?): ApiResponse {
        val method = methodRaw.uppercase()
        val path = pathRaw.substringBefore("?")
        val normalizedPath = when {
            path.startsWith("/api/bac-limits/") -> "/api/" + path.removePrefix("/api/bac-limits/")
            path == "/api/bac-limits" -> "/api"
            else -> path
        }

        return try {
            val body = if (!bodyRaw.isNullOrBlank()) JSONObject(bodyRaw) else JSONObject()
            route(method, normalizedPath, body)
        } catch (e: IllegalArgumentException) {
            err(400, e.message ?: "Bad request")
        } catch (e: Exception) {
            err(500, e.message ?: "Internal engine error")
        }
    }

    private fun route(method: String, path: String, body: JSONObject): ApiResponse {
        // GET /api/health
        if (method == "GET" && path == "/api/health") {
            val remote = gateway?.execute("GET", "/api/health", null)
            if (remote != null && remote.status in 200..299) {
                gateway?.onRemoteSuccess()
                return remote
            }
            return ok(JSONObject().put("status", "offline_local").put("offline", true))
        }

        // POST /api/login
        if (method == "POST" && path == "/api/login") {
            val username = body.optString("username", "").trim()
            val lang = body.optString("lang", "ar")
            validateUsername(username, lang)

            gateway?.flushPendingSync()
            val remote = gateway?.execute("POST", "/api/login", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        db.cacheRemoteStats(json, fallbackUsername = username)
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            val stats = db.loginOrCreate(username, lang)
            return ok(stats)
        }

        // GET /api/student/{username}/resume
        val resumeMatch = Regex("^/api/student/([^/]+)/resume$").matchEntire(path)
        if (method == "GET" && resumeMatch != null) {
            val user = decode(resumeMatch.groupValues[1])
            val remotePath = "/api/student/${encodePathSegment(user)}/resume"
            val remote = gateway?.execute("GET", remotePath, null)
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        db.cacheRemoteResume(user, json)
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }
            return ok(db.getResumeJson(user))
        }

        // GET /api/student/{username}/stats
        val statsMatch = Regex("^/api/student/([^/]+)/stats$").matchEntire(path)
        if (method == "GET" && statsMatch != null) {
            val user = decode(statsMatch.groupValues[1])
            val remotePath = "/api/student/${encodePathSegment(user)}/stats"
            val remote = gateway?.execute("GET", remotePath, null)
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        db.cacheRemoteStats(json, fallbackUsername = user)
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }
            return ok(db.getStatsJson(user))
        }

        // POST /api/student/{username}/lang
        val langMatch = Regex("^/api/student/([^/]+)/lang$").matchEntire(path)
        if (method == "POST" && langMatch != null) {
            val user = decode(langMatch.groupValues[1])
            val lang = body.optString("lang", "ar")
            db.ensureStudentRow(user, lang)
            db.updateLang(user, lang)

            val remotePath = "/api/student/${encodePathSegment(user)}/lang"
            val remote = gateway?.execute("POST", remotePath, body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            gateway?.enqueue(user, "remote_lang", remotePath, body)
            return ok(JSONObject().put("ok", true).put("lang", lang))
        }

        // POST /api/exercises/next
        if (method == "POST" && path == "/api/exercises/next") {
            val user = body.optString("username", "").trim()
            val tech = body.optString("technique", "adaptive")
            val diff = body.optInt("difficulty", 0)
            val explicitSeed = if (body.isNull("seed")) null else body.optString("seed", "").trim().takeIf { it.isNotBlank() }

            val remote = gateway?.execute("POST", "/api/exercises/next", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        db.cacheServerExercise(json, usernameRaw = user.ifBlank { null })
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            if (user.isNotBlank()) db.ensureStudentRow(user)

            val engine = OfflineLimitsEngineHolder.get()

            if (explicitSeed != null) {
                val cached = db.getCachedServerExercise(explicitSeed)
                if (cached != null) {
                    if (user.isNotBlank()) db.activateCachedServerExercise(user, cached)
                    return ok(cached)
                }
                if (!engine.isValidSeed(explicitSeed)) {
                    return err(
                        503,
                        "هذا التمرين غير محفوظ محلياً. اتصل بالإنترنت لتحميله من السيرفر. / Exercice non disponible hors-ligne."
                    )
                }
            }

            val (weakest, suggestedDiff) = db.getWeakestTechniqueAndDifficulty(user)
            val ex = engine.generate(
                requestedTechnique = tech,
                requestedDifficulty = diff,
                explicitSeed = explicitSeed,
                weakestTechnique = weakest,
                suggestedDifficulty = suggestedDiff
            )
            if (user.isNotBlank()) db.setActiveExercise(user, ex)
            return ok(db.exerciseToJson(ex))
        }

        // POST /api/exercises/hint
        if (method == "POST" && path == "/api/exercises/hint") {
            val user = body.optString("username", "").trim()
            val seed = body.optString("seed", "").trim()
            val hintIndex = body.optInt("hint_index", 0)

            val remote = gateway?.execute("POST", "/api/exercises/hint", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        val hintObj = json.optJSONObject("hint")
                        if (hintObj != null) {
                            db.cacheServerHint(user, seed, hintIndex, hintObj)
                        }
                        if (user.isNotBlank()) db.recordHintShown(user, seed, hintIndex)
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            val engine = OfflineLimitsEngineHolder.get()

            if (engine.isValidSeed(seed)) {
                val ex = engine.generate("adaptive", 0, explicitSeed = seed)
                if (hintIndex !in ex.hints.indices) {
                    return err(400, "No more hints available")
                }
                if (user.isNotBlank()) db.recordHintShown(user, ex.seed, hintIndex)
                val h = ex.hints[hintIndex]
                return ok(
                    JSONObject().put(
                        "hint",
                        JSONObject().put("ar", h.ar).put("fr", h.fr)
                    )
                )
            }

            val cachedHint = db.getCachedServerHint(seed, hintIndex)
            if (cachedHint != null) {
                if (user.isNotBlank()) db.recordHintShown(user, seed, hintIndex)
                gateway?.enqueue(user, "remote_hint", "/api/exercises/hint", body)
                return ok(JSONObject().put("hint", cachedHint))
            }
            return err(503, "السيرفر غير متصل لتحميل التلميح. / Serveur hors ligne pour charger l'indice.")
        }

        // POST /api/exercises/solution
        if (method == "POST" && path == "/api/exercises/solution") {
            val user = body.optString("username", "").trim()
            val seed = body.optString("seed", "").trim()

            val remote = gateway?.execute("POST", "/api/exercises/solution", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        db.cacheServerSolution(user, seed, json)
                        if (user.isNotBlank()) db.recordSolutionRevealed(user, seed)
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            val engine = OfflineLimitsEngineHolder.get()

            if (engine.isValidSeed(seed)) {
                val ex = engine.generate("adaptive", 0, explicitSeed = seed)
                if (user.isNotBlank()) db.recordSolutionRevealed(user, ex.seed)
                return ok(db.solutionToJson(ex))
            }

            val cachedSol = db.getCachedServerSolution(seed)
            if (cachedSol != null) {
                if (user.isNotBlank()) db.recordSolutionRevealed(user, seed)
                gateway?.enqueue(user, "remote_solution", "/api/exercises/solution", body)
                return ok(cachedSol)
            }
            return err(503, "السيرفر غير متصل لتحميل الحل المفصل. / Serveur hors ligne pour charger la correction.")
        }

        // POST /api/exercises/submit
        if (method == "POST" && path == "/api/exercises/submit") {
            val user = body.optString("username", "").trim()
            val seed = body.optString("seed", "").trim()
            val rawAnswer = body.optString("answer", "")

            val remote = gateway?.execute("POST", "/api/exercises/submit", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        val statsObj = json.optJSONObject("stats")
                        val gradeObj = json.optJSONObject("grade")
                        val solObj = json.optJSONObject("solution")
                        if (statsObj != null) {
                            db.cacheRemoteStats(statsObj, fallbackUsername = user)
                        }
                        if (solObj != null) {
                            db.cacheServerSolution(user, seed, solObj)
                        }
                        db.updateCachedResumeAfterSubmit(user, seed, rawAnswer, gradeObj, solObj)
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            val engine = OfflineLimitsEngineHolder.get()

            if (!engine.isValidSeed(seed)) {
                return err(
                    503,
                    "السيرفر غير متصل حالياً لتصحيح هذا التمرين السحابي. اضغط «تمرين جديد» للتدريب أوفلاين، أو أعد التحقق عند عودة الاتصال."
                )
            }

            val ex = engine.generate("adaptive", 0, explicitSeed = seed)
            val evalResult = engine.evaluateAnswer(
                expectedAnswer = ex.expectedAnswer,
                studentAnswer = rawAnswer,
                expectedLatex = ex.answerLatex
            )

            val gradeObj = JSONObject()
                .put("is_correct", evalResult.isCorrect)
                .put("message", JSONObject().put("ar", evalResult.messageAr).put("fr", evalResult.messageFr))

            val updatedStats = db.recordSubmission(user, ex, rawAnswer, evalResult.isCorrect, gradeObj)

            val resp = JSONObject().apply {
                put("grade", gradeObj)
                put("stats", updatedStats)
                if (evalResult.isCorrect) {
                    put("solution", db.solutionToJson(ex))
                }
            }
            return ok(resp)
        }

        return err(404, "Endpoint not found: $method $path")
    }

    private fun validateUsername(rawName: String, lang: String) {
        require(rawName.length in 2..32) {
            if (lang == "fr") "Le nom d'utilisateur doit contenir entre 2 et 32 caractères."
            else "يجب أن يتراوح اسم التلميذ بين 2 و 32 حرفاً."
        }
    }

    private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun encodePathSegment(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun ok(json: JSONObject): ApiResponse = ApiResponse(200, json.toString())

    private fun err(code: Int, detail: String): ApiResponse =
        ApiResponse(code, JSONObject().put("detail", detail).toString())
}
