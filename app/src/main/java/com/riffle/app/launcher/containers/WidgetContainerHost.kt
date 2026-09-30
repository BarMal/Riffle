package com.riffle.app.launcher.containers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.workspace.WidgetContainer

/**
 * A sized area hosting one lens and its expression, on a tonal surface. The caller sizes it (a grid page
 * gives it the cells its span covers). Its lens is observed through the shared provider, so any number of
 * widgets reading the same source share one subscription, and the observation ends when the widget leaves
 * the composition.
 */
@Composable
fun WidgetContainerHost(
    container: WidgetContainer,
    services: ContainerServices,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RiffleShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LensBoundExpression(
            binding = container.binding,
            services = services,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
