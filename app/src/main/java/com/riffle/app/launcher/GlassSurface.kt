package com.riffle.app.launcher

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.designsystem.GlassWeightTokens

/**
 * Riffle's one glass material (ADR 0003): a tinted, edge-refracting, blurred layer with the glass
 * sheen on top, drawn at one of three [GlassWeight]s. It is the only way a surface gets a
 * translucent background: the weight owns the frost, tint alpha, contrast scrim, hairline and
 * shadow (see `GlassMaterialTokens`), so call sites carry no literal surface alpha or blur radius.
 *
 * At API 33+ (AGSL/[RuntimeShader]) this samples the *actual* backdrop -- content recorded into
 * [LocalLiquidGlassBackdrop] near Home's root (see `LiquidGlassBackdrop.kt`) -- through a shader
 * that bends the sample position near the surface's own rounded-rect edge (Snell's-law-style
 * refraction, full strength at the rim and fading to nothing in the flat center), disperses that
 * bend slightly per color channel (blue offset furthest, for a hint of chromatic aberration at the
 * rim), blurs more strongly toward the interior than right at the edge (so the rim's dispersion and
 * specular stay crisp), and lays a Fresnel-style specular highlight on top near the rim facing a
 * fixed virtual light -- no gyroscope, per the design note this shipped against. Below API 33, or
 * with no backdrop installed above this composable (see [ProvideLiquidGlassBackdrop]), this falls
 * back to a blurred *tint layer* (not the backdrop) with a stronger flat scrim below API 31, where
 * [android.graphics.RenderEffect] does not exist at all.
 *
 * Glass is always on. The one fallback that is part of the material is reduced transparency
 * ([ResolvedLiquidGlass.reducedTransparency]): an opaque [tint], no backdrop sampling, the sheen
 * kept but faint, the spotlight at half strength.
 *
 * **Sheen**: the dock's glass sheen (`glassSheen`) is drawn over every weight, driven by [shape].
 *
 * **Spotlight**: with [spotlight] true the surface lights its long edges with the colours of the
 * things it holds -- content reports each through [glassSpotlightSource] -- and otherwise carries
 * the sheen only. Use it wherever a surface holds coloured things; it asks for frames only while
 * those colours move (see `GlassSpotlight.kt`).
 *
 * **Known simplification**: AGSL cannot introspect an arbitrary Compose [Shape], so the shader's
 * own rim/rounded-rect falloff uses a plain rounded-rect signed-distance-field, with a corner
 * radius read from [shape] only when it is a [CornerBasedShape] (a fixed fallback radius
 * otherwise). This only affects *where the refraction/blur/specular intensity ramps up* -- the
 * surface's actual visible silhouette is still clipped exactly to [shape] by the surrounding
 * `Modifier.clip(shape)`, as before.
 *
 * [tint] supplies the colour only; its alpha is the weight's to set (except under reduced
 * transparency, which uses a near-opaque alpha). [content] is drawn on top of the glass layer, clipped to the same
 * [shape], with [contentColor] (default: the colour that contrasts [tint]).
 */
@Composable
@Suppress("LongParameterList")
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    weight: GlassWeight = GlassWeight.Regular,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = Color.Unspecified,
    spotlight: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val liquidGlass = LocalLiquidGlassSettings.current
    val tokens = GlassMaterialTokens.forWeight(weight)
    val spotlightState = rememberGlassSpotlightState(enabled = spotlight)
    val resolvedContentColor = contentColor.takeOrElse { contentColorFor(tint) }

    // The one soft shadow sits outside the clip. A flat or opaque surface casts none.
    val shadow =
        if (tokens.shadowElevationDp > 0 && liquidGlass.samplesBackdrop) {
            Modifier.shadow(tokens.shadowElevationDp.dp, shape, clip = false)
        } else {
            Modifier
        }
    Box(modifier = modifier.then(shadow).clip(shape)) {
        GlassBackground(weight = weight, shape = shape, tint = tint)
        GlassSheenLayer(shape = shape, tint = tint, tokens = tokens)
        if (spotlightState != null) {
            Box(Modifier.matchParentSize().glassSpotlight(spotlightState, shape))
        }
        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor,
            LocalGlassSpotlight provides spotlightState,
        ) {
            content()
        }
    }
}

