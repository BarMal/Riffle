package com.riffle.app.launcher

fun interface EpochMillisProvider {
    fun nowEpochMillis(): Long
}

object SystemEpochMillisProvider : EpochMillisProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
