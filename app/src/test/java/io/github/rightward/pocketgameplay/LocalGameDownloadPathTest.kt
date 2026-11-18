package io.github.rightward.pocketgameplay

import android.net.Uri
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
                Uri.parse("https://$host/game/audio/theme.mp3"),
                host
            )
        )
        assertEquals(
            "exports/save.json",
            LocalGameDownloadPath.resolve(
                Uri.parse("https://$host/exports/save.json"),
                host
            )
        )
    }

    @Test
    fun decodesUrlPathOnce() {
        assertEquals(
            "assets/my%20sprite.png",
            LocalGameDownloadPath.resolve(
                Uri.parse("https://$host/assets/my%2520sprite.png"),
                host
            )
        )
    }

    @Test
    fun refusesExternalOrAppOwnedPaths() {
        assertNull(
            LocalGameDownloadPath.resolve(
                Uri.parse("https://example.com/assets/game.zip"),
                host
            )
        )
        assertNull(
            LocalGameDownloadPath.resolve(
                Uri.parse("https://$host/__pocket/eruda.js"),
                host
            )
        )
    }

    @Test
    fun refusesTraversalPaths() {
        assertNull(
            LocalGameDownloadPath.resolve(
                Uri.parse("https://$host/assets/%2e%2e/private.zip"),
                host
            )
        )
    }
}
