# Playback controls and fullscreen

## Player control surface

The active game is shown edge-to-edge in a native `WebView`. A small **Controls** button is the only persistent player overlay. Opening it reveals a compact native panel with:

- **Library** — stops and destroys the active WebView, releases the game's runtime resources, and returns to the library without removing the library record or clearing persistent browser-managed save data. The in-memory game session ends; localStorage, IndexedDB, cookies, and the named browser profile remain.
- **Reload** — reloads the current page in the same WebView.
- **Fullscreen** — hides the app's system bars and native player controls for an unobstructed game view.
- **Dev** — opens or closes the optional Eruda developer console.
- **Close controls** — dismisses the panel.

The panel is hidden by default, so the game retains the available screen area during normal play. The developer-console control remains inside the panel.

## HTML fullscreen and media

The player WebView's `WebChromeClient` handles the HTML fullscreen custom-view lifecycle:

- `onShowCustomView` adds the supplied fullscreen view above the player and enters immersive system UI.
- `onHideCustomView` removes the supplied view, restores the previous window fullscreen flag and system UI visibility, and reveals the player controls again.
- Android Back requests exit from the custom view before navigating game history or leaving the player.

This also covers WebView's HTML media fullscreen path, subject to the codecs and media features supported by the device's Android System WebView.

## Back navigation priority

Android 13 and newer use the platform `OnBackInvokedDispatcher` callback, with predictive Back enabled in the manifest. Android 12 and older use the legacy Activity callback. Both paths use the same resolver.

Back is resolved in this order:

1. Close an expanded native controls panel.
2. Exit an HTML fullscreen custom view.
3. Exit the player's immersive fullscreen mode.
4. Navigate backward through the game's WebView history.
5. Return to the library.
6. Use normal Activity Back behavior when already in the library.

## Rotation and window changes

The Activity handles orientation, screen-size, screen-layout, smallest-screen-size, and keyboard configuration changes without recreating the Activity. The existing WebView is retained and asked to relayout/invalidate when the display configuration changes. The app does not force portrait or landscape; games and users can rotate naturally.

## Limitations

Fullscreen rendering depends on the device's Android System WebView. Some games implement their own fullscreen UI in page content rather than using the browser fullscreen API; those in-page controls remain the game's responsibility. Returning to the library destroys the WebView and ends the in-memory game session. Persistent browser-managed data remains in the game's profile and is available the next time the game is opened.
