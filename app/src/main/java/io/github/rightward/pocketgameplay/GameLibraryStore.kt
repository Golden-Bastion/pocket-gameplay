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
    val sourceUri: String = ""
)

object LibraryStorageType {
    const val IMPORTED = "imported"
    const val FOLDER = "folder"
}

/**
 * Small file-backed registry for imported games.
 *
 * Game content itself lives in a separate app-private directory. The registry only stores
 * stable identifiers and relative paths; callers must still validate those paths before use.
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

    @Synchronized
    fun remove(id: String): Boolean {
        val games = readGames()
        val remaining = games.filterNot { it.id == id }
        if (remaining.size == games.size) return false

        writeGames(remaining)
        return true
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
                sourceUri = properties.getProperty(prefix + "sourceUri", "")
            ).also(::validate)
        }
    }

    private fun validate(game: LibraryGame) {
        if (game.id.isBlank() || game.title.isBlank() ||
            RelativeGamePath.parse(game.entryPoint) == null
        ) {
            throw IOException("Game library metadata contains invalid required fields.")
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
                    !game.sourceUri.startsWith("content://") ||
                    game.id != FolderGameId.create(game.sourceUri, game.entryPoint)
                ) {
                    throw IOException("Folder game metadata contains an invalid folder reference.")
                }
            }
            else -> throw IOException("Game library metadata contains an unsupported storage type.")
        }
    }

    private fun writeGames(games: List<LibraryGame>) {
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
        private const val MIN_SUPPORTED_VERSION = 1
        private const val FORMAT_VERSION = 2
        private const val MAX_LIBRARY_ITEMS = 10_000
    }
}
