package com.ayoub.baclimits

import com.ayoub.baclimits.data.HistoryEntry
import com.ayoub.baclimits.data.LimitsJsonParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsModelsTest {

    @Test
    fun `parseApiError handles string and FastAPI validation detail arrays`() {
        val strErr = JSONObject().put("detail", "Username invalid").toString()
        assertEquals("Username invalid", LimitsJsonParser.parseApiError(strErr, 422))

        val arrErr = JSONObject().put(
            "detail",
            JSONArray().put(JSONObject().put("msg", "ensure this value has at least 2 characters"))
        ).toString()
        assertEquals("ensure this value has at least 2 characters", LimitsJsonParser.parseApiError(arrErr, 422))

        assertEquals("HTTP 500", LimitsJsonParser.parseApiError("not-json", 500))
    }

    @Test
    fun `parseStats extracts mastery history and disambiguates accuracy`() {
        val json = JSONObject().apply {
            put("username", "ayoub")
            put("display_name", "Ayoub")
            put("total_solved", 5)
            put("accuracy", 0.85)
            put("current_streak", 3)
            put(
                "mastery_by_technique",
                JSONArray().put(
                    JSONObject()
                        .put("technique", "trig")
                        .put("mastery", 0.75)
                        .put("correct", 3)
                        .put("attempts", 4)
                )
            )
            put(
                "recent_history",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("seed", "2BAC-TRIG-L2-ABCDEF12")
                            .put("is_correct", true)
                            .put("full_limit_latex", "\\lim_{x\\to 0}\\frac{\\sin x}{x}")
                    )
                    .put(
                        JSONObject()
                            .put("seed", "2BAC-SQRT-L3-12345678")
                            .put("is_correct", JSONObject.NULL)
                            .put("full_limit_latex", "\\lim_{x\\to +\\infty}x")
                    )
            )
        }

        val stats = LimitsJsonParser.parseStats(json)
        assertEquals("ayoub", stats.username)
        assertEquals("Ayoub", stats.displayName)
        assertEquals(5, stats.totalSolved)
        assertEquals(85, stats.accuracy)
        assertEquals(3, stats.currentStreak)
        assertEquals(1, stats.masteryByTechnique.size)
        assertEquals(75, stats.masteryByTechnique[0].percent)
        assertEquals(2, stats.recentHistory.size)
        assertEquals("trig", stats.recentHistory[0].technique)
        assertEquals(2, stats.recentHistory[0].difficulty)
        assertTrue(stats.recentHistory[0].isCorrect == true)
        assertEquals("sqrt_conjugate", stats.recentHistory[1].technique)
        assertEquals(3, stats.recentHistory[1].difficulty)
        assertNull(stats.recentHistory[1].isCorrect)
    }

    @Test
    fun `parseResume handles missing and populated states safely`() {
        val empty = LimitsJsonParser.parseResume(JSONObject().put("has_exercise", false))
        assertFalse(empty.hasExercise)
        assertNull(empty.exercise)

        val populatedJson = JSONObject().apply {
            put("has_exercise", true)
            put(
                "exercise",
                JSONObject()
                    .put("seed", "2BAC-ADD0-L2-99887766")
                    .put("technique", "add_sub_zero")
                    .put("difficulty", 2)
                    .put("full_limit_latex", "\\lim_{x\\to 1}f(x)")
                    .put("total_hints", 2)
            )
            put(
                "hints",
                JSONArray().put(JSONObject().put("ar", "تلميح").put("fr", "Indice"))
            )
            put("student_answer", "1/2")
        }

        val resume = LimitsJsonParser.parseResume(populatedJson)
        assertTrue(resume.hasExercise)
        assertEquals("2BAC-ADD0-L2-99887766", resume.exercise?.seed)
        assertEquals(1, resume.hints.size)
        assertEquals("تلميح", resume.hints[0].get("ar"))
        assertEquals("1/2", resume.studentAnswer)
    }

    @Test
    fun `historyEntry infers technique and difficulty from seed code`() {
        val h = HistoryEntry(seed = "2BAC-HYBR-L3-ABCDEF1234", isCorrect = false, fullLimitLatex = "")
        assertEquals("hybrid", h.technique)
        assertEquals(3, h.difficulty)
    }
}
