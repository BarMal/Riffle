package com.riffle.app.launcher

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Riffle's glass material: a tinted, edge-refracting, blurred layer with a 1dp top hairline
 * highlight, used for floating chrome (sheets, pills, the card-stack header's pill and action
 * capsule) so it reads as a distinct surface above whatever sits behind it rather than a flat,
 * abruptly-cut card.
 *
 * At API 33+ (AGSL/[RuntimeShader]) this samples the *actual* backdrop -- content recorded into
 * [LocalLiquidGlassBackdrop] near Home's root (see `LiquidGlassBackdrop.kt`) -- through a shader
 * that bends the sample position near the surface's own rounded-rect edge (Snell's-law-style
 * refraction, full strength at the rim and fading to nothing in the flat center), disperses that
 * bend slightly per color channel (blue offset furthest, for a hint of chromatic aberration at the
 * rim), blurs more strongly toward the interior than right at the edge (so the rim's dispersion and
 * specular stay crisp), and lays a Fresnel-style specular highlight on top near the rim facing a
 * fixed virtual light -- no gyroscope, per the design note this shipped against. Below API 33, or
 * with no backdrop installed above this composable (see [ProvideLiquidGlassBackdrop]), or with
 * liquid glass switched off in settings, this falls back to today's cosmetic treatment: a blurred
 * *tint layer* (not the backdrop) with a stronger flat scrim below API 31, where
 * [android.graphics.RenderEffect] does not exist at all.
 *
 * **Known simplification**: AGSL cannot introspect an arbitrary Compose [Shape], so the shader's
 * own rim/rounded-rect falloff uses a plain rounded-rect signed-distance-field, with a corner
 * radius read from [shape] only when it is a [RoundedCornerShape] (a fixed fallback radius
 * otherwise). This only affects *where the refraction/blur/specular intensity ramps up* -- the
 * surface's actual visible silhouette is still clipped exactly to [shape] by the surrounding
 * `Modifier.clip(shape)`, as before.
 *
 * [content] is drawn on top of the glass layer, clipped to the same [shape].
 */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit = {},
) {
    val liquidGlass = LocalLiquidGlassSettings.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val supportsLegacyBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Robolectric's native graphics mode (used by the JVM-side Roborazzi screenshot suite) shadows
    // enough of the framework for a plain Canvas/blur to render correctly, but its RuntimeShader
    // binding throws IllegalArgumentException from native code on construction -- a host-JVM gap
    // unrelated to the shader's own correctness (it compiles and runs fine on a real device/emulator,
    // per Device verify). Build.FINGERPRINT == "robolectric" is that suite's own standard idiom for
    // detecting this, so it falls back to the legacy tint layer there instead of crashing every
    // screenshot test that renders any glass surface.
    val runningUnderRobolectric = Build.FINGERPRINT == "robolectric"

    Box(modifier = modifier.clip(shape)) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !runningUnderRobolectric &&
            liquidGlass.enabled &&
            backdrop != null
        ) {
            LiquidGlassShaderLayer(
                shape = shape,
                tint = tint,
                backdrop = backdrop,
                frostStrength = liquidGlass.frostStrengthFraction,
                refractionStrength = liquidGlass.refractionStrengthFraction,
            )
        } else {
            LegacyGlassTintLayer(supportsBlur = supportsLegacyBlur, tint = tint)
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .height(GLASS_HIGHLIGHT_HEIGHT_DP.dp)
                    .background(Color.White.copy(alpha = GLASS_HIGHLIGHT_ALPHA)),
        )
        content()
    }
}

/** Today's pre-AGSL treatment: a blurred flat tint layer, or a stronger flat scrim below API 31. */
@Composable
private fun BoxScope.LegacyGlassTintLayer(
    supportsBlur: Boolean,
    tint: Color,
) {
    val tintAlpha = if (supportsBlur) GLASS_TINT_ALPHA else GLASS_FALLBACK_SCRIM_ALPHA
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .then(if (supportsBlur) Modifier.blur(GLASS_BLUR_RADIUS_DP.dp) else Modifier)
                .background(tint.copy(alpha = tintAlpha)),
    )
}

