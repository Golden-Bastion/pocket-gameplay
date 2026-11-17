package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeGamePathTest {
    @Test
    fun parsesNestedRelativePaths() {
        assertEquals(
            listOf("assets", "images", "player sprite.png"),
            RelativeGamePath.parse("assets/images/player%20sprite.png")
        )
    }

    @Test
    fun preservesPlusSignsInFileNames() {
        assertEquals(listOf("sound+music.ogg"), RelativeGamePath.parse("sound+music.ogg"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertNull(RelativeGamePath.parse("../outside.txt"))
        assertNull(RelativeGamePath.parse("%2e%2e/outside.txt"))
        assertNull(RelativeGamePath.parse("/absolute/path.html"))
    }

    @Test
    fun rejectsMalformedAndAmbiguousPaths() {
        assertNull(RelativeGamePath.parse("assets//image.png"))
        assertNull(RelativeGamePath.parse("assets/%5Coutside.png"))
        assertNull(RelativeGamePath.parse("assets/%00hidden.png"))
        assertNull(RelativeGamePath.parse("assets/%GG.png"))
    }
}
