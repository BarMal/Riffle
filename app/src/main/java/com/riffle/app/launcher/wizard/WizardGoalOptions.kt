package com.riffle.app.launcher.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorChoiceRow
import com.riffle.app.launcher.editor.EditorSectionTitle
import com.riffle.app.launcher.editor.EditorText
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.ContentRecipes
import com.riffle.core.domain.launcher.workspace.wizard.FeedOption
import com.riffle.core.domain.launcher.workspace.wizard.GoalLook
import com.riffle.core.domain.launcher.workspace.wizard.GoalPicker
import com.riffle.core.domain.launcher.workspace.wizard.GoalSetup
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction

/** Above this many apps the app picker offers a filter field. */
private const val APP_FILTER_THRESHOLD = 12

/**
 * What a goal's options send. A bundle so the wizard and the first-time walkthrough can wire the same controls to
 * their own reducers (the wizard's `WizardAction`s, the walkthrough's per-goal actions).
 */
internal class GoalOptionsCallbacks(
    val onFilter: (String) -> Unit = {},
    val onLook: (String) -> Unit = {},
    val onToggleApp: (AppPick) -> Unit = {},
    val onToggleFeed: (String) -> Unit = {},
    val onQuery: (String) -> Unit = {},
)

/** A look and whether it can be chosen here (the wizard's `LookChoice.enabled`, the walkthrough's `looksFor`). */
internal data class LookOption(
    val look: GoalLook,
    val enabled: Boolean = true,
)

/**
 * The compact per-goal options view: "What should it show?" and "How should it look?" for one goal, with no step
 * frame, title or buttons, for the walkthrough's Options step to embed. [looks] are the looks offered (the
 * walkthrough passes `WalkthroughFlow.looksFor`, all enabled); [apps] and [feeds] are what the pickers offer.
 */
@Composable
internal fun GoalOptions(
    setup: GoalSetup,
    looks: List<LookOption>,
    apps: List<AppPick>,
    feeds: List<FeedOption>,
    callbacks: GoalOptionsCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        GoalContentOptions(setup, apps, feeds, callbacks)
        GoalLookOptions(setup, looks, callbacks.onLook)
    }
}

/** The wizard's Content step body: the goal's filters (when it has several) and each of its pickers. */
@Composable
internal fun GoalContentOptions(
    setup: GoalSetup,
    apps: List<AppPick>,
    feeds: List<FeedOption>,
    callbacks: GoalOptionsCallbacks,
    modifier: Modifier = Modifier,
) {
    val goal = ContentRecipes.goal(setup)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        if (goal.filters.size > 1) {
            goal.filters.forEach { filter ->
                EditorChoiceRow(
                    title = filter.label,
                    selected = setup.filterId == filter.id,
                    enabled = true,
                    onClick = { callbacks.onFilter(filter.id) },
                    modifier = Modifier.testTag(wizardFilterTag(filter.id)),
                )
            }
        }
        if (GoalPicker.APPS in goal.pickers) {
            AppPicker(setup, apps, placed = goal.placedItems, onToggle = callbacks.onToggleApp)
        }
        if (GoalPicker.FEEDS in goal.pickers) {
            FeedPicker(setup, feeds.filter { it.source in goal.sources }, callbacks.onToggleFeed)
        }
        if (GoalPicker.QUERY in goal.pickers) {
            QueryField(setup, callbacks.onQuery)
        }
    }
}

/** The wizard's Look step body: every look of the goal; one that cannot go anywhere here says so and stays visible. */
@Composable
internal fun GoalLookOptions(
    setup: GoalSetup,
    looks: List<LookOption>,
    onLook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        looks.forEach { option ->
            val look = option.look
            val hint = EditorText.expressionHint(look.expression)
            val reason = WizardText.DOES_NOT_FIT.takeIf { !option.enabled }
            val selected = setup.lookId == look.id
            EditorChoiceRow(
                title = look.label,
                selected = selected,
                enabled = option.enabled,
                onClick = { onLook(look.id) },
                supporting = hint,
                reason = reason,
                modifier = Modifier.testTag(wizardLookTag(look.id)),
                spoken = WizardText.spoken(look.label, hint, reason),
            )
        }
    }
}

@Composable
private fun AppPicker(
    setup: GoalSetup,
    apps: List<AppPick>,
    placed: Boolean,
    onToggle: (AppPick) -> Unit,
) {
    var filter by rememberSaveable(setup.goal) { mutableStateOf("") }
    val picked = setup.apps.map { it.identity }.toSet()
    // Picked apps stay listed even when the filter or the installed list would hide them, so each can be unpicked.
    val offered = (setup.apps + apps).distinctBy { it.identity }
    val shown = offered.filter { filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true) }
    EditorSectionTitle(if (placed) WizardText.APPS_PLACED else WizardText.APPS_FILTER)
    if (!placed && picked.isEmpty()) {
        Text(
            WizardText.ALL_APPS,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (offered.size > APP_FILTER_THRESHOLD) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Find an app") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().testTag(WIZARD_APP_FILTER_TAG),
        )
    }
    if (offered.isEmpty()) {
        Text(WizardText.NO_APPS, style = MaterialTheme.typography.bodyMedium)
    }
    shown.forEach { app ->
        EditorChoiceRow(
            title = app.label,
            selected = app.identity in picked,
            enabled = true,
            onClick = { onToggle(app) },
            multiple = true,
            modifier = Modifier.testTag(wizardAppTag(app.identity.packageName.value)),
        )
    }
}

@Composable
private fun FeedPicker(
    setup: GoalSetup,
    feeds: List<FeedOption>,
    onToggle: (String) -> Unit,
) {
    EditorSectionTitle(WizardText.FEEDS)
    Text(
        if (feeds.isEmpty()) WizardText.NO_FEEDS else WizardText.ALL_FEEDS.takeIf { setup.feeds.isEmpty() }.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    feeds.forEach { feed ->
        EditorChoiceRow(
            title = feed.label,
            selected = feed.key in setup.feeds,
            enabled = true,
            onClick = { onToggle(feed.key) },
            multiple = true,
            modifier = Modifier.testTag(wizardFeedTag(feed.key)),
        )
    }
}

/** The lens's own search text. The field keeps what is typed; the reducer stores it normalized. */
@Composable
private fun QueryField(
    setup: GoalSetup,
    onQuery: (String) -> Unit,
) {
    var text by rememberSaveable(setup.goal) { mutableStateOf(setup.query) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQuery(it)
        },
        label = { Text(WizardText.QUERY_LABEL) },
        supportingText = { Text(WizardText.QUERY_HELPER) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag(WIZARD_QUERY_FIELD_TAG),
    )
}

/** The shared options wired to the wizard's own actions. */
internal fun wizardCallbacks(onAction: (WizardAction) -> Unit): GoalOptionsCallbacks =
    GoalOptionsCallbacks(
        onFilter = { onAction(WizardAction.ChooseFilter(it)) },
        onLook = { onAction(WizardAction.ChooseLook(it)) },
        onToggleApp = { onAction(WizardAction.ToggleApp(it)) },
        onToggleFeed = { onAction(WizardAction.ToggleFeed(it)) },
        onQuery = { onAction(WizardAction.SetQuery(it)) },
    )
