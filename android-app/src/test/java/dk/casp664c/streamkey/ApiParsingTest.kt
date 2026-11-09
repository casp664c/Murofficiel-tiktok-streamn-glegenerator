package dk.casp664c.streamkey

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiParsingTest {
    @Test fun parsesLoginResponses() {
        val json = JSONObject("""{"user":{"id":"u1","username":"cas"},"token":"abc","expiresAt":1}""")
        assertEquals(ApiSession("abc", "cas"), ApiParsing.session(json))
    }

    @Test fun parsesProfilesWithoutExposingKeys() {
        val json = JSONObject(
            """{"id":"p1","name":"Hjemme","server":"rtmps://live.example.com/app","hasKey":true}"""
        )
        assertEquals(RemoteProfile("p1", "Hjemme", "rtmps://live.example.com/app", true), ApiParsing.profile(json))
    }

    @Test fun mapsServerErrorsToDanishMessages() {
        assertTrue(ApiParsing.errorMessage(401, "INVALID_CREDENTIALS").contains("Forkert"))
        assertTrue(ApiParsing.errorMessage(429, "RATE_LIMITED").contains("Vent"))
        assertTrue(ApiParsing.errorMessage(503, "KEY_STORAGE_DISABLED").contains("nøglelagring"))
        assertTrue(ApiParsing.errorMessage(500, null).contains("HTTP 500"))
    }

    @Test fun backendClientRequiresHttps() {
        assertFalse(BackendProbe.isValidHttpsBase("http://example.com"))
        try {
            BackendClient("http://example.com")
            throw AssertionError("Expected an IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
