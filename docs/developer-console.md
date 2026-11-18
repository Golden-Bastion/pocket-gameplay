# Developer console

Pocket Gameplay includes an optional in-game developer console powered by [Eruda](https://github.com/liriliri/eruda).

## Bundling and offline use

The Android build fetches the pinned Eruda 3.4.3 npm package from the npm registry. Gradle verifies the package tarball against its pinned SHA-512 integrity value before extracting the JavaScript bundle into generated app assets. The Eruda MIT license and version/source attribution are packaged alongside it.

The developer console is served by the app's reserved `/__pocket/` asset route on the active game's local WebView origin. It is bundled inside the APK; opening the console at runtime does not contact a CDN or require internet access.

The first build on a clean workspace needs network access to download the pinned npm package. Subsequent builds can reuse the verified archive in the local Gradle build directory.

Debug and release APK verification tasks inspect the built archive and fail CI if `assets/eruda.js`, its MIT license, or the pinned version metadata are missing. This verifies that the bundle is actually packaged, not merely generated in the Gradle workspace.

## Player behavior

The **Dev** item in the player toolbar's More menu is disabled until the game page finishes loading. Selecting it injects the locally bundled Eruda script into the current page. The integration intentionally follows Eruda's documented bookmarklet sequence: append the script to `document.body`, then call `eruda.init()` with no custom options or additional API calls.

While the local asset loads, the menu item changes to **Loading…** and remains disabled. Once Eruda initializes, the menu item changes to **Dev Loaded** and becomes disabled. Use Eruda's own floating entry button to open and close its panel and switch tools. Reloading the game resets the native control and allows Eruda to be loaded again.

The integration is idempotent within a page, so repeated taps do not add duplicate script elements or initialize multiple Eruda instances. The native player polls a small page-local status value while Eruda loads. Asset failures, initialization failures and timeouts produce a visible Android Toast with a suggested next step. No JavaScript bridge is added.

Eruda's default tools, floating button, panel behavior and default configuration are left untouched.

## Security and limitations

- No Android JavaScript bridge is exposed to game pages.
- No general internet permission is added; external game requests remain blocked by the existing WebView client.
- Eruda is loaded from an app-owned local asset route under the current game's isolated origin.
- The game's source folder and imported files are not modified.
- Eruda runs in the same JavaScript realm as the game. A game can inspect, modify, or interfere with the injected developer tools; this is a debugging aid, not a security boundary.
- The developer console may affect performance and should be closed during normal play.

Eruda is distributed under the MIT License. Its license notice is included in the APK assets as `ERUDA-LICENSE.txt`. The exact bundled version and package source are recorded in `ERUDA-VERSION.txt`.
