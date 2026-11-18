package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipGameImporterTest {
    @Test
    fun extractsNestedFilesAndDetectsSingleWrapperDirectory() {
        withTemporaryDirectory { directory ->
            val importer = ZipGameImporter(File(directory, "games"))
            val staged = importer.extract(
                zipOf(
                    "Example Game/index.html" to "<html>hello</html>",
                    "Example Game/assets/player.png" to "image-bytes"
                ),
                "Example Game.zip"
            )

            try {
                assertEquals("Example Game", staged.displayName)
                assertEquals("Example Game", staged.rootPath)
                assertTrue(File(staged.stagingDirectory, "Example Game/index.html").isFile)
                assertTrue(File(staged.stagingDirectory, "Example Game/assets/player.png").isFile)

                val published = staged.publish(File(directory, "games"))
                assertTrue(File(published, "Example Game/index.html").isFile)
                assertFalse(staged.stagingDirectory.exists())
            } finally {
                staged.cleanup()
            }
        }
    }

    @Test
    fun rejectsTraversalAndCleansStagingDirectory() {
        withTemporaryDirectory { directory ->
            val games = File(directory, "games")
            val importer = ZipGameImporter(games)

            try {
                importer.extract(
                    zipOf("../escape.txt" to "no"),
                    "Bad.zip"
                )
                throw AssertionError("Expected unsafe path rejection.")
            } catch (expected: ZipImportException) {
                assertFalse(File(directory, "escape.txt").exists())
                assertEquals(0, games.listFiles()?.size ?: 0)
            }
        }
    }

    @Test
    fun enforcesTotalExtractedSizeLimit() {
        withTemporaryDirectory { directory ->
            val importer = ZipGameImporter(
                File(directory, "games"),
                ZipImportLimits(maxEntries = 10, maxEntryBytes = 8, maxTotalBytes = 10)
            )

            try {
                importer.extract(zipOf("one.txt" to "12345678", "two.txt" to "1234"), "Large.zip")
                throw AssertionError("Expected size limit rejection.")
            } catch (expected: ZipImportException) {
                assertTrue(File(directory, "games").listFiles().isNullOrEmpty())
            }
        }
    }

    @Test
    fun enforcesEntryCountLimit() {
        withTemporaryDirectory { directory ->
            val importer = ZipGameImporter(
                File(directory, "games"),
                ZipImportLimits(maxEntries = 1)
            )

            try {
                importer.extract(zipOf("one.txt" to "one", "two.txt" to "two"), "Many.zip")
                throw AssertionError("Expected entry count rejection.")
            } catch (expected: ZipImportException) {
                assertTrue(File(directory, "games").listFiles().isNullOrEmpty())
            }
        }
    }

    @Test
    fun rejectsDirectoryEntriesContainingData() {
        withTemporaryDirectory { directory ->
            val importer = ZipGameImporter(File(directory, "games"))
            val archive = zipOf("assets/" to "unexpected")

            try {
                importer.extract(archive, "Invalid.zip")
                throw AssertionError("Expected non-empty directory rejection.")
            } catch (expected: ZipImportException) {
                assertTrue(File(directory, "games").listFiles().isNullOrEmpty())
            }
        }
    }

    @Test
    fun rejectsDuplicateEntries() {
        withTemporaryDirectory { directory ->
            val importer = ZipGameImporter(File(directory, "games"))

            try {
                importer.extract(zipOf("index.html" to "one", "index.html" to "two"), "Duplicate.zip")
                throw AssertionError("Expected duplicate path rejection.")
            } catch (expected: ZipImportException) {
                assertTrue(File(directory, "games").listFiles().isNullOrEmpty())
            }
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("pocket-gameplay-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
