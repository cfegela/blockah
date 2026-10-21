# Blockah 🛑

> Self-block websites. Stay focused. Be healthy.

**Blockah** is a minimalist, privacy-first Android app designed to block distracting websites in Google Chrome without the bloat, battery drain, tracking, subscriptions, and VPN overhead of commercial blockers like BlockSite.

---

## ⚡ Why Blockah instead of BlockSite?

| Feature | BlockSite | Blockah |
| :--- | :--- | :--- |
| **Telemetry / Tracking** | Extensive analytics & tracking | **Zero (100% Offline)** |
| **Permissions** | Internet, VPN, Accounts, History | **No `INTERNET` permission** |
| **Pricing** | Subscription / Paywalls | **100% Free & Open Source** |
| **Battery Impact** | Heavy background services | **Minimal** (sleeps unless Chrome is active) |
| **VPN Slot** | Consumes system VPN slot | **Free** (Accessibility-based) |

---

## 🧠 How It Works

1. **Accessibility Interception**: Blockah registers an [`AccessibilityService`](file:///Users/chrisfegela/git/cfeg/blockah/app/src/main/java/com/blockah/service/BlockahAccessibilityService.kt) scoped specifically to Chrome packages (`com.android.chrome`, `com.chrome.beta`, `com.chrome.dev`, `com.chrome.canary`, `org.chromium.chrome`).
2. **Smart Focus & Autocomplete Awareness**: Blockah detects when you are actively typing in the address bar and ignores suggestions/predictions until navigation is actually committed, preventing premature blocking while typing.
3. **Sub-millisecond URL Check**: When Chrome navigates, Blockah extracts the address bar URL and evaluates it against your local blocklist using [`DomainMatcher`](file:///Users/chrisfegela/git/cfeg/blockah/app/src/main/java/com/blockah/data/DomainMatcher.kt). Subdomains (e.g., `old.reddit.com`, `m.reddit.com`) are automatically matched.
4. **Calming Intervention Screen**: If a blocked destination is visited, Blockah immediately intercepts the navigation with [`BlockedActivity`](file:///Users/chrisfegela/git/cfeg/blockah/app/src/main/java/com/blockah/ui/BlockedActivity.kt) — prompting you to take a breath and providing a single "Return to Chrome" button that safely navigates Chrome away from the blocked URL without closing the browser.

---

## 🚀 Building & Installing

### Prerequisites
- JDK 17+
- Android SDK (API 35)

### Build Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install to a Connected Android Phone
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Quick Setup on Your Phone

1. Open **Blockah** on your device.
2. Tap **"Enable Blockah in Settings"** on the banner.
3. In Android's Accessibility settings, select **Blockah Chrome Interceptor** and toggle it **ON**.
4. Return to Blockah and customize your list of blocked domains (e.g., `reddit.com`, `x.com`, `instagram.com`).
5. Open Chrome and try visiting any blocked site — Blockah will intercept it immediately!

---

## 🧪 Testing

Run the local unit tests:
```bash
./gradlew test
```
