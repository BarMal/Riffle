@file:OptIn(ExperimentalFoundationApi::class)

package com.riffle.app.launcher.search.wider

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.expressions.ItemImage
import com.riffle.app.launcher.groupNameRes
import com.riffle.app.launcher.launcherPanelContentColor
import com.riffle.app.launcher.stream.messageRes
import com.riffle.core.domain.launcher.onboarding.PermissionGain
import com.riffle.core.domain.launcher.search.wider.FileKind
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.WiderSearchAction
import com.riffle.core.domain.launcher.search.wider.WiderSearchResult
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

internal const val WIDER_ROW_TEST_TAG_PREFIX = "wider-row:"
internal const val WIDER_SHOW_MORE_TEST_TAG_PREFIX = "wider-show-more:"
internal const val WIDER_ALLOW_TEST_TAG_PREFIX = "wider-allow:"
internal const val WIDER_HEADER_TEST_TAG_PREFIX = "wider-header:"
internal const val WIDER_COPY_BOTH_TEST_TAG = "wider-copy-both"
internal const val WIDER_CALL_TEST_TAG = "wider-call"
internal const val WIDER_MESSAGE_TEST_TAG = "wider-message"

private val WiderMinRowHeight = 56.dp
private val WiderGlyphSize = 40.dp

/** Where a row stands among its group's, for the screen reader: "Contacts, 2 of 4". */
internal class WiderRowPosition(
    val group: String,
    val index: Int,
    val count: Int,
)

/** One result as a row, with the actions its kind has. Primary action on the row, secondary ones beside it. */
@Composable
internal fun WiderResultRow(
    result: WiderSearchResult,
    position: WiderRowPosition,
    model: WiderSearchModel<*>,
    modifier: Modifier = Modifier,
) {
    when (result) {
        is WiderSearchResult.CalculatorAnswer ->
            AnswerRow(
                answer = result.answer,
                expression = result.expression,
                copyBothLabel = stringResource(R.string.search_copy_sum),
                copyBoth = "${result.expression} = ${result.answer}",
                result = result,
                model = model,
                modifier = modifier,
            )
        is WiderSearchResult.UnitConversion ->
            AnswerRow(
                answer = result.answer,
                expression = result.input,
                copyBothLabel = stringResource(R.string.search_copy_both),
                copyBoth = "${result.input} = ${result.answer}",
                result = result,
                model = model,
                modifier = modifier,
            )
        is WiderSearchResult.SystemSetting ->
            WiderRow(
                result = result,
                position = position,
                title = result.title,
                subtitle = null,
                glyph = Icons.Filled.Settings,
                clickLabel = stringResource(R.string.search_open_setting, result.title),
                onClick = { model.perform(WiderSearchAction.OpenSystemSetting(result.intentAction)) },
                modifier = modifier,
            )
        is WiderSearchResult.Contact -> ContactRow(result, position, model, modifier)
        is WiderSearchResult.FileHit ->
            WiderRow(
                result = result,
                position = position,
                title = result.name,
                subtitle = stringResource(result.kind.labelRes()),
                glyph = Icons.Filled.Info,
                clickLabel = null,
                onClick = { model.perform(WiderSearchAction.OpenFile(result.uri, result.mimeType)) },
                modifier = modifier,
            )
        is WiderSearchResult.FilesApp ->
            WiderRow(
                result = result,
                position = position,
                title = stringResource(R.string.search_files_app_title),
                subtitle = result.query,
                glyph = Icons.Filled.Search,
                clickLabel = null,
                onClick = { model.perform(WiderSearchAction.OpenFilesApp(result.query)) },
                modifier = modifier,
            )
    }
}

private fun FileKind.labelRes(): Int =
    when (this) {
        FileKind.IMAGE -> R.string.search_file_kind_image
        FileKind.VIDEO -> R.string.search_file_kind_video
        FileKind.AUDIO -> R.string.search_file_kind_audio
        FileKind.DOWNLOAD -> R.string.search_file_kind_download
        FileKind.DOCUMENT -> R.string.search_file_kind_document
    }

/**
 * The calculator's or the converter's answer, big: a tap copies it and says so; the long-press and the trailing
 * button copy it together with what was asked ("12 × 3 = 36").
 */
