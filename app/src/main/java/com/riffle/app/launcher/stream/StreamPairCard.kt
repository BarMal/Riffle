@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.LocalGlassCardInks
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.PairCardKind
import com.riffle.core.domain.launcher.stream.PairSide
import com.riffle.core.domain.launcher.stream.StreamAction

/** The icon of a pair's half, in the card's large form. */
private val PairIconSize: Dp = 56.dp

/** The icon of a half on a strip, which is the card's header only. */
private val PairStripIconSize: Dp = 32.dp

/**
 * A card of the Pairs stack (ADR 0003, Amendment 7): two apps side by side, the first on the start (left) pane, each
 * with its icon (large, centred in its half) and name. The ground is the first app's colour like any card's; a tap on a
 * saved pair opens both apps side by side, a tap on a half of the draft opens the picker for it and a tap on the "New
 * pair" card puts a draft in front. A strip behind the front card shows the same in miniature: a small icon and the
 * name of each app, each in its half, and nothing under them.
 */
@Composable
@Suppress("LongMethod")
internal fun PairCard(
    content: AppPairContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val menu = rememberPairMenu(content, callbacks)
    val place = LocalStripPlace.current
    val strip: () -> Float = { place?.let { (it.index - it.position()).coerceIn(0f, 1f) } ?: 0f }
    StreamCardSurface(
        content.id.value,
        content.first?.app,
        content.first?.icon,
        env,
        modifier,
        blendApp = content.second?.app,
    ) { sourceModifier ->
        Box(modifier = sourceModifier.fillMaxSize().pairCardSemantics(content, menu.actions)) {
            // The card in full while it is in front, its miniature once it is behind: cross-faded in the draw phase.
            Column(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - strip() },
            ) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PairHalf(content, secondHalf = false, env, callbacks, menu, Modifier.weight(1f))
                    PairHalf(content, secondHalf = true, env, callbacks, menu, Modifier.weight(1f))
                }
                PairHint(content)
            }
            menu.Menu()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = StreamHeaderTopPadding)
                        .graphicsLayer { alpha = strip() }
                        // The strip's words are the card's: assistive technology reads the card, not its miniature.
                        .clearAndSetSemantics {},
            ) {
                PairStripHalf(content.first, content.kind, env, Modifier.weight(1f))
                PairStripHalf(content.second, content.kind, env, Modifier.weight(1f))
            }
        }
    }
}

/** One half of the card in front: a tap target of the whole half where the half is a button (a draft's). */
@Composable
private fun PairHalf(
    content: AppPairContent,
    secondHalf: Boolean,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    menu: PairMenu,
    modifier: Modifier,
) {
    val app = if (secondHalf) content.second else content.first
    val tap =
        when (content.kind) {
            PairCardKind.SAVED -> ({ callbacks.onAction(content, StreamAction.Open) })
            PairCardKind.DRAFT -> ({ callbacks.onPairEdit(PairEdit.ChooseDraftHalf(secondHalf)) })
            PairCardKind.NEW -> ({ callbacks.onPairEdit(PairEdit.NewPair) })
        }
    val label =
        when {
            content.kind == PairCardKind.DRAFT && secondHalf -> stringResource(R.string.stream_pair_choose_second)
            content.kind == PairCardKind.DRAFT -> stringResource(R.string.stream_pair_choose_first)
            else -> null
        }
    // Each half is read on its own app's end of the blend: its ink (see GlassCardInks), not the card's one.
    val inks = LocalGlassCardInks.current
    val ink =
        if (inks == null) {
            LocalContentColor.current
        } else if (secondHalf) {
            inks.end
        } else {
            inks.start
        }
    CompositionLocalProvider(LocalContentColor provides ink) {
        Column(
            modifier =
                modifier.fillMaxHeight().pairClickable(tap, label, menu).padding(horizontal = RiffleSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PairHalfContent(content, secondHalf, app, env)
        }
    }
}

@Composable
private fun PairHalfContent(
    content: AppPairContent,
    secondHalf: Boolean,
    app: PairSide?,
    env: StreamEnvironment,
) {
    run {
        when {
            content.kind == PairCardKind.NEW && secondHalf -> Unit
            content.kind == PairCardKind.NEW -> NewPairMark()
            app != null -> {
                StreamIcon(app.icon, env, PairIconSize, lightsCard = !secondHalf)
                PairName(app.label)
            }
            else -> {
                ChooseAppMark()
                PairName(stringResource(R.string.stream_pair_choose_app))
            }
        }
    }
}

@Composable
private fun PairName(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = RiffleSpacing.s).fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The placeholder of a half with no app yet: a plus on a quiet square, the size of an icon. */
@Composable
private fun ChooseAppMark() {
    Box(
        modifier = Modifier.size(PairIconSize).graphicsLayer { alpha = PLACEHOLDER_ALPHA },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(PairIconSize / 2))
    }
}

