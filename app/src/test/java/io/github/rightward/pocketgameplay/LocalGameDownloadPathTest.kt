package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalGameDownloadPathTest {
    private val host = "game-123.appassets.androidplatform.net"

    @Test
    fun resolvesSelectedEntryPointAndRootRelativeFiles() {
        assertEquals(
            "audio/theme.mp3",
            LocalGameDownloadPath.resolve(
                "https", host, -1, "/game/audio/theme.mp3", host
            )
        )
        assertEquals(
            "exports/save.json",
            LocalGameDownloadPath.resolve(
                "https", host, 443, "/exports/save.json", host
            )
        )
    }

    @Test
    fun acceptsAndroidUriDecodedPathWithoutDecodingTwice() {
        assertEquals(
            "assets/my%20sprite.png",
            LocalGameDownloadPath.resolve(
                "https", host, -1, "/assets/my%20sprite.png", host
            )
        )
    }

    @Test
    fun refusesExternalOrAppOwnedPaths() {
        assertNull(
            LocalGameDownloadPath.resolve(
                "https", "example.com", -1, "/assets/game.zip", host
            )
        )
        assertNull(
            LocalGameDownloadPath.resolve(
                "https", host, -1, "/__pocket/eruda.js", host
            )
        )
    }

    @Test
    fun refusesTraversalPaths() {
        assertNull(
            LocalGameDownloadPath.resolve(
                "https", host, -1, "/assets/../private.zip", host
            )
        )
    }

    @Test
    fun refusesNonHttpsAndNonstandardPorts() {
        assertNull(LocalGameDownloadPath.resolve("http", host, -1, "/data.zip", host))
        assertNull(LocalGameDownloadPath.resolve("https", host, 8080, "/data.zip", host))
    }
}
