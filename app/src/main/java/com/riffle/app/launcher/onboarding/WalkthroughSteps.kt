@file:Suppress("TooManyFunctions") // One composable per walkthrough step and its rows.

package com.riffle.app.launcher.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.pool.READ_ONLY_HOME_NOTICE
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoal
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalCatalog
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.GoalPicker
import com.riffle.core.domain.launcher.workspace.wizard.GoalRole
import com.riffle.core.domain.launcher.workspace.wizard.GoalSetup
import com.riffle.core.domain.launcher.workspace.wizard.PermissionStatus
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow

internal const val WALKTHROUGH_STEP_LIST_TEST_TAG = "walkthrough-step-list"

/** Every step scrolls on its own, so the top and bottom bars (Back, Skip, Next) always stay reachable. */
@Composable
private fun StepList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(WALKTHROUGH_STEP_LIST_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        content = content,
    )
}

@Composable
internal fun WelcomeStep() {
    StepList {
        item {
            StepIntro(WalkthroughText.WELCOME_TITLE, WalkthroughText.WELCOME_BODY, WalkthroughText.WELCOME_NOTE)
        }
    }
}

@Composable
internal fun GoalsStep(
    ui: WalkthroughUi,
    onAction: (WalkthroughAction) -> Unit,
) {
    val choices = WalkthroughFlow.goalChoices(ui.context)
    val problems = WalkthroughFlow.problems(ui.state, ui.context)
    StepList {
        item { StepIntro(WalkthroughText.GOALS_TITLE, WalkthroughText.GOALS_BODY) }
        items(choices, key = { it.goal.id.key }) { choice ->
            WalkthroughChoiceRow(
                title = choice.goal.label,
                description = choice.goal.description,
                badge = WalkthroughText.availability(choice),
                selected = choice.goal.id in ui.state.chosen,
                enabled = choice.enabled,
                onClick = { onAction(WalkthroughAction.ToggleGoal(choice.goal.id)) },
            )
        }
        problems.firstOrNull()?.let { problem -> item { PoliteNotice(WalkthroughText.problem(problem)) } }
    }
}

@Composable
internal fun ArrangeStep(
    ui: WalkthroughUi,
    onAction: (WalkthroughAction) -> Unit,
) {
    val goals = ui.state.goals
    val placedCount = goals.count { ContentGoalCatalog.goal(it.goal).placedItems }
    StepList {
        item { StepIntro(WalkthroughText.ARRANGE_TITLE, WalkthroughText.ARRANGE_BODY, WalkthroughText.ARRANGE_NOTE) }
        items(goals, key = { it.goal.key }) { arranged ->
            val index = goals.indexOf(arranged)
            val placed = index < placedCount
            val first = if (placed) 0 else placedCount
            val last = if (placed) placedCount - 1 else goals.lastIndex
            ArrangeCard(
                goal = arranged.goal,
                role = arranged.role,
                roles = ui.state.setups[arranged.goal]?.let { WalkthroughFlow.rolesFor(it, ui.context) }.orEmpty(),
                isStart = ui.state.startGoal == arranged.goal,
                canMoveUp = index > first,
                canMoveDown = index < last,
                onAction = onAction,
                index = index,
            )
        }
        item {
            WalkthroughChoiceRow(
                title = WalkthroughText.OPEN_FIRST,
                kind = ChoiceKind.SINGLE,
                selected = ui.state.startGoal == null,
                onClick = { onAction(WalkthroughAction.SetStartGoal(null)) },
            )
        }
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrangeCard(
    goal: ContentGoalId,
    role: GoalRole,
    roles: List<GoalRole>,
    isStart: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAction: (WalkthroughAction) -> Unit,
    index: Int,
) {
    val label = WalkthroughText.goalLabel(goal)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onAction(WalkthroughAction.MoveGoal(goal, index - 1)) },
                    enabled = canMoveUp,
                    modifier = Modifier.semantics { contentDescription = "${WalkthroughText.MOVE_UP}: $label" },
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) }
                IconButton(
                    onClick = { onAction(WalkthroughAction.MoveGoal(goal, index + 1)) },
                    enabled = canMoveDown,
                    modifier = Modifier.semantics { contentDescription = "${WalkthroughText.MOVE_DOWN}: $label" },
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) }
            }
            if (roles.size > 1) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    roles.forEachIndexed { position, option ->
                        val name = roleName(option)
                        SegmentedButton(
                            selected = option == role,
                            onClick = { onAction(WalkthroughAction.SetRole(goal, option)) },
                            shape = SegmentedButtonDefaults.itemShape(index = position, count = roles.size),
                            modifier = Modifier.semantics { contentDescription = "$name: $label" },
                            label = { Text(name) },
                        )
                    }
                }
            } else {
                Text(
                    text = roleName(role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WalkthroughChoiceRow(
                title = WalkthroughText.OPEN_HERE,
                kind = ChoiceKind.SINGLE,
                selected = isStart,
                onClick = { onAction(WalkthroughAction.SetStartGoal(goal)) },
                description = label,
            )
        }
    }
}

