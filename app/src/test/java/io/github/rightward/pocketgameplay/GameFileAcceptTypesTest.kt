package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFileAcceptTypesTest {
    @Test
    fun preservesMimeAndWildcardAcceptTypes() {
        assertEquals(
            listOf("image/*", "audio/ogg"),
            GameFileAcceptTypes.normalize(listOf("image/*, audio/ogg"))
        )
    }

    @Test
    fun mapsExtensionAcceptTypesToMimeTypes() {
        assertEquals(
            listOf("image/png", "image/jpeg", "text/plain"),
            GameFileAcceptTypes.normalize(listOf(".png,.jpg,.txt"))
        )
    }

    @Test
    fun removesDuplicatesAndUnsupportedExtensions() {
        assertEquals(
            listOf("image/png"),
            GameFileAcceptTypes.normalize(listOf("image/png", ".png", ".unknownext", "invalid"))
        )
    }
}
