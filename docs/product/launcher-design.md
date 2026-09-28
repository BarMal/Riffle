# Riffle launcher design: Home, Library and Now

Status: **proposed**. Once accepted, this supersedes Decisions 1, 5–7 of
`modes-dock-handle-and-cards-plan.md` (Cards as Home) and becomes the top-level product model the
other product docs hang off. Decisions marked **Open** need an owner call before implementation.

## The model in one paragraph

Riffle is a standard Android launcher first. **Home** is a normal grid of pages (icons, widgets,
folders, resizeable grid) with a **dock**. Pulling the dock up opens **Library**, the app drawer,
which can also be where you live. An optional **Now** page adds a search bar and an *actionable
items* stack — notifications worth acting on — and is where Riffle's card-stack interaction
lives. Cards stop being a separate home mode; their machinery moves into Now.

## Surfaces and spatial map

```
            ┌──────────────┐
            │   Library    │   (app drawer; can be the start surface)
            └──────▲───────┘
                   │ pull dock up / pull down or Back to return
 ┌─────────┐   ┌───┴─────────┐   ┌─────────┐
 │   Now   │ ◄ │ Home page 1 │ ► │ page 2… │
 └─────────┘   └─────────────┘   └─────────┘
   optional, leftmost page (the "−1" page)
```

| Surface | What it is | Default |
|---|---|---|
| Home | Standard pages: icons, widgets, folders, editable grid | On, start surface |
| Library | App drawer: search, A–Z list or grid, categories, profiles | Always available |
| Now | Search bar + actionable items stack | Off; one toggle in Settings and in first-run |

Rules that keep this predictable:

1. **One axis, one meaning.** Horizontal = pages (Now is simply the first page). Vertical from the
   dock = Library. Swipe down on the workspace = system notification shade (platform parity).
2. **The dock is on every surface**, same content, same place. It is the constant the user orients
   by.
3. **Start surface is a setting**: Home (default), Now, or Library. "Library as home" is a
   first-class choice, not a workaround.

## The dock

Unchanged from `dock.md` in structure: one strip, a **pinned** section and a **dynamic** section.

- **Pinned:** tap opens the app, everywhere, always.
- **Dynamic:** apps with a current actionable notification that are not already pinned. Pinned apps
  with something new show a badge instead of a duplicate entry.
- **Tap on a dynamic entry:** with Now on, opens that app's **notification page** in Now (it has a
  prominent *Open app* action); with Now off, opens the app. Long-press always offers *Open app*,
  *Pin to dock*, *Mute in dock*.
  Rationale: dynamic entries come and go, so they carry no muscle memory; "what arrived?" is the
  question they answer.
- **On Now itself the dynamic section is hidden** — the actionable strip shows the same thing with
  more room, and showing it twice is noise.
- **Pull up = Library.** Commit/cancel, 1:1 tracking and reduced-motion behaviour as already built
  (`gestures.md`).

## Library (the drawer)

- **Persistence is the headline feature.** Setting *After launching an app, return to*: **Home**
  (default) or **Library**. When set to Library, Home press and Back from an app land in Library;
  Home press while in Library goes to Home (so Home is never unreachable).
- Search field pinned at the **bottom**, above the dock, thumb-reachable; typing filters in place.
  It uses the same search engine as Now (see below) but ranks apps first.
- Pull the dock back down, press Back, or press Home to leave.
- Scroll position resets on leave unless Library is the start surface (then it is remembered).

## Now

Layout, bottom-up so everything interactive is in the thumb zone:

```
 ┌──────────────────────────────┐
 │                              │  wallpaper / glance (clock, next event) — calm by default
 │                              │
 │  [ search results stack ]    │  only while typing; grows upward from the bar
 │                              │
 │  ┌────────────────────────┐  │
 │  │  🔍  Search             │  │  search bar
 │  └────────────────────────┘  │
 │  ◉ Messages 3 · ◉ Cal · +2   ⌃│  actionable strip (collapsed)
 ├──────────────────────────────┤
 │  dock (pinned only here)     │
 └──────────────────────────────┘
```

### States

| State | Shows | Enter | Leave |
|---|---|---|---|
| **Idle** (default) | Glance, search bar, collapsed strip | Swipe to Now | — |
| **Searching** | Results stack above the bar; IME up; strip and dock hidden behind IME | Tap bar or start typing (hardware keyboard) | Clear, Back, or launch a result |
| **Actionable expanded** | Stack of one card per app (top item each), ordered by priority | Tap strip or its chevron | Drag down, Back, tap outside |
| **App notification page** | All of one app's notifications as a stack, with actions | Tap an app card, a strip chip, or a dock dynamic entry | Back → where you came from |

