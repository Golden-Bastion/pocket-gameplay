package io.github.rightward.pocketgameplay

import java.util.Locale
import java.util.UUID

enum class LibrarySortOrder {
    NAME,
    RECENTLY_ADDED
}

object LibraryGameCatalog {
    fun supportsZipReplacement(game: LibraryGame): Boolean =
        game.storageType == LibraryStorageType.IMPORTED

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
    fun forGameId(gameId: String, generation: Int = 0): String {
        require(gameId.isNotBlank()) { "A game ID is required." }
        require(generation >= 0) { "A profile generation cannot be negative." }
        val identity = if (generation == 0) {
            "pocket-game-profile:" + gameId
        } else {
            "pocket-game-profile:" + gameId + ":" + generation
        }
        val token = UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))
        return "pocket-game-$token"
    }

    fun isValidProfileName(profileName: String): Boolean {
        val prefix = "pocket-game-"
        if (!profileName.startsWith(prefix)) return false
        val token = profileName.removePrefix(prefix)
        return runCatching {
            UUID.fromString(token).toString() == token
        }.getOrDefault(false)
    }
}
