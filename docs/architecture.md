# Architecture

## Runtime

Pocket Gameplay is a native Kotlin Android application. Games execute in the Android System WebView; the app is a local game launcher, not an independent browser engine.

## Folder access

The user selects a game directory through the Storage Access Framework. The app requests and persists read-only access to that tree. It does not request broad storage permissions and does not copy or reorganize a selected game's files.

## Entry-point discovery

The app searches the selected tree for `.html` and `.htm` files. Likely root entry points are sorted toward the top of the chooser, but filenames are not assumed: the user can select a game page named after the game or any other discovered HTML file.

Scanning is bounded by a maximum depth and number of visited entries so unusually large directory trees cannot cause unbounded work.

## Local resource serving

The request-aware local game route resolves URL path segments beneath the selected directory and opens the corresponding document through `ContentResolver`. It runs from `WebViewClient.shouldInterceptRequest` so it can preserve the request method and `Range` header for local file responses. Relative references retain their normal web behavior:

```html
<script src="js/game.js"></script>
<img src="assets/player.png">
```

Each game is loaded from a distinct HTTPS-style host generated beneath the reserved `appassets.androidplatform.net` domain, with `WebViewAssetLoader.Builder.setDomain`. The origin key is stable for an imported game and deterministically derived from a selected folder's Storage Access Framework URI. This keeps browser-managed origin storage isolated between games.

The selected entry point is loaded from `https://<game-origin>/game/<entry-point>`. The reserved `/game/` route and root-relative URLs such as `/assets/player.png` are resolved by the same local file handler, which preserves Range and GET/HEAD request semantics. The reserved `/__pocket/` route is handled separately by `WebViewAssetLoader` for app-owned developer assets.

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

Returning to the library stops and destroys the active WebView rather than leaving a detached instance alive. The active `GamePathHandler` root reference and Activity-held WebView/profile/game references are cleared. The named WebView profile itself is not deleted, so persistent browser-managed data remains available when the game is opened again. Opening another game creates a fresh WebView and attaches its selected profile before loading the new game root.

App immersive fullscreen hides system bars and the native player chrome; Back restores them. HTML fullscreen and fullscreen media are handled through `WebChromeClient.onShowCustomView` / `onHideCustomView`. The supplied custom view is added above the WebView and then removed when fullscreen ends, while the prior window fullscreen flag and system UI visibility are restored.

Back priority is centralized in `PlayerBackResolver` and covered with JVM tests: collapse controls, exit WebView fullscreen, exit app immersive fullscreen, navigate WebView history, return to the library, then normal Activity behavior.

The Activity handles orientation and related screen/keyboard configuration changes without recreation. On a change it requests layout and invalidates the existing WebView rather than reloading the game.

## Developer console

An optional native **Dev** control injects the pinned Eruda bundle into the active game only when requested. The bundle is built into the APK and served through a reserved `/__pocket/` path on the current per-game origin. The route is handled by `WebViewAssetLoader.AssetsPathHandler` against the app's root asset directory, not by the user-selected game folder.

The game origin remains isolated from other games. Eruda's own floating entry button is hidden; the native player button controls its visibility. Injection is lazy and idempotent within a page. The developer console uses the game's JavaScript realm and is not a security boundary: game code can inspect or interfere with the injected tool.

The build downloads the exact Eruda 3.4.3 npm archive and checks its SHA-512 integrity before extracting the browser bundle and including its MIT notice. Runtime debugging is offline and does not add the Android INTERNET permission.

## Library records and browser data

The version-3 library registry stores immutable game IDs, display titles, storage references, entry-point paths, origin keys, and optional named WebView profile names. Version-1 records are read as imported ZIP games and version-2 records retain their imported or folder-backed storage type; missing profile fields remain empty so existing saves continue using the legacy default profile.

Imported games point to app-private extracted directories. Folder-backed games store a persisted Storage Access Framework tree URI and a relative HTML entry point; they never own or delete the original directory. Folder shortcut IDs begin as a deterministic tree-URI/entry-point pair. If Android revokes access, reconnecting the folder can rebind the shortcut to a newly granted tree URI while preserving its original library ID, origin host, and browser profile. This keeps that game's saved data associated with the same shortcut.

New games receive a stable named WebView profile when the installed System WebView supports AndroidX WebKit's `MULTI_PROFILE` feature. `WebViewCompat.setProfile` is applied to a fresh WebView before configuring or navigating it. A named profile isolates browser-managed state (including cookies, web storage, and service-worker state) from profiles used by other games. Clearing or removing a named profile destroys the app's WebView for that game first, then calls `ProfileStore.deleteProfile`. WebView may finish deleting some profile data asynchronously.

Older library entries are not automatically assigned new profiles. Their historical localStorage/IndexedDB data is left in the default profile. On a supporting WebView, the user can explicitly choose **Isolate saves**; this switches that game to a new empty profile without copying or deleting the old shared data. If `MULTI_PROFILE` is unsupported, new games continue on the default profile and per-game clearing/isolation is unavailable rather than risking data belonging to other games.

The library's search and sort are display-only: they do not alter IDs, entry points, or storage. Renaming changes only the title. Removing an imported entry deletes its private imported copy and its named browser profile when supported. Removing a folder-backed entry deletes only the shortcut, releases its persisted read grant only after the last shortcut using that URI is removed, and never deletes the external source folder. Removing a legacy entry does not delete the shared default profile.

If an imported folder is incomplete, the library offers ZIP replacement under the existing storage ID and retains the game's browser profile. The replacement flow stages the archive, then swaps the private content directory and updates its root/entry-point metadata. Backups left by interrupted replacements are checked on startup: a usable current directory wins, while an invalid or missing one is rolled back from its backup. A backup that cannot be moved safely is retained rather than deleted.
