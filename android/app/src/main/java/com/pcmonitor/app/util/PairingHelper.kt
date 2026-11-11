package com.pcmonitor.app.util

object PairingHelper {
    private val CODE_REGEX = Regex("^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$")

    /**
     * Extracts and normalizes the 6-character code from:
     * - Web URL: "https://tidy-newt-65.convex.site/pair?code=ABC234"
     * - Deep Link: "nexus://pair?code=ABC234"
     * - Legacy prefix: "NEXUS-PAIR:ABC234" or "PCMONITOR-PAIR:ABC234"
     * - Plain text: "ABC 234" or "abc-234"
     */
    fun normalizeCode(rawInput: String): String {
        var input = rawInput.trim()

        // 1. If it's a URL or deep link with ?code=
        if (input.contains("code=", ignoreCase = true)) {
            val queryParam = input.substringAfter("code=", "").substringBefore("&")
            if (queryParam.isNotEmpty()) {
                input = queryParam
            }
        }

        // 2. Strip legacy prefixes, whitespace, and hyphens
        return input
            .replace("NEXUS-PAIR:", "", ignoreCase = true)
            .replace("PCMONITOR-PAIR:", "", ignoreCase = true)
            .replace(Regex("[\\s-]"), "")
            .uppercase()
            .filter { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" }
    }

    fun isValidCode(normalizedCode: String): Boolean {
        return CODE_REGEX.matches(normalizedCode)
    }
}
