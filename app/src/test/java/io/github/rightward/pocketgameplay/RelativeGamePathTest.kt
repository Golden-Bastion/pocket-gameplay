package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeGamePathTest {
    @Test
    fun parsesNestedPathsWithDecodedSpaces() {
        assertEquals(
            listOf("assets", "images", "player sprite.png"),
            RelativeGamePath.parse("assets/images/player sprite.png")
        )
    }

    @Test
    fun preservesLiteralPercentSequencesInFileNames() {
        assertEquals(
            listOf("release%20notes.html"),
            RelativeGamePath.parse("release%20notes.html")
        )
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertNull(RelativeGamePath.parse("../outside.txt"))
        assertNull(RelativeGamePath.parse("/absolute/path.html"))
        assertNull(RelativeGamePath.parse("assets/./image.png"))
    }

    @Test
    fun rejectsMalformedAndAmbiguousPaths() {
        assertNull(RelativeGamePath.parse("assets//image.png"))
        assertNull(RelativeGamePath.parse("assets/\\\\outside.png"))
        assertNull(RelativeGamePath.parse("assets/\u0000hidden.png"))
    }
}
