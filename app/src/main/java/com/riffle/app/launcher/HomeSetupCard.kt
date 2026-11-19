package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus

/**
 * The card that asks for the Home role before the user has seen anything: one sentence of purpose, the one action
 * ("Set as Home app") and "Not now". The caveat about the Home button returning to another launcher is a one-line
 * explanation shown only once the role request was declined ([requestDeclined]), where it is relevant.
 */
@Composable
internal fun PreviewSetupCard(
    homeRoleStatus: HomeRoleStatus,
    firstRunStatus: FirstRunStatus,
    onSetHome: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    requestDeclined: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier) {
        Surface(
            modifier = Modifier.heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            val checking = firstRunStatus == FirstRunStatus.REQUESTING_HOME_ROLE
            val status = stringResource(statusRes(checking, homeRoleStatus))
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .semantics { stateDescription = status },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_card_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                when {
                    checking -> {
                        Text(text = status, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.setup_card_checking_detail),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    homeRoleStatus == HomeRoleStatus.UNKNOWN ->
                        Text(
                            text = stringResource(R.string.setup_card_status_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    else ->
                        Text(
                            text = stringResource(R.string.setup_card_purpose),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                }
                if (requestDeclined && !checking && homeRoleStatus != HomeRoleStatus.DEFAULT_HOME) {
                    Text(
                        text = stringResource(R.string.setup_card_declined),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!checking) {
                    Button(onClick = onSetHome) { Text(text = stringResource(actionRes(homeRoleStatus))) }
                }
                TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.setup_card_not_now)) }
            }
        }
    }
}

private fun statusRes(
    checking: Boolean,
    homeRoleStatus: HomeRoleStatus,
): Int =
    when {
        checking -> R.string.setup_card_status_checking
        homeRoleStatus == HomeRoleStatus.DEFAULT_HOME -> R.string.setup_card_status_default
        homeRoleStatus == HomeRoleStatus.NOT_DEFAULT_HOME -> R.string.setup_card_status_not_default
        else -> R.string.setup_card_status_unknown
    }

private fun actionRes(homeRoleStatus: HomeRoleStatus): Int =
    when (homeRoleStatus) {
        HomeRoleStatus.DEFAULT_HOME -> R.string.setup_card_action_open_settings
        HomeRoleStatus.NOT_DEFAULT_HOME -> R.string.setup_card_action_set
        HomeRoleStatus.UNKNOWN -> R.string.setup_card_action_try_again
    }
