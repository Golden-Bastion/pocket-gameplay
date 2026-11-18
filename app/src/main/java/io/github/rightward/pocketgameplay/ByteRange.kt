package io.github.rightward.pocketgameplay

internal data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long
        get() = endInclusive - start + 1
}

internal sealed interface ByteRangeResult {
    data object Ignore : ByteRangeResult
    data object Unsatisfiable : ByteRangeResult
    data class Partial(val range: ByteRange) : ByteRangeResult
}

internal object ByteRangeParser {
    fun parse(header: String?, resourceLength: Long): ByteRangeResult {
        if (header.isNullOrBlank()) return ByteRangeResult.Ignore
        val normalized = header.trim()
        if (!normalized.startsWith("bytes=", ignoreCase = true)) {
            return ByteRangeResult.Ignore
        }

        val specifier = normalized.substringAfter('=').trim()
        if (',' in specifier) return ByteRangeResult.Ignore

        val match = Regex("^(\\d*)-(\\d*)$").matchEntire(specifier)
            ?: return ByteRangeResult.Ignore
        val first = match.groupValues[1]
        val last = match.groupValues[2]
        if (first.isEmpty() && last.isEmpty()) return ByteRangeResult.Ignore

        val start = first.toLongOrNull()
        val end = last.toLongOrNull()
        if ((first.isNotEmpty() && start == null) || (last.isNotEmpty() && end == null)) {
            return ByteRangeResult.Ignore
        }
        if (resourceLength <= 0) return ByteRangeResult.Unsatisfiable

        val resolved = when {
            first.isEmpty() -> {
                val suffixLength = end ?: return ByteRangeResult.Ignore
                if (suffixLength <= 0) return ByteRangeResult.Unsatisfiable
                val actualLength = minOf(suffixLength, resourceLength)
                ByteRange(resourceLength - actualLength, resourceLength - 1)
            }
            else -> {
                val rangeStart = start ?: return ByteRangeResult.Ignore
                if (rangeStart >= resourceLength) return ByteRangeResult.Unsatisfiable
                val rangeEnd = end ?: (resourceLength - 1)
                if (rangeEnd < rangeStart) return ByteRangeResult.Unsatisfiable
                ByteRange(rangeStart, minOf(rangeEnd, resourceLength - 1))
            }
        }

        return ByteRangeResult.Partial(resolved)
    }
}
