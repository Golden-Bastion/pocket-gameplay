package io.github.rightward.pocketgameplay

import android.net.Uri

internal object LocalGameDownloadPath {
    fun resolve(uri: Uri, expectedHost: String): String? {
        if (uri.scheme != "https" ||
            !uri.host.equals(expectedHost, ignoreCase = true) ||
            (uri.port != -1 && uri.port != 443)
        ) {
            return null
        }

        val path = uri.path ?: return null
        val relativePath = when {
            path.startsWith(OfflineGameWebViewClient.GAME_PATH_PREFIX) ->
                path.removePrefix(OfflineGameWebViewClient.GAME_PATH_PREFIX)
            path.startsWith(DeveloperConsole.ASSET_PATH_PREFIX) -> return null
            else -> path.removePrefix("/")
        }

        return RelativeGamePath.parse(relativePath)?.joinToString("/")
    }
}
