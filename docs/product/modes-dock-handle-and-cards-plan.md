# Modes, the dock handle, and daily-driver Cards

Status: **accepted plan**, tracked by #1196. The workstream issues link back here. Where this
document and an issue disagree, update this document in the same PR that settles the question.

## Why

Riffle is used daily, but Library mode is the default in practice because it is the least hassle.
Cards mode — the part that makes Riffle Riffle — is clunky enough that it gets avoided. The goal
of this plan is to make Cards good enough to live in, make moving between modes a single physical
gesture, and bring every surface under one design language.

The steer: **"if Apple and Sony designed it together."**

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

1. **The mode ring is user-configured.** Standard, Library and Cards remain distinct modes. The
   user enables two or three and orders them; the default ring is **Library → Cards**.
2. **One unified dock.** Exactly one dock per device class, identical in every mode: same pinned
   items, edge, size, appearance and dynamic-section budgets. No per-mode dock configuration is
   stored; anything that must differ by mode (what a dynamic-entry tap does) is derived from the
   active mode at render time. The dock is rendered once, outside the mode surface, so a mode
   change never re-lays it out — it is the fixed point you hold while the world slides past.
3. **A grabber pill on the dock is the mode control.**
   - *Placement*: centred on the dock's inner edge (top edge of a bottom dock; inner edge of a side
     dock, rotated). 36 × 5 dp visual, ≥ 48 × 48 dp touch target.
   - *Indicator*: the pill is segmented, one segment per mode in the ring, the active one lit —
     a quiet position cue, like Sony's hairline indicators.
   - *Drag along the dock axis*: interactive, 1:1-tracked transition to the adjacent mode;
     velocity/distance (dp) decides commit or cancel; interruptible mid-flight.
   - *Tap*: advance to the next mode. *Long-press*: mode overview (see W2-5).
   - *Accessibility*: a button with a state description ("Library, 1 of 2") and custom actions
     per mode; keyboard shortcut.
   - The rest of the dock keeps its existing gestures; the pill owns only drags that start on it.
4. **Transition choreography** (Library ↔ Cards): the grid recedes (scale ~0.94, dim), the card
   stack rises out of the dock's dynamic section — dock notification entries are the shared
   elements that become their stages. Reverse on the way back. Std ↔ Lib: lateral slide with
   parallax. Reduced motion: a short crossfade, same end state.
5. **In Cards, the dock's dynamic section is the compact stage selector.** The spine becomes
   optional; static dock icons **launch** their app (muscle memory), long-press offers "Show
   stage". "All" and "Now" get permanent entries.
6. **Cards opens on Index**, a text-first contents page (Niagara-like) that leads into the stacks; on
   a large screen, Index and the selected stack form a two-page book spread. See #1229.
7. **Cards always has something in it**: a pinned **Now** stage (glance), a **Recents** stage, RSS
   feed stages, and widgets as cards, alongside notification/media stages.
8. **No visual PR merges without rendered evidence**: JVM screenshot tests for the surfaces it
   touches, plus device screenshots for motion.

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

### W2 — Mode ring and dock handle (Phase 2)
1. #1205 Unify the dock across modes: one dock per device class, rendered outside the mode surface, with migration. (M2) — **Phase 1**
1b. #1225 Domain: user-configured mode ring. (M3)
2. #1206 Interactive mode-transition controller (progress, commit/cancel, interruption). (M1)
3. #1207 Dock grabber pill component. (Decision 3)
4. #1208 Transition choreography with shared dock → stage elements. (Decision 4)
5. #1209 Mode overview on long-press. (Phase 4)
6. #1210 Gesture arbitration overhaul: dp thresholds, platform primitives, nested-scroll hand-off. (M6, C1)

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
1. #1220 Library mode: App-Library-style categories, alphabet scrubber, cheaper relayout.
2. #1221 Preview-first onboarding including mode ring and notification access. (D7)
3. #1222 Settings information architecture and search. (D6)

### W7 — Validation (every phase)
1. #1223 Daily-driver validation matrix and dogfood checklist; supersedes the manual gate in #1059. (D8)
