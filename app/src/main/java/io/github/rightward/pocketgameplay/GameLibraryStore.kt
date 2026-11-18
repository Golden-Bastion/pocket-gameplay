package io.github.rightward.pocketgameplay

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class LibraryGame(
    val id: String,
    val title: String,
    val directoryName: String,
    val rootPath: String,
    val entryPoint: String,
    val importedAtEpochMillis: Long,
    val storageType: String = LibraryStorageType.IMPORTED,
    val sourceUri: String = "",
    /** Stable origin key retained when a folder permission is renewed with a new URI. */
    val originKey: String = "",
    /** Empty for records that still use the pre-isolation default WebView profile. */
    val browserProfile: String = "",
    /** Incremented whenever Clear data switches this game to a fresh named profile. */
    val browserProfileGeneration: Int = 0
)

object LibraryStorageType {
    const val IMPORTED = "imported"
    const val FOLDER = "folder"
}

/**
 * File-backed registry for imported games and folder shortcuts.
 *
 * Game content is stored separately. The registry stores stable identifiers and relative paths;
 * callers must validate those paths again before use.
 */
class GameLibraryStore(private val metadataFile: File) {
    @Synchronized
    fun all(): List<LibraryGame> = readGames()

    @Synchronized
    fun add(game: LibraryGame) {
        validate(game)
        val games = readGames().filterNot { it.id == game.id } + game
        writeGames(games)
    }

    /** Replaces metadata for an existing ID without changing that ID or its storage identity. */
    @Synchronized
    fun update(game: LibraryGame): Boolean {
        validate(game)
        val games = readGames().toMutableList()
        val index = games.indexOfFirst { it.id == game.id }
        if (index < 0) return false

        games[index] = game
        writeGames(games)
        return true
    }

    /** Changes only the user-facing title; storage paths, ID and browser-data keys stay intact. */
    @Synchronized
    fun rename(id: String, title: String): Boolean {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            throw IOException("A game title cannot be empty.")
        }

        val games = readGames().toMutableList()
        val index = games.indexOfFirst { it.id == id }
        if (index < 0) return false

        val renamed = games[index].copy(title = normalizedTitle)
        validate(renamed)
        games[index] = renamed
        writeGames(games)
        return true
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val games = readGames()
        val remaining = games.filterNot { it.id == id }
        if (remaining.size == games.size) return false

