package io.github.rightward.pocketgameplay

internal data class PlayerBackState(
    val controlsExpanded: Boolean,
    val webFullscreen: Boolean,
    val immersiveFullscreen: Boolean,
    val canNavigateBack: Boolean,
    val isPlaying: Boolean
)

internal enum class PlayerBackAction {
    CLOSE_CONTROLS,
    EXIT_WEB_FULLSCREEN,
    EXIT_IMMERSIVE_FULLSCREEN,
    NAVIGATE_GAME_HISTORY,
    RETURN_TO_LIBRARY,
    EXIT_ACTIVITY
}

internal object PlayerBackResolver {
    fun resolve(state: PlayerBackState): PlayerBackAction {
        return when {
            state.controlsExpanded -> PlayerBackAction.CLOSE_CONTROLS
            state.webFullscreen -> PlayerBackAction.EXIT_WEB_FULLSCREEN
            state.immersiveFullscreen -> PlayerBackAction.EXIT_IMMERSIVE_FULLSCREEN
            state.canNavigateBack -> PlayerBackAction.NAVIGATE_GAME_HISTORY
            state.isPlaying -> PlayerBackAction.RETURN_TO_LIBRARY
            else -> PlayerBackAction.EXIT_ACTIVITY
        }
    }
}
