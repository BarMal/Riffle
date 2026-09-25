# Modes, the dock handle, and daily-driver Cards

Status: **accepted plan**, tracked by #1196. The workstream issues link back here. Where this
document and an issue disagree, update this document in the same PR that settles the question.

> **Revision 2026-09-25 — the dock is a literal handle.** Decisions 1–4 are rewritten and
> Decisions 9–13 added. What changed: the configurable 2–3 mode ring becomes a fixed pair,
> **Home ↔ Library**, with Library as the app drawer; the dock keeps one shared content model but
> each mode has its own dock edge; the grabber pill is dropped, and **pulling the dock itself** away
> from its edge is the one and only mode-transition trigger, with every alternative trigger
> deleted. Why: a pill was a second, smaller control standing in for the handle the user already
> holds. Making the whole dock the handle gives one gesture, one place and one direction rule, and
> removes the overlapping drawer-swipe, three-finger and dock-swipe-up paths that made mode
> switching unpredictable. W2 is re-cut accordingly (see Workstreams).

## Why

Riffle is used daily, but Library mode is the default in practice because it is the least hassle.
Cards mode — the part that makes Riffle Riffle — is clunky enough that it gets avoided. The goal
of this plan is to make Cards good enough to live in, make moving between Home and Library a single
physical gesture (pulling the dock), and bring every surface under one design language.

The steer: **"if Apple and Sony designed it together."**

**Scope.** This plan covers the Cards / dock / design-language slice only. It does not reorder the
overall backlog priorities in `AGENTS.md`: standard-launcher parity work (widgets, folders,
backup/restore, drawer/search) keeps its priority and is not deferred by this epic's P0 labels.
Where this plan improves shared primitives (layout state, gestures, tokens, the dock), Standard
and Library benefit directly.

## Findings from the codebase review (at `078454b`)

### Cards mode

