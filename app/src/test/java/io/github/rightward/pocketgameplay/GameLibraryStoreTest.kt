package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Properties

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
    fun readsVersionOneMetadataAsImportedGames() {
        withTemporaryDirectory { directory ->
            val metadata = File(directory, "library.properties")
            val properties = Properties().apply {
                setProperty("version", "1")
                setProperty("count", "1")
                setProperty("game.0.id", "legacy-id")
                setProperty("game.0.title", "Legacy game")
                setProperty("game.0.directory", "legacy-id")
                setProperty("game.0.root", "wrapper")
                setProperty("game.0.entry", "start.html")
                setProperty("game.0.importedAt", "1000")
            }
            metadata.outputStream().use { properties.store(it, "Version one fixture") }

            val game = GameLibraryStore(metadata).all().single()

            assertEquals("legacy-id", game.id)
            assertEquals(LibraryStorageType.IMPORTED, game.storageType)
            assertEquals("", game.sourceUri)
        }
    }

    @Test
    fun storesFolderReferencesInVersionTwoMetadata() {
        withTemporaryDirectory { directory ->
            val metadata = File(directory, "library.properties")
            val folderUri = "content://provider/tree/folder"
            val entryPoint = "Start Page.html"
            val folderGame = LibraryGame(
                id = FolderGameId.create(folderUri, entryPoint),
                title = "My downloaded game",
                directoryName = "",
                rootPath = "",
                entryPoint = entryPoint,
                importedAtEpochMillis = 1234L,
                storageType = LibraryStorageType.FOLDER,
                sourceUri = folderUri
            )

            GameLibraryStore(metadata).add(folderGame)

            assertEquals(listOf(folderGame), GameLibraryStore(metadata).all())
            val properties = Properties()
            metadata.inputStream().use { properties.load(it) }
            assertEquals("2", properties.getProperty("version"))
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
