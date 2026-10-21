package com.blockah.data

import java.net.URI

object DomainMatcher {

    /**
     * Normalizes user-entered domains (e.g., "https://www.reddit.com/r/all" -> "reddit.com").
     */
    fun normalizeDomain(input: String): String {
        var cleaned = input.trim().lowercase()
        cleaned = cleaned.removePrefix("https://").removePrefix("http://")
        cleaned = cleaned.removePrefix("www.")
        cleaned = cleaned.substringBefore('/')
        cleaned = cleaned.substringBefore('?')
        cleaned = cleaned.substringBefore(':')
        return cleaned.trim()
    }

    /**
     * Extracts the host from a raw URL or address bar string.
     */
    fun extractHost(input: String): String {
        var str = input.trim().lowercase()
        if (!str.startsWith("http://") && !str.startsWith("https://")) {
            str = "https://$str"
        }
        return try {
            val uri = URI(str)
            val host = uri.host ?: ""
            host.removePrefix("www.")
        } catch (_: Exception) {
            input.trim().lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore(':')
        }
    }

    /**
     * Checks if a candidate URL matches any domain in the blocked set.
     * Returns the matched domain if blocked, or null if allowed.
     */
    fun findMatch(candidateUrl: String, blockedDomains: Set<String>): String? {
        val cleaned = candidateUrl.trim().lowercase()
        if (cleaned.isBlank() || blockedDomains.isEmpty()) return null

        val host = extractHost(cleaned)

        for (domain in blockedDomains) {
            val lowerDomain = domain.lowercase()

            // 1. Exact host match (e.g. host "reddit.com" == "reddit.com")
            if (host == lowerDomain) return domain

            // 2. Subdomain match (e.g. host "old.reddit.com" ends with ".reddit.com")
            if (host.endsWith(".$lowerDomain")) return domain

            // 3. Fallback for address bar texts with protocol: "https://twitter.com/..."
            if (cleaned.contains("://$lowerDomain") || cleaned.contains("://www.$lowerDomain")) {
                return domain
            }

            // 4. Fallback for address bar texts starting with domain: "reddit.com/r/..."
            if (cleaned.startsWith("$lowerDomain/") || cleaned.startsWith("www.$lowerDomain/")) {
                return domain
            }
        }

        return null
    }
}
