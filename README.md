# Pocket Gameplay

A lightweight Android app for playing locally stored HTML5 games.

Pocket Gameplay opens games from a selected folder or imports a ZIP archive into a private on-device library. It discovers HTML entry points rather than assuming every game uses `index.html`, then serves the chosen file and its sibling assets through AndroidX WebKit.

## Current scope

- Native Android application written in Kotlin.
- Open a game folder through Android's Storage Access Framework.
- Import ZIP archives into app-private storage.
- Recursive discovery of `.html` and `.htm` entry points, including non-standard filenames.
- Explicit entry-point selection when multiple HTML files are found.
- Persistent library for imported ZIP games and saved folder references, with play and remove actions.
- Persisted folder permissions allow reopening saved folder games after app restart; removing a folder entry never deletes source files.
- Local resource loading with `WebViewAssetLoader`; no `file://` URLs.
- Separate local web origins for each game, isolating browser-managed storage such as `localStorage` and IndexedDB.
- ZIP extraction safeguards for traversal, duplicate paths, and uncompressed size limits.

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

The app only reads files inside a folder explicitly selected by the user or files extracted into its app-private directory. WebView's direct file and content access remain disabled. Game resources are exposed through a custom `WebViewAssetLoader.PathHandler`, with path validation at the folder boundary. ZIP archives are treated as untrusted input and extracted only into bounded, private staging directories. The app intentionally has no general internet permission.
