# AwayMode 0.2.7

Android app (Java, no third-party runtime libraries) for Wi-Fi-based INCY VPN automation.

## Changes in 0.2.5
- Network actions are labelled **Выключить VPN** and **Включить VPN**.
- Added networks and selected rules are listed; empty search results no longer add a misleading placeholder card.
- Added an in-app event log showing the installed app version, network decisions, INCY deep-link requests, update checks, and installation results.
- Added **Проверить обновление**. It checks the latest public GitHub Release, downloads its APK, and opens Android's package installer. Android still asks the user to confirm installation.
- No Wi-Fi means request INCY connect for mobile data. Unknown Wi-Fi SSIDs remain unchanged.

## Changes in 0.2.6
- VPN requests are deduplicated by network and rule, so the same `connect` request is sent again when moving from a Wi-Fi network to mobile data.
- Changing a network rule while connected schedules a fresh evaluation.
- Added a share button for the event log. The log is only handed to an app and recipient chosen by the user; it includes Wi-Fi network names.

## Changes in 0.2.7
- Fixed in-app update installation by closing the APK output stream before committing the Android package installer session.

## Android limitation
Android does not let ordinary apps read the phone's complete saved Wi-Fi list. Add a network manually or connect to it and tap **Добавить текущую**. AwayMode keeps its configured list and rules across normal app updates.

## GitHub build and in-app updates
Push to `main` (or run the workflow manually). GitHub Actions builds a signed APK and publishes it as a GitHub Release. In AwayMode tap **Проверить обновление** to download it. The Android installer opens for confirmation; Android does not allow a normal app to silently install APKs.

The release APK must always use the same signing key or Android will reject updates. This checkout's persistent key is stored outside the repository at `%USERPROFILE%\.android\awaymode-release.p12`; keep a secure backup of it. The four GitHub Actions secrets are already configured for this repository. To create a replacement key on another machine, use JDK 17's `keytool`:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android" | Out-Null
keytool -genkeypair -v -keystore "$env:USERPROFILE\.android\awaymode-release.p12" -storetype PKCS12 -alias awaymode -keyalg RSA -keysize 3072 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\awaymode-release.p12")) | Set-Clipboard
```

Keep the keystore and passwords backed up securely. In the GitHub repository, open **Settings → Secrets and variables → Actions** and create these repository secrets:

- `AWAYMODE_KEYSTORE_BASE64` — paste the Base64 value copied by the command above.
- `AWAYMODE_KEYSTORE_PASSWORD` — the keystore password.
- `AWAYMODE_KEY_ALIAS` — `awaymode`.
- `AWAYMODE_KEY_PASSWORD` — the key password.

The workflow builds and publishes signed APKs after these secrets are configured. Since older APKs were signed with temporary GitHub debug keys, the first install of the new stable-signature release requires uninstalling the old app once. Android removes app data on uninstall, so re-enter the network rules after that one-time migration. Future releases can update over the installed app and retain its settings.

## Build without Android Studio
Android Studio is optional. Local builds need JDK 17, Gradle 8.9, Android SDK platform 35 and Build Tools 35.0.0. GitHub Actions already installs these tools, so the usual flow needs no local Android SDK or APK download.
