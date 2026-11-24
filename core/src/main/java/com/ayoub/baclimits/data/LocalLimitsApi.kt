package com.ayoub.baclimits.data

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
            val username = body.optCleanString("username").trim()
            val lang = body.optCleanString("lang", "ar")
            validateUsername(username, lang)

            gateway?.flushPendingSync()
            val remote = gateway?.execute("POST", "/api/login", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        db.cacheRemoteStats(JSONObject(remote.bodyJson), fallbackUsername = username)
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
                        db.cacheRemoteResume(user, JSONObject(remote.bodyJson))
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
                        db.cacheRemoteStats(JSONObject(remote.bodyJson), fallbackUsername = user)
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
            val lang = body.optCleanString("lang", "ar")
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
            return ok(JSONObject().put("preferred_lang", lang))
        }

        // POST /api/exercises/next
        if (method == "POST" && path == "/api/exercises/next") {
            val user = body.optCleanString("username").trim()
            val chapter = body.optCleanString("chapter", "limits").ifBlank { "limits" }
            val tech = body.optCleanString("technique", "adaptive").ifBlank { "adaptive" }
            val diff = body.optInt("difficulty", 0)
            val explicitSeed = body.optCleanString("seed").trim().takeIf { it.isNotBlank() }

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

            if (user.isNotBlank()) {
                db.ensureStudentRow(user)
                val activeIncomplete = db.getActiveIncompleteSeed(user)
                if (activeIncomplete != null && explicitSeed?.uppercase() != activeIncomplete.uppercase()) {
                    val lockDetail = JSONObject()
                        .put("code", "ACTIVE_EXERCISE_LOCKED")
                        .put("active_seed", activeIncomplete)
                        .put("ar", "أكمل التمرين الحالي أولاً (أجب عنه أو اعرض الحل المفصل) لفتح التمرين التالي.")
                        .put("fr", "Terminez d'abord l'exercice en cours (résolvez-le ou affichez la solution) avant d'en lancer un nouveau.")
                    return ApiResponse(409, JSONObject().put("detail", lockDetail).toString())
                }
            }

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

            val (weakest, suggestedDiff) = db.getWeakestTechniqueAndDifficulty(user, chapter)
            val ex = engine.generate(
                requestedTechnique = tech,
                requestedDifficulty = diff,
                explicitSeed = explicitSeed,
                weakestTechnique = weakest,
                suggestedDifficulty = suggestedDiff,
                chapter = chapter
            )

            val coachMode = if (tech == "adaptive") "balanced" else "manual"
            val (coachAr, coachFr) = if (tech == "adaptive") {
                "وضع أوفلاين الذكي: تمرين معاير لمستواك الحالي." to "Mode hors-ligne adaptatif : exercice calibré à ton niveau."
            } else {
                null to null
            }

            if (user.isNotBlank()) {
                db.setActiveExercise(user, ex, coachMode = coachMode, coachAr = coachAr, coachFr = coachFr)
            }
            val coachStr = if (coachAr != null) {
                JSONObject().put("mode", coachMode).put("reason", JSONObject().put("ar", coachAr).put("fr", coachFr)).toString()
            } else null

            return ok(db.exerciseToJson(ex, isRevealed = false, isCompleted = false, timerState = "ready", coachJsonStr = coachStr))
        }

        // POST /api/exercises/start
        if (method == "POST" && path == "/api/exercises/start") {
            val user = body.optCleanString("username").trim()
            val seed = body.optCleanString("seed").trim()

            val remote = gateway?.execute("POST", "/api/exercises/start", body.toString())
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

            if (RemoteSyncProtocol.isServerSeed(seed)) {
                gateway?.enqueue(user, "remote_start", "/api/exercises/start", body)
            }
            val started = db.startOrResumeOfflineTimer(user, seed)
            if (started != null) {
                return ok(started)
            }
            val engine = OfflineLimitsEngineHolder.get()
            if (engine.isValidSeed(seed)) {
                val ex = engine.generate("adaptive", 0, explicitSeed = seed)
                if (user.isNotBlank()) db.setActiveExercise(user, ex)
                val startedFallback = db.startOrResumeOfflineTimer(user, ex.seed)
                if (startedFallback != null) return ok(startedFallback)
                return ok(db.exerciseToJson(ex, isRevealed = true, timerState = "running"))
            }
            return err(404, "Exercise not found offline")
        }

        // POST /api/exercises/pause
        if (method == "POST" && path == "/api/exercises/pause") {
            val user = body.optCleanString("username").trim()
            val seed = body.optCleanString("seed").trim()

            val remote = gateway?.execute("POST", "/api/exercises/pause", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            if (RemoteSyncProtocol.isServerSeed(seed)) {
                gateway?.enqueue(user, "remote_pause", "/api/exercises/pause", body)
            }
            return ok(db.pauseOfflineTimer(user, seed))
        }

        // POST /api/exercises/hint
        if (method == "POST" && path == "/api/exercises/hint") {
            val user = body.optCleanString("username").trim()
            val seed = body.optCleanString("seed").trim()
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

            if (user.isNotBlank() && !db.isActiveRevealed(user, seed)) {
                return err(400, "Start the exercise first before requesting a hint.")
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
                    JSONObject()
                        .put("seed", ex.seed)
                        .put("hint_index", hintIndex)
                        .put("total_hints", ex.hints.size)
                        .put("hint", JSONObject().put("ar", h.ar).put("fr", h.fr))
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
            val user = body.optCleanString("username").trim()
            val seed = body.optCleanString("seed").trim()

            val remote = gateway?.execute("POST", "/api/exercises/solution", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        db.cacheServerSolution(user, seed, json)
                        if (user.isNotBlank()) db.recordSolutionRevealed(user, seed)
                        val statsObj = json.optJSONObject("stats")
                        if (statsObj != null) {
                            db.cacheRemoteStats(statsObj, fallbackUsername = user)
                        }
                    } catch (_: Exception) {
                    }
                    gateway?.onRemoteSuccess()
                }
                return remote
            }

            val engine = OfflineLimitsEngineHolder.get()
            if (engine.isValidSeed(seed)) {
                val ex = engine.generate("adaptive", 0, explicitSeed = seed)
                val solJson = db.solutionToJson(ex)
                solJson.put("seed", ex.seed)
                solJson.put("full_limit_latex", ex.fullLimitLatex)
                if (user.isNotBlank()) {
                    val outcome = db.recordSolutionRevealed(user, ex.seed)
                    if (outcome != null) {
                        solJson.put("fluency", outcome.opt("fluency"))
                        solJson.put("timer", outcome.opt("timer"))
                        solJson.put("is_completed", true)
                        solJson.put("stats", outcome.opt("stats"))
                    }
                }
                return ok(solJson)
            }

            val cachedSol = db.getCachedServerSolution(seed)
            if (cachedSol != null) {
                if (user.isNotBlank()) {
                    val outcome = db.recordSolutionRevealed(user, seed)
                    if (outcome != null) {
                        cachedSol.put("fluency", outcome.opt("fluency"))
                        cachedSol.put("timer", outcome.opt("timer"))
                        cachedSol.put("is_completed", true)
                        cachedSol.put("stats", outcome.opt("stats"))
                    }
                }
                gateway?.enqueue(user, "remote_solution", "/api/exercises/solution", body)
                return ok(cachedSol)
            }
            return err(503, "السيرفر غير متصل لتحميل الحل المفصل. / Serveur hors ligne pour charger la correction.")
        }

        // POST /api/exercises/submit
        if (method == "POST" && path == "/api/exercises/submit") {
            val user = body.optCleanString("username").trim()
            val seed = body.optCleanString("seed").trim()
            val rawAnswer = body.optCleanString("answer")
            val solveTimeSec = body.optNullableDouble("solve_time_sec")

            val remote = gateway?.execute("POST", "/api/exercises/submit", body.toString())
            if (remote != null) {
                if (remote.status in 200..299) {
                    try {
                        val json = JSONObject(remote.bodyJson)
                        val statsObj = json.optJSONObject("stats")
                        val gradeObj = json.optJSONObject("grade")
                        val solObj = if (json.isNull("solution")) null else json.optJSONObject("solution")
                        val isCompleted = json.optBoolean("is_completed", false)
                        if (statsObj != null) {
                            db.cacheRemoteStats(statsObj, fallbackUsername = user)
                        }
                        if (solObj != null) {
                            db.cacheServerSolution(user, seed, solObj)
                        }
                        db.updateCachedResumeAfterSubmit(user, seed, rawAnswer, gradeObj, solObj, isCompleted)
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
                    "السيرفر غير متصل حالياً لتصحيح هذا التمرين السحابي. اتصل بالإنترنت لإكمال التمرين."
                )
            }

            val ex = engine.generate("adaptive", 0, explicitSeed = seed)
            val evalResult = engine.evaluateAnswer(
                expectedAnswer = ex.expectedAnswer,
                studentAnswer = rawAnswer,
                expectedLatex = ex.answerLatex,
                expectedSympyStr = ex.answerSympyStr,
                answerKind = ex.answerKind
            )

            val gradeObj = JSONObject()
                .put("valid_syntax", evalResult.validSyntax)
                .put("is_correct", evalResult.isCorrect)
                .put("student_latex", evalResult.studentLatex)
                .put("expected_latex", evalResult.expectedLatex)
                .put("message", JSONObject().put("ar", evalResult.messageAr).put("fr", evalResult.messageFr))

            if (!evalResult.validSyntax) {
                return ok(
                    JSONObject()
                        .put("seed", ex.seed)
                        .put("grade", gradeObj)
                        .put("fluency", JSONObject.NULL)
                        .put("stats", db.getStatsJson(user))
                        .put("solution", JSONObject.NULL)
                        .put("is_completed", false)
                )
            }

            val outcome = db.recordSubmission(
                usernameKey = user,
                ex = ex,
                rawAnswer = rawAnswer,
                isCorrect = evalResult.isCorrect,
                gradeJson = gradeObj,
                solveTimeSecOverride = solveTimeSec
            )

            val resp = JSONObject().apply {
                put("seed", ex.seed)
                put("grade", gradeObj)
                put("fluency", outcome.opt("fluency"))
                put("stats", outcome.opt("stats"))
                put("timer", outcome.opt("timer"))
                put("is_completed", outcome.optBoolean("is_completed", evalResult.isCorrect))
                if (evalResult.isCorrect) {
                    put("solution", db.solutionToJson(ex))
                } else {
                    put("solution", JSONObject.NULL)
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
