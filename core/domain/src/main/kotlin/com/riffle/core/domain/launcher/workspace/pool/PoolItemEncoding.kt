package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str

internal object PoolItemEncoding {
    internal fun encodeItem(item: PoolItem): StoredValue.Obj =
        when (item) {
            is PoolApp ->
                obj(
                    "id" to str(item.id.value),
                    "kind" to str("app"),
                    "label" to str(item.label),
                    "app" to encodeIdentity(item.appIdentity),
                    "shortcut" to item.appShortcutId?.let { str(it.value) },
                )
            is PoolFolder ->
                obj(
                    "id" to str(item.id.value),
                    "kind" to str("folder"),
                    "label" to str(item.label),
                    "entries" to arr(item.entries.map(::encodeEntry)),
                )
            is PoolWidget ->
                obj(
                    "id" to str(item.id.value),
                    "kind" to str("widget"),
                    "label" to str(item.label),
                    "constraints" to encodeConstraints(item.resizeConstraints),
                    "provider" to item.provider?.let(::encodeProvider),
                    "host" to item.hostedId?.let { num(it.value) },
                )
        }

    private fun encodeEntry(entry: FolderEntry): StoredValue.Obj =
        obj(
            "id" to str(entry.entryId),
            "label" to str(entry.label),
            "app" to encodeIdentity(entry.appIdentity),
            "shortcut" to entry.appShortcutId?.let { str(it.value) },
        )

    private fun encodeIdentity(identity: AppIdentity): StoredValue.Obj =
        obj(
            "package" to str(identity.packageName.value),
            "activity" to str(identity.activityName.value),
            "profile" to str(identity.profile.id.value),
            "profileType" to str(identity.profile.type.name),
        )

    private fun encodeProvider(provider: WidgetProviderIdentity): StoredValue.Obj =
        obj(
            "package" to str(provider.packageName.value),
            "class" to str(provider.className.value),
            "profile" to str(provider.profile.id.value),
            "profileType" to str(provider.profile.type.name),
        )

    private fun encodeConstraints(c: WidgetResizeConstraints): StoredValue.Obj =
        obj(
            "minColumns" to num(c.minSpan.columns),
            "minRows" to num(c.minSpan.rows),
            "maxColumns" to c.maxSpan?.let { num(it.columns) },
            "maxRows" to c.maxSpan?.let { num(it.rows) },
            "horizontal" to StoredValue.Bool(c.supportsHorizontalResize),
            "vertical" to StoredValue.Bool(c.supportsVerticalResize),
        )
}
