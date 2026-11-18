package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZipEntryPathTest {
    @Test
    fun acceptsNestedFilesAndDirectoryEntries() {
        assertEquals("game/assets/player.png", ZipEntryPath.normalize("game/assets/player.png", false))
        assertEquals("game/assets", ZipEntryPath.normalize("game/assets/", true))
    }

    @Test
    fun rejectsTraversalAbsoluteAndDriveQualifiedPaths() {
        assertNull(ZipEntryPath.normalize("../outside.txt", false))
        assertNull(ZipEntryPath.normalize("game/../../outside.txt", false))
        assertNull(ZipEntryPath.normalize("/absolute/file.html", false))
        assertNull(ZipEntryPath.normalize("C:/outside.txt", false))
        assertNull(ZipEntryPath.normalize("game\\outside.txt", false))
    }

    @Test
    fun rejectsEmptyAndAmbiguousSegments() {
        assertNull(ZipEntryPath.normalize("", false))
        assertNull(ZipEntryPath.normalize("game//index.html", false))
        assertNull(ZipEntryPath.normalize("game/./index.html", false))
        assertNull(ZipEntryPath.normalize("game/../index.html", false))
        assertNull(ZipEntryPath.normalize("game/asset\u0000.png", false))
    }
}
