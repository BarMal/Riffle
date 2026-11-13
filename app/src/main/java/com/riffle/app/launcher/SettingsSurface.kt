package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun SettingsSurface(
    state: SettingsSurfaceState,
    initialPage: SettingsPage = SettingsPage.MAIN,
    initialAnchor: SettingKey? = null,
    onAction: (LauncherShellAction) -> Unit,
) {
    // Keep the active page and its per-page scroll position through activity recreation,
    // while this settings session remains open. Leaving Settings removes this state.
    val selectedPage = rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    val pendingAnchor = rememberSaveable(initialPage, initialAnchor) { mutableStateOf(initialAnchor) }
    val pageScrollStates = settingsPageScrollStates()
    val snackbarHostState = remember { SnackbarHostState() }
    val anchors = remember { SettingsAnchors() }
    ScrollToPendingAnchor(
        page = selectedPage.value,
        anchor = pendingAnchor.value,
        anchors = anchors,
        onScrolled = { pendingAnchor.value = null },
    )

    BackHandler(enabled = selectedPage.value != SettingsPage.MAIN) {
        pendingAnchor.value = null
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
                title = stringResource(selectedPage.value.titleRes),
                appVersionLabel = state.appVersionLabel,
                showBack = selectedPage.value != SettingsPage.MAIN,
                onBack = {
                    pendingAnchor.value = null
                    selectedPage.value = settingsBackTarget(selectedPage.value)
                },
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
                pageContentModifier.verticalScroll(settingsPageScrollStateFor(pageScrollStates, selectedPage.value))
            CompositionLocalProvider(
                LocalSettingsSnackbarHostState provides snackbarHostState,
                LocalSettingsAnchors provides anchors,
            ) {
                SettingsPageContent(
                    modifier = settingsContentModifier,
                    state = state,
                    page = selectedPage.value,
                    onPageSelected = { page ->
                        pendingAnchor.value = null
                        selectedPage.value = page
                    },
                    onAction = onAction,
                    onSettingSelected = { target ->
                        pendingAnchor.value = target.anchor
                        selectedPage.value = target.page
                    },
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
                modifier = Modifier.semantics { heading() },
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
                    SettingsButtonText(text = stringResource(R.string.settings_back))
                }
            }
            TextButton(onClick = { onAction(LauncherShellAction.OpenDefaultHome) }) {
                SettingsButtonText(text = stringResource(R.string.settings_home))
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
 * The per-section rounded container behind every [SettingsSection]'s rows: Thick [GlassSurface],
 * tinted with [ColorScheme.surfaceContainerLow] so a glassy section still reads as a Settings
 * surface rather than a colour change.
 */
@Composable
private fun SettingsSectionContainer(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        weight = GlassWeight.Thick,
        shape = RoundedCornerShape(24.dp),
        tint = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            content()
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

/**
 * Once the page that holds [anchor] has composed its row, scrolls it into view (after a frame, so it is laid out) and
 * clears the request. A page with no such row clears nothing until the page changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollToPendingAnchor(
    page: SettingsPage,
    anchor: SettingKey?,
    anchors: SettingsAnchors,
    onScrolled: () -> Unit,
) {
    LaunchedEffect(page, anchor) {
        if (anchor == null || anchor.page != page) return@LaunchedEffect
        val requester = snapshotFlow { anchors.requesters[anchor] }.filterNotNull().first()
        withFrameNanos { }
        withFrameNanos { }
        requester.bringIntoView()
        onScrolled()
    }
}
