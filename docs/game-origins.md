# Game origin isolation

Each game is loaded from a distinct HTTPS-style host beneath the reserved
`appassets.androidplatform.net` domain using `WebViewAssetLoader.Builder.setDomain`.

The origin key is stable for an imported library item and deterministically derived
from the persisted Storage Access Framework tree URI for an externally selected folder.
The scheme, host, and port define a web origin; separating hosts therefore isolates
browser-managed origin storage such as localStorage and IndexedDB between games.

Requests are intercepted only for the active game's exact host. Other hosts are blocked
by the WebView client, and the app does not declare the Android INTERNET permission.
