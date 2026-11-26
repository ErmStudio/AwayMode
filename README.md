# AwayMode 0.2.2

Android app (Java, no third-party runtime libraries) for Wi-Fi-based INCY VPN automation.

## What's new in 0.2.2
- One searchable network list combining nearby scan results, the currently connected network, and networks already configured in AwayMode.
- Each network has a three-way selector: Ignore / VPN off (white list) / VPN on (black list).
- Moving a network to one mode automatically removes it from the other mode.
- Wi-Fi scan refresh and manual SSID entry.
- Location and notification permissions are explained and requested on first launch.
- Automation is controlled by one switch.

## Important Android limitation
Ordinary Android apps cannot enumerate the full list of Wi-Fi networks saved by the system. Android 10+ returns an empty list from `WifiManager.getConfiguredNetworks()` for normal apps. AwayMode therefore shows networks visible in a nearby scan, the current connection, and networks already saved inside AwayMode. A saved network that is not nearby remains in the AwayMode list.

Wi-Fi scan results require location permission and, depending on Android/HyperOS, the phone's system Location setting to be enabled. Android may throttle scans and return cached results.

## Automation rules
- White list: send `incy://disconnect`.
- Black list: send `incy://connect`.
- Ignore/unknown network: do nothing.
- Leaving a white-list network: wait 12 seconds and re-check the current network before requesting VPN connect.
- Leaving a black-list network: do nothing.

## Build
Push the project contents to the root of the GitHub repository, including `.github`, or run **Actions → Build AwayMode 0.2.2 APK → Run workflow**. Download artifact `awaymode-0.2.2-debug-apk` and install `app-debug.apk`.

This is a debug build for local testing. Android/HyperOS may restrict launching INCY from a background service; successful intent dispatch does not itself prove the VPN state changed. Verify INCY's actual status.
