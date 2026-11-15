package com.ayoub.baclimits.data

import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.MathExprEvaluator
import org.json.JSONObject
import java.net.URLDecoder

data class ApiResponse(val status: Int, val bodyJson: String)

class LocalLimitsApi(
    private val db: StudentDatabase,
    private val onSyncEvent: () -> Unit = {}
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
        // POST /api/login
        if (method == "POST" && path == "/api/login") {
            val username = body.optString("username", "")
            val lang = body.optString("lang", "ar")
            val stats = db.loginOrCreate(username, lang)
            return ok(stats)
        }

        // GET /api/student/{username}/resume
        val resumeMatch = Regex("^/api/student/([^/]+)/resume$").matchEntire(path)
        if (method == "GET" && resumeMatch != null) {
            val user = decode(resumeMatch.groupValues[1])
            return ok(db.getResumeJson(user))
        }

        // GET /api/student/{username}/stats
        val statsMatch = Regex("^/api/student/([^/]+)/stats$").matchEntire(path)
        if (method == "GET" && statsMatch != null) {
            val user = decode(statsMatch.groupValues[1])
            return ok(db.getStatsJson(user))
        }

        // POST /api/student/{username}/lang
        val langMatch = Regex("^/api/student/([^/]+)/lang$").matchEntire(path)
        if (method == "POST" && langMatch != null) {
            val user = decode(langMatch.groupValues[1])
            val lang = body.optString("lang", "ar")
            db.updateLang(user, lang)
            return ok(JSONObject().put("ok", true).put("lang", lang))
        }

        // POST /api/exercises/next
        if (method == "POST" && path == "/api/exercises/next") {
            val user = body.optString("username", "")
            val tech = body.optString("technique", "adaptive")
            val diff = body.optInt("difficulty", 0)
            val explicitSeed = if (body.isNull("seed")) null else body.optString("seed", "").takeIf { it.isNotBlank() }

            val (weakest, suggestedDiff) = db.getWeakestTechniqueAndDifficulty(user)
            val ex = LimitsCasEngine.generate(
                requestedTechnique = tech,
                requestedDifficulty = diff,
                explicitSeed = explicitSeed,
                weakestTechnique = weakest,
                suggestedDifficulty = suggestedDiff
            )
            db.setActiveExercise(user, ex)
            return ok(db.exerciseToJson(ex))
        }

        // POST /api/exercises/hint
        if (method == "POST" && path == "/api/exercises/hint") {
            val user = body.optString("username", "")
            val seed = body.optString("seed", "")
            val hintIndex = body.optInt("hint_index", 0)
            val ex = LimitsCasEngine.generate("adaptive", 0, explicitSeed = seed)
            if (hintIndex !in ex.hints.indices) {
                return err(400, "No more hints available")
            }
            db.recordHintShown(user, ex.seed, hintIndex)
            val h = ex.hints[hintIndex]
            return ok(
                JSONObject().put(
                    "hint",
                    JSONObject().put("ar", h.ar).put("fr", h.fr)
                )
            )
        }

        // POST /api/exercises/solution
        if (method == "POST" && path == "/api/exercises/solution") {
            val user = body.optString("username", "")
            val seed = body.optString("seed", "")
            val ex = LimitsCasEngine.generate("adaptive", 0, explicitSeed = seed)
            db.recordSolutionRevealed(user, ex.seed)
            return ok(db.solutionToJson(ex))
        }

        // POST /api/exercises/submit
        if (method == "POST" && path == "/api/exercises/submit") {
            val user = body.optString("username", "")
            val seed = body.optString("seed", "")
            val rawAnswer = body.optString("answer", "")
            val ex = LimitsCasEngine.generate("adaptive", 0, explicitSeed = seed)

            val eval = MathExprEvaluator.evaluate(rawAnswer)
            val isCorrect = MathExprEvaluator.isEquivalent(ex.expectedAnswer, rawAnswer)

            val msgAr: String
            val msgFr: String
            when {
                isCorrect -> {
                    msgAr = "أحسنت! إجابة صحيحة ومتطابقة رمزياً."
                    msgFr = "Bravo ! Réponse exacte et symboliquement équivalente."
                }
                eval is com.ayoub.baclimits.math.EvalResult.Invalid -> {
                    msgAr = "تعذر فهم الصيغة الرياضية (${eval.reason}). جرب مثالاً مثل: 1/2 أو sqrt(2)/2 أو +inf"
                    msgFr = "Expression invalide (${eval.reason}). Ex : 1/2, sqrt(2)/2 ou +inf"
                }
                else -> {
                    msgAr = "إجابة غير صحيحة. استعمل زر التلميح أو راجع الإشارة والمعاملات ثم أعد المحاولة!"
                    msgFr = "Réponse incorrecte. Utilise un indice ou vérifie le signe et les coefficients !"
                }
            }

            val gradeObj = JSONObject()
                .put("is_correct", isCorrect)
                .put("message", JSONObject().put("ar", msgAr).put("fr", msgFr))

            val updatedStats = db.recordSubmission(user, ex, rawAnswer, isCorrect, gradeObj)
            onSyncEvent()

            val resp = JSONObject().apply {
                put("grade", gradeObj)
                put("stats", updatedStats)
                if (isCorrect) {
                    put("solution", db.solutionToJson(ex))
                }
            }
            return ok(resp)
        }

        return err(404, "Endpoint not found: $method $path")
    }

    private fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")

    private fun ok(json: JSONObject): ApiResponse = ApiResponse(200, json.toString())

    private fun err(code: Int, detail: String): ApiResponse =
        ApiResponse(code, JSONObject().put("detail", detail).toString())
}
