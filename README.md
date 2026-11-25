# AwayMode — INCY deep-link test

A tiny Android test app named AwayMode. It sends the documented INCY deep-link intents:
- `incy://connect`
- `incy://disconnect`

This is only a foreground command test. It does not yet include background automation, Wi-Fi monitoring, accessibility, root, or analytics. A successful Android `startActivity()` call only means the intent was handed off; verify the VPN state inside INCY.

## Build without Android Studio (GitHub Actions)

1. Upload the contents of this project folder to the root of your GitHub repository. Include the hidden `.github` folder.
2. Open the repository's **Actions** tab and allow workflows if GitHub asks.
3. Select **Build Android APK** → **Run workflow** (or push to the `main` branch).
4. When the run finishes, open it and download the artifact **awaymode-debug-apk**.
5. Unzip it and install `app-debug.apk` on the phone. Android may ask you to allow installation from the browser/file manager.

The APK is a debug build intended for this local test, not a Play Store release.