/** The surface's own fill: the flat fallbacks, the backdrop shader, or the blurred tint layer. */
@Composable
private fun BoxScope.GlassBackground(
    weight: GlassWeight,
    shape: Shape,
    tint: Color,
) {
    val liquidGlass = LocalLiquidGlassSettings.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val tokens = GlassMaterialTokens.forWeight(weight)
    val supportsLegacyBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Robolectric's native graphics mode (used by the JVM-side Roborazzi screenshot suite) shadows
    // enough of the framework for a plain Canvas/blur to render correctly, but its RuntimeShader
    // binding throws IllegalArgumentException from native code on construction -- a host-JVM gap
    // unrelated to the shader's own correctness (it compiles and runs fine on a real device/emulator,
    // per Device verify). Build.FINGERPRINT == "robolectric" is that suite's own standard idiom for
    // detecting this, so it falls back to the legacy tint layer there instead of crashing every
    // screenshot test that renders any glass surface.
    val runningUnderRobolectric = Build.FINGERPRINT == "robolectric"

    if (liquidGlass.reducedTransparency) {
        FlatGlassLayer(tint = tint.copy(alpha = GlassMaterialTokens.REDUCED_TRANSPARENCY_TINT_ALPHA))
    } else if (!runningUnderRobolectric) {
        // Nested rather than one compound condition so the inner if -- unchanged from the version
        // Android Lint's NewApi check already traces correctly -- keeps doing so.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null) {
            LiquidGlassShaderLayer(
                shape = shape,
                tint = tint,
                backdrop = backdrop,
                frostStrength = tokens.effectiveFrost(liquidGlass.frostStrengthFraction),
                refractionStrength = liquidGlass.refractionStrengthFraction,
                tokens = tokens,
            )
        } else {
            LegacyGlassTintLayer(supportsBlur = supportsLegacyBlur, tint = tint, tokens = tokens)
        }
    } else {
        LegacyGlassTintLayer(supportsBlur = supportsLegacyBlur, tint = tint, tokens = tokens)
    }
    if (tokens.contentScrimAlpha > 0f && liquidGlass.samplesBackdrop) {
        Box(Modifier.matchParentSize().background(tint.copy(alpha = tokens.contentScrimAlpha)))
    }
}

/** The reduced-transparency fill: a flat, near-opaque [tint]. */
@Composable
private fun BoxScope.FlatGlassLayer(tint: Color) {
    Box(Modifier.matchParentSize().background(tint))
}

/** The sheen over the fill, scaled by the weight and by how light the glass reads. */
@Composable
private fun BoxScope.GlassSheenLayer(
    shape: Shape,
    tint: Color,
    tokens: GlassWeightTokens,
) {
    val seen = tint.copy(alpha = tokens.tintAlpha)
    val strength =
        glassSheenStrength(seen).scaled(
            highlight = tokens.highlightScale,
            shadow = tokens.innerShadowScale,
            border = tokens.hairlineScale,
        )
    Box(Modifier.matchParentSize().glassSheen(shape, strength))
}

/**
 * A liquid-glass scrim [Modifier] for callers outside [GlassSurface] itself -- currently
 * `AdaptiveStageCardSurface`'s GLASS/FROSTED content-face scrim -- that want the real
 * backdrop-refraction material as a plain background fill in their own modifier chain, with the
 * same eligibility gate [GlassSurface] applies (API 33+, a
 * backdrop installed above the caller via [ProvideLiquidGlassBackdrop], not under Robolectric).
 * Falls back to a flat [tint] fill -- `Modifier.background(tint, shape)`, pixel-for-pixel what
 * callers drew before this existed -- whenever any of that is not the case, so a caller can use
 * this unconditionally and get today's exact look when the backdrop is unavailable or transparency is reduced.
 */
@Composable
internal fun rememberLiquidGlassScrimModifier(
    shape: Shape,
    tint: Color,
): Modifier {
    val liquidGlass = LocalLiquidGlassSettings.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val runningUnderRobolectric = Build.FINGERPRINT == "robolectric"

    // Nested rather than one compound condition, mirroring GlassSurface's own gate above -- see
    // its comment for why folding !runningUnderRobolectric into the inner condition would push it
    // over detekt's ComplexCondition threshold, and why the inner check stays a direct
    // Build.VERSION.SDK_INT comparison rather than a precomputed boolean (Android Lint's NewApi
    // check does not trace SDK_INT across a function-call boundary otherwise).
    return if (!runningUnderRobolectric) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && liquidGlass.samplesBackdrop && backdrop != null) {
            Modifier.clip(shape).liquidGlassShaderBackground(
                shape = shape,
                tint = tint,
                backdrop = backdrop,
                frostStrength = liquidGlass.frostStrengthFraction,
                refractionStrength = liquidGlass.refractionStrengthFraction,
            )
        } else {
            Modifier.background(tint, shape)
        }
    } else {
        Modifier.background(tint, shape)
    }
}

/** The pre-AGSL treatment: a blurred flat tint layer, or a stronger flat scrim below API 31. */
@Composable
private fun BoxScope.LegacyGlassTintLayer(
    supportsBlur: Boolean,
    tint: Color,
    tokens: GlassWeightTokens,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .then(if (supportsBlur) Modifier.blur(tokens.blurRadiusDp.dp) else Modifier)
                .background(tint.copy(alpha = tokens.softwareTintAlpha(supportsBlur))),
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
@Composable
private fun BoxScope.LiquidGlassShaderLayer(
    shape: Shape,
    tint: Color,
    backdrop: GraphicsLayer,
    frostStrength: Float,
    refractionStrength: Float,
    tokens: GlassWeightTokens,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .liquidGlassShaderBackground(
                    shape = shape,
                    tint = tint,
                    backdrop = backdrop,
                    frostStrength = frostStrength,
                    refractionStrength = refractionStrength,
                    tokens = tokens,
                ),
    )
}

