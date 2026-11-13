package com.riffle.app.launcher.diagnostics

// The plain-text report: shapes only, so it can never print notification text or a contact name.

fun SectionProblem.describe(): String =
    when (this) {
        SectionProblem.LAUNCHER_ROLE_MISSING -> "not available: Riffle is not the home app (launcher role)"
        SectionProblem.NOTIFICATION_ACCESS_MISSING -> "not available: notification access is off"
        SectionProblem.UNAVAILABLE -> "not available on this device"
    }

fun AppPublishReport.toPlainText(): String =
    buildString {
        appendLine("What apps publish: $appLabel ($packageName)")
        section("Shortcuts", shortcuts) { index, item ->
            appendLine("${index + 1}. ${item.headline()}")
            item.detailLines().forEach { appendLine("   $it") }
        }
        section("Notifications posted by this app", notifications) { index, item ->
            appendLine("${index + 1}. ${item.headline()}")
            item.detailLines().forEach { appendLine("   $it") }
        }
        section("Media session", media) { index, item ->
            appendLine("Session ${index + 1}")
            item.detailLines().forEach { appendLine("   $it") }
        }
        section("Widgets", widgets) { index, item ->
            appendLine("${index + 1}. ${item.headline()}")
            item.detailLines().forEach { appendLine("   $it") }
        }
        appendLine()
        appendLine("Notification text, message bodies and contact names are never included.")
    }

private fun <T> StringBuilder.section(
    title: String,
    section: PublishSection<T>,
    item: StringBuilder.(Int, T) -> Unit,
) {
    appendLine()
    appendLine("$title:")
    when {
        section.problem != null -> appendLine("  ${section.problem.describe()}")
        section.items.isEmpty() -> appendLine("  none")
        else -> section.items.forEachIndexed { index, value -> item(index, value) }
    }
}
