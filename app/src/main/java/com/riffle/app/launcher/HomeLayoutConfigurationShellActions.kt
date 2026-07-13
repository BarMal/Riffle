@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomePageEditRejectionReason
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.MAX_HOME_GRID_MARGIN_DP
import com.riffle.core.domain.launcher.home.MAX_HOME_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_MAX_LINES
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_MAX_WIDTH_DP
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_TEXT_SIZE_SP
import com.riffle.core.domain.launcher.home.MIN_HOME_GRID_MARGIN_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_MAX_LINES
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_MAX_WIDTH_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_TEXT_SIZE_SP

@Suppress("CyclomaticComplexMethod")
internal fun HomePageEngine.applyHomeLayoutConfigurationEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        is LauncherShellAction.SelectSelectedHomePageType ->
            updatePageType(
                layout = layout,
                pageId = layout.selectedPageId,
                type = action.type,
            )

        is LauncherShellAction.SelectSelectedHomePageGridDimensions ->
            updatePageGridDimensions(
                layout = layout,
                pageId = layout.selectedPageId,
                dimensions = action.dimensions,
            )

        is LauncherShellAction.SelectHomeGridDimensions ->
            updateGridDimensions(
                layout = layout.layoutForGridDimensionUpdate(),
                dimensions = action.dimensions,
            )

        is LauncherShellAction.SelectHomeGridMargin ->
            HomePageEditResult.Updated(
                layout.copy(
                    settings =
                        layout.settings.copy(
                            grid =
                                layout.settings.grid.copy(
                                    margin =
                                        GridInsets(
                                            start =
                                                action.horizontalDp.coerceIn(
                                                    MIN_HOME_GRID_MARGIN_DP,
                                                    MAX_HOME_GRID_MARGIN_DP,
                                                ),
                                            top =
                                                action.verticalDp.coerceIn(
                                                    MIN_HOME_GRID_MARGIN_DP,
                                                    MAX_HOME_GRID_MARGIN_DP,
                                                ),
                                            end =
                                                action.horizontalDp.coerceIn(
                                                    MIN_HOME_GRID_MARGIN_DP,
                                                    MAX_HOME_GRID_MARGIN_DP,
                                                ),
                                            bottom =
                                                action.verticalDp.coerceIn(
                                                    MIN_HOME_GRID_MARGIN_DP,
                                                    MAX_HOME_GRID_MARGIN_DP,
                                                ),
                                        ),
                                ),
                        ),
                ),
            )

        is LauncherShellAction.SelectLibraryPageCompaction ->
            HomePageEditResult.Updated(layout.withLibraryPageCompaction(action.enabled))

        is LauncherShellAction.SelectHomeLabelBackgroundAlpha ->
            layout.withHomeLabelBackgroundAlpha(action.alphaPercent)

        is LauncherShellAction.SelectHomeIconSize -> layout.withHomeIconSize(action.sizeDp)

        is LauncherShellAction.SelectHomeLabelTextSize ->
            layout.withHomeLabelTextSize(action.textSizeSp)

        is LauncherShellAction.SelectHomeLabelTextVisible ->
            HomePageEditResult.Updated(layout.withHomeLabelTextVisible(action.visible))

        is LauncherShellAction.SelectHomeLabelMaxWidth ->
            layout.withHomeLabelMaxWidth(action.maxWidthDp)

        is LauncherShellAction.SelectHomeLabelMaxLines ->
            layout.withHomeLabelMaxLines(action.maxLines)

        is LauncherShellAction.SelectHomeLabelSizing ->
            HomePageEditResult.Updated(layout.withHomeLabelSizing(action.sizing))

        is LauncherShellAction.SelectLauncherViewMode ->
            HomePageEditResult.Updated(layout.withLauncherViewMode(action.mode))

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomeLayout.withHomeLabelBackgroundAlpha(alphaPercent: Int): HomePageEditResult =
    when (alphaPercent) {
        in MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT..MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT ->
            HomePageEditResult.Updated(
                copy(
                    settings =
                        settings.copy(
                            labels = settings.labels.copy(backgroundAlphaPercent = alphaPercent),
                        ),
                ),
            )

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_LABEL_SETTING)
    }

private fun HomeLayout.withHomeIconSize(sizeDp: Int): HomePageEditResult =
    when (sizeDp) {
        in MIN_HOME_ICON_SIZE_DP..MAX_HOME_ICON_SIZE_DP ->
            HomePageEditResult.Updated(
                copy(settings = settings.copy(labels = settings.labels.copy(iconSizeDp = sizeDp))),
            )

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_LABEL_SETTING)
    }

private fun HomeLayout.withHomeLabelMaxLines(maxLines: Int): HomePageEditResult =
    when (maxLines) {
        in MIN_HOME_LABEL_MAX_LINES..MAX_HOME_LABEL_MAX_LINES ->
            HomePageEditResult.Updated(
                copy(
                    settings =
                        settings.copy(
                            labels = settings.labels.copy(maxLines = maxLines),
                        ),
                ),
            )

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_LABEL_SETTING)
    }

private fun HomeLayout.withHomeLabelMaxWidth(maxWidthDp: Int): HomePageEditResult =
    when (maxWidthDp) {
        in MIN_HOME_LABEL_MAX_WIDTH_DP..MAX_HOME_LABEL_MAX_WIDTH_DP ->
            HomePageEditResult.Updated(
                copy(
                    settings =
                        settings.copy(
                            labels = settings.labels.copy(maxWidthDp = maxWidthDp),
                        ),
                ),
            )

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_LABEL_SETTING)
    }

private fun HomeLayout.withHomeLabelTextVisible(visible: Boolean): HomeLayout =
    copy(
        settings =
            settings.copy(
                labels = settings.labels.copy(showText = visible),
            ),
    )

private fun HomeLayout.withHomeLabelSizing(sizing: HomeLabelSizing): HomeLayout =
    copy(
        settings =
            settings.copy(
                labels = settings.labels.copy(sizing = sizing),
            ),
    )

private fun HomeLayout.withHomeLabelTextSize(textSizeSp: Int): HomePageEditResult =
    when (textSizeSp) {
        in MIN_HOME_LABEL_TEXT_SIZE_SP..MAX_HOME_LABEL_TEXT_SIZE_SP ->
            HomePageEditResult.Updated(
                copy(
                    settings =
                        settings.copy(
                            labels = settings.labels.copy(textSizeSp = textSizeSp),
                        ),
                ),
            )

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_LABEL_SETTING)
    }

private fun HomeLayout.withLauncherViewMode(mode: LauncherViewMode): HomeLayout =
    copy(viewMode = mode).let { updatedLayout ->
        when (mode) {
            LauncherViewMode.HOME_SCREEN_LIBRARY -> updatedLayout
            LauncherViewMode.STANDARD_APP_DRAWER,
            LauncherViewMode.CARD_INTERFACE,
            -> updatedLayout.withoutHomeScreenLibraryApps()
        }
    }

private fun HomeLayout.layoutForGridDimensionUpdate(): HomeLayout =
    when {
        viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY && settings.grid.compactLibraryPages ->
            withoutHomeScreenLibraryApps().copy(viewMode = viewMode)

        else -> this
    }

private fun HomeLayout.withLibraryPageCompaction(enabled: Boolean): HomeLayout =
    copy(
        settings =
            settings.copy(
                grid = settings.grid.copy(compactLibraryPages = enabled),
            ),
    )
