# Folder-backed library entries

Games opened from a user-selected folder can be saved as library references without copying their files. The library stores the persisted Storage Access Framework tree URI and the selected HTML entry-point path.

A new entry ID is deterministic for the pair of tree URI and entry point. Different start pages in one folder remain separate library items; opening the same page again updates the same record. The ID remains stable if the user reconnects the shortcut after Android revokes its access and the replacement tree URI differs. The saved origin key and WebView profile are retained, so the reconnect does not silently abandon that game's browser data.

The version-3 metadata registry adds a stable origin key and optional named WebView profile. Version-1 records are read as imported ZIP games; version-2 folder records remain readable and migrate on the next metadata write. Legacy entries without a profile continue using the default WebView profile until the user explicitly chooses to isolate their saves on a WebView that supports multi-profile.

A folder shortcut never owns or deletes the source files. Removing a shortcut removes only its library record. The app releases the persisted read permission only after the last library record referencing that exact folder URI is removed.

When a stored folder permission is missing or the saved entry point no longer exists, the library offers a reconnect action. The user selects the folder again; if the previous start page is absent, a new HTML/HTM entry point can be selected. The shortcut's library ID, display title, origin identity, and save profile remain unchanged.
