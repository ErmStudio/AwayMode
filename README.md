# AwayMode 2.0 — Wi-Fi automation test

Android app (Java, no third-party runtime libraries) for testing Wi-Fi-based INCY VPN automation.

## Rules implemented

- Wi-Fi in the **white list**: send `incy://disconnect`.
- Wi-Fi in the **black list**: send `incy://connect`.
- Unknown Wi-Fi: do nothing, except when just leaving a white-list network.
- Leaving a white-list Wi-Fi (to mobile data, no Wi-Fi, or an unknown Wi-Fi): wait 12 seconds, then send `incy://connect`.
- Leaving a black-list Wi-Fi: do nothing.
- A network cannot be in both lists; adding it to one removes it from the other.
- Lists are stored locally in app preferences. Android does not let ordinary apps enumerate the full saved Wi-Fi network list, so add the currently connected network or type an SSID manually.

## Important test limitations

This is a **background-behaviour proof of concept**, not a claim that every Android/HyperOS version will allow fully silent VPN switching. Android restricts launching another app's Activity from the background. AwayMode runs a foreground service and attempts the documented INCY deep link; the service notification also contains an **Open INCY** action as a user-tap fallback. A successful `startActivity()` call means Android accepted the intent, not that INCY definitely changed VPN state. Verify the actual VPN state during testing.

The service is started manually from the app in this test build. Automatic restart after phone reboot, advanced Xiaomi autostart handling, and a reliable in-app readback of INCY state are not implemented yet.

## Permissions

- Location permission is requested because Android gates access to the connected Wi-Fi SSID behind location permission on many versions. Depending on Android/HyperOS, system Location may also need to be enabled.
- Notification permission is requested on Android 13+ so the foreground monitoring notification is visible.

## Build without Android Studio

1. Copy the contents of this project to the root of the GitHub repository, including the hidden `.github` directory.
2. Push to `main` (or run **Actions → Build Android APK → Run workflow**).
3. Download artifact `awaymode-v2-debug-apk` from the completed workflow.
4. Unzip and install `app-debug.apk`.

Debug APK is for local testing, not Play Store distribution.
