# AwayMode 0.2.3

Android app (Java, no third-party runtime libraries) for Wi-Fi-based INCY VPN automation.

## What's new in 0.2.3
- Network list shows only networks configured in AwayMode and the currently connected network; nearby scan results are no longer mixed in.
- Quick action to add the currently connected network, plus manual SSID entry.
- Wi-Fi loss is detected through Android's default-network callback. After 3 seconds, AwayMode checks the active connection and enables INCY when leaving a trusted network.
- Version code 5; existing settings are preserved on update.

## Android Wi-Fi limitation
Ordinary Android apps cannot enumerate the full list of Wi-Fi networks saved by the system. Android 10+ restricts `WifiManager.getConfiguredNetworks()` for normal apps. Add a saved network to AwayMode by connecting to it and tapping **Добавить текущую**, or enter its exact SSID manually. AwayMode stores its rules between app updates.

## Automation rules
- White list: send `incy://disconnect`.
- Black list: send `incy://connect`.
- Ignore/unknown network: do nothing.
- Leaving a white-list network: wait 3 seconds, check the active network again, then request VPN connect if Wi-Fi is gone or the phone is on another network.
- Leaving a black-list network: do nothing.

Android may restrict launching INCY from a background service. Successful intent dispatch does not prove the VPN state changed; check INCY's actual status. On Xiaomi/HyperOS, allow AwayMode background activity and disable its battery restrictions.

## Fast local APK build
Install Android Studio once and open this project. Let Gradle sync, then select **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Android Studio shows a notification with **Locate** when the APK is ready. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and install it; future builds from this computer can update that app while keeping its settings. No GitHub upload or download is needed.

The first local install may conflict with a copy installed from GitHub Actions because Actions signs debug APKs with a different key. Uninstall the old copy once before installing the first local build. After that, local debug APKs from this computer use the same debug signing key and install as updates. The project itself is small; Android Studio and Android SDK are the one-time setup.

GitHub Actions remains available: run **Actions → Build AwayMode 0.2.3 APK → Run workflow** and download artifact `awaymode-0.2.3-debug-apk`. Actions debug APKs may not update each other because the signing key is temporary.
