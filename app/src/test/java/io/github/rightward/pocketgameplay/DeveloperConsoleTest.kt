package io.github.rightward.pocketgameplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperConsoleTest {
    @Test
    fun developerAssetsUseAnAppOwnedRoute() {
        assertTrue(DeveloperConsole.ASSET_PATH_PREFIX.startsWith("/"))
        assertTrue(DeveloperConsole.TOGGLE_SCRIPT.contains("script.src = '/__pocket/eruda.js'"))
    }

    @Test
    fun injectsBundledScriptAndInitializesLikeTheOfficialBookmarklet() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        assertTrue(script.contains("document.body.appendChild(script)"))
        assertTrue(script.contains("window.eruda.init()"))
        assertFalse(script.contains("window.eruda.show"))
        assertFalse(script.contains("window.eruda.hide"))
        assertFalse(script.contains("window.eruda.get"))
    }

    @Test
    fun loadingIsLazyAndIdempotent() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        assertTrue(script.contains("if (state.loading)"))
        assertTrue(script.contains("if (state.initialized && window.eruda)"))
        assertTrue(script.contains("state.status = 'ready'"))
    }

    @Test
    fun exposesLoadingAndFailureStatesForNativeFeedback() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        listOf("loading", "ready", "load_error", "init_error", "timeout").forEach { status ->
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
        assertFalse(script.contains("https://"))
        assertFalse(script.contains("http://"))
    }

    @Test
    fun doesNotOverrideErudaDefaultToolsOrUiBehavior() {
        val script = DeveloperConsole.TOGGLE_SCRIPT

        assertFalse(script.contains("tool:"))
        assertFalse(script.contains("autoScale:"))
        assertFalse(script.contains("useShadowDom:"))
        assertFalse(script.contains("entryButton"))
    }
}
