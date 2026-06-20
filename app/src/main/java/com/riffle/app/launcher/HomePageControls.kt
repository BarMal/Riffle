package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun PageEditControls(
    pageCount: Int,
    selectedPageIndex: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = selectedPageIndex > 0,
            onClick = { onAction(LauncherShellAction.SelectPreviousHomePage) },
        ) {
            Text(text = "<")
        }
        TextButton(onClick = { onAction(LauncherShellAction.AddHomePage) }) {
            Text(text = "Add page")
        }
        TextButton(
            enabled = pageCount > 1,
            onClick = { onAction(LauncherShellAction.DeleteSelectedHomePage) },
        ) {
            Text(text = "Delete page")
        }
        TextButton(
            enabled = selectedPageIndex < pageCount - 1,
            onClick = { onAction(LauncherShellAction.SelectNextHomePage) },
        ) {
            Text(text = ">")
        }
    }
}

@Composable
fun PageIndicator(
    pageCount: Int,
    selectedPageIndex: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(pageIndicatorColor(index = index, selectedPageIndex = selectedPageIndex)),
            )
        }
    }
}

@Composable
private fun pageIndicatorColor(
    index: Int,
    selectedPageIndex: Int,
) = if (index == selectedPageIndex) {
    MaterialTheme.colorScheme.onSurface
} else {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
}
