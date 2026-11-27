package com.riffle.app.launcher.summon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.settings.EffectiveFeatures

private val MENU_MAX_WIDTH = 420.dp
private val MENU_ICON_SIZE = 44.dp
private val MIN_TOUCH = 48.dp

/**
 * Summon's menu: a tap on Home (or a hold released where it started) opens it. It is the non-gesture route to
 * everything the ring holds (each app as a button), and the way to Search, the Library, the Stream, Summon's settings,
 * all of Settings, and out of Summon. A tap outside closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummonMenu(
    model: SummonRingModel,
    features: EffectiveFeatures,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val run: (LauncherShellAction) -> Unit = { action ->
        onDismiss()
        onAction(action)
    }
    val closeLabel = stringResource(R.string.summon_menu_close)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = onDismiss,
                ).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(RiffleSpacing.l),
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassSurface(
            modifier = Modifier.widthIn(max = MENU_MAX_WIDTH).fillMaxWidth(),
            weight = GlassWeight.Thick,
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(Modifier.padding(RiffleSpacing.l), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
                val apps = summonApps(model)
                if (apps.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
                        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
                    ) {
                        apps.forEach { app ->
                            val openLabel = stringResource(R.string.summon_open_app, app.label)
                            Box(
                                modifier =
                                    Modifier
                                        .sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
                                        .clickable(
                                            role = Role.Button,
                                        ) { run(LauncherShellAction.LaunchApp(app.identity)) }
                                        .semantics { contentDescription = openLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                LauncherAppIcon(
                                    app.identity,
                                    app.label,
                                    appIconLoader,
                                    Modifier.size(MENU_ICON_SIZE),
                                    CircleShape,
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                    MenuButton(R.string.summon_menu_search) { run(LauncherShellAction.OpenSearch) }
                    if (features.library) {
                        MenuButton(
                            R.string.summon_menu_library,
                        ) { run(LauncherShellAction.OpenAppDrawer) }
                    }
                    if (features.stream) MenuButton(R.string.summon_menu_stream) { run(LauncherShellAction.OpenStream) }
                    MenuButton(R.string.summon_menu_summon_settings) {
                        run(LauncherShellAction.OpenSettingsPage(SettingsPage.SUMMON))
                    }
                    MenuButton(R.string.summon_menu_settings) { run(LauncherShellAction.OpenSettings) }
                    MenuButton(R.string.summon_menu_leave) { run(LauncherShellAction.SetSummonMode(false)) }
                }
                Text(stringResource(R.string.summon_menu_hint), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.sizeIn(minHeight = MIN_TOUCH)) {
        Text(stringResource(label))
    }
}
