package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.riffle.app.launcher.designsystem.RiffleSpacing

/**
 * Wraps an expression's content with the loading, unavailable and empty states every expression
 * shares, and caps the content width so text does not stretch across a tablet. The spinner is
 * replaced by static text under reduced motion.
 */
@Composable
internal fun ExpressionStateHost(
    state: ExpressionState,
    isEmpty: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = Dp.Infinity,
    emptyMessage: String = ExpressionText.EMPTY,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxContentWidth).fillMaxWidth()) {
            when {
                state is ExpressionState.Loading ->
                    ExpressionStatusMessage(ExpressionText.LOADING, showSpinner = !reducedMotion)
                state is ExpressionState.Unavailable ->
                    ExpressionStatusMessage(state.message, showSpinner = false)
                isEmpty -> ExpressionStatusMessage(emptyMessage, showSpinner = false)
                else -> content()
            }
        }
    }
}

@Composable
private fun ExpressionStatusMessage(
    message: String,
    showSpinner: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(RiffleSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(RiffleSpacing.xl))
        }
        Text(
            text = message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
