package com.blockah.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blockah.BlockahApp
import com.blockah.ui.BlockedActivity

class BlockahAccessibilityService : AccessibilityService() {

    private var lastBlockedDomain: String? = null
    private var lastBlockedTime: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isClosingTab = false
    private var pendingLaunchIntent: Intent? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rootNode = rootInActiveWindow ?: return

        // If we have initiated tab closure for a blocked site, check if the "Close tab" action is available
        if (isClosingTab) {
            if (tryClickCloseTab(rootNode)) {
                Log.i(TAG, "Successfully clicked Close Tab button!")
                isClosingTab = false
                launchBlockedActivityWithDelay()
                return
            }
        }

        val repository = BlockahApp.instance.repository
        if (!repository.isBlockingEnabled.value) return

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

            val packageName = event.packageName?.toString() ?: "com.android.chrome"

            val intent = Intent(this, BlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BlockedActivity.EXTRA_BLOCKED_DOMAIN, matchedDomain)
                putExtra(BlockedActivity.EXTRA_SOURCE_URL, detectedUrl)
                putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, packageName)
            }
            pendingLaunchIntent = intent
            isClosingTab = true

            // Trigger tab closure:
            // Find tab_switcher_button and trigger long-click to open "Close tab" menu
            val tabSwitcher = findTabSwitcherButton(rootNode)
            if (tabSwitcher != null) {
                var longClicked = tabSwitcher.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                Log.i(TAG, "tab_switcher performAction(ACTION_LONG_CLICK): $longClicked")

                if (!longClicked) {
                    val bounds = Rect()
                    tabSwitcher.getBoundsInScreen(bounds)
                    val x = bounds.centerX().toFloat()
                    val y = bounds.centerY().toFloat()
                    if (x > 0 && y > 0) {
                        val path = Path().apply { moveTo(x, y) }
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
                            .build()
                        dispatchGesture(gesture, null, null)
                        Log.i(TAG, "Dispatched long-press gesture to tab_switcher at ($x, $y)")
                    }
                }

                // Check if the menu is already rendered in the active window
                rootInActiveWindow?.let { currentRoot ->
                    if (tryClickCloseTab(currentRoot)) {
                        Log.i(TAG, "Immediately found and clicked Close Tab!")
                        isClosingTab = false
                        launchBlockedActivityWithDelay()
                        return
                    }
                }
            } else {
                Log.w(TAG, "Could not find tab_switcher_button, falling back to Back navigation")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            // Fallback safety timeout: If menu doesn't complete within 500ms, launch anyway
            mainHandler.postDelayed({
                if (isClosingTab && pendingLaunchIntent != null) {
                    Log.w(TAG, "Close tab timed out, ensuring Back navigation and launching intervention")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    isClosingTab = false
                    launchBlockedActivityImmediate()
                }
            }, TAB_CLOSE_TIMEOUT_MS)
        }
    }

    private fun launchBlockedActivityWithDelay() {
        mainHandler.postDelayed({
            launchBlockedActivityImmediate()
        }, INTERVENTION_DELAY_MS)
    }

    private fun launchBlockedActivityImmediate() {
        val intent = pendingLaunchIntent ?: return
        pendingLaunchIntent = null
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity", e)
        }
    }

    private fun findTabSwitcherButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in KNOWN_TAB_SWITCHER_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                return nodes[0]
            }
        }
        return findTabSwitcherByHeuristic(root, 0)
    }

    private fun findTabSwitcherByHeuristic(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_SEARCH_DEPTH) return null
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("tab_switcher_button")) {
            return node
        }
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("tabs") && (node.isClickable || node.isLongClickable)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findTabSwitcherByHeuristic(child, depth + 1)
            if (result != null) return result
        }
        return null
    }

    private fun tryClickCloseTab(root: AccessibilityNodeInfo): Boolean {
        // 1. Direct text search for "Close tab"
        val textNodes = root.findAccessibilityNodeInfosByText("Close tab")
        if (!textNodes.isNullOrEmpty()) {
            for (node in textNodes) {
                if (clickNodeOrParent(node)) {
                    return true
                }
            }
        }

        // 2. Direct lookup for Chrome's menu item text ID
        for (pkg in KNOWN_BROWSER_PACKAGES) {
            val itemNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/menu_item_text")
            if (!itemNodes.isNullOrEmpty()) {
                for (node in itemNodes) {
                    if (node.text?.toString()?.equals("Close tab", ignoreCase = true) == true) {
                        if (clickNodeOrParent(node)) {
                            return true
                        }
                    }
                }
            }
        }

        // 3. Fallback: Search in Tab Switcher for close button (action_button)
        for (pkg in KNOWN_BROWSER_PACKAGES) {
            val actionButtons = root.findAccessibilityNodeInfosByViewId("$pkg:id/action_button")
            if (!actionButtons.isNullOrEmpty()) {
                for (btn in actionButtons) {
                    val desc = btn.contentDescription?.toString()?.lowercase() ?: ""
                    if (desc.startsWith("close ") && desc.endsWith(" tab")) {
                        if (btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var curr = node
        while (curr != null) {
            if (curr.isClickable) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            curr = curr.parent
        }
        return false
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
        private const val INTERVENTION_DELAY_MS = 100L
        private const val TAB_CLOSE_TIMEOUT_MS = 500L
        private const val MAX_SEARCH_DEPTH = 6

        private val KNOWN_URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.chrome.beta:id/url_bar",
            "com.chrome.dev:id/url_bar",
            "com.chrome.canary:id/url_bar",
            "org.chromium.chrome:id/url_bar"
        )

        private val KNOWN_TAB_SWITCHER_IDS = listOf(
            "com.android.chrome:id/tab_switcher_button",
            "com.chrome.beta:id/tab_switcher_button",
            "com.chrome.dev:id/tab_switcher_button",
            "com.chrome.canary:id/tab_switcher_button",
            "org.chromium.chrome:id/tab_switcher_button"
        )

        private val KNOWN_BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.chromium.chrome"
        )
    }
}
