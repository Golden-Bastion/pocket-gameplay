# HTML5 runtime compatibility fixture

This is a small offline game folder for checking Pocket Gameplay against the Android System WebView installed on a device.

## Run it

1. Download or clone this repository on the Android device.
2. Open Pocket Gameplay and choose this `html5-compatibility` folder.
3. Select `index.html` when asked for the entry point.
4. Check the results shown on the page and exercise the audio, video-file picker, and download actions.

The fixture deliberately uses local sibling assets. It does not call external services or require internet access.

## Checks included

- JavaScript ES module loading.
- Same-origin JSON fetch.
- Canvas 2D and WebGL context availability.
- A minimal WebAssembly module.
- A dedicated worker.
- localStorage persistence and IndexedDB write/read.
- Web Audio after a user gesture.
- Local WAV playback with a seekable timeline (uses the app's local byte-range path).
- File input using Android's system document picker.
- Saving a local text file through the app's user-selected export location.
- SVG image and CSS loading.

## Record the device environment

Use Eruda's Dev console to inspect the user agent and console/network errors. WebGL, codecs, and individual browser APIs vary with Android System WebView version and device hardware. A green test here indicates the fixture worked on that device; it is not a promise that every HTML5 game will work.

The fixture does not attempt to emulate a web server backend, service-worker installability, camera/microphone capture, or remote network access.
