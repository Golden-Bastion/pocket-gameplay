# Library management and browser data

## Library organization

The library supports imported ZIP copies and folder-backed shortcuts. A card identifies its storage type and selected HTML/HTM start page. Details show whether the game uses a dedicated browser profile or the older shared default profile.

Search matches the game title and relative start-page path. Sorting is deterministic: alphabetical order compares titles case-insensitively and breaks ties by library ID; recently added sorts newest-first and then uses title and ID as stable tie-breakers. Search and sorting do not change a game's identity or files.

Renaming edits only the display title. The immutable library ID, imported directory, selected folder URI, origin key, start page, and browser-profile name are left unchanged.

## Storage and recovery

An imported ZIP is extracted into Pocket Gameplay's private storage. Removing an imported entry removes its library record and deletes that private copy. If its extracted files are missing or incomplete, **Replace ZIP** imports a new archive under the same library ID and updates the selected root/start page. The title and named browser profile remain unchanged. The replacement is staged before the existing directory is moved aside; interrupted backups are checked on the next app start. A valid committed directory wins, an invalid one is rolled back where possible, and a backup that cannot be moved safely is kept rather than discarded.

A folder-backed entry is a shortcut to a directory selected by the user through Android's Storage Access Framework. Removing it removes only the shortcut and never deletes files from the original folder. The persisted read permission is released after the last shortcut using that exact tree URI is removed.

If Android revokes a folder permission, or the saved start page disappears, **Reconnect** lets the user select the folder again. If the same start page is not present, another HTML/HTM file can be selected. The existing shortcut ID and origin/profile identity are retained even when Android gives the reselected folder a different URI.

## Per-game browser data

When the installed System WebView supports AndroidX WebKit's `MULTI_PROFILE` feature, every newly added game receives a deterministic named WebView profile based on its immutable library ID. The player attaches that profile to a fresh WebView before setting it up or navigating. This isolates profile-managed browser data, including cookies, localStorage/IndexedDB, caches and service-worker state, from other games.

**Clear data** deletes the named profile for one game. Pocket Gameplay destroys its WebView first, then requests deletion through `ProfileStore.deleteProfile`. The WebView implementation may finish removing some data asynchronously. The profile is created again the next time the game is played. Removing a game also deletes its named profile when the feature is supported.

### Existing library entries

Entries saved before named profiles were introduced keep using the default WebView profile so their existing saves are not silently abandoned. On a WebView with multi-profile support, **Isolate saves** switches the selected game to a fresh named profile. Existing data in the old shared profile is neither copied nor deleted; the game starts with an empty profile. The default profile is not deleted when any legacy game is removed.

If the device's System WebView does not support `MULTI_PROFILE`, the app continues using the default profile and disables per-game isolation/reset. Updating Android System WebView may make these controls available. There is no fallback that clears the whole shared profile, because doing so could erase data for unrelated games.