/**
 * The real backdrop-capture treatment: crops the shared [backdrop] layer down to exactly this
 * surface's own bounds (a private, per-surface [GraphicsLayer], not a mutation of the shared one --
 * see [LocalLiquidGlassBackdrop]'s doc comment for why that matters with more than one glass
 * surface on screen at once), then applies the AGSL refraction/blur/specular shader as that private
 * layer's own [GraphicsLayer.renderEffect] before drawing it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.LiquidGlassShaderLayer(
    shape: Shape,
    tint: Color,
    backdrop: GraphicsLayer,
    frostStrength: Float,
    refractionStrength: Float,
) {
    val density = LocalDensity.current
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val localLayer = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER_SRC) }
    val cornerRadiusPx =
        remember(shape, sizePx, density) {
            (shape as? RoundedCornerShape)
                ?.topStart
                ?.toPx(Size(sizePx.width.toFloat(), sizePx.height.toFloat()), density)
                ?: with(density) { GLASS_FALLBACK_CORNER_RADIUS_DP.dp.toPx() }
        }

    Box(
        modifier =
            Modifier
                .matchParentSize()
                .onGloballyPositioned { coordinates ->
                    positionInRoot = coordinates.positionInRoot()
                    sizePx = coordinates.size
                }
                .liquidGlassRefraction(
                    localLayer = localLayer,
                    backdrop = backdrop,
                    shader = shader,
                    positionInRoot = { positionInRoot },
                    sizePx = { sizePx },
                    cornerRadiusPx = { cornerRadiusPx },
                    frostStrength = frostStrength,
                    refractionStrength = refractionStrength,
                    tint = tint,
                ),
    )
}

/**
 * Draws [backdrop] cropped to this node's own bounds into [localLayer], applies the AGSL shader as
 * that layer's [GraphicsLayer.renderEffect], then draws the result -- every frame, so the sampled
 * backdrop and the surface's own position both stay live.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Suppress("LongParameterList")
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.liquidGlassRefraction(
    localLayer: GraphicsLayer,
    backdrop: GraphicsLayer,
    shader: RuntimeShader,
    positionInRoot: () -> Offset,
    sizePx: () -> IntSize,
    cornerRadiusPx: () -> Float,
    frostStrength: Float,
    refractionStrength: Float,
    tint: Color,
): Modifier =
    drawBehind {
        val size = sizePx()
        if (size.width <= 0 || size.height <= 0) return@drawBehind
        val position = positionInRoot()

        localLayer.record(density = this, layoutDirection = layoutDirection, size = size) {
            translate(left = -position.x, top = -position.y) {
                drawLayer(backdrop)
            }
        }

        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("cornerRadiusPx", cornerRadiusPx())
        shader.setFloatUniform("frost", frostStrength)
        shader.setFloatUniform("refraction", refractionStrength)
        shader.setFloatUniform(
            "tint",
            tint.red,
            tint.green,
            tint.blue,
            tint.alpha * (LIQUID_GLASS_TINT_BASE_ALPHA + LIQUID_GLASS_TINT_FROST_ALPHA * frostStrength),
        )
        localLayer.renderEffect =
            RenderEffect
                .createRuntimeShaderEffect(shader, LIQUID_GLASS_SHADER_CONTENT_UNIFORM)
                .asComposeRenderEffect()

        drawLayer(localLayer)
    }

/**
 * The AGSL fragment shader implementing the technique described in `GlassSurface.kt`'s own doc
 * comment: a rounded-rect SDF drives (1) how far the edge lensing displaces the backdrop sample,
 * fading to zero away from the rim, (2) a small per-channel offset on top of that displacement for
 * chromatic dispersion, (3) a tap-ring blur that strengthens toward the interior and all but
 * disappears at the rim (so dispersion there stays sharp), and (4) a Fresnel-style specular term
 * brightest where the rim's own outward normal points toward a fixed virtual light direction (no
 * device-tilt/gyroscope input, per this feature's design note).
 *
 * `content` is the implicit input this shader receives from [RenderEffect.createRuntimeShaderEffect]
 * -- the pixels of whatever [GraphicsLayer] this shader is installed as the `renderEffect` of,
 * which here is a private per-surface crop of the shared backdrop (see [liquidGlassRefraction]),
 * not a name this shader ever calls out to elsewhere.
 */
