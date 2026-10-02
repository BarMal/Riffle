package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorPreview
import com.riffle.app.launcher.editor.LensBuilder
import com.riffle.app.launcher.editor.PendingQueryFlush
import com.riffle.app.launcher.editor.previewTargetFor
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.editor.EditContext
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.settings.LensDetailModel
import com.riffle.core.domain.launcher.workspace.settings.LensProblem
import com.riffle.core.domain.launcher.workspace.settings.LensSession
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.UsedByRow

internal const val LENS_NAME_FIELD_TEST_TAG = "lens-name-field"
internal const val LENS_SAVE_TEST_TAG = "lens-save"
internal const val LENS_BACK_TEST_TAG = "lens-back"
internal const val LENS_PREVIEW_TEST_TAG = "lens-preview"
internal const val LENS_PROBLEMS_TEST_TAG = "lens-problems"
internal const val LENS_BREAKS_TEST_TAG = "lens-breaks"
internal const val LENS_DRAWS_AS_TEST_TAG = "lens-draws-as"

internal fun lensUsedByTestTag(index: Int): String = "lens-used-by-$index"

private const val NEW_LENS_KEY = "new"
private val PREVIEW_HEIGHT = 220.dp

/** What the detail page draws for the lens being built: the session, the registry's source rows and the layout. */
internal data class LensBuilderData(
    val session: LensSession,
    val sources: List<SourceChoice>,
    val layout: HomeLayoutDeviceClass,
    /** The layout is the one this device shows now, so the workspace editor can be opened for a user. */
    val canEditWorkspaces: Boolean,
)

/**
 * The lens detail and builder. The name is plain text kept here (saved across rotation); the lens itself is the
 * session's [LensBuilderData.session], edited through the shared [LensBuilder]. Everything it says about validity
 * comes from the domain's [LensDetailModel]: Save is disabled with the reasons, or, when the change would stop
 * containers working, offers the honest choice (detach them, or save a new lens) after listing them.
 */
@Composable
@Suppress("LongParameterList")
internal fun LensDetailPane(
    data: LensBuilderData,
    services: ContainerServices?,
    callbacks: LensesPageCallbacks,
    showBack: Boolean,
    openDialog: (LensDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = data.session
    var name by rememberSaveable(session.id?.value ?: NEW_LENS_KEY, session.baseline?.name) {
        mutableStateOf(session.initialName)
    }
    val detail = callbacks.queries.detailFor(name)
    val queryFlush = remember { PendingQueryFlush() }
    val requestClose = {
        queryFlush.flush()
        if (detail?.changed == true) openDialog(LensDialog.DiscardChanges) else callbacks.onCloseDetail()
    }
    // The system Back leaves the lens (asking first when there is something unsaved), not the whole page.
    BackHandler(onBack = requestClose)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        DetailHeader(session, detail, showBack, requestClose)
        NameField(name = name, onChange = { name = it }, detail = detail)
        DetailProblems(detail)
        BuilderPreview(data = data, name = name, services = services)
        LensBuilder(
            draft = session.draft,
            sources = data.sources,
            onAction = callbacks.onBuilderAction,
            onRequestSourceAccess = callbacks.onRequestSourceAccess,
            queryFlush = queryFlush,
        )
        detail?.let { DrawsAs(it) }
        detail?.let { BreaksNotice(it) }
        detail?.let { UsedBySection(it, data.canEditWorkspaces, callbacks.onEditWorkspace) }
        SaveBar(data, name, detail, callbacks, openDialog, queryFlush)
        if (detail?.isNew == false) LensActions(data, session, name, detail.usedBy.size, callbacks, openDialog)
    }
}

