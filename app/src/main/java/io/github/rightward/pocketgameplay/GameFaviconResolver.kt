package io.github.rightward.pocketgameplay

import java.net.URI

/**
 * Finds same-game-origin icon files referenced by a game's selected HTML document.
 *
 * Network URLs, data URIs, and paths that escape the game root are intentionally ignored:
 * Pocket Gameplay is an offline player and must never fetch a remote favicon.
 */
object GameFaviconResolver {
    private val linkTag = Regex("(?is)<link\\b[^>]*>")
    private val attribute = Regex(
        """(?is)([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))"""
    )
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun candidates(html: String, entryPoint: String): List<String> {
        if (RelativeGamePath.parse(entryPoint) == null) return emptyList()

        val base = runCatching {
            URI(null, null, "/$entryPoint", null)
        }.getOrNull() ?: return emptyList()

        val declared = mutableListOf<Pair<Int, String>>()
        linkTag.findAll(html).forEach { match ->
            val attributes = attribute.findAll(match.value).associate { item ->
                val name = item.groupValues[1].lowercase()
                val value = item.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
                name to value
            }
            val relTokens = attributes["rel"]
                ?.lowercase()
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val priority = when {
                "icon" in relTokens -> 0
                "apple-touch-icon" in relTokens || "apple-touch-icon-precomposed" in relTokens -> 1
                else -> return@forEach
            }
            val href = attributes["href"]?.trim().orEmpty()
            val resolved = resolveLocalPath(base, href) ?: return@forEach
            declared += priority to resolved
        }

        val result = linkedSetOf<String>()
        declared.sortedBy { it.first }.forEach { result += it.second }
        result += "favicon.ico"

        val pageDirectory = entryPoint.substringBeforeLast('/', missingDelimiterValue = "")
        if (pageDirectory.isNotEmpty()) result += "$pageDirectory/favicon.ico"

        return result.toList()
    }

    private fun resolveLocalPath(base: URI, href: String): String? {
        if (href.isBlank() || href.startsWith("//") || scheme.containsMatchIn(href)) {
            return null
        }
        if ('\\' in href || '\u0000' in href) return null

        // Query strings and fragments do not identify another file in the game directory.
        val pathOnly = href.substringBefore('#').substringBefore('?')
        if (pathOnly.isBlank()) return null

        val target = runCatching { URI(null, null, pathOnly, null) }.getOrNull() ?: return null
        val resolved = runCatching { base.resolve(target).normalize() }.getOrNull() ?: return null
        if (resolved.isAbsolute || resolved.rawAuthority != null) return null

        val absolutePath = resolved.path ?: return null
        if (!absolutePath.startsWith('/')) return null
        val relativePath = absolutePath.removePrefix("/")
        val segments = RelativeGamePath.parse(relativePath) ?: return null
        return segments.joinToString("/")
    }
}
