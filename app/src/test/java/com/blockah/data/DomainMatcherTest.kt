package com.blockah.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainMatcherTest {

    private val blockedSet = setOf("reddit.com", "x.com", "twitter.com", "instagram.com")

    @Test
    fun normalizeDomain_cleansVariousFormats() {
        assertEquals("reddit.com", DomainMatcher.normalizeDomain("reddit.com"))
        assertEquals("reddit.com", DomainMatcher.normalizeDomain("https://reddit.com"))
        assertEquals("reddit.com", DomainMatcher.normalizeDomain("https://www.reddit.com/r/all"))
        assertEquals("reddit.com", DomainMatcher.normalizeDomain("http://reddit.com/"))
        assertEquals("reddit.com", DomainMatcher.normalizeDomain("  reddit.com/something?arg=1  "))
        assertEquals("x.com", DomainMatcher.normalizeDomain("https://x.com/home"))
    }

    @Test
    fun findMatch_matchesExactAndSubdomains() {
        assertEquals("reddit.com", DomainMatcher.findMatch("reddit.com", blockedSet))
        assertEquals("reddit.com", DomainMatcher.findMatch("https://reddit.com", blockedSet))
        assertEquals("reddit.com", DomainMatcher.findMatch("https://www.reddit.com", blockedSet))
        assertEquals("reddit.com", DomainMatcher.findMatch("https://old.reddit.com/r/technology", blockedSet))
        assertEquals("reddit.com", DomainMatcher.findMatch("m.reddit.com", blockedSet))
        assertEquals("reddit.com", DomainMatcher.findMatch("reddit.com/r/all", blockedSet))
    }

    @Test
    fun findMatch_matchesOtherBlockedSites() {
        assertEquals("x.com", DomainMatcher.findMatch("https://x.com/username", blockedSet))
        assertEquals("twitter.com", DomainMatcher.findMatch("mobile.twitter.com", blockedSet))
        assertEquals("instagram.com", DomainMatcher.findMatch("https://www.instagram.com/reels/", blockedSet))
    }

    @Test
    fun findMatch_doesNotMatchUnblockedOrTrickySites() {
        assertNull(DomainMatcher.findMatch("google.com", blockedSet))
        assertNull(DomainMatcher.findMatch("github.com", blockedSet))
        assertNull(DomainMatcher.findMatch("wikipedia.org", blockedSet))
        // Should not match partial domain names that are actually different hosts
        assertNull(DomainMatcher.findMatch("notreddit.com", blockedSet))
        assertNull(DomainMatcher.findMatch("reddit.com.attacker.org", blockedSet))
    }
}
