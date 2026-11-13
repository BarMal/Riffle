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
