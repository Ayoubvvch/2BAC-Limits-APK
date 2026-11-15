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
        private const val DB_VERSION = 1
    }

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Initial version 1
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
            val syncPayload = JSONObject().apply {
                put("username", key)
                put("seed", ex.seed)
                put("technique", ex.technique)
                put("difficulty", ex.difficulty)
                put("is_correct", isCorrect)
                put("total_solved", totalSolved)
                put("accuracy", statsAfter.getInt("accuracy"))
                put("current_streak", streak)
                put("timestamp", now)
            }
            val sqCv = ContentValues().apply {
                put("username", key)
                put("event_type", "attempt")
                put("payload_json", syncPayload.toString())
                put("created_at", now)
                put("synced", 0)
            }
            db.insert("sync_queue", null, sqCv)

            db.setTransactionSuccessful()
            return statsAfter
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getResumeJson(usernameKey: String): JSONObject {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        val c = db.rawQuery(
            """
            SELECT active_seed, active_hints_count, active_solution_shown, active_last_grade_json, active_student_answer
            FROM students WHERE username = ?
            """.trimIndent(),
            arrayOf(key)
        )
        if (!c.moveToFirst() || c.isNull(0)) {
            c.close()
            return JSONObject().put("has_exercise", false)
        }
        val seed = c.getString(0)
        val hintsCount = c.getInt(1)
        val solShown = c.getInt(2) == 1
        val gradeStr = c.getString(3)
        val stuAnswer = c.getString(4) ?: ""
        c.close()

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

    @Synchronized
    fun getStatsJson(usernameKey: String): JSONObject {
        val key = usernameKey.lowercase()
        val db = readableDatabase
        val sc = db.rawQuery(
            "SELECT display_name, total_solved, total_attempts, total_correct, current_streak FROM students WHERE username = ?",
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
        sc.close()

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
    fun getPendingSyncItems(limit: Int = 50): List<Pair<Long, String>> {
        val list = mutableListOf<Pair<Long, String>>()
        val c = readableDatabase.rawQuery(
            "SELECT id, payload_json FROM sync_queue WHERE synced = 0 ORDER BY id ASC LIMIT ?",
            arrayOf(limit.toString())
        )
        while (c.moveToNext()) {
            list.add(Pair(c.getLong(0), c.getString(1)))
        }
        c.close()
        return list
    }

    @Synchronized
    fun getPendingSyncCount(): Int {
        val c = readableDatabase.rawQuery("SELECT COUNT(*) FROM sync_queue WHERE synced = 0", null)
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
