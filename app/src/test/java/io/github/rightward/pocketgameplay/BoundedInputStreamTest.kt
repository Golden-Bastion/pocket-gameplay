package io.github.rightward.pocketgameplay

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedInputStreamTest {
    @Test
    fun neverReadsBeyondConfiguredLength() {
        val stream = BoundedInputStream(ByteArrayInputStream(byteArrayOf(0, 1, 2, 3, 4)), 3)
        assertArrayEquals(byteArrayOf(0, 1, 2), stream.readBytes())
        assertEquals(-1, stream.read())
    }

    @Test
    fun limitsBulkReadsAndAvailableCount() {
        val stream = BoundedInputStream(ByteArrayInputStream(byteArrayOf(10, 11, 12, 13)), 2)
        val buffer = ByteArray(8)

        assertEquals(2, stream.available())
        assertEquals(2, stream.read(buffer, 0, buffer.size))
        assertArrayEquals(byteArrayOf(10, 11), buffer.copyOfRange(0, 2))
        assertEquals(0, stream.available())
        assertEquals(-1, stream.read(buffer, 0, buffer.size))
    }

    @Test
    fun allowsZeroLengthReadAndBoundsSkip() {
        val stream = BoundedInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        assertEquals(0, stream.read(ByteArray(1), 0, 0))
        assertEquals(2, stream.skip(2))
        assertEquals(1, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun rejectsNegativeLimits() {
        val rejected = runCatching {
            BoundedInputStream(ByteArrayInputStream(byteArrayOf(1)), -1)
        }.isFailure
        assertTrue(rejected)
    }
}
