package com.riffle.app.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherThemeColorTarget
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
internal fun ThemeColorsSetting(
    colors: LauncherThemeColors,
    onAction: (LauncherShellAction) -> Unit,
) {
    var selectedTarget by remember { mutableStateOf(LauncherThemeColorTarget.ACCENT) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsListRow(
            title = "Custom colours",
            subtitle = "Set Home, dock, and label colours. Hex values include alpha.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LauncherThemeColorTarget.entries.forEach { target ->
                TextButton(onClick = { selectedTarget = target }) {
                    SettingsButtonText(themeColorTargetLabel(target, selectedTarget == target))
                }
            }
        }
        ThemeColorPicker(
            target = selectedTarget,
            argb = colors.colorFor(selectedTarget),
            onColorChanged = { argb -> onAction(LauncherShellAction.SelectLauncherThemeColor(selectedTarget, argb)) },
            onReset = { onAction(LauncherShellAction.SelectLauncherThemeColor(selectedTarget, null)) },
        )
    }
}

@Composable
@Suppress("LongMethod")
internal fun ThemeColorPicker(
    target: LauncherThemeColorTarget,
    argb: Int?,
    onColorChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val initial = Color(argb ?: defaultThemeColor(target))
    var hue by remember(target, argb) { mutableFloatStateOf(initial.toHsv().first) }
    var saturation by remember(target, argb) { mutableFloatStateOf(initial.toHsv().second) }
    var value by remember(target, argb) { mutableFloatStateOf(initial.toHsv().third) }
    var alpha by remember(target, argb) { mutableFloatStateOf(initial.alpha) }
    var hexValue by remember(target, argb) { mutableStateOf(initial.toThemeHex()) }
    var wheelSize by remember { mutableStateOf(IntSize.Zero) }

    fun publish() {
        val selected = Color.hsv(hue, saturation, value, alpha)
        hexValue = selected.toThemeHex()
        onColorChanged(selected.value.toInt())
    }

    fun selectAt(position: Offset) {
        val radius = minOf(wheelSize.width, wheelSize.height) / 2f
        if (radius <= 0f) return
        val dx = position.x - wheelSize.width / 2f
        val dy = position.y - wheelSize.height / 2f
        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f)
        saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        publish()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                modifier =
                    Modifier
                        .size(160.dp)
                        .onSizeChanged { wheelSize = it }
                        .clip(CircleShape)
                        .pointerInput(target, argb) { detectTapGestures(onTap = ::selectAt) }
                        .pointerInput(target, argb) {
                            detectDragGestures(
                                onDragStart = ::selectAt,
                                onDrag = { change, _ ->
                                    selectAt(change.position)
                                    change.consume()
                                },
                            )
                        }
                        .semantics {
                            contentDescription = "Hue and saturation wheel for ${themeColorTargetTitle(target)}"
                        },
            ) {
                drawCircle(
                    Brush.sweepGradient(
                        listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red),
                    ),
                )
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent)))
                val angle = Math.toRadians(hue.toDouble())
                val marker =
                    Offset(
                        x = center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * saturation / 2f,
                        y = center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * saturation / 2f,
                    )
                drawCircle(Color.White, radius = 7.dp.toPx(), center = marker, style = Stroke(width = 2.dp.toPx()))
            }
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.hsv(hue, saturation, value, alpha))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .semantics {
                            contentDescription = "Selected ${themeColorTargetTitle(target)} colour $hexValue"
                        },
            )
        }
        ThemeColorSlider(label = "Brightness", value = value) {
            value = it
            publish()
        }
        ThemeColorSlider(label = "Alpha", value = alpha) {
            alpha = it
            publish()
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = hexValue,
            onValueChange = { entered ->
                hexValue = entered
                parseThemeColorHex(entered)?.let { color ->
                    val hsv = color.toHsv()
                    hue = hsv.first
                    saturation = hsv.second
                    value = hsv.third
                    alpha = color.alpha
                    onColorChanged(color.value.toInt())
                }
            },
            singleLine = true,
            label = { Text("Hex colour (#RRGGBB or #AARRGGBB)") },
        )
        TextButton(onClick = onReset) { SettingsButtonText("Use theme default") }
    }
}

@Composable
private fun ThemeColorSlider(
    label: String,
    value: Float,
    onValueChanged: (Float) -> Unit,
) {
    Column {
        Text("$label ${(value * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onValueChanged)
    }
}

internal fun parseThemeColorHex(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    val normalized =
        when (hex.length) {
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
    return normalized.toLongOrNull(16)?.takeIf { it <= 0xFFFF_FFFFL }?.let { Color(it.toInt()) }
}

internal fun Color.toThemeHex(): String = "#%08X".format(value.toInt())

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val hue =
        when {
            delta == 0f -> 0f
            maximum == red -> 60f * (((green - blue) / delta) % 6f)
            maximum == green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
    return Triple(hue, if (maximum == 0f) 0f else delta / maximum, maximum)
}

private fun defaultThemeColor(target: LauncherThemeColorTarget): Int =
    when (target) {
        LauncherThemeColorTarget.BACKGROUND -> 0xFFFFFBFF.toInt()
        LauncherThemeColorTarget.ACCENT -> 0xFF4D5C92.toInt()
        LauncherThemeColorTarget.DOCK -> 0xFFE2E1EC.toInt()
        LauncherThemeColorTarget.LABEL -> 0xFFFFFFFF.toInt()
        LauncherThemeColorTarget.LABEL_BACKGROUND -> 0xFF000000.toInt()
    }

private fun themeColorTargetTitle(target: LauncherThemeColorTarget): String =
    when (target) {
        LauncherThemeColorTarget.BACKGROUND -> "background"
        LauncherThemeColorTarget.ACCENT -> "accent"
        LauncherThemeColorTarget.DOCK -> "dock"
        LauncherThemeColorTarget.LABEL -> "label"
        LauncherThemeColorTarget.LABEL_BACKGROUND -> "label background"
    }

private fun themeColorTargetLabel(
    target: LauncherThemeColorTarget,
    selected: Boolean,
): String =
    themeColorTargetTitle(target).replaceFirstChar(Char::uppercase).let { title ->
        if (selected) "$title (selected)" else title
    }
