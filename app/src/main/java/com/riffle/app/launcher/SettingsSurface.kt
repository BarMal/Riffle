package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSurface(
    state: SettingsSurfaceState,
    initialPage: SettingsPage = SettingsPage.MAIN,
    onAction: (LauncherShellAction) -> Unit,
    onRequestAdaptiveStageAppearanceTuning: () -> Unit = {},
) {
    // Keep the active page and its per-page scroll position through activity recreation,
    // while this settings session remains open. Leaving Settings removes this state.
    val selectedPage = rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    val pageScrollStates = settingsPageScrollStates()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = selectedPage.value != SettingsPage.MAIN) {
        selectedPage.value = settingsBackTarget(selectedPage.value)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SettingsPageHeader(
                title = selectedPage.value.title,
                appVersionLabel = state.appVersionLabel,
                showBack = selectedPage.value != SettingsPage.MAIN,
                onBack = { selectedPage.value = settingsBackTarget(selectedPage.value) },
                onAction = onAction,
            )
            Spacer(modifier = Modifier.height(24.dp))
            val pageContentModifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                    .align(Alignment.CenterHorizontally)
            // The appearance page manages its own sticky preview, tabs, and inner scroll
            // rather than sharing this page-wide scroll container.
            val settingsContentModifier =
                if (selectedPage.value == SettingsPage.ADAPTIVE_STAGE_APPEARANCE) {
                    pageContentModifier
                } else {
                    pageContentModifier.verticalScroll(
                        settingsPageScrollStateFor(pageScrollStates, selectedPage.value),
                    )
                }
            CompositionLocalProvider(LocalSettingsSnackbarHostState provides snackbarHostState) {
                SettingsPageContent(
                    modifier = settingsContentModifier,
                    state = state,
                    page = selectedPage.value,
                    onPageSelected = { page -> selectedPage.value = page },
                    onAction = onAction,
                    onRequestAdaptiveStageAppearanceTuning = onRequestAdaptiveStageAppearanceTuning,
                )
            }
            // Takes no room until a page announces something (the Workspaces page's confirmations and Undo).
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ColumnScope.SettingsPageHeader(
    title: String,
    appVersionLabel: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                .align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = appVersionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showBack) {
                TextButton(onClick = onBack) {
                    SettingsButtonText(text = "Back")
                }
            }
            TextButton(onClick = { onAction(LauncherShellAction.OpenDefaultHome) }) {
                SettingsButtonText(text = "Home")
            }
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSectionContainer {
            content()
        }
    }
}

/**
 * The per-section rounded "card" container behind every [SettingsSection]'s rows.
 *
 * When liquid glass is switched on ([LocalLiquidGlassSettings.current.enabled]), this uses the
 * shared [GlassSurface] material -- the same one Settings' own "Cards appearance" tuning sheet
 * (`AdaptiveStageAppearanceTuningOverlay.kt`) already renders with -- tinted with today's
 * [ColorScheme.surfaceContainerLow] so a glassy section still reads as a Settings surface rather
 * than a color change. Settings is reached as its own top-level [ShellDestination], composed as a
 * sibling of [HomeDestination] rather than inside it (see `LauncherShell.kt`'s
 * `when (state.destination)`), so [LocalLiquidGlassBackdrop] is never installed here and
 * `GlassSurface` always takes its below-API-33/no-backdrop fallback: a blurred flat tint layer, not
 * true backdrop refraction. That is a deliberate, scoped limitation (see this PR's description) --
 * there is nothing interesting behind a settings section worth refracting (just the flat screen
 * background from [SettingsSurface]'s own root `Surface`, left untouched by this change) -- rather
 * than installing a second, pointless backdrop capture just to say the "real" shader path runs here.
 *
 * When liquid glass is switched off, this renders exactly today's flat [Surface] fill -- unchanged
 * pixels, zero regression -- since [GlassSurface]'s own disabled-state fallback is a translucent
 * blurred tint, not the flat opaque fill this container had before liquid glass existed.
 */
@Composable
private fun SettingsSectionContainer(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val tint = MaterialTheme.colorScheme.surfaceContainerLow
    if (LocalLiquidGlassSettings.current.enabled) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            tint = tint,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = tint,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun settingsPageScrollStates(): Map<SettingsPage, ScrollState> =
    buildMap {
        SettingsPage.entries.forEach { page ->
            put(page, rememberScrollState())
        }
    }

internal fun settingsPageScrollStateFor(
    pageScrollStates: Map<SettingsPage, ScrollState>,
    page: SettingsPage,
): ScrollState = pageScrollStates.getValue(page)

private const val SETTINGS_PAGE_MAX_WIDTH_DP = 840
