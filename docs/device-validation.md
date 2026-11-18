# Physical-device acceptance checklist

This checklist is the final manual validation pass for Pocket Gameplay's supported offline HTML5 player baseline. Run it on a physical Android device using the exact debug APK produced by CI. A successful emulator build or JVM unit-test run does not replace these checks.

The fixture is intentionally offline and is located at [test-fixtures/html5-compatibility](../test-fixtures/html5-compatibility/). Download the fixture folder or obtain it from the repository before enabling airplane mode. For ZIP-import checks, also prepare a ZIP of an ordinary small HTML5 game (the fixture itself can be zipped).

## Record the environment

Before testing, record:

- Device model and Android version.
- Android System WebView package version (Settings → Apps → Android System WebView → App details; on devices using Chrome as the WebView provider, record the provider/version shown by the device).
- Pocket Gameplay APK version and CI run/artifact used.
- Whether the library reports **Clear data**, **Isolate saves**, or **Data reset unavailable** for a game. These correspond to the installed WebView's per-profile support and the game's profile state.

Do not treat a missing WebGL implementation or unsupported media codec as a generic app failure without recording the device and WebView version. Compatibility varies by installed WebView and hardware.

## A. Offline launch and entry points

- [ ] Enable airplane mode and make sure Wi-Fi and mobile data are off.
- [ ] Launch Pocket Gameplay and open the fixture folder through **Choose game folder**.
- [ ] Select `index.html` when prompted. Confirm the page and its local CSS/SVG assets load without a network connection.
- [ ] Import a ZIP copy of the same fixture using **Import ZIP**. Confirm it launches from the private library.
- [ ] When using a game with more than one HTML/HTM page, confirm the entry-point chooser lists the discovered pages and that the selected page launches.
- [ ] Force-stop and relaunch Pocket Gameplay. Confirm both the imported item and the folder shortcut remain in the library and can be opened offline.

Expected result: folder-backed and imported games both launch from local storage, and no external connection is required.

## B. HTML5 runtime fixture

Open the fixture and inspect its **Runtime probes** list.

- [ ] ES module and CSS/SVG asset checks pass.
- [ ] Same-origin JSON fetch passes.
- [ ] Canvas 2D passes.
- [ ] WebAssembly passes.
- [ ] Dedicated worker passes.
- [ ] WebGL either creates a context or is recorded as unavailable on this device, with the WebView/device version noted.
- [ ] The first page load reports that localStorage and IndexedDB were initialized.
- [ ] Use the player toolbar's **Reload** icon. The localStorage and IndexedDB probes should now report that values from an earlier page load were retained.
- [ ] Leave the game, reopen it from the library, and confirm the storage probes still report the previous values.
- [ ] Tap **Test Web Audio** and confirm the short tone plays after the user gesture.

A first-run storage message is expected on a fresh profile. A persistence failure is when the stored value is not available after a reload/reopen on the same profile.

## C. Local media, file selection and export

- [ ] Play the fixture's local WAV sample.
- [ ] Drag the audio timeline to a different position and confirm seeking works.
- [ ] Tap **Choose a local video file**. Android's system document picker should appear; select a supported local video and confirm it appears in the preview.
- [ ] Seek within the selected video if its codec and WebView implementation support it.
- [ ] Tap **Save fixture export file**. Android should ask for a destination. Save the file and confirm it exists at the chosen location.
- [ ] Open the player toolbar's More menu and select **Dev**. Confirm the menu item briefly shows **Loading…**, then **Dev Loaded**, and Eruda's floating entry button appears.
- [ ] Tap Eruda's floating entry button. Confirm the panel opens using Eruda's default UI; use the same button to close it again.
- [ ] Confirm the native Dev action does not attempt to show, hide, or otherwise control Eruda after initialization.
- [ ] Confirm opening/closing the console does not reload or reset the game, and that Eruda is absent on a fresh launch until explicitly opened.
- [ ] Confirm the Dev control remains disabled while the game page is loading and becomes available after the page finishes.
- [ ] With airplane mode enabled, open Eruda for both an imported ZIP game and a folder-backed game. Both must use the bundled app asset rather than a CDN.

Use a locally stored media file. Codec support is device-dependent, so record the file type and any playback/seek failure.

## D. Player controls and navigation

