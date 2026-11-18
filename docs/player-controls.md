# Playback controls and fullscreen

## Player control surface

The active game is shown edge-to-edge in a native `WebView`. A small **Controls** button is the only persistent player overlay. Opening it reveals a compact native panel with:

- **Library** — stops the active load and returns to the library without removing the library record or clearing browser-managed save data.
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

Fullscreen rendering depends on the device's Android System WebView. Some games implement their own fullscreen UI in page content rather than using the browser fullscreen API; those in-page controls remain the game's responsibility. Returning to the library preserves the game's origin storage, but this does not guarantee preservation of an in-memory game session after the game is later reloaded.