@Composable
private fun NewPairMark() {
    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(PairIconSize / 2))
    Text(
        text = stringResource(R.string.stream_pair_new),
        modifier = Modifier.padding(top = RiffleSpacing.s),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = stringResource(R.string.stream_empty_pairs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The one line under a saved pair: what a tap does, in the muted style. */
@Composable
private fun PairHint(content: AppPairContent) {
    if (content.kind != PairCardKind.SAVED) return
    Text(
        text = stringResource(R.string.stream_pair_hint),
        modifier = Modifier.fillMaxWidth().padding(bottom = RiffleSpacing.s),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** A half of the strip: the app's small icon and its name on one line, each ellipsized within its half. */
@Composable
private fun PairStripHalf(
    app: PairSide?,
    kind: PairCardKind,
    env: StreamEnvironment,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = RiffleSpacing.s).heightInStripHeader(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (app != null) {
            StreamIcon(app.icon, env, PairStripIconSize)
        } else {
            Icon(Icons.Filled.Add, null, Modifier.size(PairStripIconSize / 2))
        }
        val placeholder = if (kind == PairCardKind.NEW) R.string.stream_pair_new else R.string.stream_pair_choose_app
        val name = app?.label ?: stringResource(placeholder)
        Text(
            text = name,
            modifier = Modifier.padding(start = RiffleSpacing.s).weight(1f, fill = false),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.heightInStripHeader(): Modifier = this.then(Modifier.padding(vertical = RiffleSpacing.xs))

private const val PLACEHOLDER_ALPHA = 0.6f

/** The card as one node: "<first> and <second>, pair. Opens side by side" (a draft and the New card say less). */
@Composable
private fun Modifier.pairCardSemantics(
    content: AppPairContent,
    actions: List<CustomAccessibilityAction>,
): Modifier {
    val spoken = pairSpokenText(content)
    return if (content.kind == PairCardKind.SAVED) {
        semantics(mergeDescendants = true) {
            contentDescription = spoken
            role = Role.Button
            customActions = actions
        }
    } else {
        this
    }
}

/** What a pair card says to assistive technology. */
@Composable
internal fun pairSpokenText(content: AppPairContent): String =
    when (content.kind) {
        PairCardKind.SAVED ->
            stringResource(R.string.stream_pair_spoken, content.first?.label.orEmpty(), content.second?.label.orEmpty())
        PairCardKind.DRAFT -> stringResource(R.string.stream_pair_draft_spoken)
        PairCardKind.NEW -> stringResource(R.string.stream_pair_new)
    }

/** The one line a pair card is named by (a strip's title, the open-tab label): "<first> and <second>". */
@Composable
internal fun pairPeekText(content: AppPairContent): String =
    if (content.kind == PairCardKind.SAVED) {
        stringResource(R.string.stream_pair_peek, content.first?.label.orEmpty(), content.second?.label.orEmpty())
    } else {
        stringResource(R.string.stream_pair_new)
    }