@Composable
private fun DetailHeader(
    session: LensSession,
    detail: LensDetailModel?,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        if (showBack) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(LENS_BACK_TEST_TAG),
            ) { Text(LensesSettingsText.BACK_TO_LIST) }
        }
        Text(
            text = session.baseline?.name ?: LensesSettingsText.NEW_LENS_TITLE,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (detail?.changed == true) {
            Text(
                text = LensesSettingsText.UNSAVED_HINT,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameField(
    name: String,
    onChange: (String) -> Unit,
    detail: LensDetailModel?,
) {
    val problem = detail?.problems?.filterIsInstance<LensProblem.Library>()?.firstOrNull()
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().testTag(LENS_NAME_FIELD_TEST_TAG),
        label = { Text(LensesSettingsText.NAME_LABEL) },
        singleLine = true,
        isError = problem != null,
        supportingText = { Text(LensesMessageText.nameCounter(name.trim().length)) },
    )
}

/** Why Save is unavailable, as words, in a polite live region so a change is announced without stealing focus. */
@Composable
private fun DetailProblems(detail: LensDetailModel?) {
    val problems = detail?.problems.orEmpty()
    if (problems.isNotEmpty()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(LENS_PROBLEMS_TEST_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
        ) {
            problems.forEach { problem ->
                Text(
                    text = LensesMessageText.problem(problem),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BuilderPreview(
    data: LensBuilderData,
    name: String,
    services: ContainerServices?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        DetailHeading(LensesSettingsText.PREVIEW_HEADING)
        if (services == null) {
            Text(
                text = LensesSettingsText.PREVIEW_UNAVAILABLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val target =
                remember(data.session.draft, data.sources) {
                    previewTargetFor(
                        data.session.lens,
                        EditContext(LayoutCapabilities(), data.sources.map { it.descriptor }),
                    )
                }
            EditorPreview(
                target = target,
                services = services,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PREVIEW_HEIGHT)
                        .testTag(LENS_PREVIEW_TEST_TAG)
                        .semantics { contentDescription = LensesDetailText.previewDescription(name) },
            )
        }
        Text(
            text = LensesSettingsText.PREVIEW_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun DrawsAs(detail: LensDetailModel) {
    if (detail.drawableAs.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(LENS_DRAWS_AS_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
        ) {
            DetailHeading(LensesSettingsText.DRAWS_AS_HEADING)
            Text(
                text = LensesDetailText.drawableAs(detail.drawableAs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The dry run of 4.4: the containers this edit would stop working, each with the domain's reasons. */
@Composable
private fun BreaksNotice(detail: LensDetailModel) {
    if (detail.broken.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(LENS_BREAKS_TEST_TAG),
            shape = RiffleShapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            tonalElevation = RiffleElevation.level0,
        ) {
            Column(
                modifier = Modifier.padding(RiffleSpacing.m).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            ) {
                Text(LensesDetailText.breaksSummary(detail.broken.size), style = MaterialTheme.typography.bodyMedium)
                Text(LensesSettingsText.ADVANCED_BREAKS_NOTE, style = MaterialTheme.typography.bodySmall)
                detail.broken.forEach { use ->
                    Text(LensesDetailText.broken(use), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun UsedBySection(
    detail: LensDetailModel,
    canEdit: Boolean,
    onEdit: (WorkspaceId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        DetailHeading(LensesSettingsText.USED_BY_HEADING)
        when {
            detail.isNew ->
                Text(
                    text = LensesSettingsText.USED_BY_NEW,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            detail.usedBy.isEmpty() ->
                Text(
                    text = LensesSettingsText.NOT_USED,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                detail.usedBy.forEachIndexed { index, row -> UsedByItem(index, row, canEdit, onEdit) }
                if (!canEdit) {
                    Text(
                        text = LensesSettingsText.EDIT_OTHER_LAYOUT_REASON,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One container that uses the lens. When the editor can open it (this device's layout) the whole row is the button
 * and says so; otherwise it is plain text, never a dead control.
 */
@Composable
private fun UsedByItem(
    index: Int,
    row: UsedByRow,
    canEdit: Boolean,
    onEdit: (WorkspaceId) -> Unit,
) {
    val base =
        Modifier
            .fillMaxWidth()
            .testTag(lensUsedByTestTag(index))
            .clip(RiffleShapes.medium)
    val rowModifier =
        if (canEdit) {
            base.clickable(role = Role.Button, onClick = { onEdit(row.workspaceId) })
        } else {
            base
        }
    ListItem(
        modifier =
            rowModifier.semantics(mergeDescendants = true) {
                contentDescription = LensesDetailText.usedBySpoken(row, canEdit)
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = LensesDetailText.usedBy(row), style = MaterialTheme.typography.bodyMedium) },
        trailingContent =
            if (canEdit) {
                { Text(text = LensesSettingsText.EDIT_WORKSPACE, style = MaterialTheme.typography.labelLarge) }
            } else {
                null
            },
    )
}

@Composable
private fun SaveBar(
    data: LensBuilderData,
    name: String,
    detail: LensDetailModel?,
    callbacks: LensesPageCallbacks,
    openDialog: (LensDialog) -> Unit,
    queryFlush: PendingQueryFlush,
) {
    val session = data.session
    val lens = session.lens
    val id = session.id
    val enabled = detail?.canSave == true && lens != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = {
                queryFlush.flush()
                when {
                    lens == null || detail == null -> Unit
                    id == null -> callbacks.onDispatch(LensesSettingsAction.Create(name, lens))
                    detail.needsChoice ->
                        openDialog(
                            LensDialog.SaveChoice(id, name, lens, detail.broken, callbacks.queries.copyNameFor(name)),
                        )
                    else -> callbacks.onDispatch(LensesSettingsAction.Save(id, name, lens))
                }
            },
            enabled = enabled,
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(LENS_SAVE_TEST_TAG),
        ) {
            Text(
                when {
                    id == null -> LensesSettingsText.CREATE
                    detail?.needsChoice == true -> LensesSettingsText.SAVE_OPTIONS
                    else -> LensesSettingsText.SAVE_CHANGES
                },
            )
        }
    }
}

/** Duplicate, Copy to each other layout and Delete: each a 48 dp button, Copy naming the layout and its count. */
@Composable
private fun LensActions(
    data: LensBuilderData,
    session: LensSession,
    name: String,
    usedBy: Int,
    callbacks: LensesPageCallbacks,
    openDialog: (LensDialog) -> Unit,
) {
    val id = session.id ?: return
    val savedName = session.baseline?.name ?: name
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs)) {
        DetailHeading(LensesSettingsText.ACTIONS_HEADING)
        DetailActionButton(LensesSettingsText.DUPLICATE) { callbacks.onDispatch(LensesSettingsAction.Duplicate(id)) }
        callbacks.queries.copyTargets(id).forEach { target ->
            DetailActionButton(
                label = LensesSettingsText.copyToLabel(target.layout),
                supporting = LensesDialogText.copyTargetLabel(target),
            ) { openDialog(LensDialog.CopyTo(id, savedName, target)) }
        }
        DetailActionButton(LensesSettingsText.DELETE) {
            openDialog(LensDialog.ConfirmDelete(id, savedName, usedBy))
        }
    }
}

@Composable
private fun DetailActionButton(
    label: String,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    Column {
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = RiffleSpacing.xxxl)) { Text(label) }
        supporting?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = RiffleSpacing.m),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