        writeGames(remaining)
        return true
    }

    /**
     * Switches a game to a fresh profile and records the old profile for safe deletion.
     * Both changes are committed together so a failed reset cannot orphan the old save data.
     */
    @Synchronized
    fun rotateBrowserProfile(id: String, expectedProfile: String): LibraryGame {
        val games = readGames().toMutableList()
        val index = games.indexOfFirst { it.id == id }
        if (index < 0) throw IOException("The library entry no longer exists.")

        val current = games[index]
        if (current.browserProfile.isBlank() || current.browserProfile != expectedProfile) {
            throw IOException("The game's browser profile changed. Reopen the library and try again.")
        }
        if (current.browserProfileGeneration == Int.MAX_VALUE) {
            throw IOException("This game's profile reset limit has been reached.")
        }

        val nextGeneration = current.browserProfileGeneration + 1
        val updated = current.copy(
            browserProfile = GameBrowserProfile.forGameId(id, nextGeneration),
            browserProfileGeneration = nextGeneration
        )
        val pending = (readPendingProfileDeletions() + expectedProfile).distinct()
        games[index] = updated
        writeGames(games, pending)
        return updated
    }

    @Synchronized
    fun queueBrowserProfileDeletion(profileName: String) {
        if (!GameBrowserProfile.isValidProfileName(profileName)) {
            throw IOException("Invalid browser profile name.")
        }
        val pending = readPendingProfileDeletions()
        if (profileName !in pending) {
            writeGames(readGames(), pending + profileName)
        }
    }

    @Synchronized
    fun pendingBrowserProfileDeletions(): List<String> = readPendingProfileDeletions()

    @Synchronized
    fun completeBrowserProfileDeletion(profileName: String) {
        val pending = readPendingProfileDeletions()
        val remaining = pending.filterNot { it == profileName }
        if (remaining.size != pending.size) {
            writeGames(readGames(), remaining)
        }
    }

    private fun readPendingProfileDeletions(): List<String> {
        if (!metadataFile.exists()) return emptyList()

        val properties = Properties()
        metadataFile.inputStream().buffered().use { input -> properties.load(input) }
        val count = properties.getProperty(KEY_PENDING_PROFILE_COUNT)?.toIntOrNull() ?: 0
        if (count !in 0..MAX_LIBRARY_ITEMS) {
            throw IOException("Game library metadata contains an invalid pending-profile count.")
        }

        return (0 until count).map { index ->
            val profileName = properties.getProperty(KEY_PENDING_PROFILE + index)
                ?: throw IOException("Game library metadata is missing a pending profile.")
            if (!GameBrowserProfile.isValidProfileName(profileName)) {
                throw IOException("Game library metadata contains an invalid pending profile.")
            }
            profileName
        }.distinct()
    }

    private fun readGames(): List<LibraryGame> {
        if (!metadataFile.exists()) return emptyList()

        val properties = Properties()
        metadataFile.inputStream().buffered().use { input -> properties.load(input) }

        val version = properties.getProperty(KEY_VERSION)?.toIntOrNull()
            ?: throw IOException("Game library metadata is missing its version.")
        if (version !in MIN_SUPPORTED_VERSION..FORMAT_VERSION) {
            throw IOException("Unsupported game library metadata version.")
        }

        val count = properties.getProperty(KEY_COUNT)?.toIntOrNull()
            ?: throw IOException("Game library metadata is missing its item count.")
        if (count !in 0..MAX_LIBRARY_ITEMS) {
            throw IOException("Game library metadata contains an invalid item count.")
        }

        return (0 until count).map { index ->
            val prefix = "game.$index."
            LibraryGame(
                id = required(properties, prefix + "id"),
                title = required(properties, prefix + "title"),
                directoryName = required(properties, prefix + "directory"),
                rootPath = properties.getProperty(prefix + "root", ""),
                entryPoint = required(properties, prefix + "entry"),
                importedAtEpochMillis = properties.getProperty(prefix + "importedAt")
                    ?.toLongOrNull()
                    ?: throw IOException("Game library metadata contains an invalid import time."),
                storageType = properties.getProperty(prefix + "storageType")
                    ?: LibraryStorageType.IMPORTED,
                sourceUri = properties.getProperty(prefix + "sourceUri", ""),
                originKey = properties.getProperty(prefix + "originKey", ""),
                browserProfile = properties.getProperty(prefix + "browserProfile", ""),
                browserProfileGeneration = properties.getProperty(prefix + "browserProfileGeneration")
                    ?.toIntOrNull() ?: 0
            ).also(::validate)
        }
    }

    private fun validate(game: LibraryGame) {
        if (game.id.isBlank() ||
            game.id == "." ||
            game.id == ".." ||
            File(game.id).name != game.id ||
            game.title.isBlank() ||
            RelativeGamePath.parse(game.entryPoint) == null
        ) {
            throw IOException("Game library metadata contains invalid required fields.")
        }

        if (game.originKey.isNotEmpty() &&
            game.originKey.any { !it.isLetterOrDigit() && it != '-' }
        ) {
            throw IOException("Game library metadata contains an invalid origin key.")
        }

        if (game.browserProfileGeneration < 0 ||
            (game.browserProfile.isEmpty() && game.browserProfileGeneration != 0) ||
            (game.browserProfile.isNotEmpty() &&
                game.browserProfile != GameBrowserProfile.forGameId(
                    game.id,
                    game.browserProfileGeneration
                ))
        ) {
            throw IOException("Game library metadata contains an invalid browser profile.")
        }

        when (game.storageType) {
            LibraryStorageType.IMPORTED -> {
                if (game.directoryName.isBlank() ||
                    game.directoryName == "." ||
                    game.directoryName == ".." ||
                    File(game.directoryName).name != game.directoryName ||
                    game.directoryName != game.id ||
                    game.sourceUri.isNotEmpty()
                ) {
                    throw IOException("Imported game metadata contains an invalid storage path.")
                }
            }
            LibraryStorageType.FOLDER -> {
                if (game.directoryName.isNotEmpty() ||
                    game.rootPath.isNotEmpty() ||
                    !game.sourceUri.startsWith("content://")
                ) {
                    throw IOException("Folder game metadata contains an invalid folder reference.")
                }
            }
            else -> throw IOException("Game library metadata contains an unsupported storage type.")
        }
    }

    private fun writeGames(
        games: List<LibraryGame>,
        pendingProfileDeletions: List<String> = readPendingProfileDeletions()
    ) {
        if (games.size > MAX_LIBRARY_ITEMS) {
            throw IOException("The game library has reached its item limit.")
        }

        val parent = metadataFile.absoluteFile.parentFile
            ?: throw IOException("The game library metadata path has no parent directory.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create the game library metadata directory.")
        }

        val properties = Properties().apply {
            setProperty(KEY_VERSION, FORMAT_VERSION.toString())
            setProperty(KEY_COUNT, games.size.toString())
            setProperty(KEY_PENDING_PROFILE_COUNT, pendingProfileDeletions.size.toString())
            pendingProfileDeletions.forEachIndexed { index, profileName ->
                if (!GameBrowserProfile.isValidProfileName(profileName)) {
                    throw IOException("Invalid pending browser profile name.")
                }
                setProperty(KEY_PENDING_PROFILE + index, profileName)
            }
            games.forEachIndexed { index, game ->
                val prefix = "game.$index."
                setProperty(prefix + "id", game.id)
                setProperty(prefix + "title", game.title)
                setProperty(prefix + "directory", game.directoryName)
                setProperty(prefix + "root", game.rootPath)
                setProperty(prefix + "entry", game.entryPoint)
                setProperty(prefix + "importedAt", game.importedAtEpochMillis.toString())
                setProperty(prefix + "storageType", game.storageType)
                setProperty(prefix + "sourceUri", game.sourceUri)
                setProperty(prefix + "originKey", game.originKey)
                setProperty(prefix + "browserProfile", game.browserProfile)
                setProperty(prefix + "browserProfileGeneration", game.browserProfileGeneration.toString())
            }
        }

        val temporaryFile = File(parent, metadataFile.name + ".tmp")
        FileOutputStream(temporaryFile).use { output ->
            properties.store(output, "Pocket Gameplay library")
            output.fd.sync()
        }

        try {
            Files.move(
                temporaryFile.toPath(),
                metadataFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                metadataFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun required(properties: Properties, key: String): String {
        return properties.getProperty(key)
            ?: throw IOException("Game library metadata is missing a required field.")
    }

    companion object {
        private const val KEY_VERSION = "version"
        private const val KEY_COUNT = "count"
        private const val KEY_PENDING_PROFILE_COUNT = "pendingProfileDeletion.count"
        private const val KEY_PENDING_PROFILE = "pendingProfileDeletion."
        private const val MIN_SUPPORTED_VERSION = 1
        private const val FORMAT_VERSION = 4
        private const val MAX_LIBRARY_ITEMS = 10_000
    }
}
