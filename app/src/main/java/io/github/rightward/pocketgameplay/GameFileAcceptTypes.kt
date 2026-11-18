package io.github.rightward.pocketgameplay

import java.util.Locale

internal object GameFileAcceptTypes {
    fun normalize(values: List<String>): List<String> {
        return values
            .flatMap { it.split(',') }
            .map { it.trim().lowercase(Locale.ROOT) }
            .mapNotNull { value ->
                when {
                    value.contains('/') -> value.takeIf { it.count { char -> char == '/' } == 1 }
                    value.startsWith('.') -> {
                        val mimeType = GameAssetMimeTypes.forFileName("selected$value")
                        mimeType.takeUnless { it == "application/octet-stream" }
                    }
                    else -> null
                }
            }
            .distinct()
    }
}