@Composable
private fun AnswerRow(
    answer: String,
    expression: String,
    copyBothLabel: String,
    copyBoth: String,
    result: WiderSearchResult,
    model: WiderSearchModel<*>,
    modifier: Modifier,
) {
    val copyAnswer = stringResource(R.string.search_copy_answer)
    val spoken = stringResource(R.string.search_calculator_equals, expression, answer)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WiderMinRowHeight)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = copyAnswer,
                    onClick = { model.perform(WiderSearchAction.Copy(answer)) },
                    onLongClickLabel = copyBothLabel,
                    onLongClick = { model.perform(WiderSearchAction.Copy(copyBoth)) },
                )
                .padding(start = 16.dp, end = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .testTag(WIDER_ROW_TEST_TAG_PREFIX + result.key),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = answer,
                style = MaterialTheme.typography.headlineSmall,
                color = launcherPanelContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expression,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag(WIDER_COPY_BOTH_TEST_TAG),
            onClick = { model.perform(WiderSearchAction.Copy(copyBoth)) },
        ) {
            Text(text = copyBothLabel)
        }
    }
}

@Composable
private fun ContactRow(
    contact: WiderSearchResult.Contact,
    position: WiderRowPosition,
    model: WiderSearchModel<*>,
    modifier: Modifier,
) {
    val number = contact.number
    WiderRow(
        result = contact,
        position = position,
        title = contact.name,
        subtitle = number,
        glyph = Icons.Filled.Person,
        photo = contact.photoUri?.let { uri -> ItemImageHandle("contact-photo:$uri") },
        clickLabel = stringResource(R.string.search_open_contact, contact.name),
        onClick = { model.perform(WiderSearchAction.OpenContact(contact.contactUri)) },
        modifier = modifier,
        model = model,
    ) {
        if (number != null) {
            IconButton(
                modifier = Modifier.testTag(WIDER_CALL_TEST_TAG),
                onClick = { model.perform(WiderSearchAction.Dial(number)) },
            ) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.search_call_contact, contact.name))
            }
            IconButton(
                modifier = Modifier.testTag(WIDER_MESSAGE_TEST_TAG),
                onClick = { model.perform(WiderSearchAction.Message(number)) },
            ) {
                Icon(
                    Icons.Filled.Email,
                    contentDescription = stringResource(R.string.search_message_contact, contact.name),
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun WiderRow(
    result: WiderSearchResult,
    position: WiderRowPosition,
    title: String,
    subtitle: String?,
    glyph: ImageVector,
    clickLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    photo: ItemImageHandle? = null,
    model: WiderSearchModel<*>? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val place = stringResource(R.string.search_group_position, position.group, position.index + 1, position.count)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WiderMinRowHeight)
                .padding(start = 12.dp, end = 4.dp)
                .testTag(WIDER_ROW_TEST_TAG_PREFIX + result.key),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = WiderMinRowHeight)
                    .combinedClickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                    .semantics(mergeDescendants = true) { stateDescription = place },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (photo != null && model != null) {
                ItemImage(handle = photo, loader = model.contactPhotos, size = WiderGlyphSize, shape = CircleShape)
            } else {
                WiderGlyph(glyph)
            }
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = launcherPanelContentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        trailing()
    }
}

@Composable
private fun WiderGlyph(glyph: ImageVector) {
    Box(
        modifier =
            Modifier
                .size(WiderGlyphSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** A group's header: its name, a quiet label above its rows. */
@Composable
internal fun WiderGroupHeader(
    source: SearchSourceId,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(source.groupNameRes()),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
                .semantics { heading() }
                .testTag(WIDER_HEADER_TEST_TAG_PREFIX + source.name),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Show N more": truncates nothing, only opens the group. A 48 dp target that names its group. */
@Composable
internal fun WiderShowMore(
    source: SearchSourceId,
    hidden: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = stringResource(source.groupNameRes())
    val description = stringResource(R.string.search_show_more_in, group)
    TextButton(
        modifier =
            modifier
                .padding(start = 8.dp)
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = description }
                .testTag(WIDER_SHOW_MORE_TEST_TAG_PREFIX + source.name),
        onClick = onClick,
    ) {
        Text(text = stringResource(R.string.search_show_more, hidden))
    }
}

/** The one inline row of a source that is on but lacks its permission: what allowing gains, and "Allow". */
@Composable
internal fun WiderAllowRow(
    source: SearchSourceId,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = WiderMinRowHeight)
                .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(PermissionGain.of(source).messageRes()),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = launcherPanelContentColor(),
        )
        FilledTonalButton(
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag(WIDER_ALLOW_TEST_TAG_PREFIX + source.name),
            onClick = onAllow,
        ) {
            Text(text = stringResource(R.string.stream_permission_action))
        }
    }
}
