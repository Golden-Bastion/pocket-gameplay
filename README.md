# Pocket Gameplay

A lightweight Android app for playing locally stored HTML5 games.

The first development slice focuses on opening a game directly from a selected folder. It discovers HTML entry points instead of assuming every game uses `index.html`, then serves the chosen file and its sibling assets through AndroidX WebKit.

## Current scope

- Native Android application written in Kotlin.
- User-selected game folders through Android's Storage Access Framework.
- Recursive discovery of `.html` and `.htm` files, including non-standard entry-point names.
- Explicit entry-point selection when multiple HTML files are found.
- Local resource loading with `WebViewAssetLoader`; no `file://` URLs.
- ZIP import and a persistent game library are planned follow-up work.

## Build

The GitHub Actions workflow installs Gradle 8.13 and Android SDK 36, then runs:

```sh
gradle testDebugUnitTest assembleDebug
```

To create a Gradle Wrapper for local development on a machine with Gradle installed:

```sh
gradle wrapper --gradle-version 8.13
```

Then use `./gradlew` for subsequent builds.

## Compatibility

Games run inside the Android System WebView installed on the device. Compatibility depends on the device's WebView version and on the browser APIs used by the game. This project targets ordinary HTML5 games; it does not emulate Flash or desktop browser runtimes.

## Security model

The app only reads files inside the folder explicitly selected by the user. WebView's direct file and content access remain disabled. Game resources are exposed through a custom `WebViewAssetLoader` path handler, with path validation at the folder boundary. The app intentionally has no general internet permission.
