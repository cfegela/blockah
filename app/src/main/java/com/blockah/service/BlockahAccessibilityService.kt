package com.blockah.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blockah.BlockahApp
import com.blockah.ui.BlockedActivity

class BlockahAccessibilityService : AccessibilityService() {

    private var lastBlockedDomain: String? = null
    private var lastBlockedTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val repository = BlockahApp.instance.repository
        if (!repository.isBlockingEnabled.value) return

        val rootNode = rootInActiveWindow ?: return

        val detectedUrl = extractUrlFromNodeTree(rootNode) ?: return
        val matchedDomain = repository.findBlockedMatch(detectedUrl)

        if (matchedDomain != null) {
            val now = System.currentTimeMillis()
            // Debounce rapid repeated events for the same domain
            if (matchedDomain == lastBlockedDomain && (now - lastBlockedTime) < DEBOUNCE_WINDOW_MS) {
                return
            }

            lastBlockedDomain = matchedDomain
            lastBlockedTime = now

            Log.i(TAG, "Blocked destination detected in Chrome: $matchedDomain (from $detectedUrl)")

            // Launch full-screen intervention
            val intent = Intent(this, BlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BlockedActivity.EXTRA_BLOCKED_DOMAIN, matchedDomain)
                putExtra(BlockedActivity.EXTRA_SOURCE_URL, detectedUrl)
                putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, event.packageName?.toString() ?: "com.android.chrome")
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    /**
     * Attempts to find the URL bar in Chrome using known resource IDs,
     * with a fallback traversal of the node hierarchy.
     *
     * Note: We intentionally ignore the address bar if it currently has user input focus
     * (isFocused == true) or has active inline autocomplete selection. This prevents
     * premature interceptions while the user is simply typing keystrokes (e.g. typing 't').
     */
    private fun extractUrlFromNodeTree(root: AccessibilityNodeInfo): String? {
        val focusedInput = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        // 1. Direct lookup by known Chrome address bar view IDs
        for (viewId in KNOWN_URL_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    // If the address bar has input focus, the user is actively typing / viewing suggestions
                    if (node.isFocused) {
                        return null
                    }
                    // If there is an active inline autocomplete selection, ignore it
                    if (node.textSelectionStart != node.textSelectionEnd && node.textSelectionEnd > 0) {
                        return null
                    }
                    // If the focused input is this address bar, ignore
                    if (focusedInput != null && focusedInput == node) {
                        return null
                    }

                    val text = node.text?.toString()
                    if (!text.isNullOrBlank()) {
                        return text
                    }
                }
            }
        }

        // Do not use heuristics if user has an active input focused
        if (focusedInput != null) {
            return null
        }

        // 2. Fallback: Depth-limited search for non-focused address text
        return findUrlByHeuristics(root, depth = 0)
    }

    private fun findUrlByHeuristics(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > MAX_SEARCH_DEPTH) return null
        if (node.isFocused) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        // Ignore suggestion dropdowns, history items, and search tiles
        if (viewId.contains("suggestion") || viewId.contains("omnibox_result") || viewId.contains("tile")) {
            return null
        }

        if (viewId.contains("url_bar") || viewId.contains("location_bar") || viewId.contains("search_box_text")) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlByHeuristics(child, depth + 1)
            if (result != null) return result
        }

        return null
    }

    companion object {
        private const val TAG = "BlockahService"
        private const val DEBOUNCE_WINDOW_MS = 2000L
        private const val MAX_SEARCH_DEPTH = 6

        private val KNOWN_URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.chrome.beta:id/url_bar",
            "com.chrome.dev:id/url_bar",
            "com.chrome.canary:id/url_bar",
            "org.chromium.chrome:id/url_bar"
        )
    }
}
