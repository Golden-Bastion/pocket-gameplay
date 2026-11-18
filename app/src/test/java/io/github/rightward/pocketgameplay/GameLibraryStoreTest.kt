package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameLibraryStoreTest {
    @Test
    fun persistsGamesAcrossStoreInstances() {
        withTemporaryDirectory { directory ->
            val metadata = File(directory, "library.properties")
            val game = LibraryGame(
                id = "game-1",
                title = "A game = with symbols",
                directoryName = "game-1",
                rootPath = "wrapper",
                entryPoint = "start page.html",
                importedAtEpochMillis = 1234L
            )

            GameLibraryStore(metadata).add(game)

            assertEquals(listOf(game), GameLibraryStore(metadata).all())
        }
    }

    @Test
    fun replacesMatchingIdAndRemovesOnlySelectedGame() {
        withTemporaryDirectory { directory ->
            val store = GameLibraryStore(File(directory, "library.properties"))
            val first = sampleGame("one")
            val second = sampleGame("two")

            store.add(first)
            store.add(second)
            store.add(first.copy(title = "Updated"))

            assertEquals(2, store.all().size)
            assertEquals("Updated", store.all().first { it.id == "one" }.title)
            assertTrue(store.remove("one"))
            assertFalse(store.remove("missing"))
            assertEquals(listOf(second), store.all())
        }
    }

    private fun sampleGame(id: String) = LibraryGame(
        id = id,
        title = id,
        directoryName = id,
        rootPath = "",
        entryPoint = "index.html",
        importedAtEpochMillis = 100L
    )

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("pocket-game-library-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
