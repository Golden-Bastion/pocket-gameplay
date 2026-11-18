package io.github.rightward.pocketgameplay

import java.util.Locale
import java.util.UUID

object GameOrigin {
    private const val APP_ASSET_DOMAIN = "appassets.androidplatform.net"

    fun forFolderUri(uri: String): String {
        return UUID.nameUUIDFromBytes(uri.toByteArray(Charsets.UTF_8)).toString()
    }

    fun domainFor(storageKey: String): String {
        val safeKey = storageKey.lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            .trim('-')

        require(safeKey.isNotBlank()) { "A game origin key must contain letters or digits." }
        return "game-$safeKey.$APP_ASSET_DOMAIN"
    }
}
