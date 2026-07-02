package com.riffle.app.launcher.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

internal fun Context.roundedBackground(alphaPercent: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpFloat(28)
        setColor(Color.argb(alphaPercent.toColorAlpha(), 31, 36, 42))
    }

internal fun Context.handleGripBackground(settings: OverlayDockSettings): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        val radius = dpFloat((settings.handleThicknessDp / 2).coerceAtMost(14))
        cornerRadii =
            when (settings.edge) {
                OverlayDockEdge.START ->
                    floatArrayOf(
                        0f,
                        0f,
                        radius,
                        radius,
                        radius,
                        radius,
                        0f,
                        0f,
                    )

                OverlayDockEdge.END ->
                    floatArrayOf(
                        radius,
                        radius,
                        0f,
                        0f,
                        0f,
                        0f,
                        radius,
                        radius,
                    )
            }
        setColor(Color.argb(settings.handleAlphaPercent.toColorAlpha(), 255, 255, 255))
    }

internal fun Context.transparentRoundedBackground(): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpFloat(18)
        setColor(Color.argb(24, 255, 255, 255))
    }

internal fun Int.toColorAlpha(): Int = (coerceIn(0, 100) * 255) / 100

internal fun Context.dpFloat(value: Int): Float = value * resources.displayMetrics.density
