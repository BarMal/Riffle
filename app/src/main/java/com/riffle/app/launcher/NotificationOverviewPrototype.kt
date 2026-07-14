package com.riffle.app.launcher

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.LauncherNotification

@Composable
internal fun NotificationGroupPrototype(
    groups: List<AppNotificationGroup>,
    selectedGroupKey: AppNotificationGroupKey,
    presentation: NotificationOverviewPresentation,
    onBack: () -> Unit,
    onGroupChanged: (AppNotificationGroupKey) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val selectedGroupIndex = notificationOverviewSelectedGroupIndex(groups, selectedGroupKey)
    val pagerState =
        rememberPagerState(
            initialPage = selectedGroupIndex,
            pageCount = { groups.size },
        )

    LaunchedEffect(groups, pagerState.currentPage) {
        groups.getOrNull(pagerState.currentPage)?.let { group ->
            onGroupChanged(group.key)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = 16.dp,
        key = { page -> groups[page].key },
    ) { page ->
        val group = groups[page]
        val app = presentation.apps.firstOrNull { installedApp -> installedApp.matches(group) }
        val listState = rememberLazyListState()
        val focusedNotification =
            notificationOverviewFocusedNotification(
                notifications = group.notifications,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
            ) ?: return@HorizontalPager
        val upcomingNotification =
            group.notifications.getOrNull(listState.firstVisibleItemIndex.coerceAtLeast(0) + 1)
        val swipeProgress =
            notificationOverviewScrollProgress(
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                firstVisibleItemSize = listState.firstVisibleItemSize,
            )
        val label = notificationOverviewGroupLabel(app = app, group = group)
        val heroPresentation = NotificationPrototypeHeroPresentation(group, app, presentation)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NotificationPrototypeActions(
                group = group,
                app = app,
                onBack = onBack,
                onAction = onAction,
            )
            NotificationPrototypeHero(
                notification = focusedNotification,
                upcomingNotification = upcomingNotification,
                swipeProgress = swipeProgress,
                presentation = heroPresentation,
                modifier = Modifier.weight(1f),
            )
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(
                    items = group.notifications,
                    key = { notification -> notification.key.value },
                ) { notification ->
                    NotificationPrototypeCard(
                        notification = notification,
                        label = label,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

internal fun notificationOverviewSelectedGroupIndex(
    groups: List<AppNotificationGroup>,
    selectedGroupKey: AppNotificationGroupKey,
): Int =
    groups.indexOfFirst { group -> group.key == selectedGroupKey }
        .takeIf { index -> index >= 0 }
        ?: 0

@Composable
private fun NotificationPrototypeActions(
    group: AppNotificationGroup,
    app: InstalledApp?,
    onBack: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(text = "All apps")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app?.let { installedApp ->
                TextButton(
                    onClick = { onAction(LauncherShellAction.LaunchApp(installedApp.identity)) },
                ) {
                    Text(text = "Open app")
                }
            }
            if (group.dismissibleNotificationKeys.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onAction(
                            LauncherShellAction.DismissNotifications(
                                group.dismissibleNotificationKeys,
                            ),
                        )
                    },
                ) {
                    Text(text = "Clear all")
                }
            }
        }
    }
}

@Composable
private fun NotificationPrototypeHero(
    notification: LauncherNotification,
    upcomingNotification: LauncherNotification?,
    swipeProgress: Float,
    presentation: NotificationPrototypeHeroPresentation,
    modifier: Modifier = Modifier,
) {
    val label = notificationOverviewGroupLabel(app = presentation.app, group = presentation.group)
    val heroNotifications = listOfNotNull(notification, upcomingNotification)
    val featuredNotification =
        heroNotifications.getOrElse(if (swipeProgress >= 0.5f) 1 else 0) { notification }
    val cardEntries =
        CardStackLayoutPolicy().entries(
            cardCount = heroNotifications.size,
            activeIndex = heroNotifications.indexOf(featuredNotification),
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        CardStack(
            entries = cardEntries,
            modifier = Modifier.fillMaxSize(),
            animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
            reducedMotion = presentation.overviewPresentation.reducedMotion,
        ) { entry ->
            NotificationPrototypeHeroArt(
                notification = heroNotifications[entry.cardIndex],
                label = label,
                app = presentation.app,
                appIconLoader = presentation.overviewPresentation.appIconLoader,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                ),
                        ),
                    ),
        )
        NotificationPrototypeHeroDetails(
            notification = featuredNotification,
            fallbackLabel = label,
            group = presentation.group,
        )
    }
}

private data class NotificationPrototypeHeroPresentation(
    val group: AppNotificationGroup,
    val app: InstalledApp?,
    val overviewPresentation: NotificationOverviewPresentation,
)

@Composable
private fun BoxScope.NotificationPrototypeHeroDetails(
    notification: LauncherNotification,
    fallbackLabel: String,
    group: AppNotificationGroup,
) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = notificationOverviewNotificationTitle(notification, fallbackLabel),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = notification.text.ifBlank { group.notificationOverviewMetadataLabel(fallbackLabel) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotificationPrototypeHeroArt(
    notification: LauncherNotification,
    label: String,
    app: InstalledApp?,
    appIconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
) {
    val largeIcon =
        remember(notification.largeIconPngBase64) {
            notification.largeIconPngBase64?.decodeNotificationArtwork()
        }

    when {
        largeIcon != null ->
            Image(
                bitmap = largeIcon,
                contentDescription = "$label notification image",
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )

        app != null ->
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                LauncherAppIcon(
                    identity = app.identity,
                    label = app.label,
                    iconLoader = appIconLoader,
                    modifier =
                        Modifier
                            .widthIn(min = 112.dp)
                            .defaultMinSize(minWidth = 112.dp, minHeight = 112.dp),
                    shape = RoundedCornerShape(28.dp),
                )
            }

        else ->
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
    }
}

@Composable
private fun NotificationPrototypeCard(
    notification: LauncherNotification,
    label: String,
    onAction: (LauncherShellAction) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .heightIn(min = 132.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text =
                    notificationOverviewNotificationTitle(
                        notification = notification,
                        fallbackLabel = label,
                    ),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = notification.text.ifBlank { notification.priority.label },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${notification.category.label} - ${notification.priority.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notification.canDismiss) {
                    TextButton(
                        onClick = {
                            onAction(
                                LauncherShellAction.DismissNotifications(
                                    listOf(notification.key),
                                ),
                            )
                        },
                    ) {
                        Text(text = "Clear")
                    }
                }
            }
        }
    }
}

internal val AppNotificationGroup.key: AppNotificationGroupKey
    get() = AppNotificationGroupKey(packageName = packageName, profileId = profileId)

internal fun notificationOverviewNotificationTitle(
    notification: LauncherNotification,
    fallbackLabel: String,
): String = notification.title.ifBlank { fallbackLabel }

internal fun notificationOverviewScrollProgress(
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
): Float =
    if (firstVisibleItemSize <= 0) {
        0f
    } else {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize)
            .coerceIn(0f, 1f)
    }

private val LazyListState.firstVisibleItemSize: Int
    get() =
        layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == firstVisibleItemIndex }
            ?.size
            ?: 0

private fun String.decodeNotificationArtwork(): ImageBitmap? =
    runCatching {
        Base64.decode(this, Base64.DEFAULT)
            .let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?.asImageBitmap()
    }.getOrNull()