private fun roleName(role: GoalRole): String =
    when (role) {
        GoalRole.PAGE -> WalkthroughText.ROLE_PAGE
        GoalRole.WIDGET -> WalkthroughText.ROLE_WIDGET
    }

/** One goal's quick options, read from the walkthrough's own [WalkthroughFlow] option APIs. */
@Composable
internal fun OptionsStep(
    model: WalkthroughScreenModel,
    onAction: (WalkthroughAction) -> Unit,
) {
    val ui = model.ui
    val id = WalkthroughFlow.currentOptionGoal(ui.state, ui.context) ?: return
    val setup = ui.state.setups[id] ?: return
    val goal = ContentGoalCatalog.goal(id)
    val optionGoals = WalkthroughFlow.optionGoals(ui.state, ui.context)
    val looks = WalkthroughFlow.looksFor(ui.state, id, ui.context)
    val problems = WalkthroughFlow.problems(ui.state, ui.context)
    StepList {
        item {
            StepIntro(
                title = goal.label,
                body = WalkthroughText.optionsProgress(ui.state.optionsIndex + 1, optionGoals.size),
            )
        }
        if (goal.filters.size > 1) {
            item { SectionLabel(WalkthroughText.OPTIONS_SHOW) }
            items(goal.filters, key = { "filter-${it.id}" }) { filter ->
                WalkthroughChoiceRow(
                    title = filter.label,
                    kind = ChoiceKind.SINGLE,
                    selected = setup.filterId == filter.id,
                    onClick = { onAction(WalkthroughAction.SetFilter(id, filter.id)) },
                )
            }
        }
        pickers(goal, setup, model, onAction)
        if (looks.size > 1) {
            item { SectionLabel(WalkthroughText.OPTIONS_LOOK) }
            items(looks, key = { "look-${it.id}" }) { look ->
                WalkthroughChoiceRow(
                    title = look.label,
                    kind = ChoiceKind.SINGLE,
                    selected = setup.lookId == look.id,
                    onClick = { onAction(WalkthroughAction.SetLook(id, look.id)) },
                )
            }
        }
        problems.firstOrNull()?.let { problem -> item { PoliteNotice(WalkthroughText.problem(problem), error = true) } }
    }
}

