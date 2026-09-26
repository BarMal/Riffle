package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity

/**
 * Extra long-press menu items the active mode adds to pinned dock apps, by app.
 *
 * The dock is mode-agnostic (Decision 2 in docs/product/modes-dock-handle-and-cards-plan.md): a tap
 * on a pinned app always opens it, in every mode, and anything a mode wants to add -- Cards' "Show
 * stage" and "Pin stage" -- arrives here as labelled actions the dock shows without interpreting.
 * A value rather than a callback so the dock can compare it across recompositions.
 */
internal data class DockItemMenuExtras(
    val byApp: Map<AppIdentity, List<ShortcutContextMenuItem>> = emptyMap(),
) {
    fun forApp(identity: AppIdentity): List<ShortcutContextMenuItem> = byApp[identity].orEmpty()
}
