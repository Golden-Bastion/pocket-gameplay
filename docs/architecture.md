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

## Library records

The library can contain two storage types. Imported games point to an app-private extracted directory. Folder-backed games store a persisted Storage Access Framework tree URI and a relative HTML entry point; they never own or delete the original directory. The versioned metadata reader continues to interpret version-1 entries as imported games while version 2 adds storage type and source URI fields.

Folder game IDs are deterministic for a tree URI plus entry point. Different entry points in the same folder are independent shortcuts. A persisted URI read grant is released only after the last library item using that tree URI is removed.
