# Pocket Gameplay

A lightweight Android app for playing locally stored HTML5 games.

Pocket Gameplay opens games from a selected folder or imports a ZIP archive into a private on-device library. It discovers HTML entry points rather than assuming every game uses `index.html`, then serves the chosen file and its sibling assets through AndroidX WebKit.

## Current scope

- Native Android application written in Kotlin.
- Open a game folder through Android's Storage Access Framework.
- Import ZIP archives into app-private storage.
- Recursive discovery of `.html` and `.htm` entry points, including non-standard filenames.
- Explicit entry-point selection when multiple HTML files are found.
- Persistent library for imported ZIP games and saved folder references, with search, A–Z/recent sorting, renaming, details, recovery actions, and explicit remove semantics.
- Per-game WebView profiles and per-game data clearing on System WebView versions that support AndroidX WebKit multi-profile APIs; legacy saves can be explicitly isolated.
- Persisted folder permissions allow reopening saved folder games after app restart; removing a folder entry never deletes source files.
- Local game favicons are discovered from the selected HTML page and cached in app-private storage for offline library thumbnails.
- Compact library cards keep Play visible and group secondary game actions in an overflow menu.
- Local resource loading with `WebViewAssetLoader`; no `file://` URLs.
- Separate local web origins for each game, isolating browser-managed storage such as `localStorage` and IndexedDB.
- ZIP extraction safeguards for traversal, duplicate paths, and uncompressed size limits.
- Optional in-game developer console powered by locally bundled Eruda 3.4.3.
- Fixed native player toolbar with one-tap library exit and reload actions, a visible game title, and an overflow menu for immersive fullscreen and the developer console.
- WebView HTML fullscreen and fullscreen media custom-view handling, including restoration of window and system UI state.
- Back navigation prioritizes dismissing player menus and exiting fullscreen modes before game history or returning to the library.
- Explicit MIME handling for common HTML5 assets, including modules, WebAssembly, fonts, audio and video.
- Single byte-range responses for local media seeking when the storage provider reports a reliable file size.
- SAF-backed file inputs and user-selected export of files inside the current game folder.
- A manual offline HTML5 runtime compatibility fixture under `test-fixtures/html5-compatibility/`.

## Build

The GitHub Actions workflow installs Gradle 8.13 and Android SDK 36, then runs:

```sh
gradle testDebugUnitTest assembleDebug
```

To create a Gradle Wrapper for local development on a machine with Gradle installed:

```sh
gradle wrapper --gradle-version 8.13
```

Then use `./gradlew` for subsequent builds. The first build on a clean workspace also downloads the pinned Eruda package from npm and verifies its SHA-512 integrity before packaging the script and MIT notice into the APK. No network access is needed to use the developer console at runtime.

GitHub Actions restores a shared Gradle cache for pull requests. Trusted builds on `main` refresh that cache after merges, and a scheduled/manual warm-up keeps it populated. The Eruda package archive is cached separately. See [CI caching](docs/ci-caching.md) for the cache scopes and security model.

See [Android releases](docs/releasing.md) for version tags, manual build/release dispatch, signing-key setup, and GitHub Release assets.

See [the developer console guide](docs/developer-console.md) for its behavior and security boundaries.

See [playback controls and fullscreen](docs/player-controls.md) for the control surface, Back priority, fullscreen lifecycle, and rotation behavior.

See [HTML5 runtime compatibility](docs/html5-runtime-compatibility.md) for MIME handling, media seeking, file inputs, downloads, and the device test fixture.

See the [physical-device acceptance checklist](docs/device-validation.md) before considering the offline player baseline fully validated.

See [library management and browser data](docs/library-and-browser-data.md) for rename/search/sort behavior, recovery, per-game save isolation, and data removal.

See [visual identity and design tokens](docs/visual-identity.md) for the app's palette, component styling, and brand principles.

## Compatibility

Games run inside the Android System WebView installed on the device. Compatibility depends on the device's WebView version and on the browser APIs used by the game. This project targets ordinary HTML5 games; it does not emulate Flash or desktop browser runtimes.

## Security model

The app only reads files inside a folder explicitly selected by the user or files extracted into its app-private directory. WebView's direct file and content access remain disabled. Game resources are served by a request-aware local file handler with path validation at the folder boundary. App-owned developer assets use `WebViewAssetLoader`. ZIP archives are treated as untrusted input and extracted only into bounded, private staging directories. The app intentionally has no general internet permission.
