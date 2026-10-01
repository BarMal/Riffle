package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.containers.LensBoundExpression
import com.riffle.app.launcher.containers.PageSetContainerHost
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.PageSetContainer

/**
 * Draws [target] through the same containers and expressions the workspace uses, over [services]' provider (the
 * real one in the app, a static one in tests). Evaluation and image loading stay off the main thread inside them.
 */
@Composable
internal fun EditorPreview(
    target: PreviewTarget,
    services: ContainerServices,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = EditorText.PREVIEW },
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = RiffleElevation.level1,
    ) {
        when (target) {
            is PreviewTarget.Empty ->
                Box(modifier = Modifier.fillMaxSize().padding(RiffleSpacing.l), contentAlignment = Alignment.Center) {
                    Text(
                        text = target.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            is PreviewTarget.Single -> LensBoundExpression(target.binding, services, Modifier.fillMaxSize())
            is PreviewTarget.PerGroup ->
                PageSetContainerHost(PageSetContainer(PREVIEW_ID, target.binding), services, Modifier.fillMaxSize())
        }
    }
}

private val PREVIEW_ID = ContainerId("editor.preview")
