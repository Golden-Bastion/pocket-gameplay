# Architecture

## Runtime

Pocket Gameplay is a native Kotlin Android application. Games execute in the Android System WebView; the app is a local game launcher, not an independent browser engine.

## Folder access

The user selects a game directory through the Storage Access Framework. The app requests and persists read-only access to that tree. It does not request broad storage permissions and does not copy or reorganize a selected game's files.

## Entry-point discovery

The app searches the selected tree for `.html` and `.htm` files. Likely root entry points are sorted toward the top of the chooser, but filenames are not assumed: the user can select a game page named after the game or any other discovered HTML file.

Scanning is bounded by a maximum depth and number of visited entries so unusually large directory trees cannot cause unbounded work.

## Local resource serving

A custom AndroidX `WebViewAssetLoader.PathHandler` resolves URL path segments beneath the selected directory and opens the corresponding document through `ContentResolver`. Relative references retain their normal web behavior:

```html
<script src="js/game.js"></script>
<img src="assets/player.png">
```

Each game is loaded from a distinct HTTPS-style host generated beneath the reserved `appassets.androidplatform.net` domain, with `WebViewAssetLoader.Builder.setDomain`. The origin key is stable for an imported game and deterministically derived from a selected folder's Storage Access Framework URI. This keeps browser-managed origin storage isolated between games.

The selected entry point is loaded from `https://<game-origin>/game/<entry-point>`. A second path route maps root-relative URLs such as `/assets/player.png` to the selected game folder, which is how a conventional local web server behaves.

Missing or invalid local paths return a 404 response rather than falling through to the network.

## WebView security

- JavaScript and DOM storage are enabled because ordinary HTML5 games require them.
- Direct `file://` and `content://` access is disabled.
- File-URL cross-origin access is disabled.
- Mixed content is blocked.
- The app does not declare the Android INTERNET permission.
- No JavaScript bridge exposes native app APIs to game code.

Games are untrusted content. WebView provides the rendering/runtime environment, but games requiring unsupported APIs or desktop-only browser behavior may not work.

## HTML5 runtime and media compatibility

`GameAssetMimeTypes` maps common HTML5 assets, fonts, images, audio and video extensions to explicit MIME types. `GamePathHandler` supports `GET` and `HEAD`; valid single byte ranges return `206 Partial Content` with `Content-Range` and a bounded stream, while unsatisfiable ranges return `416`. Multiple or malformed ranges are ignored and served as full responses. If a storage provider cannot report a reliable size, byte ranges are not advertised for that resource.

HTML file inputs are handled through Android's Storage Access Framework document picker. Only readable `content://` URIs selected by the user are returned to WebView, without persisting per-file grants. Local game-file downloads are handled through `ACTION_CREATE_DOCUMENT` and require the user to choose a save destination. External, `blob:`, and `data:` downloads are rejected rather than enabling network access or adding a JavaScript bridge.

The manual fixture in `test-fixtures/html5-compatibility` exercises browser APIs and local media on an actual device. Device WebView version, codecs and hardware still determine final compatibility.

## Playback controls and fullscreen

The WebView is hosted in a root `FrameLayout` with a small always-visible Controls button and an initially collapsed native control panel. The panel provides Library, Reload, Fullscreen, Dev, and Close actions. Controls are transient so ordinary play is not obstructed.

App immersive fullscreen hides system bars and the native player chrome; Back restores them. HTML fullscreen and fullscreen media are handled through `WebChromeClient.onShowCustomView` / `onHideCustomView`. The supplied custom view is added above the WebView and then removed when fullscreen ends, while the prior window fullscreen flag and system UI visibility are restored.

Back priority is centralized in `PlayerBackResolver` and covered with JVM tests: collapse controls, exit WebView fullscreen, exit app immersive fullscreen, navigate WebView history, return to the library, then normal Activity behavior.

The Activity handles orientation and related screen/keyboard configuration changes without recreation. On a change it requests layout and invalidates the existing WebView rather than reloading the game.

## Developer console

An optional native **Dev** control injects the pinned Eruda bundle into the active game only when requested. The bundle is built into the APK and served through a reserved `/__pocket/` path on the current per-game origin. The route is handled by `WebViewAssetLoader.AssetsPathHandler` against the app's root asset directory, not by the user-selected game folder.

The game origin remains isolated from other games. Eruda's own floating entry button is hidden; the native player button controls its visibility. Injection is lazy and idempotent within a page. The developer console uses the game's JavaScript realm and is not a security boundary: game code can inspect or interfere with the injected tool.

The build downloads the exact Eruda 3.4.3 npm archive and checks its SHA-512 integrity before extracting the browser bundle and including its MIT notice. Runtime debugging is offline and does not add the Android INTERNET permission.

## Library records

The library can contain two storage types. Imported games point to an app-private extracted directory. Folder-backed games store a persisted Storage Access Framework tree URI and a relative HTML entry point; they never own or delete the original directory. The versioned metadata reader continues to interpret version-1 entries as imported games while version 2 adds storage type and source URI fields.

Folder game IDs are deterministic for a tree URI plus entry point. Different entry points in the same folder are independent shortcuts. A persisted URI read grant is released only after the last library item using that tree URI is removed.
