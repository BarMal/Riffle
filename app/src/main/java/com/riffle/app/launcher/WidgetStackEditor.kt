package com.riffle.app.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import com.riffle.core.domain.launcher.home.WidgetStackRejection
import com.riffle.core.domain.launcher.home.WidgetStackResult
import com.riffle.core.domain.launcher.icons.IconShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The stack of [itemId] on a Home page of this layout, or null (it was removed, or is a lone widget again). */
internal fun HomeLayout.widgetStack(itemId: LauncherItemId?): WidgetItem? =
    itemId?.let { id -> pages.flatMap { it.items }.filterIsInstance<WidgetItem>().firstOrNull { it.id == id } }

/** Whether "Move out to Home" has room for member [index] of [stack] on its page (the rule the edit itself uses). */
internal fun HomeLayout.canMoveOut(
    stack: WidgetItem,
    index: Int,
): Boolean = WidgetStackEngine().moveOut(this, stack.id, index).let { it is WidgetStackResult.Updated }

/** The reason "Move out to Home" would be refused, when it would be. */
internal fun HomeLayout.moveOutRejection(
    stack: WidgetItem,
    index: Int,
): WidgetStackRejection? = (WidgetStackEngine().moveOut(this, stack.id, index) as? WidgetStackResult.Rejected)?.reason

/**
 * The icons of the platform widgets' apps and of Riffle's own widgets, read from the system. A widget whose provider
 * the system no longer knows has none.
 */
@Composable
internal fun rememberSystemStackMemberIcons(): WidgetStackMemberIcons {
    val context = LocalContext.current
    return remember(context) { PipelineStackMemberIcons(context, AppIconPipeline.shared(context)) }
}

/** The widget's app icon, resolved by the one icon pipeline so the chosen style applies here too. */
private class PipelineStackMemberIcons(
    private val context: Context,
    private val pipeline: AppIconPipeline,
) : WidgetStackMemberIcons {
    private val cache = BoundedCache<String, ImageBitmap>(STACK_ICON_CACHE)

    override val styleVersion: Int get() = pipeline.styleVersion

    override val drawsOwnShape: Boolean
        get() = pipeline.effectiveStyle.let { it.shape != IconShape.SYSTEM || it.iconPack != null }

    override fun cachedIconFor(member: StackedWidget): ImageBitmap? = cache[cacheKey(member)]

    override fun iconFor(member: StackedWidget): ImageBitmap? =
        runCatching {
            val packageName =
                if (member.glance != null) {
                    context.packageName
                } else {
                    AppWidgetManager.getInstance(
                        context,
                    ).getAppWidgetInfo(member.appWidgetId.value)?.provider?.packageName
                }
            packageName?.let { pipeline.forPackage(it, STACK_ICON_PX)?.asImageBitmap() }
        }.getOrNull()?.also { cache[cacheKey(member)] = it }

    private fun cacheKey(member: StackedWidget) =
        "${member.appWidgetId.value}/${member.glance != null}/${pipeline.styleVersion}"
}

/**
 * The sheet that edits a stack (ADR 0003, Amendment 14): its widgets in order, each with its name and its app's icon;
 * move one up or down, remove it (it is deleted, as removing a widget is), or move it out to Home in the nearest free
 * cells (disabled, and saying why, when the page has no room). A stack left with one widget is a plain widget again, so
 * the sheet closes. Drawn like the folder's surface: Regular glass over Home.
 */