/**
 * The real backdrop-refraction treatment as a plain [Modifier] extension, decoupled from being the
 * sole content of its own [Box] -- extracted so a call site that already has its own modifier chain
 * (see `DockAppearance.kt`'s `dockSurfaceAppearance`, and [rememberLiquidGlassScrimModifier] above)
 * can apply it directly, in place of a flat `Modifier.background(tint)`, without wrapping its
 * content in an extra layer. [GlassSurface] itself uses this through [LiquidGlassShaderLayer] above,
 * unchanged from before this was extracted.
 *
 * Tracks this node's own position and size via [onGloballyPositioned], exactly as
 * [LiquidGlassShaderLayer] did inline, then applies [liquidGlassRefraction] using those. Callers are
 * responsible for their own eligibility gating (API 33+, a non-null backdrop,
 * not under Robolectric) and, if a clipped silhouette narrower than their own layout bounds is
 * needed (see [rememberLiquidGlassScrimModifier]'s `Modifier.clip(shape)` call), for chaining that
 * themselves -- same as before extraction, this function carries no fallback or clip of its own.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.liquidGlassShaderBackground(
    shape: Shape,
    tint: Color,
    backdrop: GraphicsLayer,
    frostStrength: Float,
    refractionStrength: Float,
    tokens: GlassWeightTokens = GlassMaterialTokens.regular,
    wallpaperTintAlpha: Float? = null,
): Modifier {
    val density = LocalDensity.current
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val localLayer = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER_SRC) }
    val cornerRadiusPx =
        remember(shape, sizePx, density) {
            (shape as? CornerBasedShape)
                ?.topStart
                ?.toPx(Size(sizePx.width.toFloat(), sizePx.height.toFloat()), density)
                ?: with(density) { GLASS_FALLBACK_CORNER_RADIUS_DP.dp.toPx() }
        }

    return onGloballyPositioned { coordinates ->
        positionInRoot = coordinates.positionInRoot()
        sizePx = coordinates.size
    }.liquidGlassRefraction(
        localLayer = localLayer,
        backdrop = backdrop,
        shader = shader,
        positionInRoot = { positionInRoot },
        sizePx = { sizePx },
        cornerRadiusPx = { cornerRadiusPx },
        frostStrength = frostStrength,
        refractionStrength = refractionStrength,
        tint = tint,
        tokens = tokens,
        wallpaperTintAlpha = wallpaperTintAlpha,
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
    tokens: GlassWeightTokens,
    wallpaperTintAlpha: Float?,
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
            tint.alpha * tokens.shaderTintAlpha(frostStrength),
        )
        shader.setFloatUniform(
            "tintAlphaWallpaper",
            wallpaperTintAlpha ?: (tint.alpha * tokens.shaderTintAlphaOverWallpaper(frostStrength)),
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
    // The tint alpha where the backdrop is transparent (the system wallpaper, which is outside the
    // app's content and so never in the sampled layer). Mirrors GlassBlend in core/domain.
    uniform float tintAlphaWallpaper;

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
        // The backdrop is premultiplied RGBA (child shaders return premultiplied colour), and is
        // transparent over the system wallpaper: every tap is averaged as premultiplied RGBA so
        // that alpha survives, and the dispersion takes alpha from the middle tap.
        half4 refracted;
        refracted.r = content.eval(fragCoord - normal * displacement * 0.6).r;
        half4 middle = content.eval(fragCoord - normal * displacement * 0.85);
        refracted.g = middle.g;
        refracted.a = middle.a;
        refracted.b = content.eval(fragCoord - normal * displacement).b;

        float blurAmount = (1.0 - rim) * frost;
        half4 blurred = refracted;
        if (blurAmount > 0.02) {
            half4 sum = refracted;
            float sampleCount = 1.0;
            for (int tap = 0; tap < 8; tap++) {
                float angle = 0.7853982 * float(tap);
                float2 tapOffset = float2(cos(angle), sin(angle)) * blurAmount * 14.0;
                sum += content.eval(fragCoord + tapOffset);
                sampleCount += 1.0;
            }
            blurred = sum / sampleCount;
        }

        float facing = clamp(dot(normal, float2(-0.5, -0.8660254)), 0.0, 1.0);
        float specular = rim * facing * facing * 0.35;

        // Mirrors GlassBlend.shade: tint alpha t is tint.a over opaque content and
        // tintAlphaWallpaper over a transparent backdrop; the output is premultiplied and keeps the
        // backdrop's alpha, so the wallpaper shows through wherever the backdrop was empty.
        float backdropAlpha = clamp(float(blurred.a), 0.0, 1.0);
        float t = mix(tintAlphaWallpaper, tint.a, backdropAlpha);
        float outA = min(1.0, float(blurred.a) * (1.0 - t) + t + specular);
        half3 outRgb = blurred.rgb * (1.0 - t) + tint.rgb * t + half(specular);
        outRgb = min(outRgb, half3(outA));
        return half4(outRgb, half(outA));
    }
"""

private const val GLASS_FALLBACK_CORNER_RADIUS_DP = 16
