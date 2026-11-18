package io.github.rightward.pocketgameplay

import android.content.ContentResolver
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

class GamePathHandler(
    private val contentResolver: ContentResolver
) : WebViewAssetLoader.PathHandler {
    @Volatile
    private var gameRoot: DocumentFile? = null

    fun setGameRoot(root: DocumentFile) {
        gameRoot = root
    }

    override fun handle(path: String): WebResourceResponse {
        val segments = RelativeGamePath.parse(path) ?: return notFound()
        var document = gameRoot ?: return notFound()

        for ((index, segment) in segments.withIndex()) {
            document = document.findFile(segment) ?: return notFound()
            if (index < segments.lastIndex && !document.isDirectory) return notFound()
        }

        if (!document.isFile || !document.canRead()) return notFound()

        val stream = try {
            contentResolver.openInputStream(document.uri)
        } catch (_: Exception) {
            null
        } ?: return notFound()

        val mimeType = mimeTypeFor(segments.last())
        val encoding = if (isTextual(mimeType)) "UTF-8" else null

        return WebResourceResponse(
            mimeType,
            encoding,
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            stream
        )
    }

    private fun mimeTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json", "map" -> "application/json"
            "svg" -> "image/svg+xml"
            "wasm" -> "application/wasm"
            "xml" -> "application/xml"
            "txt" -> "text/plain"
            else -> {
                val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"
            }
        }
    }

    private fun isTextual(mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
            mimeType in setOf(
                "application/javascript",
                "application/json",
                "application/xml",
                "image/svg+xml"
            )
    }

    private fun notFound(): WebResourceResponse = errorResponse(404, "Not Found")

    companion object {
        fun errorResponse(statusCode: Int, reason: String): WebResourceResponse {
            val body: InputStream = ByteArrayInputStream(ByteArray(0))
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                statusCode,
                reason,
                mapOf("Cache-Control" to "no-store"),
                body
            )
        }
    }
}
