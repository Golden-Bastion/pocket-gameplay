package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteRangeParserTest {
    @Test
    fun parsesClosedRange() {
        assertEquals(
            ByteRangeResult.Partial(ByteRange(10, 19)),
            ByteRangeParser.parse("bytes=10-19", 100)
        )
    }

    @Test
    fun parsesOpenEndedRange() {
        assertEquals(
            ByteRangeResult.Partial(ByteRange(90, 99)),
            ByteRangeParser.parse("bytes=90-", 100)
        )
    }

    @Test
    fun parsesSuffixRange() {
        assertEquals(
            ByteRangeResult.Partial(ByteRange(90, 99)),
            ByteRangeParser.parse("bytes=-10", 100)
        )
    }

    @Test
    fun clampsRangeEndToResourceLength() {
        assertEquals(
            ByteRangeResult.Partial(ByteRange(90, 99)),
            ByteRangeParser.parse("bytes=90-500", 100)
        )
    }

    @Test
    fun suffixLongerThanResourceReturnsEntireResource() {
        assertEquals(
            ByteRangeResult.Partial(ByteRange(0, 99)),
            ByteRangeParser.parse("bytes=-500", 100)
        )
    }

    @Test
    fun rejectsUnsatisfiableRanges() {
        assertEquals(ByteRangeResult.Unsatisfiable, ByteRangeParser.parse("bytes=100-", 100))
        assertEquals(ByteRangeResult.Unsatisfiable, ByteRangeParser.parse("bytes=20-10", 100))
        assertEquals(ByteRangeResult.Unsatisfiable, ByteRangeParser.parse("bytes=-0", 100))
        assertEquals(ByteRangeResult.Unsatisfiable, ByteRangeParser.parse("bytes=0-", 0))
    }

    @Test
    fun ignoresMalformedOrMultiRangeHeaders() {
        assertEquals(ByteRangeResult.Ignore, ByteRangeParser.parse("items=1-2", 100))
        assertEquals(ByteRangeResult.Ignore, ByteRangeParser.parse("bytes=abc-def", 100))
        assertEquals(ByteRangeResult.Ignore, ByteRangeParser.parse("bytes=1-2,5-8", 100))
        assertEquals(ByteRangeResult.Ignore, ByteRangeParser.parse("bytes=-", 100))
    }

    @Test
    fun ignoresOverflowingNumbers() {
        assertEquals(
            ByteRangeResult.Ignore,
            ByteRangeParser.parse("bytes=999999999999999999999999-", 100)
        )
    }
}
