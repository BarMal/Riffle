@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardsChapter
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterState
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun CardsChapterSurface(
    state: CardsChapterState,
    apps: List<InstalledApp>,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            CardsChapterHeader(state, onAction)
            CardsChapterNavigator(state, apps, onAction)
            when (val chapter = state.selectedChapter) {
                CardsChapter.Overview -> CardsOverview(state, apps, notificationAccessStatus, onAction)
                is CardsChapter.App -> CardsAppChapter(chapter, apps, onAction)
            }
        }
    }
}

@Composable
private fun CardsChapterHeader(
    state: CardsChapterState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val selectedIndex = state.plan.chapterIds.indexOf(state.preferences.selectedChapterId)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(text = "Cards", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Chapter ${selectedIndex + 1} of ${state.plan.chapters.size}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.plan.chapters.size > 1) {
            Row {
                TextButton(
                    onClick = {
                        onAction(
                            LauncherShellAction.SelectCardsChapter(
                                state.plan.chapterIds.previousOf(selectedIndex),
                            ),
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = "Show previous Cards chapter" },
                ) { Text("Previous") }
                TextButton(
                    onClick = {
                        onAction(LauncherShellAction.SelectCardsChapter(state.plan.chapterIds.nextOf(selectedIndex)))
                    },
                    modifier = Modifier.semantics { contentDescription = "Show next Cards chapter" },
                ) { Text("Next") }
            }
        }
    }
}

@Composable
private fun CardsChapterNavigator(
    state: CardsChapterState,
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        items(cardsChapterNavigatorChapterIds(state), key = { chapterId -> chapterId }) { chapterId ->
            val chapter = state.plan.chapters.first { it.id == chapterId }
            val label = chapter.label(apps)
            TextButton(
                onClick = { onAction(LauncherShellAction.SelectCardsChapter(chapterId)) },
                modifier = Modifier.semantics { contentDescription = "Open $label chapter" },
            ) { Text(label) }
        }
    }
}

internal fun cardsChapterNavigatorChapterIds(state: CardsChapterState): List<CardsChapterId> = state.plan.chapterIds

@Composable
private fun CardsOverview(
    state: CardsChapterState,
    apps: List<InstalledApp>,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    when {
        notificationAccessStatus != NotificationAccessStatus.GRANTED ->
            CardsMessage(
                title = "Notification access needed",
                message = "Allow notification access to show your Cards chapters.",
                action = LauncherShellAction.RequestNotificationAccess,
                actionLabel = "Allow notification access",
                onAction = onAction,
            )

        state.plan.chapters.filterIsInstance<CardsChapter.App>().none { it.notificationGroup != null } ->
            CardsMessage(title = "No notifications", message = "New notifications will appear in Overview.")

        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.plan.chapters.filterIsInstance<CardsChapter.App>(), key = { it.id }) { chapter ->
                    chapter.notificationGroup?.let { group ->
                        val label = chapter.label(apps)
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onAction(LauncherShellAction.SelectCardsChapter(chapter.id)) }
                                    .semantics {
                                        contentDescription = "Open $label chapter, ${group.count} notifications"
                                    },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = LocalLauncherCardShape.current,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text("${group.count} notifications", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun CardsAppChapter(
    chapter: CardsChapter.App,
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    val label = chapter.label(apps)
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(
                onClick = { onAction(LauncherShellAction.ToggleCardsChapterPinned(chapter.id)) },
                modifier =
                    Modifier.semantics {
                        contentDescription = if (chapter.isPinned) "Unpin $label chapter" else "Pin $label chapter"
                    },
            ) { Text(if (chapter.isPinned) "Unpin" else "Pin") }
            apps.firstOrNull { it.matches(chapter.id) }?.let { app ->
                TextButton(
                    onClick = { onAction(LauncherShellAction.LaunchApp(app.identity)) },
                    modifier = Modifier.semantics { contentDescription = "Open $label" },
                ) { Text("Open app") }
            }
        }
        chapter.notificationGroup?.let { group ->
            Text("${group.count} notifications", style = MaterialTheme.typography.titleMedium)
            group.notifications.forEach { notification ->
                Text(notification.key.value, style = MaterialTheme.typography.bodyMedium)
            }
        } ?: Text(
            "No active notifications. This pinned chapter remains available.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CardsMessage(
    title: String,
    message: String,
    action: LauncherShellAction? = null,
    actionLabel: String? = null,
    onAction: (LauncherShellAction) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (action != null && actionLabel != null) {
            TextButton(onClick = { onAction(action) }) { Text(actionLabel) }
        }
    }
}

internal fun CardsChapter.label(apps: List<InstalledApp>): String =
    when (this) {
        CardsChapter.Overview -> "Overview"
        is CardsChapter.App -> apps.firstOrNull { it.matches(id) }?.label ?: id.packageName.value
    }

private fun InstalledApp.matches(id: CardsChapterId.App): Boolean =
    identity.packageName == id.packageName && identity.profile.id == id.profileId

private fun List<CardsChapterId>.previousOf(index: Int): CardsChapterId = this[(index - 1 + size) % size]

private fun List<CardsChapterId>.nextOf(index: Int): CardsChapterId = this[(index + 1) % size]