@Composable
internal fun WidgetStackEditorSurface(
    stack: WidgetItem,
    layout: HomeLayout,
    icons: WidgetStackMemberIcons,
    onAction: (LauncherShellAction) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassSurface(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(WIDGET_STACK_EDITOR_TEST_TAG)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        weight = GlassWeight.Thick,
        shape = LocalLauncherPanelShape.current,
        tint = launcherPanelSurfaceColor(),
        contentColor = launcherPanelContentColor(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EDITOR_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.widget_stack_edit_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_close)) }
            }
            Text(
                text =
                    stringResource(
                        R.string.widget_stack_edit_summary,
                        stack.members.size,
                        WidgetStackEngine.MAX_MEMBERS,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(stack.members) { index, member ->
                    StackMemberRow(
                        stack = stack,
                        member = member,
                        index = index,
                        canMoveOut = layout.canMoveOut(stack, index),
                        icons = icons,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun StackMemberRow(
    stack: WidgetItem,
    member: StackedWidget,
    index: Int,
    canMoveOut: Boolean,
    icons: WidgetStackMemberIcons,
    onAction: (LauncherShellAction) -> Unit,
) {
    val count = stack.members.size
    val position = stringResource(R.string.widget_stack_row_position, index + 1, count)
    Column(
        modifier =
            Modifier.fillMaxWidth().testTag(WIDGET_STACK_EDITOR_ROW_TEST_TAG).semantics {
                stateDescription = position
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StackMemberIcon(member = member, icons = icons)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = position,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A placeholder member (it could not be bound) is set up from here with the picker's own flow.
            LocalWidgetSetUpSupport.current.requestFor(stack.id, index, member)?.let { request ->
                TextButton(
                    onClick = { onAction(request) },
                    modifier = Modifier.heightIn(min = STACK_ROW_BUTTON_DP.dp),
                ) {
                    Text(text = stringResource(R.string.menu_set_up_widget))
                }
            }
            StackRowButton(
                icon = Icons.Filled.KeyboardArrowUp,
                description = stringResource(R.string.widget_stack_move_up_description, member.label),
                enabled = index > 0,
                onClick = { onAction(LauncherShellAction.MoveStackedWidget(stack.id, index, index - 1)) },
            )
            StackRowButton(
                icon = Icons.Filled.KeyboardArrowDown,
                description = stringResource(R.string.widget_stack_move_down_description, member.label),
                enabled = index < count - 1,
                onClick = { onAction(LauncherShellAction.MoveStackedWidget(stack.id, index, index + 1)) },
            )
            StackRowButton(
                icon = Icons.Filled.Home,
                description = stringResource(R.string.widget_stack_move_out_description, member.label),
                enabled = canMoveOut,
                onClick = { onAction(LauncherShellAction.MoveStackedWidgetOut(stack.id, index)) },
            )
            StackRowButton(
                icon = Icons.Filled.Delete,
                description = stringResource(R.string.widget_stack_remove_description, member.label),
                enabled = true,
                onClick = { onAction(LauncherShellAction.RemoveStackedWidget(stack.id, index)) },
            )
        }
        if (!canMoveOut) {
            Text(
                text = stringResource(R.string.widget_stack_no_room),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A 48 dp icon button of a row. */
@Composable
private fun StackRowButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(STACK_ROW_BUTTON_DP.dp)) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

@Composable
private fun StackMemberIcon(
    member: StackedWidget,
    icons: WidgetStackMemberIcons,
) {
    val version = icons.styleVersion
    var icon by remember(member, icons) { mutableStateOf(icons.cachedIconFor(member)) }
    LaunchedEffect(member, icons, version) {
        icon = icons.cachedIconFor(member) ?: withContext(Dispatchers.Default) { icons.iconFor(member) }
    }
    val shown = icon
    Box(
        modifier = Modifier.size(STACK_ICON_DP.dp).let { if (icons.drawsOwnShape) it else it.clip(CircleShape) },
        contentAlignment = Alignment.Center,
    ) {
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(STACK_ICON_DP.dp),
            )
        } else {
            // The system has no icon for it: the first letter of its name on a solid disc.
            Text(
                text = member.label.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier =
                    Modifier
                        .size(STACK_ICON_DP.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .wrapContentSize(Alignment.Center),
            )
        }
    }
}

internal const val WIDGET_STACK_EDITOR_TEST_TAG = "widget-stack-editor"
internal const val WIDGET_STACK_EDITOR_ROW_TEST_TAG = "widget-stack-editor-row"
private const val EDITOR_PADDING_DP = 20
private const val STACK_ICON_DP = 40
private const val STACK_ICON_PX = 96
private const val STACK_ICON_CACHE = 32
private const val STACK_ROW_BUTTON_DP = 48

/** Removing a stack removes every widget in it, so it asks first (a lone widget does not). */
@Composable
internal fun ConfirmRemoveWidgetStackDialog(
    widgetCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.widget_stack_remove_title)) },
        text = { Text(text = stringResource(R.string.widget_stack_remove_message, widgetCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(WIDGET_STACK_REMOVE_CONFIRM_TEST_TAG)) {
                Text(text = stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

internal const val WIDGET_STACK_REMOVE_CONFIRM_TEST_TAG = "widget-stack-remove-confirm"
