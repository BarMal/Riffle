# The dock

The dock is Riffle's central shortcut and recents surface. There is **one** dock, shared by every
view mode, configured per layout. This document is the target it is being built toward and the
honest state of it today; where the two differ, the target wins.

## What the dock is

A single strip, anchored to one edge of the screen, holding two sections:

- a **static** section of items the user pinned, which always show; and
- a **dynamic** section of items that appear because something arrived.

Swiping it open reveals a **panel**: a small home screen using standard conventions, for widgets
and shortcuts the user wants within reach without leaving where they are.

That is the whole surface. Anything that looks like a second dock — a rail beside the cards, a
separate floating bar over other apps — is the same dock in a different posture, or it should not
exist.

## Target behaviour

### One dock, every layout

The dock is available in every view mode. It is configured per layout, because a
`DockModel` belongs to a `HomeLayout` and a layout belongs to a
(view mode × device class) key — an edge that suits a tablet wastes width on a phone in portrait.

### Anchored, with space reserved for it

The dock anchors to any edge, and the space it takes is reserved rather than overlaid: a side dock
costs the workspace a column, a top or bottom dock costs it a row. Items are laid out along the
edge it sits on.

### Sized by settings

Icon size, item spacing, corner radius, background alpha and sizing are per-layout settings, and
the dock's extent follows from them rather than being fixed.

### Static and dynamic sections

The **static** section is what the user pinned. It always shows. A tap opens the item.

The **dynamic** section shows an entry when **a notification has arrived** for an app the dock is
not already showing. It is opt-in, per layout. Its meaning is "this has something waiting", not
"this is a list of things you can go to".

What a tap *does* depends on where the content lives, and this holds on both sides:

- in grid modes there is nowhere on the launcher for an app's content, so a tap opens the app;
- in Cards mode the content is already on the launcher, so a tap brings that stage forward.

That applies to the static side too. In Cards, a tap on a pinned icon selects its stage when it has
one and opens the app when it does not, so a quiet pinned app never gives a dead tap. **Open** stays
on the icon's long-press menu either way. The badge is the tell: a badged pinned icon has a stage a
tap will show, an unbadged one opens.

The static section is sized first and in full; the dynamic section takes what is left. Notifications
come and go, and a section sized first would shove the pinned icons along the dock every time one
arrived.

### Overflow and rows

The dock has a configurable number of items **visible before overflow**, and supports **multiple
rows**. Whatever does not fit is reached by scrolling, not by shrinking: an entry is always the size
of a pinned icon, because a dynamic entry a different size from the icon beside it is the seam the
single dock exists to remove.

### The panel

A gesture opens the dock into a panel — a single home screen using standard conventions, so
widgets and shortcuts are placed arbitrarily on a grid rather than into bespoke slots. It is
configurable for **rendered size**, **grid dimensions**, and **padding**.

It is deliberately not where items past the visible-before-overflow count go; those scroll in the
dock's own strip. The panel is for things you consult or act on without leaving where you are.

### Floating over other apps

The dock — the same dock, with the same two sections and the same panel — can float above other
apps. The separate overlay dock is superseded by this and is to be removed rather than maintained
alongside it.

### The Cards side rail

Superseded. The rail was a stage list beside the card stack on wide windows; the dock's dynamic
section does that job, so the rail is gone (#1159).

## Where the code is against this

| Target | State |
| --- | --- |
| One dock, every layout | Renders in every mode. Cards invokes it through its own bottom-pinned path rather than the shared one |
| Anchors to any edge, space reserved | Done for grid modes (#1148–#1152). **Not for Cards** — bottom-pinned, so the position setting is inert there. Decided: wire `resolveDockPosition` into the dock's position, which means revisiting `dockInteractionRegionHeightDp` (it reserves a height and only a height) |
| Sized by settings | Done |
| Static section | Done |
| Dynamic section exists, opt-in per layout | Done (#1154), gated on the existing per-layout switch |
| Dynamic section means "a notification arrived" | Done (#1162) — de-duplicated against the static side in every mode |
| Tap opens the app / brings the stage forward | Done (#1155), and on the static side in Cards too (#1162) |
| Static sized first, dynamic takes the remainder | Done (#1154) |
| Visible-before-overflow, scroll for the rest | Done |
| Multiple rows | **Not started** — no notion of rows exists |
| Panel exists, standard conventions | Done — a real `LauncherPage` on the same grid machinery as a home page |
| Panel configurable: size, grid, padding | **Not started** |
| Panel editing: drag in from the picker | **Not started** — needs a non-fullscreen picker so the dock stays visible |
| Dock floats over other apps | **Not started** — a separate overlay dock subsystem exists and is to be replaced |
| Rail superseded | Done (#1159) |

## Unresolved

- **The merged All-notifications page has no dock affordance on a wide window.** It used to ride the
  full-stage-list feed, which was wrong for other reasons (#1162); the compact stage carousel still
  reaches it. The `ShowAllNotifications` intent and its plumbing are deliberately left in place and
  dormant, so a dedicated affordance can host it if a wide window is decided to need one.
- **A pinned app's tap action in Cards depends on whether something is waiting.** The badge makes it
  legible, but it is a behaviour that changes without the user doing anything, and is open to veto.
- **The rail's per-stage snippet.** The rail showed a line of the most recent card per stage. A dock
  entry is one icon wide and has nowhere to put it, so that is lost with no replacement.
- **The Cards expanded shelf still shows a notification card row**, which duplicates the dock's
  dynamic section. The panel is the part worth keeping there.

## Change checklist

When changing the dock, check that: the two sections still read as one strip; the static side is
still sized first; nothing shrinks below a pinned icon's size to fit; the behaviour still holds on
every edge and in RTL; and a layout that has the dynamic section switched off is unaffected.
