package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAssetMimeTypesTest {
    @Test
    fun mapsCommonWebRuntimeTypes() {
        assertEquals("text/html", GameAssetMimeTypes.forFileName("index.HTML"))
        assertEquals("text/javascript", GameAssetMimeTypes.forFileName("entry.mjs"))
        assertEquals("application/json", GameAssetMimeTypes.forFileName("level.map"))
        assertEquals("application/wasm", GameAssetMimeTypes.forFileName("engine.wasm"))
        assertEquals("image/svg+xml", GameAssetMimeTypes.forFileName("sprite.svg"))
        assertEquals("font/woff2", GameAssetMimeTypes.forFileName("game.woff2"))
    }

    @Test
    fun mapsCommonAudioAndVideoTypes() {
        assertEquals("audio/mpeg", GameAssetMimeTypes.forFileName("theme.mp3"))
        assertEquals("audio/ogg", GameAssetMimeTypes.forFileName("voice.opus"))
        assertEquals("audio/wav", GameAssetMimeTypes.forFileName("sound.wav"))
        assertEquals("video/mp4", GameAssetMimeTypes.forFileName("intro.m4v"))
        assertEquals("video/webm", GameAssetMimeTypes.forFileName("cutscene.webm"))
    }

    @Test
    fun usesBinaryFallbackForUnknownExtensions() {
        assertEquals("application/octet-stream", GameAssetMimeTypes.forFileName("archive.unknownext"))
    }

    @Test
    fun identifiesTextResourcesForUtf8Encoding() {
        assertTrue(GameAssetMimeTypes.isTextual("text/javascript"))
        assertTrue(GameAssetMimeTypes.isTextual("application/json"))
        assertTrue(GameAssetMimeTypes.isTextual("image/svg+xml"))
    }
}
