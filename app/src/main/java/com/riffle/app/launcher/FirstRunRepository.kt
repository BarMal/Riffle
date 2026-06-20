package com.riffle.app.launcher

interface FirstRunRepository {
    fun isFirstRunComplete(): Boolean

    fun setFirstRunComplete()
}
