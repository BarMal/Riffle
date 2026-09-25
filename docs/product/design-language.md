# Riffle design language

Status: first slice (#1201). Tokens live in code; this page is the reference for when to use them.

The steer is **"if Apple and Sony designed it together."** Riffle stays a standard Android launcher
built on Material 3; the design language tunes M3 rather than replacing it. The principles below
come from the Cards/modes plan (`docs/product/modes-dock-handle-and-cards-plan.md`, W1).

| Principle | Apple cue | Sony cue | In Riffle |
|---|---|---|---|
| Calm by default | iOS Home, Focus | Xperia's restraint | One accent, generous space, nothing animates without cause |
| Physical, honest motion | Interruptible springs, 1:1 tracking | TimeScape's Spline | Three springs only (snappy, smooth, gentle); every gesture tracks the finger and can be reversed |
| Material depth | Thin/regular/thick materials | Glass-and-aluminium Xperia hardware | Real background blur (RenderEffect, API 31+) with tinted fallback; hairline highlights; soft single-source shadow |
| Precise typography | SF hierarchy, dynamic type | Sony's thin, wide-tracked labels | M3 type scale, tuned: display weights light, labels medium, generous tracking on small caps |
| Continuous shapes | Squircle corners | Omnibalance's flat planes and rounded edges | Continuous-corner shape tokens; one radius scale |
| Focus over chrome | Stage Manager | TimeScape focused tile | One thing in focus, context recedes; chrome appears on demand |

## Where the tokens live

| Layer | Location | Contents |
|---|---|---|
| Values (framework-free, unit tested) | `core/domain/.../launcher/designsystem/RiffleDesignTokens.kt` | Spacing, radius, elevation scales; spring, duration and easing parameters |
| Geometry (framework-free, unit tested) | `core/domain/.../launcher/designsystem/ContinuousCornerGeometry.kt` | Continuous-corner Bézier construction |
| Compose wrappers | `app/.../launcher/designsystem/` | `RiffleSpacing`, `RiffleShapes`, `ContinuousRoundedCornerShape`, `RiffleElevation`, `RiffleMotion` |
| Theme wiring | `app/.../launcher/LauncherTheme.kt` | `MaterialTheme(shapes = …, typography = …)`, `LocalLauncherCardShape`, `LocalLauncherPanelShape` |

New code uses the tokens. Existing raw `dp` literals and ad-hoc `spring()` calls migrate slice by
slice; do not add new ones.

## 1. Calm by default

Tokens: **spacing** (dp)

| `xxs` | `xs` | `s` | `m` | `l` | `xl` | `xxl` | `xxxl` |
|---|---|---|---|---|---|---|---|
| 2 | 4 | 8 | 12 | 16 | 24 | 32 | 48 |

Do
- Use `RiffleSpacing` for padding and gaps. `l` (16) is the default content inset; `xl`/`xxl`
  separate groups.
- Use one accent colour (the theme's `primary`) per surface. Everything else is surface tones.
- Animate only in response to the user or to a real state change.

Don't
- Don't invent in-between values (10, 14, 20 dp) to make something fit; change the layout instead.
- Don't pulse, shimmer or loop animations to attract attention.
- Don't stack multiple tinted containers inside each other.

## 2. Physical, honest motion

Tokens: **exactly three springs**, plus two tweens for non-physical changes.

| Spring | dampingRatio | stiffness | Use for |
|---|---|---|---|
| `snappy` | 1.0 | 1500 | Small, direct changes: handles, toggles, reorder nudges (same as Compose's default `spring()`) |
| `smooth` | 1.0 | 400 | Page and panel settles, container resizes |
| `gentle` | 0.8 | 200 | Large, expressive travel, e.g. a card coming to rest (a hint of overshoot, no wobble) |

| Tween | Duration | Easing |
|---|---|---|
| `standard` | 300 ms | `cubic-bezier(0.2, 0, 0, 1)` |
| `emphasized` | 500 ms | emphasized decelerate `cubic-bezier(0.05, 0.7, 0.1, 1)`; exits use emphasized accelerate `cubic-bezier(0.3, 0, 0.8, 0.15)` |
| short | 150 ms | for micro-feedback |
| reduced-motion fade | 80 ms | the only motion allowed when reduced motion is on, and only where a hard cut would disorient |

Reduced motion is a tri-state setting (System, the default; On; Off). System follows the platform
animator duration scale, which the accessibility "Remove animations" switch sets to 0. `LauncherShell`
resolves it once into `launcherSettings.motion.reducedMotion`, which is threaded through composables as
a `reducedMotion: Boolean`. Every `RiffleMotion` helper takes it and returns `snap()` when it is on, so the end state
is identical and only the travel is removed.

Do
- Track the finger 1:1 during a drag; hand off to a spring with the release velocity.
- Keep animations interruptible: animate `Animatable`s / state, never fire-and-forget timers.
- Pick the spring by the size and weight of what moves, not by taste per call site.
- Pass `reducedMotion` into `RiffleMotion.*`.

Don't
- Don't call `spring(...)` with hand-tuned parameters. If none of the three fits, raise it in the
  design-language issue rather than adding a fourth.
- Don't use visible bounce (`dampingRatio < 0.75`).
- Don't block input while something settles.

## 3. Material depth

Tokens: **elevation** (dp), used for both tonal and shadow elevation.

| `level0` | `level1` | `level2` | `level3` | `level4` |
|---|---|---|---|---|
| 0 | 1 | 3 | 6 | 12 |

Do
- Express depth with one soft, single-source shadow and a tonal step; add a hairline highlight on
  glass surfaces.
- Use the same level for a surface's tonal and shadow elevation.
- Honour the reduced-transparency setting (the Glass preset already folds a contrast scrim into its
  surface tokens; real blur is #1202).

Don't
- Don't mix unrelated elevations on siblings, or stack shadows.
- Don't put text on translucent surfaces without the contrast guarantee in `wallpaperAwareSurface`.

## 4. Precise typography

Tokens: the M3 type scale, tuned in `riffleTypography`.

| Role | Change from M3 |
|---|---|
| `display*` | weight **Light** (300) |
| `labelLarge` | weight Medium, letter spacing 0.25 sp (M3: 0.1) |
| `labelMedium` | weight Medium, letter spacing 0.6 sp (M3: 0.5) |
| `labelSmall` | weight Medium, letter spacing 0.7 sp (M3: 0.5) |

Only weight and tracking change, so the user's font-family choice (System / Monospace / preset)
still applies on top.

Do
- Use `MaterialTheme.typography` roles; large numerals and clocks use `display*`.
- Use `label*` for small caps-style metadata and chips.

Don't
- Don't set `fontSize` or `fontWeight` inline; choose a role.
- Don't bold body copy for emphasis; use colour or a title role.

## 5. Continuous shapes

Tokens: **one radius scale** (dp), rendered with continuous corners.

| `s` | `m` | `l` | `xl` | `full` |
|---|---|---|---|---|
| 8 | 16 | 24 | 32 | 50% (pill) |

`ContinuousRoundedCornerShape` follows Figma's corner-smoothing construction with smoothing 0.6
(close to Apple's continuous corners): a Bézier lead-in, a shorter circular arc and a mirrored
lead-out, so curvature ramps up instead of jumping. On small shapes it gives up smoothing before
radius, degrading to a circular corner.

Material 3 roles map onto the scale: `extraSmall`/`small` → `s`, `medium` → `m`, `large` → `l`,
`extraLarge` → `xl`. Cards default to `l` and panels to `xl`. The user's corner-style setting
(Preset / Compact / Rounded) and non-Material presets keep their own radii; when the resolved card
corner is sharp (Terminal), Material components go sharp too.

Do
- Use `MaterialTheme.shapes.*`, `LocalLauncherCardShape`, `LocalLauncherPanelShape` or
  `RiffleShapes.continuous(radius)`.
- Nest radii: an inner shape's radius = outer radius − inset, rounded to the scale.

Don't
- Don't create `RoundedCornerShape(n.dp)` for surfaces; it bypasses both the scale and the user's
  corner setting.
- Don't use continuous corners on pills and circles (`full` stays circular).

## 6. Focus over chrome

Do
- Keep one thing in focus; let context recede (scale, dim) rather than disappear abruptly.
- Show controls on demand (long-press, edit mode, the dock handle) and hide them when idle.
- Keep every on-demand control reachable by TalkBack and keyboard.

Don't
- Don't add persistent toolbars or overflow menus to focused surfaces.
- Don't reduce unselected items to low-contrast text (below 4.5:1) to create focus.

## Migration status

Migrated in #1201 (first slice):
- Page settle (`ImmediateHomePager`) and dock shelf resize (`HomeDockPolicies`) → `smooth`
  (identical parameters).
- Page overview reflow (`PageOverviewCardState`) → `snappy` (identical to the previous default spring).
- Page indicator handle settle (`HomePageControls`) → `snappy`. **Deliberate change**: it was
  medium-bouncy (dampingRatio 0.5); it is now critically damped.
- Card and panel shapes and Material shapes → continuous corners on the radius scale.

Follow-ups:
- `CardStack.kt` (card-stack easing spring and magnet settle) and `AdaptiveStageStagePager.kt`
  (stage settle, a `smooth` candidate) still call `spring()` directly.
- ~340 raw `dp` literals to move onto `RiffleSpacing` / `RiffleShapes` / `RiffleElevation`.
- A lint/detekt check against new raw `spring(` and `RoundedCornerShape(` in feature code.
- Materials (blur, hairlines) in #1202; system animation scale in #1203.
