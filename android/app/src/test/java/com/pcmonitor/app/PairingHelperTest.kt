package com.pcmonitor.app

import com.pcmonitor.app.util.PairingHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingHelperTest {

    @Test
    fun normalizeCode_extractsFromWebUrl() {
        val url = "https://tidy-newt-65.convex.site/pair?code=ABC234"
        val code = PairingHelper.normalizeCode(url)
        assertEquals("ABC234", code)
        assertTrue(PairingHelper.isValidCode(code))
    }

    @Test
    fun normalizeCode_extractsFromDeepLink() {
        val deepLink = "nexus://pair?code=XYZ789"
        val code = PairingHelper.normalizeCode(deepLink)
        assertEquals("XYZ789", code)
        assertTrue(PairingHelper.isValidCode(code))
    }

    @Test
    fun normalizeCode_stripsNexusPrefix() {
        val raw = "NEXUS-PAIR:DEF456"
        val code = PairingHelper.normalizeCode(raw)
        assertEquals("DEF456", code)
        assertTrue(PairingHelper.isValidCode(code))
    }

    @Test
    fun normalizeCode_stripsLegacyPcMonitorPrefix() {
        val raw = "PCMONITOR-PAIR:JKL234"
        val code = PairingHelper.normalizeCode(raw)
        assertEquals("JKL234", code)
        assertTrue(PairingHelper.isValidCode(code))
    }

    @Test
    fun normalizeCode_handlesWhitespaceAndHyphens() {
        val raw = "  abc - 234  "
        val code = PairingHelper.normalizeCode(raw)
        assertEquals("ABC234", code)
        assertTrue(PairingHelper.isValidCode(code))
    }

    @Test
    fun isValidCode_rejectsInvalidLengthsOrCharacters() {
        assertFalse(PairingHelper.isValidCode("ABC")) // too short
        assertFalse(PairingHelper.isValidCode("ABC2345")) // too long
        assertFalse(PairingHelper.isValidCode("ABC123")) // '1' is excluded (ambiguous with I)
        assertFalse(PairingHelper.isValidCode("ABCO23")) // 'O' is excluded (ambiguous with 0)
    }
}
