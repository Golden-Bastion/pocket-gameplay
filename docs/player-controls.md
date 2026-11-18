# Playback controls and fullscreen

## Player toolbar

The active game uses a fixed native toolbar above its WebView viewport. The toolbar takes up its own layout space, so the game page begins underneath it instead of rendering behind an overlay.

The toolbar contains:

- **Exit icon** — stops and destroys the active WebView and returns to the library. It does not remove the library entry or clear persistent browser-managed save data.
- **Reload icon** — reloads the current page in the same WebView and profile.
- **Game title** — displays the current library title, truncating long names rather than pushing the toolbar actions off screen.
- **More menu** — contains **Fullscreen** and **Dev**. Dev remains disabled until the game page finishes loading. Once loaded, Eruda's own floating button controls its panel.

The toolbar respects display cutouts and system-bar insets. Immersive fullscreen and HTML fullscreen hide the toolbar; leaving fullscreen restores it. The game viewport expands into the space previously occupied by the toolbar while fullscreen is active.

The More menu is a native Android popup. Opening it counts as an expanded player control for Back-navigation priority, and selecting an action or dismissing the popup closes it.

## Game touch gestures

The WebView receives game touch input directly. On Android 10 and newer, Pocket Gameplay requests narrow, centered gesture-exclusion regions along both horizontal screen edges so HTML5 games can recognize edge-originating swipes, such as SugarCube's sidebar gestures. The exclusion is limited to at most 200 dp vertically per edge; Android Back remains available along the rest of each side edge. Older Android versions do not need this system-gesture exclusion.

## HTML fullscreen and media

The player WebView's `WebChromeClient` handles the HTML fullscreen custom-view lifecycle:

- `onShowCustomView` adds the supplied fullscreen view above the player and enters immersive system UI.
- `onHideCustomView` removes the supplied view, restores the previous window fullscreen flag and system UI visibility, and reveals the player toolbar again.
- Android Back requests exit from the custom view before navigating game history or leaving the player.

This also covers WebView's HTML media fullscreen path, subject to the codecs and media features supported by the device's Android System WebView.

## Back navigation priority

Android 13 and newer use the platform `OnBackInvokedDispatcher` callback, with predictive Back enabled in the manifest. Android 12 and older use the legacy Activity callback. Both paths use the same resolver.

Back is resolved in this order:

1. Dismiss the open player More menu.
2. Exit an HTML fullscreen custom view.
3. Exit the player's immersive fullscreen mode.
4. Navigate backward through the game's WebView history.
5. Return to the library.
6. When already in the library, the first Back press displays **Press Back again to exit**. A second press within two seconds finishes the Activity; a later press starts a new confirmation window.

The library exit confirmation uses monotonic elapsed time, so wall-clock changes do not affect its timeout. Entering the library resets any previous confirmation.

## Rotation and window changes

The Activity handles orientation, screen-size, screen-layout, smallest-screen-size, and keyboard configuration changes without recreating the Activity. The existing WebView is retained and asked to relayout/invalidate when the display configuration changes. The app does not force portrait or landscape; games and users can rotate naturally.

## Limitations

Fullscreen rendering depends on the device's Android System WebView. Some games implement their own fullscreen UI in page content rather than using the browser fullscreen API; those in-page controls remain the game's responsibility. Returning to the library destroys the WebView and ends the in-memory game session. Persistent browser-managed data remains in the game's profile and is available the next time the game is opened.
