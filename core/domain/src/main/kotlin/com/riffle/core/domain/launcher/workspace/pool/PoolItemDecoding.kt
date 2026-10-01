package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.guarded
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.string

internal object PoolItemDecoding {
    internal fun decodeItem(root: StoredValue.Obj): PoolItem? =
        guarded {
            val id = root.string("id")?.let(::PoolItemId)
            val label = root.string("label").orEmpty()
            when {
                id == null -> null
                root.string("kind") == "app" ->
                    root.obj("app")?.let(::decodeIdentity)?.let {
                        PoolApp(id, it, label, root.string("shortcut")?.let(::AppShortcutId))
                    }
                root.string("kind") == "folder" ->
                    PoolFolder(
                        id,
                        label,
                        root.array("entries").mapNotNull { (it as? StoredValue.Obj)?.let(::decodeEntry) },
                    )
                root.string("kind") == "widget" -> decodeWidget(id, label, root)
                else -> null
            }
        }

    private fun decodeWidget(
        id: PoolItemId,
        label: String,
        root: StoredValue.Obj,
    ): PoolWidget =
        PoolWidget(
            id = id,
            label = label,
            resizeConstraints = root.obj("constraints")?.let(::decodeConstraints) ?: WidgetResizeConstraints(),
            provider = root.obj("provider")?.let(::decodeProvider),
            hostedId = root.int("host")?.let(::HostedWidgetId),
        )

    private fun decodeEntry(root: StoredValue.Obj): FolderEntry? {
        val id = root.string("id")
        val identity = root.obj("app")?.let(::decodeIdentity)
        return if (id == null || identity == null) {
            null
        } else {
            FolderEntry(id, identity, root.string("label").orEmpty(), root.string("shortcut")?.let(::AppShortcutId))
        }
    }

    private fun decodeProfile(root: StoredValue.Obj): AppProfile? {
        val id = root.string("profile")
        val type = enumOrNull<AppProfileType>(root.string("profileType"))
        return if (id == null || type == null) null else AppProfile(AppProfileId(id), type)
    }

    private fun decodeIdentity(root: StoredValue.Obj): AppIdentity? {
        val pkg = root.string("package")
        val activity = root.string("activity")
        val profile = decodeProfile(root)
        return if (pkg == null || activity == null || profile == null) {
            null
        } else {
            AppIdentity(AppPackageName(pkg), AppActivityName(activity), profile)
        }
    }

    private fun decodeProvider(root: StoredValue.Obj): WidgetProviderIdentity? {
        val pkg = root.string("package")
        val className = root.string("class")
        val profile = decodeProfile(root)
        return if (pkg == null || className == null || profile == null) {
            null
        } else {
            WidgetProviderIdentity(AppPackageName(pkg), WidgetProviderClassName(className), profile)
        }
    }

    private fun decodeConstraints(root: StoredValue.Obj): WidgetResizeConstraints {
        val maxColumns = root.int("maxColumns")
        val maxRows = root.int("maxRows")
        return WidgetResizeConstraints(
            minSpan = GridSpan(root.int("minColumns") ?: 1, root.int("minRows") ?: 1),
            maxSpan = if (maxColumns != null && maxRows != null) GridSpan(maxColumns, maxRows) else null,
            supportsHorizontalResize = root.bool("horizontal") ?: true,
            supportsVerticalResize = root.bool("vertical") ?: true,
        )
    }
}
