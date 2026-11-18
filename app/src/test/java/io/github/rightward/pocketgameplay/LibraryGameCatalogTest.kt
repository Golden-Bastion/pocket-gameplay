package io.github.rightward.pocketgameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGameCatalogTest {
    @Test
    fun searchesTitleAndEntryPointWithoutChangingStoredGames() {
        val games = listOf(game("one", "River Quest", "pages/start.html"), game("two", "Stone Keep", "index.html"))

        assertEquals(listOf("one"), LibraryGameCatalog.filterAndSort(games, "river", LibrarySortOrder.NAME).map { it.id })
        assertEquals(listOf("one"), LibraryGameCatalog.filterAndSort(games, "pages/start", LibrarySortOrder.NAME).map { it.id })
        assertEquals(listOf("one", "two"), games.map { it.id })
    }

    @Test
    fun alphabeticalSortUsesStableIdTieBreak() {
        val games = listOf(game("z", "Same", "index.html"), game("a", "same", "index.html"))

        assertEquals(
            listOf("a", "z"),
            LibraryGameCatalog.filterAndSort(games, "", LibrarySortOrder.NAME).map { it.id }
        )
    }

    @Test
    fun recentlyAddedSortUsesNewestFirstWithStableTieBreaks() {
        val games = listOf(
            game("older", "Older", "index.html", 10L),
            game("z", "Same", "index.html", 20L),
            game("a", "same", "index.html", 20L)
        )

        assertEquals(
            listOf("a", "z", "older"),
            LibraryGameCatalog.filterAndSort(games, "", LibrarySortOrder.RECENTLY_ADDED).map { it.id }
        )
    }

    @Test
    fun browserProfilesAreStableAndDistinctPerGame() {
        val first = GameBrowserProfile.forGameId("game-one")
        val second = GameBrowserProfile.forGameId("game-two")

        assertEquals(first, GameBrowserProfile.forGameId("game-one"))
        assertTrueProfileName(first)
        org.junit.Assert.assertNotEquals(first, second)
    }

    private fun assertTrueProfileName(value: String) {
        org.junit.Assert.assertTrue(value.matches(Regex("pocket-game-[0-9a-f-]{36}")))
    }

    private fun game(
        id: String,
        title: String,
        entryPoint: String,
        importedAt: Long = 0L
    ) = LibraryGame(
        id = id,
        title = title,
        directoryName = id,
        rootPath = "",
        entryPoint = entryPoint,
        importedAtEpochMillis = importedAt
    )
}
