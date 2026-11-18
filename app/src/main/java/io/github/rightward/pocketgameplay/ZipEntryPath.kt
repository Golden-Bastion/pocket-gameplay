package io.github.rightward.pocketgameplay

/**
 * Validates ZIP entry names before mapping them to paths on the app's private filesystem.
 *
 * ZIP entry names use forward slashes regardless of the host platform. Backslashes and
 * platform-specific absolute paths are rejected instead of being interpreted.
 */
object ZipEntryPath {
    const val MAX_PATH_LENGTH = 1_024

    fun normalize(entryName: String, isDirectory: Boolean): String? {
        if (entryName.isBlank() || entryName.length > MAX_PATH_LENGTH) return null
        if (entryName.startsWith('/') || '\\' in entryName || '\u0000' in entryName) return null

        val normalized = if (isDirectory && entryName.endsWith('/')) {
            entryName.dropLast(1)
        } else {
            entryName
        }
        if (normalized.isBlank()) return null

        val segments = normalized.split('/')
        if (segments.any { segment ->
                segment.isEmpty() || segment == "." || segment == ".." ||
                    '\\' in segment || '\u0000' in segment
            }
        ) {
            return null
        }

        // Reject Windows drive-qualified names even though extraction runs on Android.
        if (segments.first().matches(Regex("^[A-Za-z]:.*"))) return null

        return segments.joinToString("/")
    }
}
