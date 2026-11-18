package io.github.rightward.pocketgameplay

import java.util.Locale
import java.util.UUID

enum class LibrarySortOrder {
    NAME,
    RECENTLY_ADDED
}

object LibraryGameCatalog {
    fun filterAndSort(
        games: List<LibraryGame>,
        query: String,
        sortOrder: LibrarySortOrder
    ): List<LibraryGame> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val matching = games.filter { game ->
            normalizedQuery.isEmpty() ||
                game.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                game.entryPoint.lowercase(Locale.ROOT).contains(normalizedQuery)
        }

        return when (sortOrder) {
            LibrarySortOrder.NAME -> matching.sortedWith(
                compareBy<LibraryGame> { it.title.trim().lowercase(Locale.ROOT) }
                    .thenBy { it.id }
            )
            LibrarySortOrder.RECENTLY_ADDED -> matching.sortedWith(
                compareByDescending<LibraryGame> { it.importedAtEpochMillis }
                    .thenBy { it.title.trim().lowercase(Locale.ROOT) }
                    .thenBy { it.id }
            )
        }
    }
}

/**
 * Stable per-game WebView profile identity.
 *
 * The profile is deliberately derived from the immutable library ID, not the display title,
 * folder URI, or imported directory's display name.
 */
object GameBrowserProfile {
    fun forGameId(gameId: String): String {
        require(gameId.isNotBlank()) { "A game ID is required." }
        val token = UUID.nameUUIDFromBytes(
            ("pocket-game-profile:" + gameId).toByteArray(Charsets.UTF_8)
        )
        return "pocket-game-$token"
    }
}
