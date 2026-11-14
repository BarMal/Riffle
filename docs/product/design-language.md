# Riffle design language

Status: first slice (#1201). Tokens live in code; this page is the reference for when to use them.

The steer is **"if Apple and Sony designed it together."** Riffle stays a standard Android launcher
with one glass material on top of the Material 3 component baseline; the design language tunes M3 rather than replacing it. The principles below
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
| Glass material | `core/domain/.../launcher/designsystem/GlassMaterialTokens.kt` (weights), `app/.../launcher/GlassSurface.kt`, `GlassSheen.kt`, `GlassSpotlight*.kt` | `GlassWeight`, the weight tokens, `GlassSurface`, `glassSheen`, the spotlight |
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

**The one exception: the trampoline** (ADR 0003, Amendment 3; dock modes slice 2). The Dock's press dips the Dock
and, on release, it springs back past rest with a visible bounce (damping ratio 0.675 at the default trampoline setting,
i.e. below the 0.75 bounce limit below), while a critically damped spring with a slight magnet carries Home and the
Stream's cards. These two springs, their constants and the reversed closing live in
`core/domain/.../launcher/dock/DockMotion.kt` (`DockMotionConstants`, `DockMotionTuning`: speed 200 ms, trampoline,
magnet), not in the three tokens above, and nothing else may bounce. The reduced-motion fade (80 ms) is the token's.

Do
- Track the finger 1:1 during a drag; hand off to a spring with the release velocity.
- Keep animations interruptible: animate `Animatable`s / state, never fire-and-forget timers.
- Pick the spring by the size and weight of what moves, not by taste per call site.
- Pass `reducedMotion` into `RiffleMotion.*`.

Don't
- Don't call `spring(...)` with hand-tuned parameters. If none of the three fits, raise it in the
  design-language issue rather than adding a fourth.
- Don't use visible bounce (`dampingRatio < 0.75`), except the Dock's trampoline above.
- Don't block input while something settles.

## 3. Material depth

Tokens: **elevation** (dp), used for both tonal and shadow elevation.

| `level0` | `level1` | `level2` | `level3` | `level4` |
|---|---|---|---|---|
| 0 | 1 | 3 | 6 | 12 |

### One glass, three weights

Glass is the only look (ADR 0003). Every translucent surface is drawn by one primitive,
`GlassSurface(weight, shape, tint)`, at one of three weights. The material is **one flat translucent
tint with its light at the edges**, modelled on the owner's desktop bar. The numbers live in
`core/domain/.../designsystem/GlassMaterialTokens.kt` (`GlassWeightTokens`, `GlassEdgeTokens`), so
feature code carries no literal surface alpha or dp.

#### The reference: the desktop bar ("Serein")

The owner's Quickshell bar (`dotfiles/config/quickshell`: `GlassPill.qml`, `GlassPillOverlay.qml`,
`Hairline.qml`, `Serein.qml`, `docs/shell-design-plan.md`) is the anatomy: one flat translucent tint, a
1 px border that is a light rather than a box, a specular line just inside the top edge that fades out
at both ends, and tint as strips of light along the top and bottom edges. One implied light, top-centre;
fading lines rather than boxes; generous rounding; nothing loops. (Its caustic spot, bottom shadow and
wide top specular band are not part of the look the owner means.) Riffle's values are lighter and more
see-through than the bar's, signed off by the owner on a design canvas over a dark wallpaper; they are the
numbers below.

#### The Riffle material

- **Fill.** One flat tint colour at one alpha over whatever is behind. No gradient across the
  body, no bevel, no inner shadow, no caustic, no refraction or dispersion, no blur, and nothing that
  scales with the surface's height. The weight changes the alpha, and Thick also the colour:

  | Weight | Dark theme | Light theme |
  |---|---|---|
  | `Thin` | light blue-white `rgb(196, 202, 236)` at **12%** | near-white at 30% |
  | `Regular` | the same tint at **16%** | near-white at 40% |
  | `Thick` | a darker, heavier `rgb(58, 62, 96)` at **56%** | pale grey-white at 78% |

  The surface's own `tint` (the theme's surface, so dynamic colour) is mixed in at 20%; the weight's
  base is the other 80% (`GlassMaterialTokens.fill`, `glassFillColor`). Whether the theme is dark is
  read from the tint's luminance. The Dock follows its "Dock background" setting: 0% clear, the Thin
  fill at the default (72%), opaque (its own surface colour) at 100%, linear between
  (`GlassMaterialTokens.dockFill`).
- **Edge lights** (`GlassEdgeLights.kt`, plain Compose drawing along the surface's own `Outline`, so
  pills, circles, continuous corners and any `Shape` are followed exactly):
  - a 1 dp white **border**, 55% along the top edge, falling over 24 dp to 14% down the sides, and
    easing over the last 16 dp to 6% at the bottom edge (a vertical gradient with stops at fixed dp);
  - a 1 dp **inner hairline** just inside the top border, 18% at the middle, 9% at 12% from
    either end, 0 at the ends;
  - on light glass the sides and bottom turn from white toward black (a white edge on a light pane
    is invisible; its shaded edge is not) and the alphas are boosted by up to 1.6;
  - the weight scales all of it a little (`edgeScale`: 0.85, 1, 1.15).
  - It is defensive about degenerate sizes: nothing is drawn for a surface under 1 px on a side or not
    finite, the outline's radii are clamped to half the shortest side, a malformed outline falls back to
    the rectangle and gradient stops are kept strictly increasing (`safeEdgeOutline`; tests
    `GlassEdgeOutlineTest` and `GlassDegenerateSizeTest`).
