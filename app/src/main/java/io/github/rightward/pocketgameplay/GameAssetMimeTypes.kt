package io.github.rightward.pocketgameplay

import android.webkit.MimeTypeMap
import java.util.Locale

internal object GameAssetMimeTypes {
    private val explicitTypes = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "cjs" to "text/javascript",
        "json" to "application/json",
        "map" to "application/json",
        "wasm" to "application/wasm",
        "svg" to "image/svg+xml",
        "xml" to "application/xml",
        "txt" to "text/plain",
        "webmanifest" to "application/manifest+json",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "eot" to "application/vnd.ms-fontobject",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "m4b" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "ogv" to "video/ogg",
        "mov" to "video/quicktime",
        "m3u8" to "application/vnd.apple.mpegurl",
        "mpd" to "application/dash+xml",
        "ts" to "video/mp2t",
        "avif" to "image/avif",
        "webp" to "image/webp",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "ico" to "image/x-icon"
    )

    fun forFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return explicitTypes[extension]
            ?: MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    fun isTextual(mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
            mimeType in setOf(
                "application/javascript",
                "text/javascript",
                "application/json",
                "application/manifest+json",
                "application/xml",
                "image/svg+xml"
            )
    }
}
