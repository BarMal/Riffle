# Dock modes: build plan

Builds ADR 0003 Amendments 1 to 3 on a folded phone. Each slice is one pull request that leaves the app
working. The unfolded layout (Amendment 2) follows once folded is on a device.

The reference for behaviour and feel is the set of working prototypes on the UX canvas (boards M1 to M3).
Their spring, magnet and timing constants are the starting values; the settings in slice 6 expose them.

| Slice | What it delivers | Depends on |
| --- | --- | --- |
| 1. Dock modes | The Dock's Home and contextual modes: contextual tiles appended after the live apps, the row scrolls in the contextual mode, the open icon is ringed. Press-down-and-release opens and closes. The Stream spine, page header and Stream button go. | none |
| 2. Trampoline and fan | The dip under the finger, the bounce on release, cards fanning out in sequence, Home fading, and closing as the opening reversed. Reduced-motion fallback. | 1 |
| 3. Hold to choose | Press and hold or slide while pressed: early extend, selection ring, name label, edge scrolling, haptic tick per icon. Starting a press on an icon opens that icon's stack. | 1, 2 |
| 4. Stacks | The bottom-anchored card stack with clipped peeking cards, drag, fling and magnet; swiping left and right between stacks in Dock order; shortcuts stacks for apps with nothing live; the Today page (agenda and tasks). | 1 |
| 5. Cards | App-tinted cards, images in browse and open cards, a card growing in place when opened, and its actions in the Dock. | 4 |
| 6. Settings | Stream settings (open card position, actions position, tint, background, pinned apps), motion settings, the accessibility action and the first-run hint. | 2 to 5 |
| 7. Home and Library | The combined search-and-page handle with quick-drag, search opening from the Dock, the paged Library, Home mode (Placed items or App library) and the feature switches. | none |

## Rules for every slice

- No behaviour is removed without its replacement in the same slice.
- New gestures follow ADR 0002 (platform gesture primitives).
- Every new gesture has a non-gesture route (an accessibility action or a menu entry).
- JVM tests for pure logic; device tests for the gesture's outcome, not its pixels.
- The slice's row in this table is updated with what was built and what differed.

## As built

Filled in as slices land.

### Slice 1: Dock modes (built)

**Built.**

- **Two modes, one order** (`core/domain/.../dock/`). `DockMode` (Home, or contextual while the Stream shows).
  `DockDynamicSide.entries` gives the live apps and now playing and, for the contextual mode, one `ContextTile` per
  Stream page that is not an app (`contextSections`, from the pages the Stream publishes). The Stream button is gone from
  the domain, the UI and the strings. In the UI the contextual mode is `DockContextualRow`: the pinned icons at full size
  and spacing, a divider, the live entries, a divider and the tiles, in one row scrolled with the platform's
  `horizontalScroll` / `verticalScroll` (fling included), on every edge. `DockRunGeometry` says where each item lies and
  `DockRunScroll.toReveal` scrolls the ringed icon into view, with a margin so its neighbour peeks, whenever the ring
  moves; `DockRing` says which icon is ringed.
- **Pages follow Dock order.** `DockPageOrder` reorders the planner's pages: live apps in the Dock's order, the pages of
  apps beyond the Dock's slots, now playing's Now page, then the built-in pages in layout order. `StreamSurface` applies it
  and publishes the pages and the open one (the pager's `currentPage`, so the ring moves when a swipe passes halfway)
  through `StreamDockLink`; the Dock reads them. A tap on a tile or a live app in the contextual mode asks the Stream to
  land on the page through `StreamFocusRequests`, as before.
- **Spine, page header and Stream button removed.** The page options (Show as, Move, Hide) and "Show <hidden section>" are
  on a long-press of the tile (or of a live app's / now playing's icon) in the contextual mode, applied by the Stream
  through `StreamDockLink` so each keeps its Undo; Move steps over sections that draw no tile (`DockTileMoves`). The
  Stage's own bar and menu (unfolded) stay.
- **The press** (`DockPress`, `DockPressState`, one pointer loop in `DockPull.kt`). A drag toward the Dock's edge past
  touch slop is claimed as a press, and released past 28 dp (`GestureThresholds.DOCK_PRESS_COMMIT_DP`) it toggles the modes
  by running the existing Home and Stream transition from rest. A press that starts on a live app's or now playing's icon
  opens that page. `DockPressState` exposes `progress` (0 to 1) and `onReleased` for slice 2. The pull away from the edge
  now always opens the Library.
- **Accessibility.** The Dock's custom actions are "Show live pages" / "Back to Home" and "Open Library"; in the Home mode a
  live app keeps "Show <app> in Stream"; every tile is a focusable 48 dp target with a content description, a click action
  ("Show Agenda") and a long-click action.
- **Tests.** Pure logic in `DockContextualTest`, `DockPressTest`, `DockDynamicSideTest`, `DockPullEntryTest` and
  `DockDynamicSectionMetricsTest`; behaviour on the JVM in `DockContextualModeTest`, `DockLiveAppEntriesTest`,
  `DockPullScreenshotTest`, `StreamHomeDestinationTest`, `StreamOpenTransitionTest`, `StreamBehaviorTest`,
  `SplitScreenshotTest`; on a device in `DockContextualRowTest`, `DockPressInteractionTest` and the updated
  `StreamPagesInteractionTest`.

**What differed from the design.**

- **Now playing keeps its entry.** The amendment's Home mode lists only apps with notifications; the now playing entry stays
  (a tap still opens the player) and, while shown, stands for the Now page, so the Now tile is not drawn then and the order
  is unchanged either way.
- **Tiles follow the layout's order, not the prototype's.** The default layout order is Now, Notifications, Recents, Agenda,
  Tasks, Feeds, so the tiles read Now, Recents, Agenda, Tasks, Feeds, where the prototype has Now, Today, Recents, Feeds.
  Agenda and Tasks are two tiles, as asked. "Move earlier / later" on a tile is the layout's Move.
- **Notifications has a tile while it holds only a state** ("No notifications", "Allow access"), so the page and its action
  stay reachable; with live apps it has none.
- **The Stream opens on the page it was last left on, else its first page** (Now, in the default layout), not on a live
  app: the Stream's data arrives after it opens, and the page in front stays in front while it does.
- **Pull is always the Library**, from any part of the Dock. The Stream's pull (tracking the finger) and the split's
  three-state settle by pull are no longer started by a gesture (the press, the divider and their actions reach the three
  states); `DockPullState`'s split path remains for the press's settle.
- **Pinned apps in the contextual mode** launch on a tap (they have no page yet).
- **A Dock with no pins and nothing live** keeps a body one icon long, so it can still be pressed (the Stream button used
  to give it one).
- **Stream-level menu.** The pager's spine held the Stream's own menu (Show as Pages or Stage on a wide posture, Show for
  each hidden section). Hidden sections are offered by the tiles' long-press; Pages or Stage is Settings > Stream (and the
  Stage's own bar).

**Left for later.** Everything the slice list assigns to slices 2 to 7: the dip, bounce and card fan (the press only exposes
its progress and release), hold to choose, shortcuts stacks for pinned apps, bottom-anchored stacks, card tinting,
settings and the first-run hint (the onboarding text now describes the press), the Library and Home handle, and the unfolded
layout. Not done in this slice either: the owner's Today page (Agenda and Tasks stay two pages), and the dead split-settle
path in `DockPullState` can go when slice 2 rewrites the transition.
