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
    fun initializesExpectedMobileDebugTools() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        listOf("console", "elements", "network", "resources", "sources").forEach { tool ->
            assertTrue("Missing Eruda tool: $tool", script.contains("'$tool'"))
        }
    }
}
