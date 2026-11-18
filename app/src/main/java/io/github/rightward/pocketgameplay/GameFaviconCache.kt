package io.github.rightward.pocketgameplay

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Stores small, app-private copies of each game's local favicon.
 *
 * The cache is keyed by immutable library ID, so renames do not invalidate it and folder games
 * never write into their source directories.
 */
class GameFaviconCache(
    private val cacheDirectory: File,
    private val contentResolver: ContentResolver
) {
    fun load(gameId: String): Bitmap? {
        val file = cacheFile(gameId)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun refresh(gameId: String, gameRoot: DocumentFile, entryPoint: String): Boolean {
        val htmlFile = findDocument(gameRoot, entryPoint) ?: run {
            remove(gameId)
            return false
        }
        val html = readDocument(htmlFile, MAX_HTML_BYTES)?.toString(Charsets.UTF_8) ?: run {
            remove(gameId)
            return false
        }

        for (path in GameFaviconResolver.candidates(html, entryPoint)) {
            val iconDocument = findDocument(gameRoot, path) ?: continue
            val bytes = readDocument(iconDocument, MAX_ICON_BYTES) ?: continue
            val bitmap = decodeSmallBitmap(bytes) ?: continue
            try {
                save(gameId, bitmap)
                return true
            } finally {
                bitmap.recycle()
            }
        }

        remove(gameId)
        return false
    }

    fun remove(gameId: String) {
        cacheFile(gameId).delete()
    }

    private fun findDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        val segments = RelativeGamePath.parse(relativePath) ?: return null
        var current = root
        for ((index, segment) in segments.withIndex()) {
            current = current.findFile(segment) ?: return null
            if (index < segments.lastIndex && !current.isDirectory) return null
        }
        return current.takeIf { it.isFile && it.canRead() }
    }

    private fun readDocument(document: DocumentFile, limit: Int): ByteArray? {
        val stream = runCatching { contentResolver.openInputStream(document.uri) }.getOrNull()
            ?: return null
        return stream.use { input -> readBounded(input, limit) }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeSmallBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > MAX_SOURCE_DIMENSION || bounds.outHeight > MAX_SOURCE_DIMENSION
        ) {
            return null
        }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_OUTPUT_DIMENSION) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null
        val largestSide = maxOf(decoded.width, decoded.height)
        if (largestSide <= MAX_OUTPUT_DIMENSION) return decoded

        val scale = MAX_OUTPUT_DIMENSION.toFloat() / largestSide.toFloat()
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decoded, width, height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun save(gameId: String, bitmap: Bitmap) {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IllegalStateException("Could not create the favicon cache directory.")
        }
        val destination = cacheFile(gameId)
        val temporary = File(cacheDirectory, destination.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("Could not encode the game favicon.")
            }
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun cacheFile(gameId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(gameId.toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return File(cacheDirectory, "$key.png")
    }

    companion object {
        private const val MAX_HTML_BYTES = 1024 * 1024
        private const val MAX_ICON_BYTES = 2 * 1024 * 1024
        private const val MAX_SOURCE_DIMENSION = 16_384
        private const val MAX_OUTPUT_DIMENSION = 192
    }
}
