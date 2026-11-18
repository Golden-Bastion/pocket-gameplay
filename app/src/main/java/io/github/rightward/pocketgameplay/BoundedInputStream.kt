package io.github.rightward.pocketgameplay

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Exposes no more than [limit] bytes from [input], then reports end-of-stream.
 */
internal class BoundedInputStream(
    input: InputStream,
    limit: Long
) : FilterInputStream(input) {
    private var remaining = limit.also { require(it >= 0) }

    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > buffer.size - offset) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) return 0
        if (remaining <= 0) return -1

        val allowed = minOf(length.toLong(), remaining).toInt()
        val count = super.read(buffer, offset, allowed)
        if (count > 0) remaining -= count
        return count
    }

    override fun skip(byteCount: Long): Long {
        if (byteCount <= 0 || remaining <= 0) return 0

        var toSkip = minOf(byteCount, remaining)
        var skipped = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (toSkip > 0) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), toSkip).toInt())
            when {
                count > 0 -> {
                    skipped += count
                    toSkip -= count
                }
                count < 0 -> break
                else -> {
                    if (read() < 0) break
                    skipped++
                    toSkip--
                }
            }
        }

        return skipped
    }

    override fun available(): Int {
        return minOf(super.available().toLong(), remaining).toInt()
    }

    override fun markSupported(): Boolean = false

    override fun mark(readlimit: Int) {
        // Mark/reset is intentionally unsupported because the visible limit is stateful.
    }

    override fun reset() {
        throw IOException("Mark/reset is not supported by BoundedInputStream.")
    }
}