Depth is capped at **two levels** below Idle (expanded → app page). Back always pops one level.

### Actionable items

- **What counts:** alerting notifications, conversations, time-sensitive items, active media
  session, missed calls. **Excluded by default:** silent, ongoing/foreground-service, group
  summaries. A per-app *Hide from Now* on long-press.
- **Order:** conversations → time-sensitive → others; recency within each. Stable while visible
  (new arrivals appear with a gentle indicator, they do not reshuffle what the finger is on).
- **Collapsed strip:** up to 4 app chips with counts, then `+N`. Empty → the strip is not drawn
  (no empty-state chrome). No notification access → a single one-line prompt, dismissible forever.
- **Expanded stack:** focused card on top shows the item with its actions (reply, mark read,
  dismiss, snooze); cards behind recede. Swipe along the stack to rifle; swipe a card sideways to
  dismiss (with undo).
- **App page:** the old "stage" — reuse the Cards stack, planner and focus reconciliation. Header:
  app icon, name, *Open app*, *Clear all*.
- **Expand by tap, not swipe-up.** The strip sits directly above the dock, whose swipe-up is the
  Library pull. Two adjacent regions answering the same gesture differently will be misfired
  constantly; a tap target (the whole strip) avoids it. Drag-down on the expanded stack collapses.

### Search

- **One engine, many sources:** apps, shortcuts, contacts, settings, launcher settings, calculator /
  unit conversion, web fallback. (Extends `LauncherSearchProvider`.)
- **Results as a stack, but readable.** A pure card stack hides results, which is wrong for
  scanning. Proposal:
  - **Top hit** is the focused card, largest, nearest the search bar; **Enter / Go launches it**.
  - Other results are **grouped by source**; each group is one card behind the top hit showing its
    first 3–4 rows. Rifling brings a group forward; tapping a group card expands it.
  - Results are bottom-anchored (best result closest to the thumb and IME).
- Results update per keystroke with no reorder of the focused card while its match still holds
  (prevents tapping the wrong thing mid-type).
- Optional: *Open keyboard when Now opens* (off by default so the actionable strip is visible).

## Motion and accessibility

- Reuse the three springs from `design-language.md`. Strip → stack is a shared-element expansion
  from the strip; stack → app page grows from the tapped card. Reduced motion: crossfades.
- Every level has a heading for TalkBack, the stack exposes a collection with position
  ("3 of 7"), and the focused card is announced on settle only (not during a fling — see C11).
- Every gesture has a visible or custom-action equivalent: *Expand actionable items*, *Open app*,
  *Dismiss*, and the existing *Switch to Library*.

## What this changes in the existing plan

| Existing | Proposed |
|---|---|
| Home = Cards; Standard retires into Cards (W2-f, #1244) | Home = Standard grid, permanently. **Cards retires as a home mode**; its stack, planner and focus code move into Now |
| Dock dynamic section = stage selector in Cards | Dynamic section = apps with actionable notifications, in every surface; tap → that app's Now page |
| Index (#1229), All view, spine | Dropped. Now's expanded stack *is* "All"; the strip *is* the selector |
| Now glance stage (#1216) | Becomes Now's Idle glance |
| RSS, Todoist, widgets-as-cards (W5) | Deferred. If kept, they are optional Now sources below actionable items, never mixed into the notification stack |
| Library return setting (#1243) | Kept; becomes the "drawer as home" feature |

## Open decisions

1. **Swipe up on the workspace** (not just the dock) to open Library? Every major launcher does
   this; `gestures.md` deleted it for predictability. Recommendation: **allow it on Home only**,
   because parity is priority #1 and the workspace has no competing vertical gesture.
2. **Dock dynamic entry tap** → Now app page (recommended) or open the app?
3. **Now placement:** leftmost page (recommended — discoverable, standard "−1" convention) vs. a
   swipe-down from Home (conflicts with the notification shade).
4. **Cards retirement:** remove the Cards home mode outright with a migration to Standard, or keep
   it hidden behind a flag for one release?
5. **Glance content** on Now Idle: clock + next event only, or empty (pure search)?

## Suggested slices

1. Now shell: page, toggle, search bar, glance; dock hides dynamic section on Now.
2. Unified search engine + grouped results stack; wire the Library search field to it.
3. Actionable items domain: filter, grouping, ordering (pure Kotlin, unit tested).
4. Actionable strip + expanded stack (reusing Cards stack), accessibility pass.
5. App notification page (lift from Cards stage) + dock dynamic-entry routing.
6. Library-as-home: start-surface setting, return behaviour.
7. Cards home-mode retirement + settings migration.
