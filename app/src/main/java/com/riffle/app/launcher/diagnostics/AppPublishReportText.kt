package com.riffle.app.launcher.diagnostics

/*
 * The text of a report, one function per item so the screen and the copied report say the same thing. Only shapes go
 * in, so nothing here can print notification text or a contact name.
 */

private fun Boolean.yesNo(): String = if (this) "yes" else "no"

fun ShortcutShape.headline(): String = shortLabel.ifBlank { "(no short label)" }

fun ShortcutShape.detailLines(): List<String> =
    listOf(
        "Long label: ${longLabel ?: "none"}",
        "Type: ${types.joinToString().ifEmpty { "unknown" }}",
    )

fun NotificationShape.headline(): String = "Category: ${category ?: "none"}"

fun NotificationShape.detailLines(): List<String> =
    buildList {
        add("Ongoing: ${isOngoing.yesNo()}")
        add("Template: ${template ?: "none"}")
        add("Has title: ${hasTitle.yesNo()}")
        add("Has text: ${hasText.yesNo()}")
        add("Has large icon: ${hasLargeIcon.yesNo()}")
        add("Has big picture: ${hasBigPicture.yesNo()}")
        if (messageCount != null) {
            add(
                "Messages: $messageCount ($messagesWithPerson with a person, " +
                    "$messagesWithPersonIcon with a person icon)",
            )
        }
        add(
            progress?.let {
                if (it.indeterminate) "Progress: indeterminate" else "Progress: ${it.current} of ${it.max}"
            } ?: "Progress: none",
        )
        add(
            "Custom views: content ${hasCustomContentView.yesNo()}, big ${hasCustomBigContentView.yesNo()}, " +
                "heads-up ${hasCustomHeadsUpView.yesNo()}",
        )
        if (actions.isEmpty()) {
            add("Actions: none")
        } else {
            actions.forEach { add("Action: \"${it.title}\", remote input ${it.hasRemoteInput.yesNo()}") }
        }
        addAll(layoutOutline("content", hasCustomContentView, contentLayout))
        addAll(layoutOutline("big", hasCustomBigContentView, bigContentLayout))
    }

/**
 * The outline of one of a notification's own layouts: a line per view in drawing order, indented by its depth, with
 * its class, the name of its id, whether it takes a tap, its visibility and, for a text view, how long its text is and
 * its size (never the text), for an image view the size of its picture. Nothing for a notification without the layout.
 */
private fun layoutOutline(
    which: String,
    present: Boolean,
    views: List<LayoutViewShape>?,
): List<String> =
    when {
        !present -> emptyList()
        views == null -> listOf("Layout ($which): could not be read")
        else -> listOf("Layout ($which): ${views.size} views") + views.map(LayoutViewShape::outlineLine)
    }

/** "    TextView #stops_away, clickable no, visible, text 12 chars, 14 sp". */
fun LayoutViewShape.outlineLine(): String =
    buildString {
        append("  ".repeat(depth + 1))
        append(className)
        resourceEntryName?.let { append(" #").append(it) }
        append(", clickable ").append(clickable.yesNo())
        append(", ").append(visibility)
        textLength?.let { append(", text ").append(it).append(" chars") }
        textSizeSp?.let { append(", ").append(it).append(" sp") }
        imageSize?.let { append(", image ").append(it) }
    }

fun MediaShape.detailLines(): List<String> =
    buildList {
        add("Playback state: $playbackState")
        add(
            "Metadata: title ${metadata.title.yesNo()}, artist ${metadata.artist.yesNo()}, " +
                "album ${metadata.album.yesNo()}, duration ${metadata.duration.yesNo()}, " +
                "art bitmap ${metadata.artBitmap.yesNo()}, art URI ${metadata.artUri.yesNo()}",
        )
        add("Actions: ${actions.joinToString().ifEmpty { "none" }}")
        add("Custom actions: ${customActions.joinToString { "\"$it\"" }.ifEmpty { "none" }}")
        add("Queue size: $queueSize")
        queueItems.forEachIndexed { index, item ->
            add(
                "Queue ${index + 1}: \"${item.title ?: "(no title)"}\", icon bitmap ${item.hasIconBitmap.yesNo()}, " +
                    "icon URI ${item.hasIconUri.yesNo()}",
            )
        }
    }

fun WidgetShape.headline(): String = label.ifBlank { "(no label)" }

fun WidgetShape.detailLines(): List<String> = listOf("Minimum size: $minWidthDp x $minHeightDp dp")
