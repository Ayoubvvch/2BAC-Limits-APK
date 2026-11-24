package com.ayoub.baclimits.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ayoub.baclimits.math.GeneratedExercise
import com.ayoub.baclimits.math.LimitsCasEngine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

class StudentDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "bac_limits_v1.db"
        private const val DB_VERSION = 3
        private const val MAX_SEGMENT_MS = 1_800_000L
        private const val INITIAL_MASTERY = 0.28
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
                best_streak INTEGER NOT NULL DEFAULT 0,
                active_seed TEXT,
                active_chapter TEXT DEFAULT 'limits',
                active_technique TEXT,
                active_difficulty INTEGER DEFAULT 1,
                active_answer_kind TEXT DEFAULT 'constant',
                active_prompt_json TEXT,
                active_latex TEXT,
                active_total_hints INTEGER DEFAULT 0,
                active_hints_count INTEGER DEFAULT 0,
                active_solution_shown INTEGER DEFAULT 0,
                active_last_grade_json TEXT,
                active_student_answer TEXT,
                active_timer_state TEXT NOT NULL DEFAULT 'ready',
                active_elapsed_ms INTEGER NOT NULL DEFAULT 0,
                active_resumed_at_ms INTEGER NOT NULL DEFAULT 0,
                active_is_revealed INTEGER NOT NULL DEFAULT 0,
                active_is_completed INTEGER NOT NULL DEFAULT 0,
                active_coach_json TEXT,
                active_fluency_json TEXT,
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
                avg_time_sec REAL,
                last_practiced_at INTEGER,
                PRIMARY KEY (username, technique)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history (
                username TEXT NOT NULL,
                seed TEXT NOT NULL,
                chapter TEXT NOT NULL DEFAULT 'limits',
                technique TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                full_limit_latex TEXT NOT NULL,
                is_correct INTEGER,
                solved_once INTEGER NOT NULL DEFAULT 0,
                is_revealed INTEGER NOT NULL DEFAULT 1,
                solution_viewed INTEGER NOT NULL DEFAULT 0,
                elapsed_sec REAL NOT NULL DEFAULT 0,
                fluency_code TEXT,
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

    private fun addColIfMissing(db: SQLiteDatabase, table: String, column: String, ddl: String) {
        if (!hasColumn(db, table, column)) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $ddl")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            addColIfMissing(db, "students", "remote_stats_json", "TEXT")
            addColIfMissing(db, "students", "remote_resume_json", "TEXT")
            addColIfMissing(db, "students", "dirty_offline", "INTEGER NOT NULL DEFAULT 0")
            createServerExercisesTable(db)
            db.execSQL("UPDATE sync_queue SET synced = 1 WHERE event_type = 'attempt'")
        }
        if (oldVersion < 3) {
            addColIfMissing(db, "students", "best_streak", "INTEGER NOT NULL DEFAULT 0")
            addColIfMissing(db, "students", "active_chapter", "TEXT DEFAULT 'limits'")
            addColIfMissing(db, "students", "active_answer_kind", "TEXT DEFAULT 'constant'")
            addColIfMissing(db, "students", "active_prompt_json", "TEXT")
            addColIfMissing(db, "students", "active_timer_state", "TEXT NOT NULL DEFAULT 'ready'")
            addColIfMissing(db, "students", "active_elapsed_ms", "INTEGER NOT NULL DEFAULT 0")
            addColIfMissing(db, "students", "active_resumed_at_ms", "INTEGER NOT NULL DEFAULT 0")
            addColIfMissing(db, "students", "active_is_revealed", "INTEGER NOT NULL DEFAULT 0")
            addColIfMissing(db, "students", "active_is_completed", "INTEGER NOT NULL DEFAULT 0")
            addColIfMissing(db, "students", "active_coach_json", "TEXT")
            addColIfMissing(db, "students", "active_fluency_json", "TEXT")
            // Preserve pre-v3 active exercises as revealed & paused
            db.execSQL(
                "UPDATE students SET active_is_revealed = 1, active_timer_state = 'paused' WHERE active_seed IS NOT NULL AND active_timer_state = 'ready'"
            )

            addColIfMissing(db, "mastery", "avg_time_sec", "REAL")
            addColIfMissing(db, "mastery", "last_practiced_at", "INTEGER")

            addColIfMissing(db, "history", "chapter", "TEXT NOT NULL DEFAULT 'limits'")
            addColIfMissing(db, "history", "is_revealed", "INTEGER NOT NULL DEFAULT 1")
            addColIfMissing(db, "history", "solution_viewed", "INTEGER NOT NULL DEFAULT 0")
            addColIfMissing(db, "history", "elapsed_sec", "REAL NOT NULL DEFAULT 0")
            addColIfMissing(db, "history", "fluency_code", "TEXT")
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
        } else {
            val cv = ContentValues().apply {
                put("display_name", display)
                put("lang", if (lang == "fr") "fr" else "ar")
                put("updated_at", now)
            }
            db.update("students", cv, "username = ?", arrayOf(key))
        }
        ensureAllTechniqueRows(db, key)
        return getStatsJson(key)
    }

    private fun ensureAllTechniqueRows(db: SQLiteDatabase, key: String) {
        for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
            db.execSQL(
                "INSERT OR IGNORE INTO mastery (username, technique, attempts, correct) VALUES (?, ?, 0, 0)",
                arrayOf(key, tech)
            )
        }
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
    fun getActiveIncompleteSeed(usernameKey: String): String? {
        val key = usernameKey.lowercase()
        val c = readableDatabase.rawQuery(
            "SELECT active_seed, active_is_completed, active_solution_shown FROM students WHERE username = ?",
            arrayOf(key)
        )
        var res: String? = null
        if (c.moveToFirst()) {
            val seed = c.getString(0)
            val completed = c.getInt(1) == 1 || c.getInt(2) == 1
            if (!seed.isNullOrBlank() && !completed) {
                res = seed
            }
        }
        c.close()
        return res
    }

    @Synchronized
    fun isActiveRevealed(usernameKey: String, seed: String): Boolean {
        val key = usernameKey.lowercase()
        val c = readableDatabase.rawQuery(
            "SELECT active_seed, active_is_revealed, active_is_completed FROM students WHERE username = ?",
            arrayOf(key)
        )
        var revealed = true
        if (c.moveToFirst() && c.getString(0) == seed) {
            revealed = c.getInt(1) == 1 || c.getInt(2) == 1
        }
        c.close()
        return revealed
    }

    @Synchronized
    fun getWeakestTechniqueAndDifficulty(usernameKey: String, chapter: String = "limits"): Pair<String, Int> {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        val chapterPool = LimitsCasEngine.techniquesForChapter(chapter)
        var weakest = chapterPool.first()
        var lowestScore = Double.MAX_VALUE

        val c = db.rawQuery(
            "SELECT technique, attempts, correct FROM mastery WHERE username = ?",
            arrayOf(key)
        )
        while (c.moveToNext()) {
            val tech = c.getString(0)
            if (tech !in chapterPool) continue
            val att = c.getInt(1)
            val cor = c.getInt(2)
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
    fun setActiveExercise(
        usernameKey: String,
        ex: GeneratedExercise,
        coachMode: String? = null,
        coachAr: String? = null,
        coachFr: String? = null
    ) {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val promptJson = JSONObject().put("ar", ex.prompt.ar).put("fr", ex.prompt.fr).toString()
        val coachJson = if (!coachAr.isNullOrBlank() || !coachFr.isNullOrBlank()) {
            JSONObject()
                .put("mode", coachMode ?: "balanced")
                .put("reason", JSONObject().put("ar", coachAr.orEmpty()).put("fr", coachFr.orEmpty()))
                .toString()
        } else {
            null
        }

        val cv = ContentValues().apply {
            put("active_seed", ex.seed)
            put("active_chapter", ex.chapter)
            put("active_technique", ex.technique)
            put("active_difficulty", ex.difficulty)
            put("active_answer_kind", ex.answerKind)
            put("active_prompt_json", promptJson)
            put("active_latex", ex.fullLimitLatex)
            put("active_total_hints", ex.hints.size)
            put("active_hints_count", 0)
            put("active_solution_shown", 0)
            putNull("active_last_grade_json")
            put("active_student_answer", "")
            put("active_timer_state", "ready")
            put("active_elapsed_ms", 0L)
            put("active_resumed_at_ms", 0L)
            put("active_is_revealed", 0)
            put("active_is_completed", 0)
            if (coachJson != null) put("active_coach_json", coachJson) else putNull("active_coach_json")
            putNull("active_fluency_json")
            putNull("remote_resume_json")
            put("updated_at", now)
        }
        db.update("students", cv, "username = ?", arrayOf(key))

        val hc = db.rawQuery(
            "SELECT is_correct, solved_once, is_revealed, elapsed_sec FROM history WHERE username = ? AND seed = ?",
            arrayOf(key, ex.seed)
        )
        if (hc.moveToFirst()) {
            val hcv = ContentValues().apply { put("updated_at", now) }
            db.update("history", hcv, "username = ? AND seed = ?", arrayOf(key, ex.seed))
        } else {
            val hcv = ContentValues().apply {
                put("username", key)
                put("seed", ex.seed)
                put("chapter", ex.chapter)
                put("technique", ex.technique)
                put("difficulty", ex.difficulty)
                put("full_limit_latex", ex.fullLimitLatex)
                putNull("is_correct")
                put("solved_once", 0)
                put("is_revealed", 0)
                put("solution_viewed", 0)
                put("elapsed_sec", 0.0)
                putNull("fluency_code")
                put("updated_at", now)
            }
            db.insert("history", null, hcv)
        }
        hc.close()
    }

    @Synchronized
    fun startOrResumeOfflineTimer(usernameKey: String, seed: String): JSONObject? {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val c = db.rawQuery(
            """
            SELECT active_seed, active_chapter, active_technique, active_difficulty, active_answer_kind,
                   active_prompt_json, active_latex, active_total_hints, active_timer_state,
                   active_elapsed_ms, active_resumed_at_ms, active_is_completed, active_coach_json, remote_resume_json
            FROM students WHERE username = ?
            """.trimIndent(),
            arrayOf(key)
        )
        if (!c.moveToFirst() || c.getString(0) != seed) {
            c.close()
            return null
        }

        val chapter = c.getString(1) ?: "limits"
        val tech = c.getString(2) ?: RemoteSyncProtocol.inferTechniqueFromSeed(seed)
        val diff = c.getInt(3).coerceIn(1, 3)
        val answerKind = c.getString(4) ?: "constant"
        val promptStr = c.getString(5)
        val latex = c.getString(6) ?: ""
        val totalHints = c.getInt(7)
        val tState = c.getString(8) ?: "ready"
        val elapsedMs = c.getLong(9)
        val isCompleted = c.getInt(11) == 1
        val coachStr = c.getString(12)
        val remoteResumeStr = c.getString(13)
        c.close()

        val nextState = if (isCompleted) "completed" else "running"
        val nextResumedMs = if (!isCompleted && tState != "running") now else if (isCompleted) 0L else now

        val cv = ContentValues().apply {
            put("active_is_revealed", 1)
            put("active_timer_state", nextState)
            if (!isCompleted && tState != "running") {
                put("active_resumed_at_ms", nextResumedMs)
            }
            put("updated_at", now)
        }
        if (!remoteResumeStr.isNullOrBlank()) {
            try {
                val rJson = JSONObject(remoteResumeStr)
                val exObj = rJson.optJSONObject("exercise")
                if (exObj != null) {
                    exObj.put("is_revealed", true)
                    exObj.put("timer", buildTimerJson(nextState, elapsedMs, elapsedMs))
                    rJson.put("exercise", exObj)
                    cv.put("remote_resume_json", rJson.toString())
                }
            } catch (_: Exception) {
            }
        }
        db.update("students", cv, "username = ?", arrayOf(key))

        val hCv = ContentValues().apply {
            put("is_revealed", 1)
            put("updated_at", now)
        }
        db.update("history", hCv, "username = ? AND seed = ?", arrayOf(key, seed))

        val elapsedSec = elapsedMs / 1000.0
        return JSONObject().apply {
            put("seed", seed)
            put("chapter", chapter)
            put("technique", tech)
            put("difficulty", diff)
            put("answer_kind", answerKind)
            if (!promptStr.isNullOrBlank()) {
                try { put("prompt", JSONObject(promptStr)) } catch (_: Exception) { put("prompt", JSONObject.NULL) }
            } else {
                put("prompt", JSONObject.NULL)
            }
            put("full_limit_latex", latex)
            put("is_revealed", true)
            put("is_completed", isCompleted)
            put("total_hints", totalHints)
            put("verified_by_cas", true)
            put("timer", buildTimerJson(nextState, (elapsedSec * 100).toLong() * 10, elapsedMs))
            if (!coachStr.isNullOrBlank()) {
                try { put("coach", JSONObject(coachStr)) } catch (_: Exception) { put("coach", JSONObject.NULL) }
            } else {
                put("coach", JSONObject.NULL)
            }
        }
    }

    @Synchronized
    fun pauseOfflineTimer(usernameKey: String, seed: String): JSONObject {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val c = db.rawQuery(
            "SELECT active_seed, active_timer_state, active_elapsed_ms, active_resumed_at_ms, active_is_revealed, active_is_completed, active_difficulty FROM students WHERE username = ?",
            arrayOf(key)
        )
        var elapsedMs = 0L
        var timerState = "paused"
        var isRevealed = true
        var isCompleted = false
        var diff = 1
        if (c.moveToFirst() && c.getString(0) == seed) {
            timerState = c.getString(1) ?: "ready"
            elapsedMs = c.getLong(2)
            val resumedAt = c.getLong(3)
            isRevealed = c.getInt(4) == 1
            isCompleted = c.getInt(5) == 1
            diff = c.getInt(6).coerceIn(1, 3)
            if (!isCompleted && timerState == "running" && resumedAt > 0L) {
                val seg = (now - resumedAt).coerceIn(0L, MAX_SEGMENT_MS)
                elapsedMs += seg
            }
            if (!isCompleted) timerState = "paused"
        }
        c.close()

        val cv = ContentValues().apply {
            put("active_timer_state", timerState)
            put("active_elapsed_ms", elapsedMs)
            put("active_resumed_at_ms", 0L)
            put("active_is_revealed", 1)
            put("updated_at", now)
        }
        db.update("students", cv, "username = ?", arrayOf(key))

        val hCv = ContentValues().apply {
            put("elapsed_sec", round((elapsedMs / 1000.0) * 10.0) / 10.0)
            put("is_revealed", 1)
        }
        db.update("history", hCv, "username = ? AND seed = ?", arrayOf(key, seed))

        return JSONObject().apply {
            put("seed", seed)
            put("timer", buildTimerJson(timerState, elapsedMs, elapsedMs, diff))
            put("is_revealed", true)
            put("is_completed", isCompleted)
        }
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
    fun recordSolutionRevealed(usernameKey: String, seed: String): JSONObject? {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val c = db.rawQuery(
            "SELECT active_seed, active_timer_state, active_elapsed_ms, active_resumed_at_ms, active_is_completed, active_difficulty FROM students WHERE username = ?",
            arrayOf(key)
        )
        var elapsedMs = 0L
        var alreadyCompleted = false
        var diff = 1
        var matchedActive = false
        if (c.moveToFirst() && c.getString(0) == seed) {
            matchedActive = true
            val tState = c.getString(1) ?: "ready"
            elapsedMs = c.getLong(2)
            val resumedAt = c.getLong(3)
            alreadyCompleted = c.getInt(4) == 1
            diff = c.getInt(5).coerceIn(1, 3)
            if (tState == "running" && resumedAt > 0L) {
                elapsedMs += (now - resumedAt).coerceIn(0L, MAX_SEGMENT_MS)
            }
        }
        c.close()

        val fluencyObj = JSONObject()
            .put("code", "revealed")
            .put("label", JSONObject().put("ar", "تم عرض الحل — سنراجعه قريباً").put("fr", "Solution consultée — révision planifiée"))

        if (matchedActive) {
            val cv = ContentValues().apply {
                put("active_solution_shown", 1)
                put("active_is_revealed", 1)
                put("active_is_completed", 1)
                put("active_timer_state", "completed")
                put("active_elapsed_ms", elapsedMs)
                put("active_resumed_at_ms", 0L)
                put("active_fluency_json", fluencyObj.toString())
                if (!alreadyCompleted) put("current_streak", 0)
                putNull("active_seed")
                putNull("remote_resume_json")
                put("updated_at", now)
            }
            db.update("students", cv, "username = ?", arrayOf(key))
        }

        val hCv = ContentValues().apply {
            put("solution_viewed", 1)
            put("is_revealed", 1)
            put("elapsed_sec", round((elapsedMs / 1000.0) * 10.0) / 10.0)
            put("fluency_code", "revealed")
            put("updated_at", now)
        }
        db.update("history", hCv, "username = ? AND seed = ?", arrayOf(key, seed))

        return JSONObject().apply {
            put("fluency", fluencyObj)
            put("timer", buildTimerJson("completed", elapsedMs, elapsedMs, diff))
            put("is_completed", true)
            put("stats", getStatsJson(key))
        }
    }

    @Synchronized
    fun recordSubmission(
        usernameKey: String,
        ex: GeneratedExercise,
        rawAnswer: String,
        isCorrect: Boolean,
        gradeJson: JSONObject,
        solveTimeSecOverride: Double? = null
    ): JSONObject {
        val key = usernameKey.lowercase()
        val now = System.currentTimeMillis()
        val db = writableDatabase

        db.beginTransaction()
        try {
            val hc = db.rawQuery(
                "SELECT solved_once, is_correct FROM history WHERE username = ? AND seed = ?",
                arrayOf(key, ex.seed)
            )
            val alreadySolved = hc.moveToFirst() && hc.getInt(0) == 1
            val hadWrongAttempt = if (hc.moveToFirst()) hc.getInt(1) == 0 else false
            hc.close()

            val sc = db.rawQuery(
                """
                SELECT total_solved, total_attempts, total_correct, current_streak, best_streak,
                       active_timer_state, active_elapsed_ms, active_resumed_at_ms, active_hints_count
                FROM students WHERE username = ?
                """.trimIndent(),
                arrayOf(key)
            )
            var totalSolved = 0
            var totalAttempts = 0
            var totalCorrect = 0
            var streak = 0
            var bestStreak = 0
            var elapsedMs = 0L
            var hintsUsed = 0
            if (sc.moveToFirst()) {
                totalSolved = sc.getInt(0)
                totalAttempts = sc.getInt(1)
                totalCorrect = sc.getInt(2)
                streak = sc.getInt(3)
                bestStreak = sc.getInt(4)
                val tState = sc.getString(5) ?: "ready"
                elapsedMs = sc.getLong(6)
                val resumedAt = sc.getLong(7)
                hintsUsed = sc.getInt(8)
                if (tState == "running" && resumedAt > 0L) {
                    elapsedMs += (now - resumedAt).coerceIn(0L, MAX_SEGMENT_MS)
                }
            }
            sc.close()

            if (elapsedMs <= 0L && solveTimeSecOverride != null && solveTimeSecOverride > 0.0) {
                elapsedMs = (solveTimeSecOverride * 1000.0).toLong().coerceAtMost(MAX_SEGMENT_MS)
            }
            val elapsedSec = round((elapsedMs / 1000.0) * 10.0) / 10.0

            if (!alreadySolved) {
                if (!hadWrongAttempt || isCorrect) {
                    if (!hadWrongAttempt) totalAttempts += 1
                }
                if (isCorrect) {
                    totalCorrect += 1
                    streak += 1
                    if (streak > bestStreak) bestStreak = streak
                    totalSolved += 1
                } else {
                    streak = 0
                }
            }

            val fluencyObj = classifyOfflineFluency(
                difficulty = ex.difficulty,
                isCorrect = isCorrect,
                solutionViewed = false,
                hintsUsed = hintsUsed,
                hadWrongBefore = hadWrongAttempt,
                solveTimeSec = if (elapsedSec > 0) elapsedSec else null
            )
            val nextTimerState = if (isCorrect || alreadySolved) "completed" else "paused"

            val stCv = ContentValues().apply {
                put("total_solved", totalSolved)
                put("total_attempts", totalAttempts)
                put("total_correct", totalCorrect)
                put("current_streak", streak)
                put("best_streak", bestStreak)
                put("active_student_answer", rawAnswer)
                put("active_last_grade_json", gradeJson.toString())
                put("active_timer_state", nextTimerState)
                put("active_elapsed_ms", elapsedMs)
                put("active_resumed_at_ms", 0L)
                put("active_is_revealed", 1)
                put("active_fluency_json", fluencyObj.toString())
                if (isCorrect) {
                    put("active_solution_shown", 1)
                    put("active_is_completed", 1)
                    putNull("active_seed")
                    putNull("remote_resume_json")
                }
                put("dirty_offline", 1)
                put("updated_at", now)
            }
            db.update("students", stCv, "username = ?", arrayOf(key))

            val mc = db.rawQuery(
                "SELECT attempts, correct, avg_time_sec FROM mastery WHERE username = ? AND technique = ?",
                arrayOf(key, ex.technique)
            )
            var mAtt = 0
            var mCor = 0
            var oldAvg: Double? = null
            if (mc.moveToFirst()) {
                mAtt = mc.getInt(0)
                mCor = mc.getInt(1)
                oldAvg = if (mc.isNull(2)) null else mc.getDouble(2)
            }
            mc.close()

            val newAvg = if (isCorrect && elapsedSec > 0.0) {
                if (oldAvg == null) elapsedSec else round((0.7 * oldAvg + 0.3 * elapsedSec) * 10.0) / 10.0
            } else {
                oldAvg
            }

            val mCv = ContentValues().apply {
                put("username", key)
                put("technique", ex.technique)
                put("attempts", mAtt + (if (!alreadySolved && !hadWrongAttempt) 1 else 0))
                put("correct", mCor + (if (isCorrect && !alreadySolved) 1 else 0))
                if (newAvg != null) put("avg_time_sec", newAvg) else putNull("avg_time_sec")
                put("last_practiced_at", now)
            }
            db.replace("mastery", null, mCv)

            val hCv = ContentValues().apply {
                put("username", key)
                put("seed", ex.seed)
                put("chapter", ex.chapter)
                put("technique", ex.technique)
                put("difficulty", ex.difficulty)
                put("full_limit_latex", ex.fullLimitLatex)
                put("is_correct", if (isCorrect) 1 else 0)
                put("solved_once", if (alreadySolved || isCorrect) 1 else 0)
                put("is_revealed", 1)
                put("solution_viewed", 0)
                put("elapsed_sec", elapsedSec)
                put("fluency_code", fluencyObj.optString("code"))
                put("updated_at", now)
            }
            db.replace("history", null, hCv)

            val statsAfter = getStatsJson(key)
            db.setTransactionSuccessful()
            return JSONObject().apply {
                put("stats", statsAfter)
                put("fluency", fluencyObj)
                put("timer", buildTimerJson(nextTimerState, elapsedMs, elapsedMs, ex.difficulty))
                put("is_completed", isCorrect || alreadySolved)
            }
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
        }
        ensureAllTechniqueRows(db, key)
        return key
    }

    @Synchronized
    fun cacheRemoteStats(rawStatsJson: JSONObject, fallbackUsername: String? = null) {
        val rawUser = rawStatsJson.optCleanString("username", fallbackUsername ?: "").trim()
        if (rawUser.isEmpty()) return
        val key = ensureStudentRow(rawUser, rawStatsJson.optCleanString("preferred_lang", rawStatsJson.optCleanString("lang", "ar")))
        val display = rawStatsJson.optCleanString("display_name", rawUser).ifBlank { rawUser }
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
            val bestStreak = rawStatsJson.optInt("best_streak", streak)

            if (hasMastery) {
                val arr = rawStatsJson.getJSONArray("mastery_by_technique")
                var sumAtt = 0
                var sumCor = 0
                val seenTechs = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val tech = item.optCleanString("technique").trim()
                    if (tech.isEmpty()) continue
                    val att = item.optInt("attempts", 0)
                    val cor = item.optInt("correct", 0)
                    val avgT = item.optNullableDouble("avg_time_sec")
                    sumAtt += att
                    sumCor += cor
                    seenTechs.add(tech)
                    val mCv = ContentValues().apply {
                        put("username", key)
                        put("technique", tech)
                        put("attempts", att)
                        put("correct", cor)
                        if (avgT != null) put("avg_time_sec", avgT) else putNull("avg_time_sec")
                    }
                    db.replace("mastery", null, mCv)
                }
                for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
                    if (tech !in seenTechs) {
                        db.execSQL(
                            "INSERT OR IGNORE INTO mastery (username, technique, attempts, correct) VALUES (?, ?, 0, 0)",
                            arrayOf(key, tech)
                        )
                    }
                }
                if (totalAttempts == 0 && sumAtt > 0) totalAttempts = sumAtt
                if (totalCorrect < 0) totalCorrect = sumCor
            }

            if (totalCorrect < 0) {
                val accPct = RemoteSyncProtocol.extractAccuracyPercent(rawStatsJson, totalSolved, totalAttempts)
                totalCorrect = if (totalAttempts > 0) {
                    round((accPct / 100.0) * totalAttempts).toInt()
                } else {
                    totalSolved
                }
            }

            if (hasHistory) {
                val histArr = rawStatsJson.getJSONArray("recent_history")
                for (i in 0 until histArr.length()) {
                    val h = histArr.optJSONObject(i) ?: continue
                    val seed = h.optCleanString("seed").trim()
                    if (seed.isEmpty()) continue
                    val latex = h.optCleanString("full_limit_latex")
                    val isCorr: Int? = if (h.isNull("is_correct")) null else if (h.optBoolean("is_correct", false)) 1 else 0
                    val tech = h.optCleanString("technique", RemoteSyncProtocol.inferTechniqueFromSeed(seed))
                    val chap = h.optCleanString("chapter", LimitsCasEngine.chapterOfTechnique(tech))
                    val diff = h.optInt("difficulty", inferDifficultyFromSeed(seed))
                    val revealed = if (h.has("is_revealed")) h.optBoolean("is_revealed", true) else true
                    val solViewed = h.optBoolean("solution_viewed", false)
                    val elapsedSec = h.optDouble("elapsed_sec", 0.0).let { if (it.isNaN()) 0.0 else it }
                    val fluencyCode = h.optCleanString("fluency_code").takeIf { it.isNotBlank() }

                    val hCv = ContentValues().apply {
                        put("username", key)
                        put("seed", seed)
                        put("chapter", chap)
                        put("technique", tech)
                        put("difficulty", diff)
                        put("full_limit_latex", latex)
                        if (isCorr == null) putNull("is_correct") else put("is_correct", isCorr)
                        put("solved_once", if (isCorr == 1) 1 else 0)
                        put("is_revealed", if (revealed) 1 else 0)
                        put("solution_viewed", if (solViewed) 1 else 0)
                        put("elapsed_sec", elapsedSec)
                        if (fluencyCode != null) put("fluency_code", fluencyCode) else putNull("fluency_code")
                        put("updated_at", now - i)
                    }
                    db.replace("history", null, hCv)
                }
            }

            val remoteJsonToStore = if (hasMastery && hasHistory) {
                rawStatsJson.toString()
            } else {
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
                    if (rawStatsJson.has("best_streak")) merged.put("best_streak", bestStreak)
                    if (rawStatsJson.has("accuracy")) merged.put("accuracy", rawStatsJson.get("accuracy"))
                    if (rawStatsJson.has("active_lock")) merged.put("active_lock", rawStatsJson.get("active_lock"))
                    if (rawStatsJson.has("active_seed")) merged.put("active_seed", rawStatsJson.get("active_seed"))
                    merged.toString()
                } else {
                    null
                }
            }

            val stCv = ContentValues().apply {
                put("display_name", display)
                if (rawStatsJson.has("preferred_lang")) {
                    put("lang", if (rawStatsJson.optCleanString("preferred_lang") == "fr") "fr" else "ar")
                } else if (rawStatsJson.has("lang")) {
                    put("lang", if (rawStatsJson.optCleanString("lang") == "fr") "fr" else "ar")
                }
                put("total_solved", totalSolved)
                if (totalAttempts > 0 || hasMastery) {
                    put("total_attempts", totalAttempts)
                    put("total_correct", totalCorrect)
                }
                put("current_streak", streak)
                put("best_streak", bestStreak)
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
        val hintsArr = rawResumeJson.optJSONArray("hints")
        val solObj = if (rawResumeJson.isNull("solution")) null else rawResumeJson.optJSONObject("solution")
        val gradeObj = if (rawResumeJson.isNull("last_grade")) null else rawResumeJson.optJSONObject("last_grade")
        val isCompleted = (exObj?.optBoolean("is_completed", false) == true) ||
            solObj != null ||
            gradeObj?.optBoolean("is_correct", false) == true

        val cv = ContentValues().apply {
            put("remote_resume_json", rawResumeJson.toString())
            put("updated_at", now)
            if (exObj != null && !isCompleted) {
                val seed = exObj.optCleanString("seed")
                val tech = exObj.optCleanString("technique", RemoteSyncProtocol.inferTechniqueFromSeed(seed))
                val chap = exObj.optCleanString("chapter", LimitsCasEngine.chapterOfTechnique(tech))
                val diff = exObj.optInt("difficulty", inferDifficultyFromSeed(seed))
                val latex = exObj.optCleanString("full_limit_latex")
                val totalHints = exObj.optInt("total_hints", 0)
                val stuAns = rawResumeJson.optCleanString("student_answer")
                val isRevealed = exObj.optBoolean("is_revealed", true)
                val timerObj = exObj.optJSONObject("timer")
                val tState = timerObj?.optCleanString("state", if (isRevealed) "paused" else "ready") ?: "ready"
                val elapsedMs = ((timerObj?.optDouble("elapsed_sec", 0.0) ?: 0.0) * 1000.0).toLong()

                put("active_seed", seed)
                put("active_chapter", chap)
                put("active_technique", tech)
                put("active_difficulty", diff)
                put("active_answer_kind", exObj.optCleanString("answer_kind", "constant"))
                put("active_latex", latex)
                put("active_total_hints", totalHints)
                put("active_hints_count", hintsArr?.length() ?: 0)
                put("active_solution_shown", 0)
                put("active_is_revealed", if (isRevealed) 1 else 0)
                put("active_is_completed", 0)
                put("active_timer_state", tState)
                put("active_elapsed_ms", elapsedMs)
                put("active_resumed_at_ms", if (tState == "running") now else 0L)
                if (gradeObj != null) put("active_last_grade_json", gradeObj.toString()) else putNull("active_last_grade_json")
                put("active_student_answer", stuAns)

                if (seed.isNotEmpty()) {
                    cacheServerExerciseInternal(db, exObj, hintsArr, solObj, now)
                }
            } else {
                put("active_is_completed", 1)
                putNull("active_seed")
            }
        }
        db.update("students", cv, "username = ?", arrayOf(key))
    }

    @Synchronized
    fun cacheServerExercise(exJson: JSONObject, usernameRaw: String? = null) {
        val seed = exJson.optCleanString("seed").trim()
        if (seed.isEmpty()) return
        val now = System.currentTimeMillis()
        val db = writableDatabase
        cacheServerExerciseInternal(db, exJson, null, null, now)

        if (!usernameRaw.isNullOrBlank()) {
            val key = ensureStudentRow(usernameRaw)
            val isRevealed = exJson.optBoolean("is_revealed", false)
            val isCompleted = exJson.optBoolean("is_completed", false)
            val timerObj = exJson.optJSONObject("timer")
            val tState = timerObj?.optCleanString("state", if (isCompleted) "completed" else if (isRevealed) "paused" else "ready") ?: "ready"
            val elapsedMs = ((timerObj?.optDouble("elapsed_sec", 0.0) ?: 0.0) * 1000.0).toLong()

            val resumeJson = JSONObject().apply {
                put("has_exercise", true)
                put("exercise", exJson)
                put("hints", JSONArray())
                put("last_grade", JSONObject.NULL)
                put("solution", JSONObject.NULL)
                put("student_answer", "")
            }
            val tech = exJson.optCleanString("technique", RemoteSyncProtocol.inferTechniqueFromSeed(seed))
            val chap = exJson.optCleanString("chapter", LimitsCasEngine.chapterOfTechnique(tech))
            val diff = exJson.optInt("difficulty", inferDifficultyFromSeed(seed))
            val latex = exJson.optCleanString("full_limit_latex")
            val totalHints = exJson.optInt("total_hints", 0)

            val cv = ContentValues().apply {
                if (isCompleted) putNull("active_seed") else put("active_seed", seed)
                put("active_chapter", chap)
                put("active_technique", tech)
                put("active_difficulty", diff)
                put("active_answer_kind", exJson.optCleanString("answer_kind", "constant"))
                put("active_latex", latex)
                put("active_total_hints", totalHints)
                put("active_hints_count", 0)
                put("active_solution_shown", if (isCompleted) 1 else 0)
                put("active_is_revealed", if (isRevealed) 1 else 0)
                put("active_is_completed", if (isCompleted) 1 else 0)
                put("active_timer_state", tState)
                put("active_elapsed_ms", elapsedMs)
                put("active_resumed_at_ms", if (tState == "running") now else 0L)
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
                    put("chapter", chap)
                    put("technique", tech)
                    put("difficulty", diff)
                    put("full_limit_latex", latex)
                    putNull("is_correct")
                    put("solved_once", 0)
                    put("is_revealed", if (isRevealed) 1 else 0)
                    put("solution_viewed", 0)
                    put("elapsed_sec", elapsedMs / 1000.0)
                    put("updated_at", now)
                }
                db.insert("history", null, hCv)
            } else if (isRevealed && latex.isNotBlank()) {
                val hCv = ContentValues().apply {
                    put("full_limit_latex", latex)
                    put("is_revealed", 1)
                }
                db.update("history", hCv, "username = ? AND seed = ?", arrayOf(key, seed))
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
        val seed = exJson.optCleanString("seed").trim()
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
            put("technique", exJson.optCleanString("technique", RemoteSyncProtocol.inferTechniqueFromSeed(seed)))
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
                    put("active_is_completed", 1)
                    put("active_timer_state", "completed")
                    putNull("active_seed")
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
        solObj: JSONObject?,
        isCompleted: Boolean
    ) {
        if (usernameRaw.isBlank()) return
        val key = usernameRaw.lowercase()
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val sc = db.rawQuery("SELECT active_seed, remote_resume_json FROM students WHERE username = ?", arrayOf(key))
        if (sc.moveToFirst() && sc.getString(0) == seed) {
            val done = isCompleted || solObj != null || gradeObj?.optBoolean("is_correct", false) == true
            val resumeRaw = sc.getString(1)
            val cv = ContentValues().apply {
                put("active_student_answer", answer)
                if (gradeObj != null) put("active_last_grade_json", gradeObj.toString())
                if (solObj != null || done) put("active_solution_shown", 1)
                if (done) {
                    put("active_is_completed", 1)
                    put("active_timer_state", "completed")
                    putNull("active_seed")
                    putNull("remote_resume_json")
                }
                put("updated_at", now)
                if (!resumeRaw.isNullOrBlank() && !done) {
                    try {
                        val rJson = JSONObject(resumeRaw)
                        rJson.put("student_answer", answer)
                        if (gradeObj != null) rJson.put("last_grade", gradeObj)
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
    fun getLastActiveStudent(): Pair<String, String>? {
        val c = readableDatabase.rawQuery(
            "SELECT display_name, lang FROM students ORDER BY updated_at DESC LIMIT 1",
            null
        )
        val res = if (c.moveToFirst()) {
            val name = c.getString(0)
            val lang = if (c.getString(1) == "fr") "fr" else "ar"
            if (!name.isNullOrBlank()) name to lang else null
        } else null
        c.close()
        return res
    }

    @Synchronized
    fun getRecentUsernames(limit: Int = 6): List<String> {
        val list = mutableListOf<String>()
        val c = readableDatabase.rawQuery(
            "SELECT display_name FROM students ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        while (c.moveToNext()) {
            val name = c.getString(0)?.trim().orEmpty()
            if (name.isNotEmpty()) list.add(name)
        }
        c.close()
        return list
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
                val path = env.optCleanString("path")
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
            SELECT active_seed, active_hints_count, active_solution_shown, active_last_grade_json,
                   active_student_answer, remote_resume_json, active_timer_state, active_elapsed_ms,
                   active_resumed_at_ms, active_is_revealed, active_is_completed, active_coach_json, active_fluency_json
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
        val timerState = c.getString(6) ?: "ready"
        val elapsedMs = c.getLong(7)
        val resumedAtMs = c.getLong(8)
        val isRevealed = c.getInt(9) == 1
        val isCompleted = c.getInt(10) == 1
        val coachStr = c.getString(11)
        val fluencyStr = c.getString(12)
        c.close()

        if (seed.isNullOrBlank() || solShown || isCompleted) {
            return JSONObject().put("has_exercise", false)
        }

        if (!gradeStr.isNullOrBlank()) {
            try {
                val g = JSONObject(gradeStr)
                if (g.optBoolean("is_correct", false)) {
                    return JSONObject().put("has_exercise", false)
                }
            } catch (_: Exception) {
            }
        }

        val liveMs = if (timerState == "running" && resumedAtMs > 0L) {
            elapsedMs + (System.currentTimeMillis() - resumedAtMs).coerceIn(0L, MAX_SEGMENT_MS)
        } else {
            elapsedMs
        }

        val engine = OfflineLimitsEngineHolder.get()
        if (engine.isValidSeed(seed)) {
            return try {
                val ex = engine.generate("adaptive", 0, explicitSeed = seed)
                val hintsArr = JSONArray()
                if (isRevealed) {
                    for (i in 0 until minOf(hintsCount, ex.hints.size)) {
                        hintsArr.put(
                            JSONObject()
                                .put("ar", ex.hints[i].ar)
                                .put("fr", ex.hints[i].fr)
                        )
                    }
                }
                val exJson = exerciseToJson(
                    ex = ex,
                    isRevealed = isRevealed,
                    isCompleted = false,
                    timerState = timerState,
                    elapsedMs = elapsedMs,
                    liveElapsedMs = liveMs,
                    coachJsonStr = coachStr
                )
                JSONObject().apply {
                    put("has_exercise", true)
                    put("exercise", exJson)
                    put("hints", hintsArr)
                    put("last_grade", if (gradeStr.isNullOrBlank()) JSONObject.NULL else JSONObject(gradeStr))
                    put("solution", JSONObject.NULL)
                    put("fluency", if (fluencyStr.isNullOrBlank()) JSONObject.NULL else JSONObject(fluencyStr))
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
        return JSONObject().apply {
            put("has_exercise", true)
            put("exercise", cachedEx)
            put("hints", JSONArray())
            put("last_grade", if (gradeStr.isNullOrBlank()) JSONObject.NULL else JSONObject(gradeStr))
            put("solution", JSONObject.NULL)
            put("student_answer", stuAnswer)
        }
    }

    @Synchronized
    fun getStatsJson(usernameKey: String): JSONObject {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        val sc = db.rawQuery(
            """
            SELECT display_name, total_solved, total_attempts, total_correct, current_streak,
                   best_streak, remote_stats_json, dirty_offline, lang, active_seed, active_is_completed
            FROM students WHERE username = ?
            """.trimIndent(),
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
        val bestStreak = maxOf(streak, sc.getInt(5))
        val remoteStatsStr = sc.getString(6)
        val dirtyOffline = sc.getInt(7)
        val studentLang = if (sc.getString(8) == "fr") "fr" else "ar"
        val activeSeed = sc.getString(9)
        val activeCompleted = sc.getInt(10) == 1
        sc.close()

        if (!remoteStatsStr.isNullOrBlank() && dirtyOffline == 0) {
            try {
                return JSONObject(remoteStatsStr)
            } catch (_: Exception) {
            }
        }

        val accuracy = if (attempts == 0) 0 else round((correct * 100.0) / attempts).toInt()

        val masteryMap = mutableMapOf<String, Triple<Int, Int, Double?>>()
        val mc = db.rawQuery(
            "SELECT technique, attempts, correct, avg_time_sec FROM mastery WHERE username = ?",
            arrayOf(key)
        )
        while (mc.moveToNext()) {
            val avgT = if (mc.isNull(3)) null else mc.getDouble(3)
            masteryMap[mc.getString(0)] = Triple(mc.getInt(1), mc.getInt(2), avgT)
        }
        mc.close()

        // Compute solved by chapter
        val chapSolved = mutableMapOf("limits" to 0, "sequences" to 0)
        val hcChap = db.rawQuery(
            "SELECT chapter, COUNT(CASE WHEN is_correct = 1 THEN 1 END) FROM history WHERE username = ? GROUP BY chapter",
            arrayOf(key)
        )
        while (hcChap.moveToNext()) {
            val ch = hcChap.getString(0) ?: "limits"
            chapSolved[ch] = hcChap.getInt(1)
        }
        hcChap.close()

        val limSolved = chapSolved["limits"] ?: 0
        val seqSolved = chapSolved["sequences"] ?: 0

        fun techRatio(t: String): Double {
            val (a, c, _) = masteryMap[t] ?: Triple(0, 0, null)
            return if (a == 0) INITIAL_MASTERY else (c.toDouble() / a.toDouble()).coerceIn(0.12, 0.98)
        }
        fun techCorrect(t: String): Int = masteryMap[t]?.second ?: 0
        fun techAttempts(t: String): Int = masteryMap[t]?.first ?: 0

        val unlockedMap = mapOf(
            "poly_factor" to (true to (1 to LocalizedMessage("متاح منذ البداية", "Disponible dès le début"))),
            "sqrt_conjugate" to ((techRatio("poly_factor") >= 0.38 || techCorrect("poly_factor") >= 2 || techAttempts("sqrt_conjugate") > 0 || limSolved >= 2) to (2 to LocalizedMessage("يُفتح بعد تثبيت أساسيات التعميل.", "Débloqué après les bases de factorisation."))),
            "trig" to ((techRatio("poly_factor") >= 0.42 || techAttempts("trig") > 0 || limSolved >= 3) to (2 to LocalizedMessage("يُفتح بعد حل 3 تمارين أساسية.", "Débloqué après 3 réussites."))),
            "nth_root" to ((techRatio("sqrt_conjugate") >= 0.48 || techCorrect("sqrt_conjugate") >= 2 || techAttempts("nth_root") > 0 || limSolved >= 5) to (3 to LocalizedMessage("يُفتح بعد إتقان المرافق التربيعي.", "Débloqué après le conjugué."))),
            "ln_limits" to ((techRatio("poly_factor") >= 0.44 || techAttempts("ln_limits") > 0 || limSolved >= 4) to (3 to LocalizedMessage("يُفتح بعد 4 تمارين في النهايات.", "Débloqué après 4 réussites en limites."))),
            "exp_limits" to ((techRatio("ln_limits") >= 0.42 || techRatio("poly_factor") >= 0.48 || techAttempts("exp_limits") > 0 || limSolved >= 5) to (3 to LocalizedMessage("يُفتح بعد أساسيات اللوغاريتم.", "Débloqué après les bases de ln."))),
            "add_sub_zero" to (((techRatio("sqrt_conjugate") >= 0.52 && techRatio("nth_root") >= 0.42) || techAttempts("add_sub_zero") > 0 || limSolved >= 8) to (4 to LocalizedMessage("يُفتح بعد المرافق والجذور النونية.", "Débloqué après conjugué et racines n-ièmes."))),
            "hybrid" to ((limSolved >= 11 || techAttempts("hybrid") > 0 || listOf("poly_factor", "sqrt_conjugate", "trig", "nth_root", "ln_limits", "exp_limits", "add_sub_zero").count { techRatio(it) >= 0.55 } >= 3) to (5 to LocalizedMessage("يُفتح بعد إتقان 3 تقنيات أساسية.", "Débloqué après 3 techniques maîtrisées."))),
            "seq_limits" to (true to (1 to LocalizedMessage("متاح منذ البداية", "Disponible dès le début"))),
            "seq_arith_geo" to ((techRatio("seq_limits") >= 0.38 || techCorrect("seq_limits") >= 2 || techAttempts("seq_arith_geo") > 0 || seqSolved >= 2) to (2 to LocalizedMessage("يُفتح بعد تثبيت نهايات المتتاليات.", "Débloqué après 2 réussites."))),
            "seq_fixed_point" to ((techRatio("seq_arith_geo") >= 0.40 || techAttempts("seq_fixed_point") > 0 || seqSolved >= 3) to (2 to LocalizedMessage("يُفتح بعد 3 تمارين في المتتاليات.", "Débloqué après 3 exercices en suites."))),
            "seq_homographic" to ((techRatio("seq_arith_geo") >= 0.48 || techCorrect("seq_arith_geo") >= 2 || techAttempts("seq_homographic") > 0 || seqSolved >= 4) to (3 to LocalizedMessage("يُفتح بعد المتتاليات الترجعية الخطية.", "Débloqué après les suites arithmético-géométriques.")))
        )

        val masteryArr = JSONArray()
        val effScores = mutableListOf<Double>()
        for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
            val (tAtt, tCor, avgT) = masteryMap[tech] ?: Triple(0, 0, null)
            val mRatio = if (tAtt == 0) INITIAL_MASTERY else (tCor.toDouble() / tAtt.toDouble()).coerceIn(0.12, 0.98)
            if (tAtt > 0) effScores.add(mRatio)
            val info = unlockedMap[tech] ?: (true to (1 to LocalizedMessage("", "")))
            masteryArr.put(
                JSONObject()
                    .put("chapter", LimitsCasEngine.chapterOfTechnique(tech))
                    .put("technique", tech)
                    .put("mastery", mRatio)
                    .put("effective_mastery", mRatio)
                    .put("correct", tCor)
                    .put("attempts", tAtt)
                    .put("avg_time_sec", avgT ?: JSONObject.NULL)
                    .put("is_unlocked", info.first)
                    .put("stage", info.second.first)
                    .put("unlock_hint", JSONObject().put("ar", info.second.second.ar).put("fr", info.second.second.fr))
            )
        }

        val overallMastery = if (effScores.isEmpty()) INITIAL_MASTERY else round((effScores.sum() / effScores.size) * 1000.0) / 1000.0

        val histArr = JSONArray()
        var avgSolveSum = 0.0
        var avgSolveCnt = 0
        val hc = db.rawQuery(
            """
            SELECT seed, chapter, technique, difficulty, is_correct, full_limit_latex,
                   is_revealed, solution_viewed, elapsed_sec, fluency_code
            FROM history WHERE username = ?
            ORDER BY updated_at DESC LIMIT 20
            """.trimIndent(),
            arrayOf(key)
        )
        while (hc.moveToNext()) {
            val isCorrVal: Any = if (hc.isNull(4)) JSONObject.NULL else (hc.getInt(4) == 1)
            val revealed = hc.getInt(6) == 1
            val solViewed = hc.getInt(7) == 1
            val elapsed = hc.getDouble(8)
            if (hc.getInt(4) == 1 && !solViewed && elapsed > 0.0) {
                avgSolveSum += elapsed
                avgSolveCnt += 1
            }
            histArr.put(
                JSONObject()
                    .put("seed", hc.getString(0))
                    .put("chapter", hc.getString(1) ?: "limits")
                    .put("technique", hc.getString(2) ?: RemoteSyncProtocol.inferTechniqueFromSeed(hc.getString(0)))
                    .put("difficulty", hc.getInt(3))
                    .put("is_correct", isCorrVal)
                    .put("is_revealed", revealed)
                    .put("solution_viewed", solViewed)
                    .put("elapsed_sec", elapsed)
                    .put("fluency_code", hc.getString(9) ?: JSONObject.NULL)
                    .put("full_limit_latex", if (revealed) hc.getString(5) else JSONObject.NULL)
            )
        }
        hc.close()

        val hasActiveLock = !activeSeed.isNullOrBlank() && !activeCompleted
        return JSONObject().apply {
            put("username", key)
            put("display_name", display)
            put("preferred_lang", studentLang)
            put("total_solved", solved)
            put("total_attempted", attempts)
            put("accuracy", accuracy)
            put("current_streak", streak)
            put("best_streak", bestStreak)
            put("avg_solve_time_sec", if (avgSolveCnt > 0) round((avgSolveSum / avgSolveCnt) * 10.0) / 10.0 else JSONObject.NULL)
            put("overall_mastery", overallMastery)
            put("due_reviews_count", 0)
            put("active_lock", hasActiveLock)
            put("active_seed", if (hasActiveLock) activeSeed else JSONObject.NULL)
            put("solved_by_chapter", JSONObject().put("limits", limSolved).put("sequences", seqSolved))
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

    private fun buildTimerJson(
        state: String,
        elapsedMs: Long,
        liveElapsedMs: Long,
        difficulty: Int = 1
    ): JSONObject {
        val (fastSec, normalSec) = when (difficulty) {
            1 -> 45 to 120
            2 -> 90 to 220
            else -> 150 to 340
        }
        return JSONObject()
            .put("state", state)
            .put("elapsed_sec", round((elapsedMs / 1000.0) * 100.0) / 100.0)
            .put("live_elapsed_sec", round((liveElapsedMs / 1000.0) * 100.0) / 100.0)
            .put("target_fast_sec", fastSec)
            .put("target_normal_sec", normalSec)
    }

    private fun classifyOfflineFluency(
        difficulty: Int,
        isCorrect: Boolean,
        solutionViewed: Boolean,
        hintsUsed: Int,
        hadWrongBefore: Boolean,
        solveTimeSec: Double?
    ): JSONObject {
        val (code, ar, fr) = when {
            solutionViewed -> Triple("revealed", "تم عرض الحل — سنراجعه قريباً", "Solution consultée — révision planifiée")
            !isCorrect -> Triple("incorrect", "محاولة خاطئة — راجع الحساب", "Réponse incorrecte — vérifiez vos calculs")
            hintsUsed > 0 || hadWrongBefore -> Triple("assisted", "صحيح بعد مساعدة / محاولة ثانية", "Exact après aide / second essai")
            solveTimeSec == null || solveTimeSec <= 0.0 -> Triple("good", "إجابة صحيحة ومتقنة", "Réponse exacte et solide")
            else -> {
                val (fast, normal) = when (difficulty) {
                    1 -> 45.0 to 120.0
                    2 -> 90.0 to 220.0
                    else -> 150.0 to 340.0
                }
                when {
                    solveTimeSec <= fast -> Triple("fast", "⚡ إتقان وسرعة ممتازة!", "⚡ Maîtrise et rapidité excellentes !")
                    solveTimeSec <= normal -> Triple("good", "✓ وقت مثالي وحل متزن", "✓ Temps idéal et démarche solide")
                    else -> Triple("slow", "صحيح، لكن يحتاج تحسين السرعة", "Exact, mais la rapidité reste à travailler")
                }
            }
        }
        return JSONObject()
            .put("code", code)
            .put("label", JSONObject().put("ar", ar).put("fr", fr))
    }

    fun exerciseToJson(
        ex: GeneratedExercise,
        isRevealed: Boolean = false,
        isCompleted: Boolean = false,
        timerState: String = if (isCompleted) "completed" else if (isRevealed) "paused" else "ready",
        elapsedMs: Long = 0L,
        liveElapsedMs: Long = elapsedMs,
        coachJsonStr: String? = null
    ): JSONObject {
        return JSONObject().apply {
            put("seed", ex.seed)
            put("chapter", ex.chapter)
            put("technique", ex.technique)
            put("difficulty", ex.difficulty)
            put("answer_kind", ex.answerKind)
            put(
                "prompt",
                if (isRevealed) JSONObject().put("ar", ex.prompt.ar).put("fr", ex.prompt.fr) else JSONObject.NULL
            )
            put("full_limit_latex", if (isRevealed) ex.fullLimitLatex else JSONObject.NULL)
            put("is_revealed", isRevealed)
            put("is_completed", isCompleted)
            put("total_hints", ex.hints.size)
            put("verified_by_cas", true)
            put("timer", buildTimerJson(timerState, elapsedMs, liveElapsedMs, ex.difficulty))
            if (!coachJsonStr.isNullOrBlank()) {
                try { put("coach", JSONObject(coachJsonStr)) } catch (_: Exception) { put("coach", JSONObject.NULL) }
            } else {
                put("coach", JSONObject.NULL)
            }
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
