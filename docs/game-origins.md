# Game origin and browser profile isolation

Each game is loaded from a distinct HTTPS-style host beneath the reserved
`appassets.androidplatform.net` domain using `WebViewAssetLoader.Builder.setDomain`.

The origin key is stable for an imported library item and is initially derived from the
persisted Storage Access Framework tree URI for an externally selected folder. A folder
shortcut retains its origin key if the user reconnects it through a different tree URI.
Different folder entry points can share an origin host, but new library entries still use
distinct named WebView profiles when supported.

The scheme, host, and port define a web origin. Separate hosts isolate origin-scoped
storage such as localStorage and IndexedDB, but origin separation alone is not a complete
per-game data deletion mechanism.

## Named WebView profiles

When AndroidX WebKit reports `WebViewFeature.MULTI_PROFILE`, each newly added game is
assigned a deterministic profile name derived from its immutable library ID.
`WebViewCompat.setProfile` is called on a fresh WebView before it is configured or
navigated. The profile provides a separate browser-data container for that game, so
cookies, web storage, service workers, and other profile-managed state are not shared
with other game profiles.

Per-game reset first destroys the app's WebView associated with the profile, then calls
`ProfileStore.getInstance().deleteProfile(name)`. Some deletion work may complete
asynchronously. The profile is created again on the next launch of that game.

Legacy library entries have no named profile and keep using the default profile, preserving
their existing data. The user can explicitly isolate one of these entries, which starts it
with a fresh named profile. Its previous data remains in the legacy default profile; the
app does not attempt an unsafe migration or delete the shared profile. If the installed
System WebView does not support multi-profile, the app keeps using the default profile and
does not offer a destructive per-game reset.

Requests are intercepted only for the active game's exact host. Other hosts are blocked
by the WebView client, and the app does not declare the Android INTERNET permission.
