package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GameOriginTest {
    @Test
    fun producesDistinctLocalOriginsForDistinctGames() {
        val first = GameOrigin.domainFor("01234567-89ab-cdef-0123-456789abcdef")
        val second = GameOrigin.domainFor("fedcba98-7654-3210-fedc-ba9876543210")

        assertNotEquals(first, second)
        assertEquals(
            "game-01234567-89ab-cdef-0123-456789abcdef.appassets.androidplatform.net",
            first
        )
    }

    @Test
    fun folderUriProducesStableOriginKey() {
        assertEquals(
            GameOrigin.forFolderUri("content://provider/tree/primary%3ADownloads%2Fgame"),
            GameOrigin.forFolderUri("content://provider/tree/primary%3ADownloads%2Fgame")
        )
    }
}
