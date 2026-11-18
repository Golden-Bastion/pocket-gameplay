package io.github.rightward.pocketgameplay

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ZipImportLimits(
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Long = 100L * 1024L * 1024L,
    val maxTotalBytes: Long = 512L * 1024L * 1024L
)

class ZipImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class StagedGame(
    val id: String,
    val displayName: String,
    val stagingDirectory: File,
    val rootPath: String
) {
    private var published = false

    fun publish(gamesDirectory: File): File {
        check(!published) { "This staged game has already been published." }

        val destination = File(gamesDirectory, id)
        if (destination.exists()) {
            throw ZipImportException("A game with this storage identifier already exists.")
        }
        if (!stagingDirectory.renameTo(destination)) {
            throw ZipImportException("Could not move the imported game into the library.")
        }

        published = true
        return destination
    }

    fun cleanup() {
        if (!published) stagingDirectory.deleteRecursively()
    }
}

/**
 * Extracts untrusted ZIP input into a temporary directory under app-private storage.
 * A caller must publish the staged game only after entry-point discovery and selection succeed.
 */
class ZipGameImporter(
    private val gamesDirectory: File,
    private val limits: ZipImportLimits = ZipImportLimits()
) {
    init {
        require(limits.maxEntries > 0)
        require(limits.maxEntryBytes > 0)
        require(limits.maxTotalBytes > 0)
    }

    fun extract(input: InputStream, archiveDisplayName: String): StagedGame {
        if (!gamesDirectory.exists() && !gamesDirectory.mkdirs()) {
            input.close()
            throw ZipImportException("Could not create the private game storage directory.")
        }
        if (!gamesDirectory.isDirectory) {
            input.close()
            throw ZipImportException("The private game storage path is not a directory.")
        }

        val id = UUID.randomUUID().toString()
        val stagingDirectory = File(gamesDirectory, ".staging-$id")
        if (!stagingDirectory.mkdir()) {
            input.close()
            throw ZipImportException("Could not create a temporary import directory.")
        }

        try {
            extractEntries(input, stagingDirectory)
            val rootPath = findSingleWrapperDirectory(stagingDirectory)
            return StagedGame(
                id = id,
                displayName = displayNameFrom(archiveDisplayName),
                stagingDirectory = stagingDirectory,
                rootPath = rootPath
            )
        } catch (exception: Exception) {
            stagingDirectory.deleteRecursively()
            if (exception is ZipImportException) throw exception
            throw ZipImportException("The ZIP archive could not be extracted.", exception)
        }
    }

    private fun extractEntries(input: InputStream, destination: File) {
        val canonicalRoot = destination.canonicalFile
        val seenEntries = HashSet<String>()
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw ZipImportException("ZIP import was cancelled.")
                }

                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > limits.maxEntries) {
                    throw ZipImportException("The archive contains too many entries.")
                }

                val relativePath = ZipEntryPath.normalize(entry.name, entry.isDirectory)
                    ?: throw ZipImportException("The archive contains an unsafe path.")

                if (!seenEntries.add(relativePath)) {
                    throw ZipImportException("The archive contains duplicate paths.")
                }

                val target = File(destination, relativePath.replace('/', File.separatorChar))
                ensureContained(canonicalRoot, target)

                if (entry.isDirectory) {
                    ensureDirectory(target)
                    if (entry.size > 0 || zip.read() >= 0) {
                        throw ZipImportException("A directory entry unexpectedly contains file data.")
                    }
                } else {
                    if (entry.size > limits.maxEntryBytes) {
                        throw ZipImportException("An archive file exceeds the per-file size limit.")
                    }

                    val parent = target.parentFile
                        ?: throw ZipImportException("An archive entry has no parent directory.")
                    ensureDirectory(parent)

                    if (target.exists()) {
                        throw ZipImportException("The archive contains conflicting file paths.")
                    }

                    var entryBytes = 0L
                    FileOutputStream(target).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) {
                                throw ZipImportException("ZIP import was cancelled.")
                            }

                            val bytesRead = zip.read(buffer)
                            if (bytesRead < 0) break

                            entryBytes += bytesRead
                            totalBytes += bytesRead

                            if (entryBytes > limits.maxEntryBytes) {
                                throw ZipImportException("An archive file exceeds the per-file size limit.")
                            }
                            if (totalBytes > limits.maxTotalBytes) {
                                throw ZipImportException("The archive exceeds the total extracted size limit.")
                            }

                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                zip.closeEntry()
            }
        }

        if (entryCount == 0) {
            throw ZipImportException("The selected archive is empty.")
        }
    }

    private fun ensureContained(root: File, target: File) {
        val rootPath = root.path + File.separator
        val targetPath = target.canonicalFile.path
        if (!targetPath.startsWith(rootPath)) {
            throw ZipImportException("An archive entry escapes the extraction directory.")
        }
    }

    private fun ensureDirectory(directory: File) {
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw ZipImportException("The archive contains conflicting file paths.")
            }
        } else if (!directory.mkdirs()) {
            throw ZipImportException("Could not create an archive directory.")
        }
    }

    private fun findSingleWrapperDirectory(stagingDirectory: File): String {
        val children = stagingDirectory.listFiles() ?: return ""
        if (children.size == 1 && children[0].isDirectory) {
            return children[0].name
        }
        return ""
    }

    private fun displayNameFrom(archiveDisplayName: String): String {
        val baseName = archiveDisplayName
            .substringAfterLast('/')
            .substringAfterLast('\\\\')
            .trim()
        val withoutExtension = if (baseName.endsWith(".zip", ignoreCase = true)) {
            baseName.dropLast(4)
        } else {
            baseName
        }

        return withoutExtension.take(80).ifBlank { "Imported Game" }
    }
}
