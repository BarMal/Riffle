package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockModel

internal fun DockModel.primaryDock(showShelf: Boolean): DockModel =
    if (showShelf) {
        copy(items = items.take(capacity))
    } else {
        this
    }

internal fun DockModel.overflowShelfDock(): DockModel =
    copy(
        capacity = capacity,
        items = items.drop(capacity),
    )
