package io.github.rightward.pocketgameplay

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Converts a WebView path suffix into safe, decoded path segments below the selected game root.
 *
 * This is deliberately independent of Android classes so path traversal rules can be unit tested.
 */
object RelativeGamePath {
    fun parse(path: String): List<String>? {
        if (path.isBlank() || path.startsWith('/')) return null

        val decoded = try {
            // A plus sign is a literal path character, not a form-encoded space.
            URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (decoded.isBlank() || decoded.startsWith('/') || '\\' in decoded || '\u0000' in decoded) {
            return null
        }

        val segments = decoded.split('/')
        if (segments.any { segment ->
                segment.isEmpty() || segment == "." || segment == ".." ||
                    '\\' in segment || '\u0000' in segment
            }
        ) {
            return null
        }

        return segments
    }
}
