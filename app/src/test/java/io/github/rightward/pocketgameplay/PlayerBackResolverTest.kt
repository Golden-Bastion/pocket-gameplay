package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackResolverTest {
    @Test
    fun closesExpandedControlsBeforeNavigatingGame() {
        assertEquals(
            PlayerBackAction.CLOSE_CONTROLS,
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = true,
                    webFullscreen = false,
                    immersiveFullscreen = false,
                    canNavigateBack = true,
                    isPlaying = true
                )
            )
        )
    }

    @Test
    fun exitsHtmlFullscreenBeforeImmersiveOrHistory() {
        assertEquals(
            PlayerBackAction.EXIT_WEB_FULLSCREEN,
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = false,
                    webFullscreen = true,
                    immersiveFullscreen = true,
                    canNavigateBack = true,
                    isPlaying = true
                )
            )
        )
    }

    @Test
    fun exitsImmersiveFullscreenBeforeNavigatingHistory() {
        assertEquals(
            PlayerBackAction.EXIT_IMMERSIVE_FULLSCREEN,
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = false,
                    webFullscreen = false,
                    immersiveFullscreen = true,
                    canNavigateBack = true,
                    isPlaying = true
                )
            )
        )
    }

    @Test
    fun navigatesHistoryBeforeReturningToLibrary() {
        assertEquals(
            PlayerBackAction.NAVIGATE_GAME_HISTORY,
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = false,
                    webFullscreen = false,
                    immersiveFullscreen = false,
                    canNavigateBack = true,
                    isPlaying = true
                )
            )
        )
    }

    @Test
    fun returnsToLibraryAtGameHistoryRoot() {
        assertEquals(
            PlayerBackAction.RETURN_TO_LIBRARY,
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = false,
                    webFullscreen = false,
                    immersiveFullscreen = false,
                    canNavigateBack = false,
                    isPlaying = true
                )
            )
        )
    }

    @Test
    fun exitsActivityFromLibrary() {
        assertEquals(
            PlayerBackAction.EXIT_ACTIVITY,
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = false,
                    webFullscreen = false,
                    immersiveFullscreen = false,
                    canNavigateBack = false,
                    isPlaying = false
                )
            )
        )
    }
}
