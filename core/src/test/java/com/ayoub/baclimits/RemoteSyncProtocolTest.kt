package com.ayoub.baclimits

import com.ayoub.baclimits.data.RemoteSyncProtocol
import com.ayoub.baclimits.math.LimitsCasEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncProtocolTest {

    @Test
    fun `normalizeBaseUrl defaults to 51_170_138_59 and cleans suffixes`() {
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl(null))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl(""))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl("   "))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl("51.170.138.59"))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl("http://51.170.138.59/"))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl("http://51.170.138.59/docs"))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl("http://51.170.138.59/redoc/"))
        assertEquals("http://51.170.138.59", RemoteSyncProtocol.normalizeBaseUrl("http://51.170.138.59/openapi.json"))
        assertEquals("https://limits.example.com:8443", RemoteSyncProtocol.normalizeBaseUrl("https://limits.example.com:8443/"))
    }

    @Test
    fun `buildEndpointUrl preserves base and avoids duplicate api prefixes`() {
        assertEquals(
            "http://51.170.138.59/api/login",
            RemoteSyncProtocol.buildEndpointUrl("http://51.170.138.59", "/api/login")
        )
        assertEquals(
            "http://51.170.138.59/api/student/ayoub/resume",
            RemoteSyncProtocol.buildEndpointUrl("http://51.170.138.59/", "/api/student/ayoub/resume")
        )
        assertEquals(
            "http://51.170.138.59/api/bac-limits/exercises/next",
            RemoteSyncProtocol.buildEndpointUrl("http://51.170.138.59/api/bac-limits", "/api/exercises/next")
        )
        assertEquals(
            "http://51.170.138.59/api/health",
            RemoteSyncProtocol.buildEndpointUrl("http://51.170.138.59/api", "/api/health")
        )
    }

    @Test
    fun `distinguishes 10-hex backend seeds from 8-hex local Kotlin CAS seeds across all new tags`() {
        val serverSeed = "2BAC-SQRT-L1-956B2E27A8"
        assertTrue(RemoteSyncProtocol.isServerSeed(serverSeed))
        assertFalse(RemoteSyncProtocol.isLocalKotlinSeed(serverSeed))
        assertFalse(LimitsCasEngine.isValidSeed(serverSeed))

        val lnEx = LimitsCasEngine.generate("ln_limits", 2, chapter = "limits")
        assertTrue(lnEx.seed.contains("-LNLM-"))
        assertTrue(RemoteSyncProtocol.isLocalKotlinSeed(lnEx.seed))
        assertTrue(LimitsCasEngine.isValidSeed(lnEx.seed))

        val seqEx = LimitsCasEngine.generate("seq_arith_geo", 2, chapter = "sequences")
        assertTrue(seqEx.seed.contains("-SQAG-"))
        assertTrue(RemoteSyncProtocol.isLocalKotlinSeed(seqEx.seed))
        assertTrue(LimitsCasEngine.isValidSeed(seqEx.seed))

        val nthEx = LimitsCasEngine.generate("nth_root", 2, chapter = "limits")
        assertTrue(nthEx.seed.contains("-NTHR-"))
        assertTrue(LimitsCasEngine.isValidSeed("2BAC-NROT-L2-1234ABCD"))
        assertFalse(LimitsCasEngine.isValidSeed("INVALID-SEED"))
    }

    @Test
    fun `http status helpers distinguish transient outage from validation errors`() {
        assertTrue(RemoteSyncProtocol.isServerDownStatus(502))
        assertTrue(RemoteSyncProtocol.isServerDownStatus(503))
        assertTrue(RemoteSyncProtocol.isServerDownStatus(504))
        assertFalse(RemoteSyncProtocol.isServerDownStatus(200))
        assertFalse(RemoteSyncProtocol.isServerDownStatus(400))
        assertFalse(RemoteSyncProtocol.isServerDownStatus(404))
        assertFalse(RemoteSyncProtocol.isServerDownStatus(422))

        assertTrue(RemoteSyncProtocol.isRetryableQueueFailure(500))
        assertTrue(RemoteSyncProtocol.isRetryableQueueFailure(503))
        assertTrue(RemoteSyncProtocol.isRetryableQueueFailure(408))
        assertTrue(RemoteSyncProtocol.isRetryableQueueFailure(429))
        assertFalse(RemoteSyncProtocol.isRetryableQueueFailure(400))
        assertFalse(RemoteSyncProtocol.isRetryableQueueFailure(404))
        assertFalse(RemoteSyncProtocol.isRetryableQueueFailure(422))
    }

    @Test
    fun `extractAccuracyPercent handles ratios percentages and fallbacks`() {
        assertEquals(90, RemoteSyncProtocol.extractAccuracyPercent(JSONObject().put("accuracy", 0.9), 9, 10))
        assertEquals(100, RemoteSyncProtocol.extractAccuracyPercent(JSONObject().put("accuracy", 1.0), 5, 5))
        assertEquals(1, RemoteSyncProtocol.extractAccuracyPercent(JSONObject().put("accuracy", 1), 1, 100))
        assertEquals(85, RemoteSyncProtocol.extractAccuracyPercent(JSONObject().put("accuracy", 85), 17, 20))
        assertEquals(0, RemoteSyncProtocol.extractAccuracyPercent(JSONObject().put("accuracy", 0), 0, 5))
        assertEquals(75, RemoteSyncProtocol.extractAccuracyPercent(JSONObject(), 3, 4))
    }
}