- **Tint light.** A surface with a colour shows it as light, never as a tinted body (card tinting is a
  later slice). The spotlight (`glassSpotlightSource`, the icon-colour glow that follows the outline) is the
  main case: the Dock, folders, Library tiles, search results, Stream cards and stacks. A surface with one
  fixed colour passes `edgeTint` and gets strips: 7 dp at 38% to 0 along the top, 5 dp at 0 to 20% along
  the bottom. The fill stays a neutral surface colour.
- **No idle animation.** Nothing in the material animates. The spotlight draws frames only while its
  sources change, then stops. Serein's "breathe" clock is deliberately not ported.
- **Reduced transparency** makes every surface take the **Thick fill** (its colour, in the theme's own
  light or dark base) at 92%, so nothing busy shows through behind text; the edge lights stay at half
  strength and so does the spotlight. Reduced motion makes the spotlight snap.
- **Legibility.** The fills are light and see-through on purpose, so over a bright or busy wallpaper
  Thin and Regular text can lose contrast: they are meant for a dark wallpaper and for short labels.
  Anything that carries dense text sits on Thick (`GlassBlendTest` pins that Thick keeps 4.5:1 over a
  mid-grey wallpaper and the lighter weights do not). There is no per-weight legibility floor any more.

| Weight | Used for | Edge lights |
|---|---|---|
| `Thin` | Dock, chips, row backgrounds, page indicator, spine, headers | 0.85x |
| `Regular` | Cards, widgets' frames, folders, the search pill and results panel | 1x |
| `Thick` | Library sheet, menus, pickers, the focused card, settings sections, panels | 1.15x |

- **Glass over the system wallpaper cannot blur it.** The wallpaper is outside the app's content, so
  nothing the app draws can sample, blur or refract it (the first liquid-glass build tried: commit
  3fa90ba). A flat material does not need to: the tint lets the real wallpaper show through. There is
  no backdrop capture and no shader.
- **Tint** comes from two places only: the wallpaper (chrome) and an app's own colour (as light at
  the edges of its cards, stacks and tiles).
- **Glass is always on**: there is no switch for it and no other theme preset; reduced transparency
  is the one off switch.

### Stream card geometry

The Stream's stacks use one set of geometry tokens, taken from Calm's card pages and kept in
`core/domain/.../launcher/stream/StreamCardStackSpec.kt` (never as literals in the UI): a **168 dp** card and **56 dp**
between neighbouring cards' tops. The stack is **anchored to the Dock** (dock modes slice 4a): the front card sits just
above the Dock, nearest the thumb, and older cards peek above it, three at most, each a strip of **52 dp** (the 56 dp step
less a 4 dp gap) and each 4 percent smaller than the one in front of it. A peeking card is the real card clipped to its
top strip (who or what it is, and when), and the clip is the part the card in front does not cover, so translucent fills
never overlap and add up; as the card comes toward the front the clip opens and the rest of it is revealed. The card in
front sinks toward the Dock and fades (down to 92 percent of its size) as the next one is revealed above it. The layout
is a pure function of one continuous position (`StackGeometry` in `StackMotion.kt`; `r = index - position`: 0 is the
front card, -1 a card sunk and gone, 1 to 3 peeking, 4 and beyond hidden), so a drag, a coast and a settle all draw
from the same numbers and at most five cards are composed. Cards are `Regular` glass lit by the owning app's colour; rows' panels are `Thin`. Content that needs more than 168 dp (transport and scrubber, a lead
image, a checklist) is taller, and a pile is as tall as its tallest card so the strips line up. The Stage rail's
thumbnails are miniatures at 88 dp.

Do
- Light the edge, not the body: a bright thin top edge that fades down the sides and out at both ends
  of the specular hairline. Fixed dp thicknesses.
- Pick a weight, not an alpha.
- Honour the reduced-transparency setting: it is one switch, read through `LocalLiquidGlassSettings`.

Don't
- Don't let anything in the material scale with the surface's height (the old sheen's top band,
  caustic and bottom shadow did, and read as a bevel, a seam and dark wedge corners on tall surfaces).
- Don't add a gradient across the body, an inner shadow, a bevel, a caustic, refraction or an idle
  animation.
- Don't tint the body with an app's colour; show it as light at the edge.
- Don't mix unrelated elevations on siblings, or stack shadows.
- Don't put text on translucent surfaces without the contrast guarantee in `wallpaperAwareSurface`.
- Don't draw a translucent background outside `GlassSurface`.

## 4. Precise typography

Tokens: the M3 type scale, tuned in `riffleTypography`.

| Role | Change from M3 |
|---|---|
| `display*` | weight **Light** (300) |
| `labelLarge` | weight Medium, letter spacing 0.25 sp (M3: 0.1) |
| `labelMedium` | weight Medium, letter spacing 0.6 sp (M3: 0.5) |
| `labelSmall` | weight Medium, letter spacing 0.7 sp (M3: 0.5) |

Only weight and tracking change, so the user's font-family choice (System / Monospace)
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
(Standard / Compact / Rounded, "Corners" in Settings) picks the radii for cards, panels, the dock and
rows; Material components follow the same scale.

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
- Materials (blur, hairlines) in #1202 and, as one primitive at three weights, in places-views slice P1;
  system animation scale in #1203. The adaptive stage card surface still has its own treatment until P4.
