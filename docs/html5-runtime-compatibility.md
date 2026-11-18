# HTML5 runtime and local media compatibility

Pocket Gameplay executes game code in the device's Android System WebView. The app serves the selected game directory through an isolated local HTTPS-style origin; it is not a general-purpose browser or a server-side application runtime.

## Local resource types

The local path handler returns explicit MIME types for common browser assets:

- HTML, CSS, JavaScript and ES modules.
- JSON, source maps, XML, web manifests and SVG.
- WebAssembly modules.
- WOFF/WOFF2, TTF, OTF and EOT fonts.
- Common PNG, JPEG, GIF, WebP, AVIF and icon images.
- Common MP3, M4A, AAC, Ogg, Opus, WAV and FLAC audio.
- MP4, M4V, WebM, Ogg video, QuickTime, HLS playlists, DASH manifests and MPEG transport streams.

Unknown extensions use Android's MIME map when available and otherwise fall back to `application/octet-stream`.

## Media range requests

The local game route supports HTTP `GET` and `HEAD`. For a valid single `Range: bytes=...` request it returns:

- `206 Partial Content` and `Content-Range` for a satisfiable range.
- `416 Range Not Satisfiable` with `Content-Range: bytes */<length>` when the requested range cannot be satisfied.
- `Accept-Ranges: bytes` and a bounded response stream so bytes outside the requested interval are not returned.

Closed, open-ended, and suffix ranges are supported. Multiple ranges and malformed range headers are ignored and receive a normal full-file response instead of a multipart response. If a storage provider does not report a reliable file size, the handler serves the complete stream and does not claim byte-range support for that file.

For folder-backed games, byte ranges are read through the selected document provider's input stream. Some cloud-backed or unusual document providers may implement seeking inefficiently; local device storage is preferred for large media files.

## File inputs

When a game requests a file through an HTML `<input type="file">`, Pocket Gameplay opens Android's Storage Access Framework document picker. Accepted MIME types are passed to the picker, and multiple selection is enabled when the page requests it.

Only readable `content://` URIs returned by the picker are passed back to WebView. The app does not persist those individual file grants or expose broad storage access. A page's selected file is available to that page session; a game must implement its own import/save behavior.

Camera capture and directory upload are not implemented. The picker supports opening files only.

## Downloads and export

A game can offer a download link to a file that exists inside its currently selected game folder. Pocket Gameplay validates the local origin and path, then opens Android's `ACTION_CREATE_DOCUMENT` picker so the user chooses where to save a copy.

Downloads are limited to local game files. Remote URLs, `blob:` URLs, and `data:` URLs are rejected; the app does not add internet access or a native JavaScript bridge to support them. To export generated game data, the game should write it to its own storage or present a local file already in its game folder.

## Browser APIs and limits

The fixture at [test-fixtures/html5-compatibility](../test-fixtures/html5-compatibility/) exercises ES modules, same-origin JSON fetch, Canvas 2D, WebGL, WebAssembly, dedicated workers, localStorage, IndexedDB, Web Audio, local WAV playback/seeking, the SAF file picker, and a user-mediated local download. Its storage probes now distinguish first-run initialization from values retained across a reload.

Use the [physical-device acceptance checklist](device-validation.md) to verify the complete player baseline and record device-specific limitations.

The fixture is a device-level diagnostic, not an emulator. Actual support for WebGL, audio/video codecs, workers and browser APIs depends on the installed Android System WebView and device hardware. Service-worker-based offline installation, server-side APIs, WebRTC capture, remote networking and Flash are outside this player baseline.
