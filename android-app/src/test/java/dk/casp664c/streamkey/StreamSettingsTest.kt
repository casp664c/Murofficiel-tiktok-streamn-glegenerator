package dk.casp664c.streamkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSettingsTest {
    @Test fun acceptsValidRtmpAndRtmpsServers() {
        assertTrue(StreamSettings.validServer("rtmp://example.org/live"))
        assertTrue(StreamSettings.validServer("rtmps://example.org/live"))
    }

    @Test fun rejectsInvalidAndUnsafeAddresses() {
        assertFalse(StreamSettings.validServer("https://example.org/live"))
        assertFalse(StreamSettings.validServer("rtmps://"))
        assertFalse(StreamSettings.validServer("rtmp://user:pass@example.org/app"))
        assertFalse(StreamSettings.validServer("rtmp://example.org/app\nsecret"))
    }

    @Test fun keyValidationAvoidsMultiLinePastes() {
        assertFalse(StreamSettings.validKey(""))
        assertFalse(StreamSettings.validKey("secret\nother"))
        assertTrue(StreamSettings.validKey("abc-123"))
    }

    @Test fun combinesOnlySimpleValidAddresses() {
        assertEquals("rtmps://example.org/live/abc",
            StreamSettings.combinedUrl("rtmps://example.org/live/", "abc"))
        assertNull(StreamSettings.combinedUrl("rtmps://example.org/live?test=1", "abc"))
        assertNull(StreamSettings.combinedUrl("https://example.org/live", "abc"))
    }
}
