package com.blockah.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BlocklistRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _blockedDomains = MutableStateFlow<Set<String>>(loadBlockedDomains())
    val blockedDomains: StateFlow<Set<String>> = _blockedDomains.asStateFlow()

    private val _isBlockingEnabled = MutableStateFlow(loadIsBlockingEnabled())
    val isBlockingEnabled: StateFlow<Boolean> = _isBlockingEnabled.asStateFlow()

    private fun loadBlockedDomains(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_DOMAINS, DEFAULT_BLOCKED_DOMAINS)
            ?: DEFAULT_BLOCKED_DOMAINS
    }

    private fun loadIsBlockingEnabled(): Boolean {
        return prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
    }

    fun addDomain(rawInput: String): Boolean {
        val normalized = DomainMatcher.normalizeDomain(rawInput)
        if (normalized.isBlank() || !normalized.contains(".")) return false

        val updated = _blockedDomains.value.toMutableSet().apply {
            add(normalized)
        }
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, updated).apply()
        _blockedDomains.value = updated
        return true
    }

    fun removeDomain(domain: String) {
        val updated = _blockedDomains.value.toMutableSet().apply {
            remove(domain)
        }
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, updated).apply()
        _blockedDomains.value = updated
    }

    fun setBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, enabled).apply()
        _isBlockingEnabled.value = enabled
    }

    /**
     * Checks if a detected URL or text from Chrome matches any blocked domain.
     * Returns the matched domain if blocked, or null if allowed.
     */
    fun findBlockedMatch(urlOrText: String): String? {
        if (!_isBlockingEnabled.value) return null
        return DomainMatcher.findMatch(urlOrText, _blockedDomains.value)
    }

    companion object {
        private const val PREFS_NAME = "blockah_prefs"
        private const val KEY_BLOCKED_DOMAINS = "blocked_domains"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"

        val DEFAULT_BLOCKED_DOMAINS = setOf(
            "reddit.com",
            "x.com",
            "twitter.com",
            "instagram.com",
            "tiktok.com"
        )
    }
}
