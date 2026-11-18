# Developer console

Pocket Gameplay includes an optional in-game developer console powered by [Eruda](https://github.com/liriliri/eruda).

## Bundling and offline use

The Android build fetches the pinned Eruda 3.4.3 npm package from the npm registry. Gradle verifies the package tarball against its pinned SHA-512 integrity value before extracting the JavaScript bundle into generated app assets. The Eruda MIT license and version/source attribution are packaged alongside it.

The developer console is served by the app's reserved `/__pocket/` asset route on the active game's local WebView origin. It is bundled inside the APK; opening the console at runtime does not contact a CDN or require internet access.

The first build on a clean workspace needs network access to download the pinned npm package. Subsequent builds can reuse the verified archive in the local Gradle build directory.

## Player behavior

The native **Dev** button is disabled until the game page finishes loading. Tapping it injects Eruda into the current page on demand. Eruda's own floating entry button is hidden so the native control remains the single entry point. Tapping **Dev** again hides or reopens the console without reloading the game.

The configured tools include Console, Elements, Network, Resources, Sources, Info, and Snippets. The integration is idempotent within a page, so repeated taps do not add duplicate script elements or initialize multiple Eruda instances.

## Security and limitations

- No Android JavaScript bridge is exposed to game pages.
- No general internet permission is added; external game requests remain blocked by the existing WebView client.
- Eruda is loaded from an app-owned local asset route under the current game's isolated origin.
- The game's source folder and imported files are not modified.
- Eruda runs in the same JavaScript realm as the game. A game can inspect, modify, or interfere with the injected developer tools; this is a debugging aid, not a security boundary.
- The developer console may affect performance and should be closed during normal play.

Eruda is distributed under the MIT License. Its license notice is included in the APK assets as `ERUDA-LICENSE.txt`. The exact bundled version and package source are recorded in `ERUDA-VERSION.txt`.