private fun LazyListScope.pickers(
    goal: ContentGoal,
    setup: GoalSetup,
    model: WalkthroughScreenModel,
    onAction: (WalkthroughAction) -> Unit,
) {
    if (GoalPicker.APPS in goal.pickers) {
        val title = if (goal.placedItems) WalkthroughText.PICK_APPS else WalkthroughText.ONLY_THESE_APPS
        item { SectionLabel(title) }
        if (!goal.placedItems && setup.apps.isEmpty()) item { Text(WalkthroughText.ALL_APPS_WHEN_NONE) }
        val picked = setup.apps.map { it.identity }.toSet()
        val apps = (setup.apps + model.apps).distinctBy { it.identity }
        items(apps, key = { "app-${it.identity}" }) { app ->
            WalkthroughChoiceRow(
                title = app.label,
                selected = app.identity in picked,
                onClick = { onAction(WalkthroughAction.ToggleApp(goal.id, app)) },
            )
        }
    }
    if (GoalPicker.FEEDS in goal.pickers) {
        val feeds = model.ui.context.feeds.filter { it.source in goal.sources }
        item { SectionLabel(WalkthroughText.FEEDS) }
        item { Text(if (feeds.isEmpty()) WalkthroughText.NO_FEEDS else WalkthroughText.ALL_FEEDS_WHEN_NONE) }
        items(feeds, key = { "feed-${it.source.value}-${it.key}" }) { feed ->
            WalkthroughChoiceRow(
                title = feed.label,
                selected = feed.key in setup.feeds,
                onClick = { onAction(WalkthroughAction.ToggleFeed(goal.id, feed.key)) },
            )
        }
    }
    if (GoalPicker.QUERY in goal.pickers) {
        item { QueryField(goal.id, setup.query, onAction) }
    }
}

@Composable
private fun QueryField(
    goal: ContentGoalId,
    query: String,
    onAction: (WalkthroughAction) -> Unit,
) {
    // The field keeps what is typed (spaces included); the stored query is the normalized one.
    var text by remember(goal) { mutableStateOf(query) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onAction(WalkthroughAction.SetQuery(goal, it))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(WalkthroughText.SEARCH_FOR) },
        supportingText = { Text(WalkthroughText.SEARCH_HELPER) },
        singleLine = true,
    )
}

@Composable
internal fun DockStep(
    model: WalkthroughScreenModel,
    onAction: (WalkthroughAction) -> Unit,
) {
    val ui = model.ui
    val capacity = ui.context.dockCapacity
    val pins = ui.state.dockPins
    val pinned = pins.map { it.identity }.toSet()
    val canHide = WalkthroughFlow.canHideDock(ui.context)
    val apps = (pins + model.apps).distinctBy { it.identity }
    StepList {
        item {
            StepIntro(
                title = WalkthroughText.DOCK_TITLE,
                body = WalkthroughText.dockBody(capacity),
                note = WalkthroughText.dockCount(pins.size, capacity),
            )
        }
        item {
            WalkthroughChoiceRow(
                title = WalkthroughText.HIDE_DOCK,
                kind = ChoiceKind.SWITCH,
                selected = ui.state.dockHidden && canHide,
                enabled = canHide,
                description = if (canHide) null else WalkthroughText.HIDE_DOCK_REFUSED,
                onClick = { onAction(WalkthroughAction.SetDockHidden(!ui.state.dockHidden)) },
            )
        }
        if (pins.size >= capacity) item { PoliteNotice(WalkthroughText.DOCK_FULL) }
        items(apps, key = { "dock-${it.identity}" }) { app ->
            val selected = app.identity in pinned
            WalkthroughChoiceRow(
                title = app.label,
                selected = selected,
                enabled = selected || pins.size < capacity,
                onClick = { onAction(WalkthroughAction.ToggleDockPin(app)) },
            )
        }
    }
}

/**
 * Each permission the choices need, one at a time: "Allow" shows the rationale, "Continue" opens the existing explicit
 * flow, "Not now" skips. A granted permission stays listed as "Allowed" (it no longer needs asking).
 */
