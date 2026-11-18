package io.github.rightward.pocketgameplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryExitConfirmationTest {
    @Test
    fun firstPressRequestsConfirmationInsteadOfExiting() {
        val confirmation = LibraryExitConfirmation()

        assertFalse(confirmation.shouldExit(1_000L))
    }

    @Test
    fun secondPressWithinWindowExits() {
        val confirmation = LibraryExitConfirmation()

        assertFalse(confirmation.shouldExit(1_000L))
        assertTrue(confirmation.shouldExit(3_000L))
    }

    @Test
    fun pressAfterWindowStartsANewConfirmation() {
        val confirmation = LibraryExitConfirmation()

        assertFalse(confirmation.shouldExit(1_000L))
        assertFalse(confirmation.shouldExit(3_001L))
        assertTrue(confirmation.shouldExit(5_001L))
    }

    @Test
    fun confirmationCanBeResetWhenLeavingLibrary() {
        val confirmation = LibraryExitConfirmation()

        assertFalse(confirmation.shouldExit(1_000L))
        confirmation.reset()

        assertFalse(confirmation.shouldExit(1_100L))
    }

    @Test
    fun nonMonotonicTimestampStartsANewConfirmation() {
        val confirmation = LibraryExitConfirmation()

        assertFalse(confirmation.shouldExit(2_000L))
        assertFalse(confirmation.shouldExit(1_000L))
        assertTrue(confirmation.shouldExit(3_000L))
    }
}
