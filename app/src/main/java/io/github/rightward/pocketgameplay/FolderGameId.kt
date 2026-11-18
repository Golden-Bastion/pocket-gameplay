package io.github.rightward.pocketgameplay

import java.util.UUID

object FolderGameId {
    fun create(treeUri: String, entryPoint: String): String {
        require(treeUri.isNotBlank()) { "A folder URI is required." }
        require(RelativeGamePath.parse(entryPoint) != null) { "A safe relative entry point is required." }

        val identity = treeUri + "\n" + entryPoint
        return UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString()
    }
}