@Composable
internal fun PermissionsStep(
    ui: WalkthroughUi,
    onAction: (WalkthroughAction) -> Unit,
    onCheckAgain: () -> Unit,
) {
    val needed = WalkthroughFlow.neededPermissions(ui.state, ui.context)
    val shown =
        ui.state.goals.flatMap { ContentGoalCatalog.goal(it.goal).permissions }.distinct()
            .filter { it in needed || ui.state.permissions[it] != null }
            .sortedBy { it.ordinal }
    StepList {
        item { StepIntro(WalkthroughText.PERMISSIONS_TITLE, WalkthroughText.PERMISSIONS_BODY) }
        items(shown, key = { it.name }) { permission ->
            PermissionCard(
                permission = permission,
                status = WalkthroughFlow.permissionStatus(ui.state, permission, ui.context),
                goals = ui.state.chosen.filter { permission in ContentGoalCatalog.goal(it).permissions },
                otherPending = ui.state.pendingRequest != null && ui.state.pendingRequest != permission,
                onAction = onAction,
                onCheckAgain = onCheckAgain,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PermissionCard(
    permission: SourcePermission,
    status: PermissionStatus,
    goals: List<ContentGoalId>,
    otherPending: Boolean,
    onAction: (WalkthroughAction) -> Unit,
    onCheckAgain: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Text(text = WalkthroughText.permissionName(permission), style = MaterialTheme.typography.titleMedium)
            Text(text = WalkthroughText.permissionRationale(permission), style = MaterialTheme.typography.bodyMedium)
            if (goals.isNotEmpty()) {
                Text(
                    text = WalkthroughText.neededBy(goals),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WalkthroughText.permissionStatus(status)?.let { PoliteNotice(it) }
            when (status) {
                PermissionStatus.PENDING, PermissionStatus.SKIPPED, PermissionStatus.DENIED ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                        FilledTonalButton(
                            onClick = { onAction(WalkthroughAction.ExplainPermission(permission)) },
                            enabled = !otherPending,
                        ) { Text(WalkthroughText.ALLOW) }
                        if (status == PermissionStatus.PENDING) {
                            TextButton(
                                onClick = { onAction(WalkthroughAction.SkipPermission(permission)) },
                                enabled = !otherPending,
                            ) { Text(WalkthroughText.NOT_NOW) }
                        }
                    }
                PermissionStatus.EXPLAINING -> {
                    PoliteNotice(WalkthroughText.permissionNext(permission))
                    Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                        Button(onClick = { onAction(WalkthroughAction.RequestPermission(permission)) }) {
                            Text(WalkthroughText.CONTINUE)
                        }
                        TextButton(onClick = { onAction(WalkthroughAction.SkipPermission(permission)) }) {
                            Text(WalkthroughText.NOT_NOW)
                        }
                    }
                }
                PermissionStatus.REQUESTED -> TextButton(onClick = onCheckAgain) { Text(WalkthroughText.CHECK_AGAIN) }
                PermissionStatus.GRANTED -> Unit
            }
        }
    }
}

@Composable
internal fun ReviewStep(model: WalkthroughScreenModel) {
    val ui = model.ui
    val review = WalkthroughFlow.review(ui.state, ui.context)
    val problems = WalkthroughFlow.problems(ui.state, ui.context)
    StepList {
        item { StepIntro(WalkthroughText.REVIEW_TITLE, review.workspaceName) }
        items(review.pages.indices.toList(), key = { "page-$it" }) { index ->
            val page = review.pages[index]
            ReviewRow(WalkthroughText.reviewPage(index, page), if (page.isStart) WalkthroughText.OPENS_HERE else null)
        }
        item { ReviewRow(WalkthroughText.reviewDock(review), null) }
        if (review.needsAccess.isNotEmpty()) {
            item { PoliteNotice(WalkthroughText.reviewNeedsAccess(review.needsAccess)) }
        }
        item {
            Text(
                text = WalkthroughText.reviewScope(review),
                modifier = Modifier.padding(vertical = RiffleSpacing.s),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        problems.firstOrNull()?.let { problem -> item { PoliteNotice(WalkthroughText.problem(problem), error = true) } }
        when (model.applyBlock) {
            ApplyBlock.LOADING -> item { PoliteNotice(WalkthroughText.LOADING) }
            ApplyBlock.READ_ONLY -> item { PoliteNotice(READ_ONLY_HOME_NOTICE, error = true) }
            null -> Unit
        }
    }
}

@Composable
private fun ReviewRow(
    text: String,
    note: String?,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            note?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
