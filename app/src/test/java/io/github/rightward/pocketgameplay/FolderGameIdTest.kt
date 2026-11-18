package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FolderGameIdTest {
    @Test
    fun sameFolderAndEntryPointProduceSameId() {
        assertEquals(
            FolderGameId.create("content://provider/tree/game", "start.html"),
            FolderGameId.create("content://provider/tree/game", "start.html")
        )
    }

    @Test
    fun entryPointsAndFoldersProduceDifferentIds() {
        val first = FolderGameId.create("content://provider/tree/game", "start.html")
        assertNotEquals(first, FolderGameId.create("content://provider/tree/game", "menu.html"))
        assertNotEquals(first, FolderGameId.create("content://provider/tree/other", "start.html"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeEntryPoint() {
        FolderGameId.create("content://provider/tree/game", "../outside.html")
    }
}
