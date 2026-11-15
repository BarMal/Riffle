package com.riffle.app.launcher.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.QuickActionsRow
import com.riffle.app.launcher.SearchWebPanel
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.searchWebPreview
import com.riffle.app.launcher.stream.LocalStreamCardSizes
import com.riffle.app.launcher.stream.PageQuickActions
import com.riffle.app.launcher.stream.StreamCardHeader
import com.riffle.app.launcher.stream.StreamCardSurface
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamGlyph
import com.riffle.app.launcher.stream.StreamHeaderIconSize
import com.riffle.app.launcher.stream.StreamHeaderTopPadding
import com.riffle.app.launcher.stream.StreamMinTarget
import com.riffle.app.launcher.stream.cardSpotlightSource
import com.riffle.core.domain.launcher.search.SearchStackCard
import com.riffle.core.domain.launcher.search.SearchStackNotification
import com.riffle.core.domain.launcher.stream.StreamAppRef

/** One card's face: the Stream's header (an icon and a name), then what the result has to show under it. */
@Composable
@Suppress("LongParameterList")
internal fun SearchStackCardContent(
    card: SearchStackCard,
    isFront: Boolean,
    env: StreamEnvironment,
    context: AppListContext,
    emptyText: String,
    actions: () -> SearchStackActions,
    modifier: Modifier,
) {
    val title = card.spokenName()
    StreamCardSurface(card.key, card.appRef(), null, env, modifier) { sourceModifier ->
        Column(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .padding(
                        start = RiffleSpacing.l,
                        end = RiffleSpacing.l,
                        top = StreamHeaderTopPadding,
                        bottom = RiffleSpacing.xs,
                    ),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            StreamCardHeader(
                icon = { SearchStackIcon(card, context) },
                title = title,
                trailing = { if (!isFront && card.hasMore) SearchStackMoreMark(card.key) },
            )
            // What a card has under its header starts below what a strip of it shows (a pitch): the Stream's rule, so
            // none of it peeks out along the cut, whatever the posture's pitch.
            val pitch = LocalStreamCardSizes.current.pitchDp.dp
            Spacer(Modifier.height((pitch - StreamHeaderTopPadding - StreamMinTarget).coerceAtLeast(0.dp)))
            SearchStackCardBody(card, env, emptyText, actions)
        }
    }
}

/** The app a card belongs to, to light it with the app's colour; none for a setting or the web. */
private fun SearchStackCard.appRef(): StreamAppRef? =
    when (this) {
        is SearchStackCard.App -> app.identity.let { StreamAppRef(it.packageName.value, it.profile.id.value) }
        is SearchStackCard.Shortcut ->
            shortcut.appIdentity.let {
                StreamAppRef(
                    it.packageName.value,
                    it.profile.id.value,
                )
            }
        is SearchStackCard.Setting, is SearchStackCard.Web -> null
    }

/** What a card has under its header. */
@Composable
private fun SearchStackCardBody(
    card: SearchStackCard,
    env: StreamEnvironment,
    emptyText: String,
    actions: () -> SearchStackActions,
) {
    when (card) {
        is SearchStackCard.App -> AppCardBody(card, env, actions)
        is SearchStackCard.Shortcut ->
            DetailLine(
                card.shortcut.longLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.search_stack_shortcut),
            )
        is SearchStackCard.Setting -> DetailLine(card.result.subtitle.orEmpty())
        is SearchStackCard.Web -> {
            DetailLine(if (card.noResults) emptyText else card.query, lines = 2)
            searchWebPreview(card.query)?.let { preview ->
                SearchWebPanel(preview = preview, onAction = { actions().open(card) })
            }
        }
    }
}

@Composable
private fun AppCardBody(
    card: SearchStackCard.App,
    env: StreamEnvironment,
    actions: () -> SearchStackActions,
) {
    val notification = card.notification
    if (notification != null) {
        NotificationLine(notification)
    } else if (card.quickActions.isEmpty()) {
        DetailLine(stringResource(R.string.stream_nothing_new))
    }
    if (card.quickActions.isNotEmpty()) {
        val quick =
            remember(card, env) {
                PageQuickActions(
                    pageKey = "search:${card.key}",
                    appLabel = card.app.label,
                    actions = card.quickActions,
                    env = env,
                    onLaunch = { action -> actions().launchQuickAction(card, action) },
                )
            }
        QuickActionsRow(quick)
    }
}

/** The newest notification's line (or, with only the count or a withheld text, what can be said of it). */
@Composable
private fun NotificationLine(notification: SearchStackNotification) {
    val text =
        when {
            notification.hidden -> stringResource(R.string.stream_hidden_content)
            notification.newestLine != null -> notification.newestLine.orEmpty()
            else -> pluralStringResource(R.plurals.search_stack_notifications, notification.count, notification.count)
        }
    DetailLine(text, modifier = Modifier.testTag(SEARCH_STACK_NOTIFICATION_LINE_TEST_TAG))
}

@Composable
private fun DetailLine(
    text: String,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/** The icon of a card's header: the app's, a setting's "SET" tile or the web's glyph. */
@Composable
private fun SearchStackIcon(
    card: SearchStackCard,
    context: AppListContext,
) {
    when (card) {
        is SearchStackCard.App ->
            LauncherAppIcon(
                identity = card.app.identity,
                label = card.app.label,
                iconLoader = context.appIconLoader,
                modifier = Modifier.size(StreamHeaderIconSize).cardSpotlightSource("icon"),
            )
        is SearchStackCard.Shortcut ->
            LauncherAppIcon(
                identity = card.shortcut.appIdentity,
                label = card.shortcut.shortLabel,
                iconLoader = context.appIconLoader,
                modifier = Modifier.size(StreamHeaderIconSize).cardSpotlightSource("icon"),
            )
        is SearchStackCard.Setting ->
            Surface(
                modifier = Modifier.size(StreamHeaderIconSize),
                shape = RoundedCornerShape(SETTING_TILE_CORNER),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "SET",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        is SearchStackCard.Web -> StreamGlyph(Icons.Filled.Search)
    }
}

private val SETTING_TILE_CORNER = 12.dp

/**
 * The quiet mark of a strip that has more to show (quick actions, a notification): the same "more" glyph the Stream's
 * cards use for their menus, dimmed, in the header's trailing target. Decorative: the strip's state description
 * says it.
 */
@Composable
private fun SearchStackMoreMark(key: String) {
    Box(
        modifier = Modifier.size(StreamMinTarget).testTag(searchStackMoreMarkTag(key)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(MoreMarkSize).alpha(MORE_MARK_ALPHA),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MoreMarkSize: Dp = 20.dp
private const val MORE_MARK_ALPHA = 0.6f
