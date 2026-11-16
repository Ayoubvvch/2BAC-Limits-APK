package com.ayoub.baclimits.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ayoub.baclimits.math.GeneratedExercise
import com.ayoub.baclimits.math.LimitsCasEngine
import org.json.JSONArray
import org.json.JSONObject

class StudentDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "bac_limits_v1.db"
        private const val DB_VERSION = 2
    }

    data class QueuedRemoteEvent(
        val id: Long,
        val username: String,
        val eventType: String,
        val apiPath: String,
        val bodyJson: String
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE students (
                username TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                lang TEXT NOT NULL DEFAULT 'ar',
                total_solved INTEGER NOT NULL DEFAULT 0,
                total_attempts INTEGER NOT NULL DEFAULT 0,
                total_correct INTEGER NOT NULL DEFAULT 0,
                current_streak INTEGER NOT NULL DEFAULT 0,
                active_seed TEXT,
                active_technique TEXT,
                active_difficulty INTEGER DEFAULT 1,
                active_latex TEXT,
                active_total_hints INTEGER DEFAULT 0,
                active_hints_count INTEGER DEFAULT 0,
                active_solution_shown INTEGER DEFAULT 0,
                active_last_grade_json TEXT,
                active_student_answer TEXT,
                remote_stats_json TEXT,
                remote_resume_json TEXT,
                dirty_offline INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE mastery (
                username TEXT NOT NULL,
                technique TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                correct INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (username, technique)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history (
                username TEXT NOT NULL,
                seed TEXT NOT NULL,
                technique TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                full_limit_latex TEXT NOT NULL,
                is_correct INTEGER,
                solved_once INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (username, seed)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                event_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        createServerExercisesTable(db)
    }

    private fun createServerExercisesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS server_exercises (
                seed TEXT PRIMARY KEY,
                technique TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                exercise_json TEXT NOT NULL,
                hints_json TEXT,
                solution_json TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        var found = false
        while (c.moveToNext()) {
            if (c.getString(1) == column) {
                found = true
                break
            }
        }
        c.close()
        return found
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            if (!hasColumn(db, "students", "remote_stats_json")) {
                db.execSQL("ALTER TABLE students ADD COLUMN remote_stats_json TEXT")
            }
            if (!hasColumn(db, "students", "remote_resume_json")) {
                db.execSQL("ALTER TABLE students ADD COLUMN remote_resume_json TEXT")
            }
            if (!hasColumn(db, "students", "dirty_offline")) {
                db.execSQL("ALTER TABLE students ADD COLUMN dirty_offline INTEGER NOT NULL DEFAULT 0")
            }
            createServerExercisesTable(db)
            // Retire legacy v1 local-only event rows that cannot be replayed to the backend API
            db.execSQL("UPDATE sync_queue SET synced = 1 WHERE event_type = 'attempt'")
        }
    }

    @Synchronized
    fun loginOrCreate(rawName: String, lang: String): JSONObject {
        val display = rawName.trim()
        require(display.length in 2..32) {
            if (lang == "fr") "Le nom d'utilisateur doit contenir entre 2 et 32 caractères."
            else "يجب أن يتراوح اسم التلميذ بين 2 و 32 حرفاً."
        }
        val key = display.lowercase()
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val cursor = db.rawQuery("SELECT username FROM students WHERE username = ?", arrayOf(key))
        val exists = cursor.moveToFirst()
        cursor.close()

        if (!exists) {
            val cv = ContentValues().apply {
                put("username", key)
                put("display_name", display)
                put("lang", if (lang == "fr") "fr" else "ar")
                put("updated_at", now)
            }
            db.insertOrThrow("students", null, cv)
            for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
                val mcv = ContentValues().apply {
                    put("username", key)
                    put("technique", tech)
                    put("attempts", 0)
                    put("correct", 0)
                }
                db.insert("mastery", null, mcv)
            }
        } else {
            val cv = ContentValues().apply {
                put("display_name", display)
                put("lang", if (lang == "fr") "fr" else "ar")
                put("updated_at", now)
            }
            db.update("students", cv, "username = ?", arrayOf(key))
        }
        return getStatsJson(key)
    }

    @Synchronized
    fun updateLang(usernameKey: String, lang: String) {
        val cleanLang = if (lang == "fr") "fr" else "ar"
        val cv = ContentValues().apply {
            put("lang", cleanLang)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("students", cv, "username = ?", arrayOf(usernameKey.lowercase()))
    }

    @Synchronized
    fun getWeakestTechniqueAndDifficulty(usernameKey: String): Pair<String, Int> {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        var weakest = LimitsCasEngine.CORE_TECHNIQUES.first()
        var lowestScore = Double.MAX_VALUE

        val c = db.rawQuery(
            "SELECT technique, attempts, correct FROM mastery WHERE username = ?",
            arrayOf(key)
        )
        while (c.moveToNext()) {
            val tech = c.getString(0)
            val att = c.getInt(1)
            val cor = c.getInt(2)
            // Unattempted techniques get priority, then lowest ratio
            val score = if (att == 0) -1.0 else cor.toDouble() / att.toDouble()
            if (score < lowestScore) {
                lowestScore = score
                weakest = tech
            }
        }
        c.close()

        val sc = db.rawQuery(
            "SELECT total_attempts, total_correct, current_streak FROM students WHERE username = ?",
            arrayOf(key)
        )
        var diff = 1
        if (sc.moveToFirst()) {
            val att = sc.getInt(0)
            val cor = sc.getInt(1)
            val streak = sc.getInt(2)
            val acc = if (att == 0) 0.0 else cor.toDouble() / att.toDouble()
            diff = when {
                streak >= 4 || (att >= 6 && acc >= 0.78) -> 3
                streak >= 2 || (att >= 3 && acc >= 0.5) -> 2
                else -> 1
            }
        }
        sc.close()
        return Pair(weakest, diff)
    }

    @Synchronized
    fun setActiveExercise(usernameKey: String, ex: GeneratedExercise) {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("active_seed", ex.seed)
            put("active_technique", ex.technique)
            put("active_difficulty", ex.difficulty)
            put("active_latex", ex.fullLimitLatex)
            put("active_total_hints", ex.hints.size)
            put("active_hints_count", 0)
            put("active_solution_shown", 0)
            putNull("active_last_grade_json")
            put("active_student_answer", "")
            putNull("remote_resume_json")
            put("updated_at", now)
        }
        db.update("students", cv, "username = ?", arrayOf(key))

        val hc = db.rawQuery(
            "SELECT is_correct, solved_once FROM history WHERE username = ? AND seed = ?",
            arrayOf(key, ex.seed)
        )
        if (hc.moveToFirst()) {
            val hcv = ContentValues().apply {
                put("updated_at", now)
            }
            db.update("history", hcv, "username = ? AND seed = ?", arrayOf(key, ex.seed))
        } else {
            val hcv = ContentValues().apply {
                put("username", key)
                put("seed", ex.seed)
                put("technique", ex.technique)
                put("difficulty", ex.difficulty)
                put("full_limit_latex", ex.fullLimitLatex)
                putNull("is_correct")
                put("solved_once", 0)
                put("updated_at", now)
            }
            db.insert("history", null, hcv)
        }
        hc.close()
    }

    @Synchronized
    fun recordHintShown(usernameKey: String, seed: String, hintIndex: Int) {
        val key = usernameKey.lowercase()
        val db = writableDatabase
        val c = db.rawQuery(
            "SELECT active_seed, active_hints_count FROM students WHERE username = ?",
            arrayOf(key)
        )
        if (c.moveToFirst() && c.getString(0) == seed) {
            val current = c.getInt(1)
            val nextCount = maxOf(current, hintIndex + 1)
            val cv = ContentValues().apply {
                put("active_hints_count", nextCount)
                put("updated_at", System.currentTimeMillis())
            }
            db.update("students", cv, "username = ?", arrayOf(key))
        }
        c.close()
    }

    @Synchronized
    fun recordSolutionRevealed(usernameKey: String, seed: String) {
        val key = usernameKey.lowercase()
        val db = writableDatabase
        val c = db.rawQuery("SELECT active_seed FROM students WHERE username = ?", arrayOf(key))
        if (c.moveToFirst() && c.getString(0) == seed) {
            val cv = ContentValues().apply {
                put("active_solution_shown", 1)
                put("updated_at", System.currentTimeMillis())
            }
            db.update("students", cv, "username = ?", arrayOf(key))
        }
        c.close()
    }

    @Synchronized
    fun recordSubmission(
        usernameKey: String,
        ex: GeneratedExercise,
        rawAnswer: String,
        isCorrect: Boolean,
        gradeJson: JSONObject
    ): JSONObject {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase

        db.beginTransaction()
        try {
            val hc = db.rawQuery(
                "SELECT solved_once FROM history WHERE username = ? AND seed = ?",
                arrayOf(key, ex.seed)
            )
            val alreadySolved = hc.moveToFirst() && hc.getInt(0) == 1
            hc.close()

            val sc = db.rawQuery(
                "SELECT total_solved, total_attempts, total_correct, current_streak FROM students WHERE username = ?",
                arrayOf(key)
            )
            var totalSolved = 0
            var totalAttempts = 0
            var totalCorrect = 0
            var streak = 0
            if (sc.moveToFirst()) {
                totalSolved = sc.getInt(0)
                totalAttempts = sc.getInt(1)
                totalCorrect = sc.getInt(2)
                streak = sc.getInt(3)
            }
            sc.close()

            totalAttempts += 1
            if (isCorrect) {
                totalCorrect += 1
                streak += 1
                if (!alreadySolved) {
                    totalSolved += 1
                }
            } else {
                streak = 0
            }

            val stCv = ContentValues().apply {
                put("total_solved", totalSolved)
                put("total_attempts", totalAttempts)
                put("total_correct", totalCorrect)
                put("current_streak", streak)
                put("active_student_answer", rawAnswer)
                put("active_last_grade_json", gradeJson.toString())
                if (isCorrect) put("active_solution_shown", 1)
                put("dirty_offline", 1)
                put("updated_at", now)
            }
            db.update("students", stCv, "username = ?", arrayOf(key))

            val mc = db.rawQuery(
                "SELECT attempts, correct FROM mastery WHERE username = ? AND technique = ?",
                arrayOf(key, ex.technique)
            )
            var mAtt = 0
            var mCor = 0
            if (mc.moveToFirst()) {
                mAtt = mc.getInt(0)
                mCor = mc.getInt(1)
            }
            mc.close()

            val mCv = ContentValues().apply {
                put("username", key)
                put("technique", ex.technique)
                put("attempts", mAtt + 1)
                put("correct", mCor + (if (isCorrect) 1 else 0))
            }
            db.replace("mastery", null, mCv)

            val hCv = ContentValues().apply {
                put("username", key)
                put("seed", ex.seed)
                put("technique", ex.technique)
                put("difficulty", ex.difficulty)
                put("full_limit_latex", ex.fullLimitLatex)
                put("is_correct", if (isCorrect) 1 else 0)
                put("solved_once", if (alreadySolved || isCorrect) 1 else 0)
                put("updated_at", now)
            }
            db.replace("history", null, hCv)

            val statsAfter = getStatsJson(key)
            db.setTransactionSuccessful()
            return statsAfter
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun ensureStudentRow(usernameRaw: String, lang: String = "ar"): String {
        val display = usernameRaw.trim().ifEmpty { "student" }
        val key = display.lowercase()
        val db = writableDatabase
        val c = db.rawQuery("SELECT username FROM students WHERE username = ?", arrayOf(key))
        val exists = c.moveToFirst()
        c.close()
        if (!exists) {
            val cv = ContentValues().apply {
                put("username", key)
                put("display_name", display)
                put("lang", if (lang == "fr") "fr" else "ar")
                put("updated_at", System.currentTimeMillis())
            }
            db.insert("students", null, cv)
            for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
                val mcv = ContentValues().apply {
                    put("username", key)
                    put("technique", tech)
                    put("attempts", 0)
                    put("correct", 0)
                }
                db.insert("mastery", null, mcv)
            }
        }
        return key
    }

    @Synchronized
    fun cacheRemoteStats(rawStatsJson: JSONObject, fallbackUsername: String? = null) {
        val rawUser = rawStatsJson.optString("username", fallbackUsername ?: "").trim()
        if (rawUser.isEmpty()) return
        val key = ensureStudentRow(rawUser, rawStatsJson.optString("preferred_lang", rawStatsJson.optString("lang", "ar")))
        val display = rawStatsJson.optString("display_name", rawUser).ifBlank { rawUser }
        val now = System.currentTimeMillis()
        val db = writableDatabase

        val hasMastery = rawStatsJson.has("mastery_by_technique") && !rawStatsJson.isNull("mastery_by_technique")
        val hasHistory = rawStatsJson.has("recent_history") && !rawStatsJson.isNull("recent_history")

        db.beginTransaction()
        try {
            var totalSolved = rawStatsJson.optInt("total_solved", 0)
            var totalAttempts = rawStatsJson.optInt(
                "total_attempted",
                rawStatsJson.optInt("total_attempts", 0)
            )
            var totalCorrect = rawStatsJson.optInt("total_correct", -1)
            val streak = rawStatsJson.optInt("current_streak", 0)

            if (hasMastery) {
                val arr = rawStatsJson.getJSONArray("mastery_by_technique")
                var sumAtt = 0
                var sumCor = 0
                val seenTechs = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val tech = item.optString("technique", "").trim()
                    if (tech.isEmpty()) continue
                    val att = item.optInt("attempts", 0)
                    val cor = item.optInt("correct", 0)
                    sumAtt += att
                    sumCor += cor
                    seenTechs.add(tech)
                    val mCv = ContentValues().apply {
                        put("username", key)
                        put("technique", tech)
                        put("attempts", att)
                        put("correct", cor)
                    }
                    db.replace("mastery", null, mCv)
                }
                for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
                    if (tech !in seenTechs) {
                        val mCv = ContentValues().apply {
                            put("username", key)
                            put("technique", tech)
                            put("attempts", 0)
                            put("correct", 0)
                        }
                        db.replace("mastery", null, mCv)
                    }
                }
                if (totalAttempts == 0 && sumAtt > 0) totalAttempts = sumAtt
                if (totalCorrect < 0) totalCorrect = sumCor
            }

            if (totalCorrect < 0) {
                val accPct = RemoteSyncProtocol.extractAccuracyPercent(rawStatsJson, totalSolved, totalAttempts)
                totalCorrect = if (totalAttempts > 0) {
                    kotlin.math.round((accPct / 100.0) * totalAttempts).toInt()
                } else {
                    totalSolved
                }
            }

            if (hasHistory) {
                val histArr = rawStatsJson.getJSONArray("recent_history")
                for (i in 0 until histArr.length()) {
                    val h = histArr.optJSONObject(i) ?: continue
                    val seed = h.optString("seed", "").trim()
                    if (seed.isEmpty()) continue
                    val latex = h.optString("full_limit_latex", "")
                    val isCorr: Int? = if (h.isNull("is_correct")) null else if (h.optBoolean("is_correct", false)) 1 else 0
                    val tech = h.optString("technique", inferTechniqueFromSeed(seed))
                    val diff = h.optInt("difficulty", inferDifficultyFromSeed(seed))
                    val hCv = ContentValues().apply {
                        put("username", key)
                        put("seed", seed)
                        put("technique", tech)
                        put("difficulty", diff)
                        put("full_limit_latex", latex)
                        if (isCorr == null) putNull("is_correct") else put("is_correct", isCorr)
                        put("solved_once", if (isCorr == 1) 1 else 0)
                        put("updated_at", now - i)
                    }
                    db.replace("history", null, hCv)
                }
            }

            val remoteJsonToStore = if (hasMastery && hasHistory) {
                rawStatsJson.toString()
            } else {
                // Merge partial stats (e.g. from /submit) into existing cached remote_stats_json
                val existingCursor = db.rawQuery(
                    "SELECT remote_stats_json FROM students WHERE username = ?",
                    arrayOf(key)
                )
                val existingStr = if (existingCursor.moveToFirst()) existingCursor.getString(0) else null
                existingCursor.close()
                if (!existingStr.isNullOrBlank()) {
                    val merged = JSONObject(existingStr)
                    if (rawStatsJson.has("total_solved")) merged.put("total_solved", totalSolved)
                    if (rawStatsJson.has("current_streak")) merged.put("current_streak", streak)
                    if (rawStatsJson.has("accuracy")) merged.put("accuracy", rawStatsJson.get("accuracy"))
                    merged.toString()
                } else {
                    null
                }
            }

            val stCv = ContentValues().apply {
                put("display_name", display)
                if (rawStatsJson.has("preferred_lang")) {
                    put("lang", if (rawStatsJson.optString("preferred_lang") == "fr") "fr" else "ar")
                } else if (rawStatsJson.has("lang")) {
                    put("lang", if (rawStatsJson.optString("lang") == "fr") "fr" else "ar")
                }
                put("total_solved", totalSolved)
                if (totalAttempts > 0 || hasMastery) {
                    put("total_attempts", totalAttempts)
                    put("total_correct", totalCorrect)
                }
                put("current_streak", streak)
                if (remoteJsonToStore != null) {
                    put("remote_stats_json", remoteJsonToStore)
                    put("dirty_offline", 0)
                }
                put("updated_at", now)
            }
            db.update("students", stCv, "username = ?", arrayOf(key))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun cacheRemoteResume(usernameRaw: String, rawResumeJson: JSONObject) {
        val key = ensureStudentRow(usernameRaw)
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val hasExercise = rawResumeJson.optBoolean("has_exercise", false)
        val exObj = if (hasExercise) rawResumeJson.optJSONObject("exercise") else null

        val cv = ContentValues().apply {
            put("remote_resume_json", rawResumeJson.toString())
            put("updated_at", now)
            if (exObj != null) {
                val seed = exObj.optString("seed", "")
                val tech = exObj.optString("technique", inferTechniqueFromSeed(seed))
                val diff = exObj.optInt("difficulty", inferDifficultyFromSeed(seed))
                val latex = exObj.optString("full_limit_latex", "")
                val totalHints = exObj.optInt("total_hints", 0)
                val hintsArr = rawResumeJson.optJSONArray("hints")
                val solObj = rawResumeJson.optJSONObject("solution")
                val gradeObj = rawResumeJson.optJSONObject("last_grade")
                val stuAns = rawResumeJson.optString("student_answer", "")

                put("active_seed", seed)
                put("active_technique", tech)
                put("active_difficulty", diff)
                put("active_latex", latex)
                put("active_total_hints", totalHints)
                put("active_hints_count", hintsArr?.length() ?: 0)
                put("active_solution_shown", if (solObj != null) 1 else 0)
                if (gradeObj != null) put("active_last_grade_json", gradeObj.toString()) else putNull("active_last_grade_json")
                put("active_student_answer", stuAns)

                if (seed.isNotEmpty()) {
                    cacheServerExerciseInternal(db, exObj, hintsArr, solObj, now)
                }
            } else {
                putNull("active_seed")
            }
        }
        db.update("students", cv, "username = ?", arrayOf(key))
    }

    @Synchronized
    fun cacheServerExercise(exJson: JSONObject, usernameRaw: String? = null) {
        val seed = exJson.optString("seed", "").trim()
        if (seed.isEmpty()) return
        val now = System.currentTimeMillis()
        val db = writableDatabase
        cacheServerExerciseInternal(db, exJson, null, null, now)

        if (!usernameRaw.isNullOrBlank()) {
            val key = ensureStudentRow(usernameRaw)
            val resumeJson = JSONObject().apply {
                put("has_exercise", true)
                put("exercise", exJson)
                put("hints", JSONArray())
                put("last_grade", JSONObject.NULL)
                put("solution", JSONObject.NULL)
                put("student_answer", "")
            }
            val tech = exJson.optString("technique", inferTechniqueFromSeed(seed))
            val diff = exJson.optInt("difficulty", inferDifficultyFromSeed(seed))
            val latex = exJson.optString("full_limit_latex", "")
            val totalHints = exJson.optInt("total_hints", 0)

            val cv = ContentValues().apply {
                put("active_seed", seed)
                put("active_technique", tech)
                put("active_difficulty", diff)
                put("active_latex", latex)
                put("active_total_hints", totalHints)
                put("active_hints_count", 0)
                put("active_solution_shown", 0)
                putNull("active_last_grade_json")
                put("active_student_answer", "")
                put("remote_resume_json", resumeJson.toString())
                put("updated_at", now)
            }
            db.update("students", cv, "username = ?", arrayOf(key))

            val hc = db.rawQuery(
                "SELECT is_correct, solved_once FROM history WHERE username = ? AND seed = ?",
                arrayOf(key, seed)
            )
            if (!hc.moveToFirst()) {
                val hCv = ContentValues().apply {
                    put("username", key)
                    put("seed", seed)
                    put("technique", tech)
                    put("difficulty", diff)
                    put("full_limit_latex", latex)
                    putNull("is_correct")
                    put("solved_once", 0)
                    put("updated_at", now)
                }
                db.insert("history", null, hCv)
            }
            hc.close()
        }
    }

    private fun cacheServerExerciseInternal(
        db: SQLiteDatabase,
        exJson: JSONObject,
        hintsArr: JSONArray?,
        solObj: JSONObject?,
        now: Long
    ) {
        val seed = exJson.optString("seed", "").trim()
        if (seed.isEmpty()) return
        val existing = db.rawQuery(
            "SELECT hints_json, solution_json FROM server_exercises WHERE seed = ?",
            arrayOf(seed)
        )
        var oldHints: String? = null
        var oldSol: String? = null
        if (existing.moveToFirst()) {
            oldHints = existing.getString(0)
            oldSol = existing.getString(1)
        }
        existing.close()

        val mergedHints = if (hintsArr != null && hintsArr.length() > 0) hintsArr.toString() else oldHints
        val mergedSol = solObj?.toString() ?: oldSol

        val cv = ContentValues().apply {
            put("seed", seed)
            put("technique", exJson.optString("technique", inferTechniqueFromSeed(seed)))
            put("difficulty", exJson.optInt("difficulty", inferDifficultyFromSeed(seed)))
            put("exercise_json", exJson.toString())
            if (mergedHints != null) put("hints_json", mergedHints) else putNull("hints_json")
            if (mergedSol != null) put("solution_json", mergedSol) else putNull("solution_json")
            put("updated_at", now)
        }
        db.replace("server_exercises", null, cv)
    }

    @Synchronized
    fun cacheServerHint(usernameRaw: String, seed: String, hintIndex: Int, hintObj: JSONObject) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val c = db.rawQuery("SELECT hints_json FROM server_exercises WHERE seed = ?", arrayOf(seed))
        if (c.moveToFirst()) {
            val arr = try {
                val raw = c.getString(0)
                if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }
            while (arr.length() <= hintIndex) {
                arr.put(JSONObject.NULL)
            }
            arr.put(hintIndex, hintObj)
            val cv = ContentValues().apply {
                put("hints_json", arr.toString())
                put("updated_at", now)
            }
            db.update("server_exercises", cv, "seed = ?", arrayOf(seed))
        }
        c.close()

        if (usernameRaw.isNotBlank()) {
            val key = usernameRaw.lowercase()
            val sc = db.rawQuery("SELECT active_seed, remote_resume_json FROM students WHERE username = ?", arrayOf(key))
            if (sc.moveToFirst() && sc.getString(0) == seed) {
                val resumeRaw = sc.getString(1)
                if (!resumeRaw.isNullOrBlank()) {
                    try {
                        val rJson = JSONObject(resumeRaw)
                        val rHints = rJson.optJSONArray("hints") ?: JSONArray()
                        if (rHints.length() <= hintIndex) {
                            while (rHints.length() < hintIndex) {
                                rHints.put(JSONObject.NULL)
                            }
                            rHints.put(hintObj)
                        } else {
                            rHints.put(hintIndex, hintObj)
                        }
                        rJson.put("hints", rHints)
                        val uCv = ContentValues().apply {
                            put("remote_resume_json", rJson.toString())
                            put("active_hints_count", rHints.length())
                            put("updated_at", now)
                        }
                        db.update("students", uCv, "username = ?", arrayOf(key))
                    } catch (_: Exception) {
                    }
                }
            }
            sc.close()
        }
    }

    @Synchronized
    fun cacheServerSolution(usernameRaw: String, seed: String, solJson: JSONObject) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("solution_json", solJson.toString())
            put("updated_at", now)
        }
        db.update("server_exercises", cv, "seed = ?", arrayOf(seed))

        if (usernameRaw.isNotBlank()) {
            val key = usernameRaw.lowercase()
            val sc = db.rawQuery("SELECT active_seed, remote_resume_json FROM students WHERE username = ?", arrayOf(key))
            if (sc.moveToFirst() && sc.getString(0) == seed) {
                val resumeRaw = sc.getString(1)
                val uCv = ContentValues().apply {
                    put("active_solution_shown", 1)
                    put("updated_at", now)
                    if (!resumeRaw.isNullOrBlank()) {
                        try {
                            val rJson = JSONObject(resumeRaw)
                            rJson.put("solution", solJson)
                            put("remote_resume_json", rJson.toString())
                        } catch (_: Exception) {
                        }
                    }
                }
                db.update("students", uCv, "username = ?", arrayOf(key))
            }
            sc.close()
        }
    }

    @Synchronized
    fun updateCachedResumeAfterSubmit(
        usernameRaw: String,
        seed: String,
        answer: String,
        gradeObj: JSONObject?,
        solObj: JSONObject?
    ) {
        if (usernameRaw.isBlank()) return
        val key = usernameRaw.lowercase()
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val sc = db.rawQuery("SELECT active_seed, remote_resume_json FROM students WHERE username = ?", arrayOf(key))
        if (sc.moveToFirst() && sc.getString(0) == seed) {
            val resumeRaw = sc.getString(1)
            val cv = ContentValues().apply {
                put("active_student_answer", answer)
                if (gradeObj != null) put("active_last_grade_json", gradeObj.toString())
                if (solObj != null) put("active_solution_shown", 1)
                put("updated_at", now)
                if (!resumeRaw.isNullOrBlank()) {
                    try {
                        val rJson = JSONObject(resumeRaw)
                        rJson.put("student_answer", answer)
                        if (gradeObj != null) rJson.put("last_grade", gradeObj)
                        if (solObj != null) rJson.put("solution", solObj)
                        put("remote_resume_json", rJson.toString())
                    } catch (_: Exception) {
                    }
                }
            }
            db.update("students", cv, "username = ?", arrayOf(key))
        }
        sc.close()
    }

    @Synchronized
    fun getCachedServerExercise(seed: String): JSONObject? {
        val c = readableDatabase.rawQuery("SELECT exercise_json FROM server_exercises WHERE seed = ?", arrayOf(seed.trim()))
        val obj = if (c.moveToFirst()) {
            try { JSONObject(c.getString(0)) } catch (_: Exception) { null }
        } else null
        c.close()
        return obj
    }

    @Synchronized
    fun getCachedServerHint(seed: String, hintIndex: Int): JSONObject? {
        val c = readableDatabase.rawQuery("SELECT hints_json FROM server_exercises WHERE seed = ?", arrayOf(seed.trim()))
        var res: JSONObject? = null
        if (c.moveToFirst()) {
            val raw = c.getString(0)
            if (!raw.isNullOrBlank()) {
                try {
                    val arr = JSONArray(raw)
                    if (hintIndex in 0 until arr.length() && !arr.isNull(hintIndex)) {
                        res = arr.getJSONObject(hintIndex)
                    }
                } catch (_: Exception) {
                }
            }
        }
        c.close()
        return res
    }

    @Synchronized
    fun getCachedServerSolution(seed: String): JSONObject? {
        val c = readableDatabase.rawQuery("SELECT solution_json FROM server_exercises WHERE seed = ?", arrayOf(seed.trim()))
        var res: JSONObject? = null
        if (c.moveToFirst()) {
            val raw = c.getString(0)
            if (!raw.isNullOrBlank()) {
                try { res = JSONObject(raw) } catch (_: Exception) { }
            }
        }
        c.close()
        return res
    }

    @Synchronized
    fun activateCachedServerExercise(usernameRaw: String, exJson: JSONObject) {
        cacheServerExercise(exJson, usernameRaw)
    }

    @Synchronized
    fun getLastActiveUsername(): String? {
        val c = readableDatabase.rawQuery(
            "SELECT display_name FROM students ORDER BY updated_at DESC LIMIT 1",
            null
        )
        val user = if (c.moveToFirst()) c.getString(0) else null
        c.close()
        return user
    }

    @Synchronized
    fun enqueueRemoteEvent(usernameRaw: String, eventType: String, apiPath: String, payload: JSONObject) {
        val key = usernameRaw.trim().lowercase()
        val envelope = JSONObject().apply {
            put("path", apiPath)
            put("body", payload)
        }
        val cv = ContentValues().apply {
            put("username", key)
            put("event_type", eventType)
            put("payload_json", envelope.toString())
            put("created_at", System.currentTimeMillis())
            put("synced", 0)
        }
        writableDatabase.insert("sync_queue", null, cv)
    }

    @Synchronized
    fun getPendingRemoteEvents(limit: Int = 50): List<QueuedRemoteEvent> {
        val list = mutableListOf<QueuedRemoteEvent>()
        val c = readableDatabase.rawQuery(
            "SELECT id, username, event_type, payload_json FROM sync_queue WHERE synced = 0 AND event_type LIKE 'remote_%' ORDER BY id ASC LIMIT ?",
            arrayOf(limit.toString())
        )
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val user = c.getString(1)
            val evType = c.getString(2)
            val raw = c.getString(3)
            try {
                val env = JSONObject(raw)
                val path = env.optString("path", "")
                val body = env.optJSONObject("body")?.toString() ?: "{}"
                if (path.isNotBlank()) {
                    list.add(QueuedRemoteEvent(id, user, evType, path, body))
                }
            } catch (_: Exception) {
            }
        }
        c.close()
        return list
    }

    private fun inferTechniqueFromSeed(seed: String): String {
        val parts = seed.uppercase().split("-")
        if (parts.size >= 2) {
            return when (parts[1]) {
                "POLY" -> "poly_factor"
                "SQRT" -> "sqrt_conjugate"
                "NROT" -> "nth_root"
                "ADD0" -> "add_sub_zero"
                "TRIG" -> "trig"
                "HYBR" -> "hybrid"
                else -> "adaptive"
            }
        }
        return "adaptive"
    }

    private fun inferDifficultyFromSeed(seed: String): Int {
        val parts = seed.uppercase().split("-")
        if (parts.size >= 3 && parts[2].startsWith("L")) {
            return parts[2].removePrefix("L").toIntOrNull()?.coerceIn(1, 3) ?: 1
        }
        return 1
    }

    @Synchronized
    fun getResumeJson(usernameKey: String): JSONObject {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        val c = db.rawQuery(
            """
            SELECT active_seed, active_hints_count, active_solution_shown, active_last_grade_json, active_student_answer, remote_resume_json
            FROM students WHERE username = ?
            """.trimIndent(),
            arrayOf(key)
        )
        if (!c.moveToFirst()) {
            c.close()
            return JSONObject().put("has_exercise", false)
        }
        val seed = c.getString(0)
        val hintsCount = c.getInt(1)
        val solShown = c.getInt(2) == 1
        val gradeStr = c.getString(3)
        val stuAnswer = c.getString(4) ?: ""
        val remoteResumeStr = c.getString(5)
        c.close()

        if (seed.isNullOrBlank()) {
            return JSONObject().put("has_exercise", false)
        }

        if (LimitsCasEngine.isValidSeed(seed)) {
            return try {
                val ex = LimitsCasEngine.generate("adaptive", 0, explicitSeed = seed)
                val hintsArr = JSONArray()
                for (i in 0 until minOf(hintsCount, ex.hints.size)) {
                    hintsArr.put(
                        JSONObject()
                            .put("ar", ex.hints[i].ar)
                            .put("fr", ex.hints[i].fr)
                    )
                }
                JSONObject().apply {
                    put("has_exercise", true)
                    put("exercise", exerciseToJson(ex))
                    put("hints", hintsArr)
                    put("last_grade", if (gradeStr.isNullOrBlank()) JSONObject.NULL else JSONObject(gradeStr))
                    put("solution", if (solShown) solutionToJson(ex) else JSONObject.NULL)
                    put("student_answer", stuAnswer)
                }
            } catch (_: Exception) {
                JSONObject().put("has_exercise", false)
            }
        }

        if (!remoteResumeStr.isNullOrBlank()) {
            try {
                return JSONObject(remoteResumeStr)
            } catch (_: Exception) {
            }
        }

        val cachedEx = getCachedServerExercise(seed) ?: return JSONObject().put("has_exercise", false)
        val cachedSol = if (solShown) getCachedServerSolution(seed) else null
        return JSONObject().apply {
            put("has_exercise", true)
            put("exercise", cachedEx)
            put("hints", JSONArray())
            put("last_grade", if (gradeStr.isNullOrBlank()) JSONObject.NULL else JSONObject(gradeStr))
            put("solution", cachedSol ?: JSONObject.NULL)
            put("student_answer", stuAnswer)
        }
    }

    @Synchronized
    fun getStatsJson(usernameKey: String): JSONObject {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        val sc = db.rawQuery(
            "SELECT display_name, total_solved, total_attempts, total_correct, current_streak, remote_stats_json, dirty_offline FROM students WHERE username = ?",
            arrayOf(key)
        )
        if (!sc.moveToFirst()) {
            sc.close()
            throw IllegalArgumentException("Student not found")
        }
        val display = sc.getString(0)
        val solved = sc.getInt(1)
        val attempts = sc.getInt(2)
        val correct = sc.getInt(3)
        val streak = sc.getInt(4)
        val remoteStatsStr = sc.getString(5)
        val dirtyOffline = sc.getInt(6)
        sc.close()

        if (!remoteStatsStr.isNullOrBlank() && dirtyOffline == 0) {
            try {
                return JSONObject(remoteStatsStr)
            } catch (_: Exception) {
            }
        }

        val accuracy = if (attempts == 0) 0 else kotlin.math.round((correct * 100.0) / attempts).toInt()

        val masteryMap = mutableMapOf<String, Pair<Int, Int>>()
        val mc = db.rawQuery(
            "SELECT technique, attempts, correct FROM mastery WHERE username = ?",
            arrayOf(key)
        )
        while (mc.moveToNext()) {
            masteryMap[mc.getString(0)] = Pair(mc.getInt(1), mc.getInt(2))
        }
        mc.close()

        val masteryArr = JSONArray()
        for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
            val (tAtt, tCor) = masteryMap[tech] ?: Pair(0, 0)
            val mRatio = if (tAtt == 0) 0.0 else tCor.toDouble() / tAtt.toDouble()
            masteryArr.put(
                JSONObject()
                    .put("technique", tech)
                    .put("mastery", mRatio)
                    .put("correct", tCor)
                    .put("attempts", tAtt)
            )
        }

        val histArr = JSONArray()
        val hc = db.rawQuery(
            """
            SELECT seed, is_correct, full_limit_latex
            FROM history WHERE username = ?
            ORDER BY updated_at DESC LIMIT 20
            """.trimIndent(),
            arrayOf(key)
        )
        while (hc.moveToNext()) {
            val isCorrVal: Any = if (hc.isNull(1)) JSONObject.NULL else (hc.getInt(1) == 1)
            histArr.put(
                JSONObject()
                    .put("seed", hc.getString(0))
                    .put("is_correct", isCorrVal)
                    .put("full_limit_latex", hc.getString(2))
            )
        }
        hc.close()

        return JSONObject().apply {
            put("username", key)
            put("display_name", display)
            put("total_solved", solved)
            put("accuracy", accuracy)
            put("current_streak", streak)
            put("mastery_by_technique", masteryArr)
            put("recent_history", histArr)
        }
    }

    @Synchronized
    fun getPendingSyncCount(): Int {
        val c = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM sync_queue WHERE synced = 0 AND event_type LIKE 'remote_%'",
            null
        )
        val count = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return count
    }

    @Synchronized
    fun markSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("synced", 1) }
            for (id in ids) {
                db.update("sync_queue", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun exerciseToJson(ex: GeneratedExercise): JSONObject {
        return JSONObject().apply {
            put("seed", ex.seed)
            put("technique", ex.technique)
            put("difficulty", ex.difficulty)
            put("full_limit_latex", ex.fullLimitLatex)
            put("total_hints", ex.hints.size)
        }
    }

    fun solutionToJson(ex: GeneratedExercise): JSONObject {
        val stepsArr = JSONArray()
        for (st in ex.steps) {
            val stepObj = JSONObject().apply {
                put("title", JSONObject().put("ar", st.title.ar).put("fr", st.title.fr))
                put("explanation", JSONObject().put("ar", st.explanation.ar).put("fr", st.explanation.fr))
                put("latex", st.latex ?: JSONObject.NULL)
            }
            stepsArr.put(stepObj)
        }
        return JSONObject().apply {
            put("answer_latex", ex.answerLatex)
            put("steps", stepsArr)
        }
    }
}
