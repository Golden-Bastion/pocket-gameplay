package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEntryPointScannerTest {
    @Test
    fun acceptsHtmlAndHtmWithoutCaseSensitivity() {
        assertTrue(GameEntryPointScanner.isHtmlFile("index.html"))
        assertTrue(GameEntryPointScanner.isHtmlFile("START.HTM"))
        assertFalse(GameEntryPointScanner.isHtmlFile("game.js"))
        assertFalse(GameEntryPointScanner.isHtmlFile("index.html.backup"))
    }

    @Test
    fun ranksLikelyRootEntryPointsButKeepsArbitraryNames() {
        val ordered = GameEntryPointScanner.orderPaths(
            listOf(
                "assets/index.html",
                "Puzzle Adventure.html",
                "readme.htm",
                "index.html"
            ),
            folderName = "Puzzle Adventure"
        )

        assertEquals(
            listOf(
                "index.html",
                "Puzzle Adventure.html",
                "readme.htm",
                "assets/index.html"
            ),
            ordered
        )
    }

    @Test
    fun keepsEveryDistinctHtmlEntryPoint() {
        assertEquals(
            listOf("start.html", "story.htm"),
            GameEntryPointScanner.orderPaths(
                listOf("story.htm", "start.html", "start.html"),
                folderName = "a-game"
            ).sorted()
        )
    }
}
