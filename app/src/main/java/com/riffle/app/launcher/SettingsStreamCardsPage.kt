package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.designsystem.CardTint
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.settings.StreamSettings

internal const val STREAM_CARD_PREVIEW_TEST_TAG = "settings-stream-card-preview"

internal fun openCardPlacementTestTag(placement: OpenCardPlacement): String =
    "settings-stream-open-card-${placement.name.lowercase()}"

internal fun cardActionsPlacementTestTag(placement: CardActionsPlacement): String =
    "settings-stream-actions-${placement.name.lowercase()}"

/** A stand-in app colour for the preview card: the card's colour at the chosen strength, as the Stream draws it. */
private val PreviewAppColor = Color(0xFF3B82F6)

/** Stream > Cards: card colour (with a live preview), the glow, and where an open card and its actions sit. */
@Composable
internal fun SettingsStreamCardsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) = SettingsStreamCards(settings = state.settings.stream, layout = state.settings.layout, onAction = onAction)

/** The page over plain [settings], so a screenshot test draws it without a shell. */
@Composable
internal fun SettingsStreamCards(
    settings: StreamSettings,
    onAction: (LauncherShellAction) -> Unit,
    layout: LayoutSettings = LayoutSettings(),
) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(R.string.settings_stream_cards_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsSection(title = stringResource(R.string.settings_stream_cards_preview)) {
        StreamCardPreview(settings = settings)
    }
    SettingsSection(title = stringResource(R.string.settings_section_colour)) {
        DiscreteSettingSlider(
            modifier = Modifier.settingAnchor(SettingKey.STREAM_CARD_TINT),
            title = SettingKey.STREAM_CARD_TINT.label(),
            value = settings.cardTintPercent,
            valueRange = CardTint.MIN_STRENGTH_PERCENT..CardTint.MAX_STRENGTH_PERCENT,
            valueLabel = { percent -> "$percent%" },
            onValueChange = { percent -> onAction(LauncherShellAction.SelectStreamCardTint(percent)) },
        )
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.STREAM_CARD_GLOW),
            title = SettingKey.STREAM_CARD_GLOW.label(),
            subtitle =
                stringResource(
                    if (settings.cardTopGlow) {
                        R.string.settings_stream_card_glow_on
                    } else {
                        R.string.settings_stream_card_glow_off
                    },
                ),
            checked = settings.cardTopGlow,
            onCheckedChange = { enabled -> onAction(LauncherShellAction.SetStreamCardTopGlow(enabled)) },
        )
    }
    SettingsSection(title = stringResource(R.string.setting_stream_apps_behind_cards)) {
        // The same stored value as Settings > Layout: one per posture, two ways to reach it.
        LayoutPosture.entries.forEachIndexed { index, posture ->
            val on = layout.forPosture(posture).appPeekThrough
            SettingsSwitchRow(
                modifier =
                    Modifier
                        .then(
                            if (index == 0) Modifier.settingAnchor(SettingKey.STREAM_APPS_BEHIND_CARDS) else Modifier,
                        )
                        .testTag(streamAppsBehindTestTag(posture)),
                title =
                    stringResource(
                        if (posture == LayoutPosture.FOLDED) {
                            R.string.settings_stream_apps_behind_cards_folded
                        } else {
                            R.string.settings_stream_apps_behind_cards_unfolded
                        },
                    ),
                subtitle =
                    stringResource(
                        if (on) R.string.settings_layout_peek_through_on else R.string.settings_layout_peek_through_off,
                    ),
                checked = on,
                onCheckedChange = { enabled -> onAction(LauncherShellAction.SetAppPeekThrough(posture, enabled)) },
            )
        }
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = stringResource(R.string.settings_stream_apps_behind_cards_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SettingsSection(title = stringResource(R.string.stream_open_card)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.STREAM_OPEN_CARD_POSITION),
            title = SettingKey.STREAM_OPEN_CARD_POSITION.label(),
            subtitle =
                if (settings.openCardPlacement == OpenCardPlacement.GROWS_OUT_OF_DOCK) {
                    stringResource(R.string.settings_stream_open_grows_out_note)
                } else {
                    null
                },
            options = OpenCardPlacement.entries,
            selected = settings.openCardPlacement,
            onSelect = { placement -> onAction(LauncherShellAction.SelectOpenCardPlacement(placement)) },
            label = { placement -> stringResource(placement.labelRes()) },
            optionTag = ::openCardPlacementTestTag,
        )
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.STREAM_ACTIONS_POSITION),
            title = SettingKey.STREAM_ACTIONS_POSITION.label(),
            options = CardActionsPlacement.entries,
            selected = settings.cardActionsPlacement,
            onSelect = { placement -> onAction(LauncherShellAction.SelectCardActionsPlacement(placement)) },
            label = { placement -> stringResource(placement.labelRes()) },
            optionTag = ::cardActionsPlacementTestTag,
        )
    }
}

/** A sample card drawn with the same glass and tint a Stream card gets, so the strength can be judged as it moves. */
@Composable
private fun StreamCardPreview(settings: StreamSettings) {
    val description = stringResource(R.string.settings_stream_cards_preview_description, settings.cardTintPercent)
    GlassSurface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag(STREAM_CARD_PREVIEW_TEST_TAG)
                .semantics { contentDescription = description },
        weight = GlassWeight.Regular,
        shape = LocalLauncherCardShape.current,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
        cardTint = GlassCardTint(PreviewAppColor, settings.cardTintPercent, settings.cardTopGlow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).clearAndSetSemantics {},
        ) {
            Text(
                text = stringResource(R.string.settings_stream_cards_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_stream_cards_preview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun OpenCardPlacement.labelRes(): Int =
    when (this) {
        OpenCardPlacement.CENTRE -> R.string.settings_stream_open_centre
        OpenCardPlacement.ABOVE_DOCK -> R.string.settings_stream_open_above_dock
        OpenCardPlacement.GROWS_OUT_OF_DOCK -> R.string.settings_stream_open_grows_out
    }

private fun CardActionsPlacement.labelRes(): Int =
    when (this) {
        CardActionsPlacement.IN_DOCK -> R.string.settings_stream_actions_in_dock
        CardActionsPlacement.ON_CARD -> R.string.settings_stream_actions_on_card
    }

/** The test tag of the "show apps behind the cards" switch for [posture] on Settings > Stream > Cards. */
internal fun streamAppsBehindTestTag(posture: LayoutPosture): String = "stream-apps-behind-${posture.name.lowercase()}"
