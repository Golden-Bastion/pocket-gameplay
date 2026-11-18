package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFaviconResolverTest {
    @Test
    fun resolvesRelativeFaviconAgainstSelectedHtmlFile() {
        val candidates = GameFaviconResolver.candidates(
            """<html><head><link rel="icon" type="image/png" href="../assets/game%20icon.png"></head></html>""",
            "pages/start.html"
        )

        assertEquals("assets/game icon.png", candidates.first())
    }

    @Test
    fun resolvesRootRelativeFaviconFromGameRoot() {
        val candidates = GameFaviconResolver.candidates(
            """<link href="/art/favicon.png?v=2#icon" rel="icon">""",
            "nested/game.html"
        )

        assertEquals("art/favicon.png", candidates.first())
    }

    @Test
    fun prefersRegularIconBeforeAppleTouchIcon() {
        val candidates = GameFaviconResolver.candidates(
            """
                <link rel="apple-touch-icon" href="touch.png">
                <link rel="shortcut icon" href="favicon.ico">
            """.trimIndent(),
            "index.html"
        )

        assertEquals("favicon.ico", candidates.first())
        assertEquals("touch.png", candidates[1])
    }

    @Test
    fun ignoresRemoteDataAndPathTraversalReferences() {
        val candidates = GameFaviconResolver.candidates(
            """
                <link rel="icon" href="https://example.com/icon.png">
                <link rel="icon" href="//example.com/icon.png">
                <link rel="icon" href="data:image/png;base64,AAAA">
                <link rel="icon" href="../../../outside.png">
                <link rel="icon" href="assets\\\\outside.png">
            """.trimIndent(),
            "pages/start.html"
        )

        assertEquals(listOf("favicon.ico", "pages/favicon.ico"), candidates)
    }

    @Test
    fun fallsBackToConventionalFaviconWhenNoLinkExists() {
        assertEquals(
            listOf("favicon.ico"),
            GameFaviconResolver.candidates("<html><head></head></html>", "index.html")
        )
    }

    @Test
    fun rejectsInvalidEntryPoint() {
        assertEquals(
            emptyList<String>(),
            GameFaviconResolver.candidates(
                """<link rel="icon" href="favicon.png">""",
                "../index.html"
            )
        )
    }
}
