package com.riffle.app.launcher

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.riffle.app.R
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.WidgetProviderCatalogStatus
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

/** What a placeholder (a platform widget with no host id) can do about itself. */
internal enum class PlaceholderState {
    /** The widget is bound, or one of Riffle's own: nothing to set up. */
    NOT_A_PLACEHOLDER,

    /** No provider was recorded for it: it keeps its old text and can only be removed. */
    NO_PROVIDER,

    /** The provider's app is gone: it says so and can only be removed. */
    PROVIDER_GONE,

    /** Tapping it (or "Set up widget") binds it again, asks for the permission and configures it. */
    CAN_SET_UP,
}

/**
 * What Home knows about the installed widget providers, for setting up a placeholder (ADR 0003, Amendment 18). While
 * the catalog is not loaded ([catalogReady] false) nothing is called gone: the system decides when the flow runs.
 */
internal class WidgetSetUpSupport(
    private val catalogReady: Boolean = false,
    providers: List<InstalledWidgetProvider> = emptyList(),
) {
    private val byIdentity: Map<WidgetProviderIdentity, InstalledWidgetProvider> = providers.associateBy { it.identity }

    fun stateOf(member: StackedWidget): PlaceholderState =
        when {
            !member.needsSetUp -> PlaceholderState.NOT_A_PLACEHOLDER
            member.provider == null -> PlaceholderState.NO_PROVIDER
            catalogReady && member.provider !in byIdentity -> PlaceholderState.PROVIDER_GONE
            else -> PlaceholderState.CAN_SET_UP
        }

    /** The request that runs the same bind, permission and configuration flow as the picker, in place. */
    fun requestFor(
        itemId: LauncherItemId,
        memberIndex: Int,
        member: StackedWidget,
    ): LauncherShellAction.RequestAddWidget? {
        val provider = member.provider
        return if (provider == null || stateOf(member) != PlaceholderState.CAN_SET_UP) {
            null
        } else {
            val installed = byIdentity[provider]
            LauncherShellAction.RequestAddWidget(
                provider = provider,
                label = member.label,
                dimensions = installed?.dimensions ?: WidgetProviderDimensions(minWidthDp = 1, minHeightDp = 1),
                supportsHorizontalResize = installed?.supportsHorizontalResize,
                supportsVerticalResize = installed?.supportsVerticalResize,
                target = WidgetAddTarget.HOME_PLACEHOLDER,
                targetItemId = itemId,
                targetMemberIndex = memberIndex,
            )
        }
    }

    /** The request for the member a (lone or stacked) item shows, when that is a placeholder that can be set up. */
    fun requestForShown(item: WidgetItem): LauncherShellAction.RequestAddWidget? {
        val index = if (item.isStack) item.shownIndex.coerceIn(0, item.members.lastIndex) else 0
        return requestFor(item.id, index, item.members[index])
    }
}

internal val LocalWidgetSetUpSupport = compositionLocalOf { WidgetSetUpSupport() }

/** Where a frame's placeholder reports a tap: which item and member it is, and where the action goes. */
internal class WidgetSetUpHandle(
    val itemId: LauncherItemId,
    val memberIndex: Int,
    val onAction: (LauncherShellAction) -> Unit,
)

/** Whether the loaded catalog says [provider] is no longer installed; while it loads or failed, the system decides. */
internal fun LauncherShellState.isWidgetProviderGone(provider: WidgetProviderIdentity): Boolean =
    widgetProviderCatalogStatus == WidgetProviderCatalogStatus.READY &&
        installedWidgetProviders.none { it.identity == provider }

/** What is said when a request to add a widget came to nothing; a set-up names the widget it was for. */
internal fun Context.widgetAddFailedText(action: LauncherShellAction.RequestAddWidget): String =
    if (action.target == WidgetAddTarget.HOME_PLACEHOLDER) {
        getString(R.string.widget_set_up_failed, action.label)
    } else {
        "Widget could not be added"
    }

internal fun Context.widgetSetUpGoneText(action: LauncherShellAction.RequestAddWidget): String =
    getString(R.string.widget_set_up_provider_gone, action.label)
