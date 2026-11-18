package io.github.rightward.pocketgameplay

import androidx.documentfile.provider.DocumentFile
import java.util.Locale

data class HtmlEntryPoint(val relativePath: String)

object GameEntryPointScanner {
    private const val MAX_DEPTH = 16
    private const val MAX_VISITED_ENTRIES = 10_000

    fun scan(root: DocumentFile): List<HtmlEntryPoint> {
        val discovered = mutableListOf<String>()
        var visitedEntries = 0

        fun visit(directory: DocumentFile, parentPath: String, depth: Int) {
            if (depth > MAX_DEPTH || visitedEntries >= MAX_VISITED_ENTRIES) return

            val children = try {
                directory.listFiles().sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }
            } catch (_: RuntimeException) {
                emptyList()
            }

            for (child in children) {
                if (visitedEntries >= MAX_VISITED_ENTRIES) break
                visitedEntries++

                val name = child.name?.takeIf { it.isNotBlank() } ?: continue
                val relativePath = if (parentPath.isEmpty()) name else "$parentPath/$name"

                when {
                    child.isDirectory -> visit(child, relativePath, depth + 1)
                    child.isFile && isHtmlFile(name) -> discovered += relativePath
                }
            }
        }

        visit(root, parentPath = "", depth = 0)

        return orderPaths(discovered, root.name.orEmpty()).map(::HtmlEntryPoint)
    }

    fun isHtmlFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return extension.equals("html", ignoreCase = true) ||
            extension.equals("htm", ignoreCase = true)
    }

    fun orderPaths(paths: Iterable<String>, folderName: String): List<String> {
        val expectedName = folderName.substringBeforeLast('.', folderName)
            .trim()
            .lowercase(Locale.ROOT)

        return paths.distinct().sortedWith(
            compareBy<String> { priority(it, expectedName) }
                .thenBy { it.count { character -> character == '/' } }
                .thenBy { it.lowercase(Locale.ROOT) }
        )
    }

    private fun priority(path: String, expectedName: String): Int {
        val isRootEntry = '/' !in path
        val fileName = path.substringAfterLast('/').lowercase(Locale.ROOT)
        val nameWithoutExtension = fileName.substringBeforeLast('.')

        return when {
            isRootEntry && fileName == "index.html" -> 0
            isRootEntry && fileName == "index.htm" -> 1
            isRootEntry && expectedName.isNotEmpty() &&
                nameWithoutExtension == expectedName -> 2
            isRootEntry && fileName in setOf("main.html", "main.htm") -> 3
            isRootEntry -> 4
            fileName == "index.html" -> 5
            fileName == "index.htm" -> 6
            else -> 7
        }
    }
}
