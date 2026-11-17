package io.github.rightward.pocketgameplay

/**
 * Validates a path that has already been URL-decoded by Android's Uri API.
 *
 * The caller must pass Uri.path (not Uri.encodedPath). Decoding a second time would
 * corrupt legitimate filenames containing percent characters and can create ambiguity.
 */
object RelativeGamePath {
    fun parse(path: String): List<String>? {
        if (path.isBlank() || path.startsWith('/') || '\\' in path || '\u0000' in path) {
            return null
        }

        val segments = path.split('/')
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