| # | Finding | Where |
|---|---|---|
| C1 | The stack consumes every vertical drag, even at the first/last card or with one card, so home swipes (drawer, three-finger exit) die over the stack. No overscroll hand-off or edge feedback. | `CardStack.kt:396` |
| C2 | Card travel, settle and fling thresholds are raw pixels (64 px, 500 px/s): ~24 dp per card on a 2.6× phone, so one flick skips many cards. | `AdaptiveStageAppStageSurface.kt:1979,1728,2153`, `CardStack.kt:943` |
| C3 | Every card is composed (`maxVisibleDepth = cardCount - 1`), entries recomputed every drag frame — breaks the performance budget. | `AdaptiveStageAppStageSurface.kt:2321`, `performance-budgets.md:15` |
| C4 | Artwork is Base64/bitmap-decoded on the main thread during composition. | `AdaptiveStageAppStageSurface.kt:1769,2202` |
| C5 | Stage state reconciled on every recomposition; O(n²) spine lookups. | `:3018`, `:2948` |
| C6 | Dead ends: Settings only via a stage overflow that disappears on "All"; the Settings gesture is filtered out; "All notifications" has no chip. | `:1377`, `:127` |
| C7 | Header clutter: Pin twice, "Add stage" is an unsearchable dropdown of every app, `Text("⋮")` instead of an icon, spine doesn't scroll to selection. | `:1380-1409` |
| C8 | Expanded layouts only on foldables: tablets/desktop/large landscape fall back to compact. Tabletop/book postures have no layout. Pane math ignores the dock width, so content can cross the hinge. | `AdaptiveStagePaneLayoutPolicy.kt:300` |
| C9 | Two/three-pane has no stage selector; a pinned stage with no notifications and no dock icon is unreachable by touch. | `:469-538` |
| C10 | Rail tiles are icon + label chips, not the live mini-previews the spec asked for (#1059). | `AdaptiveStageStageRailTile` |
| C11 | Reduced-motion gaps (thread recede), 0.45-alpha unselected chips, Polite live region on the focused card floods TalkBack during flings. | `:1660-1670`, `:2999`, `:1779` |
| C12 | `AdaptiveStageAppStageSurface.kt` is a 3,073-line monolith; per-stage and "All" stacks are near copies. | `:1524`, `:2017` |
| C13 | Declared but unwired: `LauncherCard` AGENDA/ALARM/TASK/WIDGET_PROVIDER, `FeedStageSurface` (RSS). | `core/domain/.../cards/` |
| C14 | Tuning overlay always previews the compact layout, even when editing the unfolded profile. | `AdaptiveStageAppearanceTuningOverlay.kt:136` |

### Modes, dock and shell

| # | Finding | Where |
|---|---|---|
| M1 | Mode changes are a plain `when` swap — no transition infrastructure (no `SharedTransitionLayout`, `LookaheadScope`, `movableContentOf`). | `LauncherHomeDestination.kt:34` |
| M2 | Each mode owns a separate dock, so switching mode swaps the dock — incompatible with "the dock is the handle". | `HomeLayoutSet.kt:60` |
| M3 | Std ↔ Lib has no gesture; the dock swipe-up only does anything in Cards. | `DockSwipeUpGesture.kt:21-33` |
| M4 | Reducers reload the layout set from disk with `runBlocking` on every edit; root cause of #1176, only the Home path was fixed. | `DataStoreHomeLayoutRepository.kt:47-58`, `LauncherShellHomeLayoutState.kt:243` |
| M5 | Mode gestures target `settingsLayoutDeviceClass`, not the active device class — a switch can silently do nothing. | `LauncherShellHomeLayoutState.kt:47,57` |
| M6 | Gesture thresholds in px; gesture layers are hand-rolled despite ADR 0002; dock shelf, dock swipe-up, section scroll and root home gestures overlap. | `HomeGestureInput.kt:115`, `DockShelfGesture.kt` |
| M7 | Dead/duplicated paths: `HomePageShellActions.kt:63,74`, `DockToHomeDestinationDialog`, the View-drawn overlay dock (~4.5k lines, slated for removal). | |

### Design system

| # | Finding |
|---|---|
| D1 | No spacing/elevation/motion token layer: ~341 raw `dp` literals, seven differently-tuned `spring()`s, shapes bypass `MaterialTheme.shapes`. |
| D2 | "Glass" is translucency only; `Modifier.blur` blurs the content, not what's behind, and is a no-op below API 31 (minSdk 28). |
| D3 | Reduced motion ignores the system animator scale / "remove animations". |
| D4 | Legacy `android:Theme.Material` XML parent, no SplashScreen, no predictive back. |
| D5 | Zero string resources — blocks localisation and a real RTL review. |
| D6 | 15 settings pages built from bespoke rows; few M3 structures; no search. |
| D7 | No onboarding beyond the home-role card; notification access lives only in Settings. |
| D8 | Visual work has repeatedly passed CI without anyone seeing it rendered (#1059). |

## Decisions

1. **Two surfaces, not a ring.** The launcher has exactly two mode surfaces, **Home** and
   **Library**, as a fixed pair. Home is Cards; Standard stays selectable as the Home surface until
   Cards hosts widgets and standard app pages, then Standard retires with a migration (W2-f).
   Library *is* the app drawer: there is no separate drawer surface. The user-configured 2–3 mode
   ring (#1225, shipped in #1239) collapses to this pair; there is nothing to enable or order.
2. **Dock content is shared; dock edge is per mode.** One `DockModel` per device class holds the
   dock's content and look (pinned items, size, appearance, dynamic-section budgets) as
   #1205/#1236 store it, identical in both modes. Each mode (Home, Library) stores its own dock
   **edge** (BOTTOM / LEFT / RIGHT / TOP, limited to the edges that mode's surface can draw); both
   modes may use the same edge. What a dynamic entry does is still derived from the active mode at
   render time. The dock is rendered once, outside the mode surface, and survives a mode change as
   the same instance; only its position and orientation animate.
   *Boundary:* the shared dock is a mode-agnostic component. It renders a `DockModel` at an edge and
   emits neutral intents (item tapped, dynamic entry tapped, item dragged out, dock pulled); each
   mode supplies an interpreter for those intents. Cards-specific behaviour (stage selection, stage
   previews) lives behind that interface in the Cards feature code, never in the dock or in
   Standard/Library code.
3. **The pull is the trigger.** Every dock edge has a *natural pull direction*: away from the edge,
   toward the screen interior. A bottom dock pulls up, a top dock down, a left dock right, a right
   dock left.
   - A pull starts on the **dock body** (background, icons, sections), never in the system-gesture
     inset at the screen edge.
   - It tracks the finger 1:1. On release, travel or velocity past the commit threshold (dp, from
     `GestureThresholds`) commits the switch to the other mode; anything less cancels and springs
     back. A pull is interruptible: catching the dock mid-flight resumes tracking from where it is.
   - Only motion along the natural pull direction drives the transition. Drags along the dock run
     still scroll its sections, and long-press + drag still reorders items or drags them out.
   - After commit the dock animates to the edge and orientation the new mode expects, which may be
     the edge it already has.
4. **Choreography.** During the pull the dock's background loses alpha in proportion to progress;
   its icons re-orient to the target orientation as they snap into their new positions; the
   background fades back in on settle. The two layouts slide with pull progress: the outgoing
   layout moves with the dock, and the incoming layout follows in from the side the dock came from.
   Reduced motion: a short crossfade to the same end state.
5. **In Cards, the dock's dynamic section is the compact stage selector.** The spine becomes
   optional; static dock icons **launch** their app (muscle memory), long-press offers "Show
   stage". "All" and "Now" get permanent entries.
6. **Cards opens on Index**, a text-first contents page (Niagara-like) that leads into the stacks; on
   a large screen, Index and the selected stack form a two-page book spread. See #1229.
7. **Cards always has something in it**: a pinned **Now** stage (glance), a **Recents** stage, RSS
   feed stages, and widgets as cards, alongside notification/media stages.
8. **No visual PR merges without rendered evidence**: JVM screenshot tests for the surfaces it
   touches, plus device screenshots for motion.
9. **The dock pull is the only mode-transition trigger.** The alternatives are **deleted**, not
   migrated and not kept dormant: the app-drawer swipe (the `OPEN_APP_DRAWER` home-gesture binding
   path, swipe-up-to-drawer), the three-finger `NEXT_MODE` / `PREVIOUS_MODE` bindings, the dock
   swipe-up gesture setting and its input modifier (`DockSwipeUpGesture`), and the
   `SelectNext/PreviousLauncherViewMode` shell actions if nothing else uses them. Stored settings
   that name a removed action or setting still decode without crashing: unknown values map to no
   action or are dropped. No user-facing notice.
10. **Library-as-drawer return behaviour is a setting.** After launching an app from Library, a
    Home press, or Back from Library, the launcher resets to **Home** (default) or stays on
    **Library**, as the user chooses. Library is never the cold-start mode unless that setting says
    so.
11. **Dock expansion is disabled for now.** The shelf/panel expand gesture is switched off behind a
    feature flag (not a user setting or button), which also removes its overlap with the pull.
    Revisit after the pull has shipped.
12. **Accessibility equivalents of the pull.** A dock custom action ("Switch to Library" / "Switch
    to Home") and a keyboard shortcut perform the same commit. They are equivalents of the pull, not
    separate visible UI.
13. **Side docks and system Back.** A pull must start on the dock body, which is already inset from
    the screen edge, so a side dock does not compete with the Back edge gesture by construction.
    Add `systemGestureExclusion` over the dock only if device testing shows conflicts (the platform
    caps the excluded height).

## Design language: Apple × Sony

| Principle | Apple cue | Sony cue | In Riffle |
|---|---|---|---|
| Calm by default | iOS Home, Focus | Xperia's restraint | One accent, generous space, nothing animates without cause |
| Physical, honest motion | Interruptible springs, 1:1 tracking | TimeScape's Spline | Three springs only (snappy, smooth, gentle); every gesture tracks the finger and can be reversed |
| Material depth | Thin/regular/thick materials | Glass-and-aluminium Xperia hardware | Real background blur (RenderEffect, API 31+) with tinted fallback; hairline highlights; soft single-source shadow |
| Precise typography | SF hierarchy, dynamic type | Sony's thin, wide-tracked labels | M3 type scale, tuned: display weights light, labels medium, generous tracking on small caps |
| Continuous shapes | Squircle corners | Omnibalance's flat planes and rounded edges | Continuous-corner shape tokens; one radius scale |
| Focus over chrome | Stage Manager | TimeScape focused tile | One thing in focus, context recedes; chrome appears on demand |

**Reference points** for specific features: Palm webOS (card stacks + a gesture area as the
system handle — the closest ancestor of this plan), iOS (Smart Stack, App Library, jiggle-mode
editing, Spotlight), macOS (Stage Manager, Dock), GNOME (Activities overview, 1:1 workspace
swipes), KDE Plasma (Activities as whole-setup modes), Windows Timeline (chronology), BlackBerry
Hub (unified inbox → "All"), Olauncher and other minimalist launchers (text-first Index), Pixel Launcher (At a Glance, predictive back), Niagara (list-first
drawer, alphabet scrubber), Sony Xperia (TimeScape, Side Sense).

## Workstreams

Phases are ordered by what makes Cards usable soonest; #1196 holds the phase checklist.

### W0 — Foundations (Phase 1)
1. #1197 JVM screenshot tests for Cards, Home, Dock (compact / unfolded / tabletop), as CI artifacts.
2. #1198 In-memory layout state as the source of truth; no `runBlocking` disk reloads; active device class for mode edits. (M4, M5)
3. #1199 Break up `AdaptiveStageAppStageSurface.kt` and feature-package the flat launcher package. (C12)
4. #1200 Remove dead paths, the overlay dock, stale docs. (M7, C13 cleanup)

### W1 — Design language (Phases 1–2)
1. #1201 Design-language spec + token layer: spacing, radius, elevation, motion springs, materials. (D1)
2. #1202 Real glass materials with fallback and reduced transparency. (D2)
3. #1203 Platform integration: system animation scale, M3 DayNight + SplashScreen, predictive back, modern haptics. (D3, D4)
4. #1204 String resources and RTL pass. (D5)

### W2 — Home ↔ Library and the dock pull (Phase 2)
Shipped before the 2026-09-25 revision: #1205 one dock per device class, rendered outside the mode
surface (#1236, #1238); #1225 user-configured mode ring (#1239, collapsed by W2-a); #1210 gesture
arbitration overhaul (#1235). Superseded by the revision: #1208 (shared dock → stage
choreography; the layout slide now lives in W2-c/d) and #1209 (mode overview on long-press; two
surfaces need no overview).

- a. #1241 Delete the alternative mode triggers and collapse the mode ring to the fixed Home ↔
  Library pair; stored settings with removed names decode safely. (Decisions 1, 9)
- b. #1242 Per-mode dock edge model (migration from the shared edge) and the pull-direction /
  commit domain logic. (Decisions 2, 3)
- c. #1206 Interactive mode-transition controller (progress, commit/cancel, interruption). (M1)
- d. #1207 Dock pull handle + dock re-orientation choreography. (Decisions 3, 4, 11, 12, 13)
- e. #1243 Library-as-drawer return setting. (Decision 10)
- f. #1244 Cards absorbs Standard: widgets and standard app pages in Cards, then Standard
  retirement with migration. (Decision 1)

### W3 — Cards, compact/folded (Phase 1)
1. #1211 Stack feel and performance: dp thresholds, overscroll hand-off, depth-bounded composition, off-main artwork, memoised stage state. (C1–C5)
2. #1212 Compact layout: header clean-up, dock-as-stage-selector, Settings/All/Now always reachable. (C6, C7, Decision 5)
3. #1213 Card anatomy and visual redesign, live mini-previews. (C10, C11)
4. #1214 Focused-card actions: reply, dismiss, snooze, open, thread detail, Back.

### W4 — Cards, expanded/unfolded (Phase 3)
1. #1215 Adaptive panes by window size class plus posture: tablets, tabletop, dock-aware hinge math, stage rail in multi-pane, tuning preview uses the real layout. (C8, C9, C14)

### W5 — Card content (Phases 3–4)
1. #1216 Now glance stage.
2. #1217 Recents/frequent apps stage.
3. #1218 RSS feed stages.
4. #1219 Widgets as cards.
5. #1226 Card source integration framework — one provider interface so integrations don't special-case the stage planner.
6. #1227 Media integration: now-playing card with queue and full controls.
7. #1228 Todoist integration: today's tasks, complete, quick-add (personal token; no shipped secrets).
8. #1229 **Index** — a minimalist, book-style default screen for Cards (Niagara-inspired text-first
   list with inline snippets; on unfolded it is the left page of a two-page spread whose right page
   is the selected stack, with the hinge as the spine).

### W6 — Home, Library and shell polish (Phase 4)
1. #1220 Library (the app drawer): App-Library-style categories, alphabet scrubber, cheaper relayout.
2. #1221 Preview-first onboarding including the dock pull and notification access. (D7)
3. #1222 Settings information architecture and search. (D6)

### W7 — Validation (every phase)
1. #1223 Daily-driver validation matrix and dogfood checklist; supersedes the manual gate in #1059. (D8)
