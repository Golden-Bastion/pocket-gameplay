# ZIP import and game library

ZIP archives are untrusted input. The importer extracts into a uniquely named staging directory beneath the app-private `filesDir/games` directory. A game is moved into its stable library directory only after extraction and HTML entry-point selection succeed.

## Extraction safeguards

- Reject absolute paths, drive-qualified names, backslashes, empty path segments, `.`, `..`, NUL characters, and excessively long entry names.
- Reject duplicate normalized archive paths and file/directory collisions.
- Resolve each destination to a canonical path and require it to remain inside the staging directory.
- Limit entry count, individual extracted file size, and total extracted size.
- Stream files to disk; do not load complete assets or archives into memory.
- Check for thread interruption during extraction and remove staging content on failure.

The Java ZIP APIs used here do not create symbolic links from archive metadata; entries are written as ordinary files. Unix permission and symlink attributes are not applied.

## Wrapper directories

If the archive contains exactly one top-level directory, that directory is treated as the game's effective root. Its structure remains intact, and the chosen HTML entry point is stored relative to that root. This preserves the expected behavior of paths such as `assets/sprite.png` and root-relative `/assets/sprite.png`.

## Library metadata

The library registry is a small versioned properties file, written to a temporary sibling file and atomically replaced where supported. Extracted game files remain separate from metadata. The registry stores only identifiers and relative paths; stored paths are validated again before a game is launched or removed.
