package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchWebPanel(
    preview: SearchWebPreview,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Web · ${preview.subtitle}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = { onAction(preview.action) }) {
                    Text(text = preview.actionLabel)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            preview.examples.forEach { result ->
                SearchWebExampleButton(
                    result = result,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchWebExampleButton(
    result: SearchWebExampleResult,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        modifier = modifier,
        onClick = { onAction(result.action) },
        label = {
            Text(
                text = result.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = result.sourceIcon,
                contentDescription = result.sourceIconContentDescription,
            )
        },
    )
}

private val SearchWebExampleResult.sourceIcon: ImageVector
    get() =
        when (title) {
            "Images" -> SearchImageIcon
            "News" -> SearchNewsIcon
            "Videos" -> SearchVideoIcon
            else -> SearchWebIcon
        }

private val SearchWebExampleResult.sourceIconContentDescription: String
    get() = "$title search icon"

private val SearchImageIcon =
    ImageVector.Builder(
        name = "SearchImage",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        ) {
            moveTo(3f, 4f)
            lineTo(21f, 4f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            close()
            moveTo(5f, 17f)
            lineTo(9f, 12f)
            lineTo(12f, 15f)
            lineTo(15f, 10f)
            lineTo(19f, 17f)
            close()
        }
    }.build()

private val SearchNewsIcon =
    ImageVector.Builder(
        name = "SearchNews",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        ) {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(7f, 7f)
            lineTo(11f, 7f)
            lineTo(11f, 12f)
            lineTo(7f, 12f)
            close()
            moveTo(13f, 7f)
            lineTo(18f, 7f)
            lineTo(18f, 9f)
            lineTo(13f, 9f)
            close()
            moveTo(13f, 11f)
            lineTo(18f, 11f)
            lineTo(18f, 13f)
            lineTo(13f, 13f)
            close()
            moveTo(7f, 15f)
            lineTo(18f, 15f)
            lineTo(18f, 17f)
            lineTo(7f, 17f)
            close()
        }
    }.build()

private val SearchVideoIcon =
    ImageVector.Builder(
        name = "SearchVideo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        ) {
            moveTo(3f, 6f)
            lineTo(16f, 6f)
            lineTo(16f, 18f)
            lineTo(3f, 18f)
            close()
            moveTo(17f, 10f)
            lineTo(21f, 7f)
            lineTo(21f, 17f)
            lineTo(17f, 14f)
            close()
            moveTo(8f, 9f)
            lineTo(8f, 15f)
            lineTo(13f, 12f)
            close()
        }
    }.build()

private val SearchWebIcon =
    ImageVector.Builder(
        name = "SearchWeb",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            lineTo(12f, 21f)
            lineTo(10f, 21f)
            lineTo(10f, 3f)
            close()
            moveTo(3f, 11f)
            lineTo(21f, 11f)
            lineTo(21f, 13f)
            lineTo(3f, 13f)
            close()
        }
    }.build()
