# Folder-backed library entries

Games opened from a user-selected folder can be saved as library references without copying their files. The library stores the persisted Storage Access Framework tree URI and the selected HTML entry-point path.

The entry identity is deterministic for the pair of tree URI and entry point. Different start pages in one folder remain separate library items; opening the same page again updates the same record.

The metadata registry is version 2. Version-1 records are interpreted as imported ZIP games, preserving existing library items and allowing the next write to migrate the registry.

A folder shortcut never owns or deletes the source files. When removing a shortcut, the app releases the persisted read permission only after the last library record referencing that exact folder URI is removed.
