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
            assertEquals("", game.browserProfile)
        }
    }

    @Test
    fun readsVersionTwoFolderShortcutsAndMigratesOnNextWrite() {
        withTemporaryDirectory { directory ->
            val metadata = File(directory, "library.properties")
            val folderUri = "content://provider/tree/new-game"
            val entryPoint = "Game Start.html"
            val folderId = FolderGameId.create(folderUri, entryPoint)
            val properties = Properties().apply {
                setProperty("version", "2")
                setProperty("count", "1")
                setProperty("game.0.id", folderId)
                setProperty("game.0.title", "Legacy folder")
                setProperty("game.0.directory", "")
                setProperty("game.0.root", "")
                setProperty("game.0.entry", entryPoint)
                setProperty("game.0.importedAt", "1000")
                setProperty("game.0.storageType", LibraryStorageType.FOLDER)
                setProperty("game.0.sourceUri", folderUri)
            }
            metadata.outputStream().use { properties.store(it, "Version two fixture") }

            val store = GameLibraryStore(metadata)
            assertEquals(folderId, store.all().single().id)
            store.rename(folderId, "Renamed folder")

            val migratedProperties = Properties()
            metadata.inputStream().use { migratedProperties.load(it) }
            assertEquals("3", migratedProperties.getProperty("version"))
        }
    }

    @Test
    fun rejectsFolderRecordWithInvalidStorageFields() {
        withTemporaryDirectory { directory ->
            val store = GameLibraryStore(File(directory, "library.properties"))
            val game = LibraryGame(
                id = "folder-id",
                title = "Invalid folder game",
                directoryName = "not-empty",
                rootPath = "",
                entryPoint = "index.html",
                importedAtEpochMillis = 100L,
                storageType = LibraryStorageType.FOLDER,
                sourceUri = "content://provider/tree/game"
            )

            try {
                store.add(game)
                throw AssertionError("Expected folder storage validation failure.")
            } catch (expected: java.io.IOException) {
                assertTrue(store.all().isEmpty())
            }
        }
    }

    @Test
    fun renameChangesOnlyDisplayTitle() {
        withTemporaryDirectory { directory ->
            val store = GameLibraryStore(File(directory, "library.properties"))
            val original = sampleImportedGame("renamed")
            store.add(original)

            assertTrue(store.rename(original.id, "  Better title  "))

            val renamed = store.all().single()
            assertEquals("Better title", renamed.title)
            assertEquals(original.id, renamed.id)
            assertEquals(original.directoryName, renamed.directoryName)
            assertEquals(original.rootPath, renamed.rootPath)
            assertEquals(original.entryPoint, renamed.entryPoint)
            assertEquals(original.originKey, renamed.originKey)
            assertEquals(original.browserProfile, renamed.browserProfile)
            assertFalse(store.rename("missing", "No game"))
        }
    }

    @Test
    fun folderPermissionCanBeReboundWithoutChangingGameOrOriginIdentity() {
        withTemporaryDirectory { directory ->
            val store = GameLibraryStore(File(directory, "library.properties"))
            val oldUri = "content://provider/tree/old"
            val newUri = "content://provider/tree/reselected"
            val entryPoint = "start/index.html"
            val original = LibraryGame(
                id = FolderGameId.create(oldUri, entryPoint),
                title = "Folder game",
                directoryName = "",
                rootPath = "",
                entryPoint = entryPoint,
                importedAtEpochMillis = 1234L,
                storageType = LibraryStorageType.FOLDER,
                sourceUri = oldUri,
                originKey = GameOrigin.forFolderUri(oldUri),
                browserProfile = GameBrowserProfile.forGameId(FolderGameId.create(oldUri, entryPoint))
            )
            store.add(original)

            val rebound = original.copy(sourceUri = newUri)
            assertTrue(store.update(rebound))

            val saved = store.all().single()
            assertEquals(original.id, saved.id)
            assertEquals(original.originKey, saved.originKey)
            assertEquals(original.browserProfile, saved.browserProfile)
            assertEquals(newUri, saved.sourceUri)
        }
    }

    @Test
    fun replacesMatchingIdAndRemovesOnlySelectedGame() {
        withTemporaryDirectory { directory ->
            val store = GameLibraryStore(File(directory, "library.properties"))
            val first = sampleImportedGame("one")
            val second = sampleImportedGame("two")

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

    @Test
    fun rejectsAnInvalidBrowserProfileName() {
        withTemporaryDirectory { directory ->
            val store = GameLibraryStore(File(directory, "library.properties"))
            val game = sampleImportedGame("profile").copy(browserProfile = "shared-profile")

            try {
                store.add(game)
                throw AssertionError("Expected profile identity validation failure.")
            } catch (expected: java.io.IOException) {
                assertTrue(store.all().isEmpty())
            }
        }
    }

    private fun sampleImportedGame(id: String) = LibraryGame(
        id = id,
        title = id,
        directoryName = id,
        rootPath = "",
        entryPoint = "index.html",
        importedAtEpochMillis = 100L,
        originKey = id,
        browserProfile = GameBrowserProfile.forGameId(id)
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
