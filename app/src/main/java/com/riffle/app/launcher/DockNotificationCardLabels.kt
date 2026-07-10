package com.riffle.app.launcher

internal fun dockNotificationCardLabel(card: DockNotificationCardState): String =
    card.app?.label ?: card.group.packageName.value.dockNotificationFallbackLabel()

private fun String.dockNotificationFallbackLabel(): String =
    substringAfterLast('.')
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .takeIf { parts -> parts.isNotEmpty() }
        ?.joinToString(separator = " ") { part ->
            part.lowercase().replaceFirstChar { character -> character.titlecase() }
        }
        ?: "App"
