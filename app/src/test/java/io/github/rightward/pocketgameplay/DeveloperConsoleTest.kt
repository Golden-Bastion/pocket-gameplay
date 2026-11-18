package io.github.rightward.pocketgameplay

import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperConsoleTest {
    @Test
    fun developerAssetsUseAnAppOwnedRoute() {
        assertTrue(DeveloperConsole.ASSET_PATH_PREFIX.startsWith("/"))
        assertTrue(DeveloperConsole.TOGGLE_SCRIPT.contains("script.src = '/__pocket/eruda.js'"))
    }

    @Test
    fun consoleLoaderIsLazyAndIdempotent() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        assertTrue(script.contains("if (state.loading)"))
        assertTrue(script.contains("state.initialized"))
        assertTrue(script.contains("window.eruda.init"))
        assertTrue(script.contains("entryButton.hide()"))
    }

    @Test
    fun nativeToggleCanShowAndHideTheConsole() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        assertTrue(script.contains("window.eruda.show('console')"))
        assertTrue(script.contains("window.eruda.hide()"))
        assertTrue(script.contains("state.visible"))
    }

    @Test
    fun exposesLoadingAndFailureStatesForNativeFeedback() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        listOf("loading", "shown", "hidden", "load_error", "init_error", "timeout").forEach { status ->
            assertTrue("Missing console state: $status", script.contains("'$status'"))
        }
        assertTrue(script.contains("window.setTimeout"))
        assertTrue(script.contains("script.onerror"))
        assertTrue(script.contains("script.parentNode.removeChild(script)"))
        assertTrue(DeveloperConsole.STATUS_SCRIPT.contains("state.status"))
    }

    @Test
    fun loadsOnlyTheBundledLocalAsset() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        assertTrue(script.contains("script.src = '/__pocket/eruda.js'"))
        assertTrue(!script.contains("https://"))
        assertTrue(!script.contains("http://"))
    }

    @Test
    fun initializesExpectedMobileDebugTools() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        listOf("console", "elements", "network", "resources", "sources").forEach { tool ->
            assertTrue("Missing Eruda tool: $tool", script.contains("'$tool'"))
        }
    }
}