private const val LIQUID_GLASS_SHADER_CONTENT_UNIFORM = "content"

private const val LIQUID_GLASS_SHADER_SRC = """
    uniform shader content;
    uniform float2 size;
    uniform float cornerRadiusPx;
    uniform float frost;
    uniform float refraction;
    uniform float4 tint;

    float roundedRectSdf(float2 point, float2 halfSize, float radius) {
        float2 q = abs(point) - halfSize + radius;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }

    // AGSL/RuntimeShader has no screen-space derivatives (no dFdx/dFdy, unlike full GLSL fragment
    // shaders) -- Android's single-sample AGSL execution model has no neighboring-pixel quad to
    // approximate them from. The rounded-rect SDF's gradient is closed-form instead: outward-facing
    // and axis-aligned along a flat edge, radially outward (mirrored into the point's own quadrant)
    // in the rounded corner region.
    float2 roundedRectNormal(float2 point, float2 halfSize, float radius) {
        float2 q = abs(point) - halfSize + radius;
        float2 s = sign(point);
        if (q.x > 0.0 && q.y > 0.0) {
            return s * (q / max(length(q), 0.0001));
        } else if (q.x > q.y) {
            return float2(s.x, 0.0);
        } else {
            return float2(0.0, s.y);
        }
    }

    half4 main(float2 fragCoord) {
        float2 center = size * 0.5;
        float2 local = fragCoord - center;
        float dist = roundedRectSdf(local, center, cornerRadiusPx);

        // 0 across the flat interior, ramping to 1 right at the rim over a fixed-width band.
        float rim = 1.0 - smoothstep(-24.0, 0.0, dist);

        float2 normal = roundedRectNormal(local, center, cornerRadiusPx);

        float displacement = rim * refraction * 18.0;
        half3 refracted;
        refracted.r = content.eval(fragCoord - normal * displacement * 0.6).r;
        refracted.g = content.eval(fragCoord - normal * displacement * 0.85).g;
        refracted.b = content.eval(fragCoord - normal * displacement).b;

        float blurAmount = (1.0 - rim) * frost;
        half3 blurred = refracted;
        if (blurAmount > 0.02) {
            half3 sum = refracted;
            float sampleCount = 1.0;
            for (int tap = 0; tap < 8; tap++) {
                float angle = 0.7853982 * float(tap);
                float2 tapOffset = float2(cos(angle), sin(angle)) * blurAmount * 14.0;
                sum += content.eval(fragCoord + tapOffset).rgb;
                sampleCount += 1.0;
            }
            blurred = sum / sampleCount;
        }

        float facing = clamp(dot(normal, float2(-0.5, -0.8660254)), 0.0, 1.0);
        float specular = rim * facing * facing * 0.35;

        half3 tinted = mix(blurred, tint.rgb, tint.a);
        half3 outColor = tinted + specular;
        return half4(outColor, 1.0);
    }
"""

private const val GLASS_TINT_ALPHA = 0.55f
private const val GLASS_FALLBACK_SCRIM_ALPHA = 0.75f
private const val GLASS_BLUR_RADIUS_DP = 24
private const val GLASS_HIGHLIGHT_HEIGHT_DP = 1
private const val GLASS_HIGHLIGHT_ALPHA = 0.12f
private const val GLASS_FALLBACK_CORNER_RADIUS_DP = 16
private const val LIQUID_GLASS_TINT_BASE_ALPHA = 0.35f
private const val LIQUID_GLASS_TINT_FROST_ALPHA = 0.45f
