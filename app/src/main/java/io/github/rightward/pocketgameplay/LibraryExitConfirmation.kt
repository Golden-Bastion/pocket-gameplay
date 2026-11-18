package io.github.rightward.pocketgameplay

/**
 * Requires two Back presses within a bounded interval before exiting from the library.
 *
 * [nowMillis] must use a monotonic time source such as SystemClock.elapsedRealtime().
 */
internal class LibraryExitConfirmation(
    private val confirmationWindowMillis: Long = DEFAULT_CONFIRMATION_WINDOW_MILLIS
) {
    private var firstPressAtMillis: Long? = null

    init {
        require(confirmationWindowMillis > 0) {
            "The exit confirmation window must be positive."
        }
    }

    fun shouldExit(nowMillis: Long): Boolean {
        val previousPressAt = firstPressAtMillis
        if (previousPressAt != null &&
            nowMillis >= previousPressAt &&
            nowMillis - previousPressAt <= confirmationWindowMillis
        ) {
            reset()
            return true
        }

        firstPressAtMillis = nowMillis
        return false
    }

    fun reset() {
        firstPressAtMillis = null
    }

    companion object {
        const val DEFAULT_CONFIRMATION_WINDOW_MILLIS = 2_000L
    }
}
