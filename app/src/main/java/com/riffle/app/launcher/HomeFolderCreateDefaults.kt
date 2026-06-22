package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId

fun List<AppShortcutItem>.folderCreationItemIds(): List<LauncherItemId> = map { shortcut -> shortcut.id }

fun List<AppShortcutItem>.defaultFolderLabel(): String =
    map { shortcut -> shortcut.label.trim() }
        .filter { label -> label.isNotEmpty() }
        .let { labels ->
            when (labels.size) {
                0 -> DEFAULT_FOLDER_LABEL
                1 -> labels.first()
                2 -> labels.joinToString(separator = " & ")
                else -> "${labels.first()} + ${labels.size - 1} more"
            }
        }
        .truncateFolderLabel()

private fun String.truncateFolderLabel(): String =
    if (length <= MAX_FOLDER_LABEL_LENGTH) {
        this
    } else {
        "${take(MAX_FOLDER_LABEL_LENGTH - TRUNCATED_LABEL_SUFFIX.length).trimEnd()}$TRUNCATED_LABEL_SUFFIX"
    }

private const val DEFAULT_FOLDER_LABEL = "Folder"
private const val MAX_FOLDER_LABEL_LENGTH = 28
private const val TRUNCATED_LABEL_SUFFIX = "..."
