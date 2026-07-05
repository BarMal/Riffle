package com.riffle.app.launcher.apps

internal fun String.normalizedPlatformLabel(fallback: String): String = normalizedPlatformLabelOrNull() ?: fallback

internal fun String?.normalizedOptionalPlatformLabel(): String? = this?.normalizedPlatformLabelOrNull()

private fun String.normalizedPlatformLabelOrNull(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null

    return buildString {
        var pendingWhitespace = false
        trimmed.forEach { character ->
            if (character.isWhitespace()) {
                pendingWhitespace = true
            } else {
                if (pendingWhitespace && isNotEmpty()) append(' ')
                append(character)
                pendingWhitespace = false
            }
        }
    }
}
