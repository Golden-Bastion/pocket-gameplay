# Library management and browser data

## Library organization

The library supports imported ZIP copies and folder-backed shortcuts. A card identifies its storage type and selected HTML/HTM start page. Details show whether the game uses a dedicated browser profile or the older shared default profile.

Search matches the game title and relative start-page path. Sorting is deterministic: alphabetical order compares titles case-insensitively and breaks ties by library ID; recently added sorts newest-first and then uses title and ID as stable tie-breakers. Search and sorting do not change a game's identity or files.

Each game card shows a compact favicon thumbnail when its selected HTML page declares a local `rel=icon` or Apple touch icon. If no usable link is declared, the app checks for a conventional `favicon.ico` in the game root and then beside the selected page. The image is copied into Pocket Gameplay's private `game-icons` cache, keyed by the immutable library ID. It is available offline, survives display-title changes, and is refreshed when imported game files are replaced or a folder shortcut is reconnected. If no supported local image is found, the app mark remains as the fallback. Remote URLs and data URIs are ignored.

The **Play** action remains directly on each compact card. Less frequent actions—ZIP replacement, folder reconnection, rename, details, save-data isolation/reset, and removal—are grouped under the vertical three-dot menu. When a game is unavailable, the primary action becomes **Reconnect** or **Update ZIP** as appropriate.

Renaming edits only the display title. The immutable library ID, imported directory, selected folder URI, origin key, start page, and browser-profile name are left unchanged.

## Storage and recovery

An imported ZIP is extracted into Pocket Gameplay's private storage. Removing an imported entry removes its library record and deletes that private copy. If its extracted files are missing or incomplete, **Replace ZIP** imports a new archive under the same library ID and updates the selected root/start page. The title and named browser profile remain unchanged. The replacement is staged before the existing directory is moved aside; interrupted backups are checked on the next app start. A valid committed directory wins, an invalid one is rolled back where possible, and a backup that cannot be moved safely is kept rather than discarded.

A folder-backed entry is a shortcut to a directory selected by the user through Android's Storage Access Framework. Removing it removes only the shortcut and never deletes files from the original folder. The persisted read permission is released after the last shortcut using that exact tree URI is removed.

If Android revokes a folder permission, or the saved start page disappears, **Reconnect** lets the user select the folder again. If the same start page is not present, another HTML/HTM file can be selected. The existing shortcut ID and origin/profile identity are retained even when Android gives the reselected folder a different URI.

## Per-game browser data

When the installed System WebView supports AndroidX WebKit's `MULTI_PROFILE` feature, every newly added game receives a deterministic named WebView profile based on its immutable library ID. The player attaches that profile to a fresh WebView before setting it up or navigating. This isolates profile-managed browser data, including cookies, localStorage/IndexedDB, caches and service-worker state, from other games.

**Clear data** immediately switches the game to a new, empty profile generation and stores the old profile name in a persistent cleanup queue. Pocket Gameplay detaches and destroys the WebView, then attempts to delete the old profile. Some WebView providers keep a profile marked as in-use until the app process exits; when that happens, the old name remains queued and is retried on the next cold app-process start, before a new game WebView is created. The game can be launched with clean saves immediately because its library record already points to the new profile. Removing a game uses the same cleanup queue, without risking another game's profile.

### Existing library entries

Entries saved before named profiles were introduced keep using the default WebView profile so their existing saves are not silently abandoned. On a WebView with multi-profile support, **Isolate saves** switches the selected game to a fresh named profile. Existing data in the old shared profile is neither copied nor deleted; the game starts with an empty profile. The default profile is not deleted when any legacy game is removed.

If the device's System WebView does not support `MULTI_PROFILE`, the app continues using the default profile and disables per-game isolation/reset. Updating Android System WebView may make these controls available. There is no fallback that clears the whole shared profile, because doing so could erase data for unrelated games.