- [ ] Confirm the fixed player toolbar stays above the game viewport and that the page starts below it rather than drawing behind the toolbar.
- [ ] Confirm the toolbar shows an exit icon, a reload icon, the current game title, and a More menu. Long game titles should truncate without hiding the toolbar actions.
- [ ] Open and dismiss the More menu. Confirm it contains **Fullscreen** and **Dev**.
- [ ] Use the toolbar's **Reload** icon and confirm the current game page reloads.
- [ ] Select **Fullscreen** from the More menu and confirm system bars and the toolbar hide; use Android Back to exit immersive fullscreen and confirm the toolbar returns.
- [ ] Test a game's own HTML fullscreen or fullscreen media control, if available, and confirm Android Back exits that fullscreen view first.
- [ ] Rotate the device while a game is running. Confirm the activity/WebView remains usable and the game is not unexpectedly returned to the library.
- [ ] Press Android Back with the More menu open; it should dismiss the menu before leaving the game.
- [ ] With no overlay open, use Back in a game that has navigation history; it should navigate game history before returning to the library.
- [ ] In a SugarCube game, swipe inward from the left and right screen edges to open/close its sidebar. Confirm both gestures reach the game instead of triggering system Back.
- [ ] Confirm system Back remains available from the unexcluded upper/lower portions of either screen edge and still follows the existing player Back priority.
- [ ] Confirm normal taps, vertical scrolling, horizontal drags and multi-touch controls continue to work after testing edge swipes.
- [ ] Return to the library and confirm the game entry and its browser-managed saves remain present.
- [ ] Return from a throwaway imported game to the library, then immediately remove that entry. Confirm deletion completes without an "in-use" or locked-files error.
- [ ] Return from an imported game to the library, then immediately replace/update its ZIP when that workflow is available. Confirm the previous WebView no longer holds the extracted files.
- [ ] Reopen a retained game after returning to the library and confirm its localStorage/IndexedDB values are still present; closing the WebView must not clear its persistent browser profile.
- [ ] While already in the library, press Android Back once and confirm the exit prompt appears without closing the app.
- [ ] Press Back again within two seconds and confirm Pocket Gameplay exits.
- [ ] Relaunch, press Back once, wait more than two seconds, then press again; confirm the second press starts a new confirmation instead of exiting immediately.

## E. Library lifecycle and save isolation

- [ ] Rename a library entry. Confirm the display title changes but the game still launches from the same files and start page.
- [ ] Search by part of the title and by part of the start-page path. Confirm filtering works.
- [ ] Switch between A–Z and Recently added sorting. Confirm the list order changes predictably.
- [ ] Confirm games with local `rel=icon` links display the correct favicon in the library; games without one retain the Pocket Gameplay fallback mark.
- [ ] Confirm favicons still display after force-stop/relaunch and with airplane mode enabled.
- [ ] Confirm remote icon URLs are not fetched and do not prevent the game from opening offline.
- [ ] Confirm the card keeps **Play** visible and moves secondary actions behind the vertical three-dot menu.
- [ ] Open the three-dot menu and verify ZIP replacement, folder reconnection where applicable, rename, details, save-data action, and removal are available.
- [ ] Open **Details** and verify it identifies an imported ZIP copy or a folder shortcut and shows the selected start page.
- [ ] Import the fixture twice as two distinct games. On a WebView that supports named profiles, each new game should have a dedicated profile. Write data in both games, reload each, then clear data for only one game. The cleared game should report a fresh first load; the other game's previous localStorage/IndexedDB values should remain.
- [ ] If the device shows **Isolate saves** for an older library item, only test this on a game whose existing saves you are willing to leave behind in the legacy shared profile. Confirm it starts with fresh data after isolation. Do not expect automatic migration of the old shared data.
- [ ] If **Data reset unavailable** is shown, record that the installed WebView does not expose the required multi-profile feature. Do not attempt to clear all WebView data through Android Settings as a substitute.

## F. Folder shortcut and imported-copy semantics

- [ ] Add a folder-backed game and rename its library entry. Confirm the original folder and files remain unchanged.
- [ ] Remove the folder shortcut. Confirm the original folder and its files still exist in the file manager.
- [ ] Add the folder again and keep the shortcut.
- [ ] To exercise reconnection, move/rename the selected source folder so the saved start page can no longer be opened, then tap **Play**. Use the recovery dialog's **Reconnect** action to select the replacement folder; choose an alternative HTML/HTM start page if the original is absent.
- [ ] Confirm the library title and shortcut remain and the game can launch from the replacement folder. If the replacement selection resulted in a different SAF URI, confirm the same game still uses its prior browser data.
- [ ] Remove an imported ZIP game. Confirm its private imported copy is removed from the library and that no unrelated game or folder-backed source folder is removed.
- [ ] On a healthy imported game, use **Update ZIP** and select a newer valid archive. Confirm the existing library card is updated in place and no duplicate item is created.
- [ ] Use a replacement archive with multiple HTML/HTM pages. Choose a different start page and confirm the updated game launches from it.
- [ ] Cancel the document picker and, separately, the start-page chooser. Confirm the prior game files remain usable after each cancellation.
- [ ] Try an invalid ZIP and a valid ZIP with no HTML/HTM page. Each must fail without replacing the previous game files.
- [ ] Before and after updating, compare the library ID implicitly through the same card, display title, and save-data behavior. Reload/reopen the updated game and confirm its prior localStorage/IndexedDB values remain available.
- [ ] Confirm folder-backed shortcuts do not show **Update ZIP** and remain linked to their original external folders.

## G. Security boundaries

- [ ] Keep airplane mode enabled throughout the fixture checks; local page assets, the optional console and local file actions should continue to work.
- [ ] Confirm the app does not request broad shared-storage access. Folder and individual file access should be granted only through Android's document pickers.
- [ ] Confirm file input and download actions require user interaction with the system picker.
- [ ] Confirm external network content is not needed for the fixture and that no unexpected remote page is loaded.

## Recording results

For each failed or unavailable check, record the section/item, exact action, observed result, Android version, System WebView provider/version, and whether the issue is repeatable. Distinguish a WebView/device limitation from an app regression. Attach a screenshot or short recording when useful, but do not include private game files or personal media.

The baseline should only be marked fully device-validated after the supported checks have been exercised on a physical device and any failures have either been fixed or explicitly documented as platform limitations.
