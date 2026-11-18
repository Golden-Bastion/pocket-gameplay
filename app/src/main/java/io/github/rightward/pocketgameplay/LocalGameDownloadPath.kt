package io.github.rightward.pocketgameplay

internal object LocalGameDownloadPath {
    fun resolve(
        scheme: String?,
        host: String?,
        port: Int,
        path: String?,
        expectedHost: String
    ): String? {
        if (!scheme.equals("https", ignoreCase = true) ||
            !host.equals(expectedHost, ignoreCase = true) ||
            (port != -1 && port != 443)
        ) {
            return null
        }

        val decodedPath = path ?: return null
        val relativePath = when {
            decodedPath.startsWith(OfflineGameWebViewClient.GAME_PATH_PREFIX) ->
                decodedPath.removePrefix(OfflineGameWebViewClient.GAME_PATH_PREFIX)
            decodedPath.startsWith(DeveloperConsole.ASSET_PATH_PREFIX) -> return null
            else -> decodedPath.removePrefix("/")
        }

        return RelativeGamePath.parse(relativePath)?.joinToString("/")
    }
}
