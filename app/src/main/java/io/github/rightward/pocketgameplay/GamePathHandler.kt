package io.github.rightward.pocketgameplay

import android.content.ContentResolver
import android.webkit.WebResourceResponse
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class GamePathHandler(
    private val contentResolver: ContentResolver
) {
    @Volatile
    private var gameRoot: DocumentFile? = null

    fun setGameRoot(root: DocumentFile) {
        gameRoot = root
    }

    fun handle(path: String): WebResourceResponse {
        return handle(path, rangeHeader = null, method = "GET")
    }

    fun handle(
        path: String,
        rangeHeader: String?,
        method: String
    ): WebResourceResponse {
        if (!method.equals("GET", ignoreCase = true) &&
            !method.equals("HEAD", ignoreCase = true)
        ) {
            return errorResponse(405, "Method Not Allowed", mapOf("Allow" to "GET, HEAD"))
        }

        val document = resolveDocument(path) ?: return notFound()
        val contentLength = knownLength(document)
        val isHead = method.equals("HEAD", ignoreCase = true)
        val rangeResult = if (isHead || contentLength < 0) {
            ByteRangeResult.Ignore
        } else {
            ByteRangeParser.parse(rangeHeader, contentLength)
        }

        if (rangeResult == ByteRangeResult.Unsatisfiable) {
            return errorResponse(
                statusCode = 416,
                reason = "Range Not Satisfiable",
                headers = mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes */$contentLength"
                )
            )
        }

        val mimeType = GameAssetMimeTypes.forFileName(path.substringAfterLast('/'))
        val encoding = if (GameAssetMimeTypes.isTextual(mimeType)) "UTF-8" else null
        val responseHeaders = linkedMapOf("Cache-Control" to "no-store")
        if (contentLength >= 0) responseHeaders["Accept-Ranges"] = "bytes"

        val statusCode: Int
        val reasonPhrase: String
        val bodyLength: Long
        val bodyStream: InputStream

        when (rangeResult) {
            is ByteRangeResult.Partial -> {
                val range = rangeResult.range
                statusCode = 206
                reasonPhrase = "Partial Content"
                bodyLength = range.length
                responseHeaders["Content-Range"] =
                    "bytes ${range.start}-${range.endInclusive}/$contentLength"
                responseHeaders["Content-Length"] = bodyLength.toString()

                bodyStream = if (isHead) {
                    ByteArrayInputStream(ByteArray(0))
                } else {
                    val source = openDocument(document)
                        ?: return errorResponse(500, "Could Not Open Local File")
                    try {
                        skipFully(source, range.start)
                        BoundedInputStream(source, bodyLength)
                    } catch (_: IOException) {
                        source.close()
                        return errorResponse(500, "Could Not Read Local File")
                    }
                }
            }

            ByteRangeResult.Ignore,
            ByteRangeResult.Unsatisfiable -> {
                statusCode = 200
                reasonPhrase = "OK"
                bodyLength = contentLength
                if (contentLength >= 0) {
                    responseHeaders["Content-Length"] = contentLength.toString()
                }
                bodyStream = if (isHead) {
                    ByteArrayInputStream(ByteArray(0))
                } else {
                    openDocument(document) ?: return notFound()
                }
            }
        }

        return WebResourceResponse(
            mimeType,
            encoding,
            statusCode,
            reasonPhrase,
            responseHeaders,
            bodyStream
        )
    }

    fun resolveDocument(path: String): DocumentFile? {
        val segments = RelativeGamePath.parse(path) ?: return null
        var document = gameRoot ?: return null

        for ((index, segment) in segments.withIndex()) {
            document = document.findFile(segment) ?: return null
            if (index < segments.lastIndex && !document.isDirectory) return null
        }

        return document.takeIf { it.isFile && it.canRead() }
    }

    private fun knownLength(document: DocumentFile): Long {
        val documentLength = runCatching { document.length() }.getOrDefault(-1L)
        if (documentLength > 0) return documentLength

        val descriptorLength = runCatching {
            contentResolver.openAssetFileDescriptor(document.uri, "r")?.use { it.length }
        }.getOrNull()

        return when {
            descriptorLength != null && descriptorLength >= 0 -> descriptorLength
            descriptorLength != null -> -1L
            documentLength >= 0 -> documentLength
            else -> -1L
        }
    }

    private fun openDocument(document: DocumentFile): InputStream? {
        return try {
            contentResolver.openInputStream(document.uri)
        } catch (_: Exception) {
            null
        }
    }

    @Throws(IOException::class)
    private fun skipFully(input: InputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw EOFException("Unexpected end of local file while seeking.")
                remaining -= read
            }
        }
    }

    private fun notFound(): WebResourceResponse = errorResponse(404, "Not Found")

    companion object {
        fun errorResponse(
            statusCode: Int,
            reason: String,
            headers: Map<String, String> = emptyMap()
        ): WebResourceResponse {
            val responseHeaders = linkedMapOf("Cache-Control" to "no-store")
            responseHeaders.putAll(headers)
            val body: InputStream = ByteArrayInputStream(ByteArray(0))
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                statusCode,
                reason,
                responseHeaders,
                body
            )
        }
    }
}
