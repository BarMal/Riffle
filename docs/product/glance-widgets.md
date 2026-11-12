# Glance widgets

Status: **built** (slice P11 of [`places-views-plan.md`](places-views-plan.md), [ADR 0003](../architecture/adr/0003-places-and-views.md)).

Glance widgets are how dynamic content appears on Home: a fixed catalogue of three Riffle-owned widgets that are
placed from the widget picker like any other widget and live on Home pages as placed, resizable items.

| Widget | Reads | Draws | Spans (min / default / max) | Full layout from |
| --- | --- | --- | --- | --- |
| Now playing | Stream section Now: the newest media | The Stream's media card (artwork, title, artist, transport) | 2x1 / 4x2 / 4x4 | 3x3 |
| Next event | Stream section Agenda: the first upcoming event (alarms excluded) | The Stream's event card (time span, title, place, countdown) | 2x1 / 4x2 / 4x3 | 3x2 |
| Top story | Stream section Feeds: the newest unread article | The Stream's article card (lead image, headline, feed, age) | 2x1 / 4x2 / 4x4 | 3x3 |

The catalogue, spans, layout rule and the choice of what to show are in `core/domain/.../launcher/glance/GlanceWidgets.kt`
(unit tested); the drawing is in `app/.../launcher/glance/`.

## Model

A glance widget is a `PoolWidget` whose provider is the reserved identity `com.riffle.app` / `glance.NowPlaying`,
`glance.NextEvent` or `glance.TopStory` and whose host id is always null. No new `PoolItem` variant, no new codec key:

- It is placed, moved, resized, removed, copied and garbage-collected by the existing pool operations and grid engines.
  `ArrangementAdapter` projects it to a `WidgetItem` with `glance` set (from the provider), `ArrangementIngest` writes
  the provider back for a widget the engines created, and `WidgetEngine.addGlanceWidgetToPage` places a new one with no
  host id (several of a kind are allowed, each with its own item id).
- It never touches `AppWidgetHost`: nothing is allocated, bound or configured, so there is nothing to release, delete
  or rebind. The dock and its panel hold platform widgets only; a request to put one there is answered with "Riffle
  widgets go on Home pages".
- A restore from backup keeps it as itself (it is not counted among the widgets that come back unbound).

### Stored data, both ways

The stored shape is exactly that of any widget item (`kind: "widget"`, `constraints`, `provider`, no `host`), so:

- A blob from an earlier build decodes unchanged here (its widgets are platform widgets: the provider names no
  Riffle class).
- A blob written by this build decodes on the previous build without error: it reads the item as a widget with a
  provider and no host id, an unbound placeholder drawn with its label ("Now playing") in a glass frame. That build
  never binds a placeholder by itself, so nothing is lost; saving there keeps the provider, and the widgets work again
  here. (A pool item *kind* a build does not know is dropped with its placements and reported as an issue, never a
  crash; the codec tests pin both.)
- A `glance.` class this build does not list reads as an ordinary unbound widget.

## Picker

The picker's list gets a "Riffle" group at the top with the three widgets, each with a small preview drawn from the same
glass and a description, resizable in both directions. They are `InstalledWidgetProvider`s built in the app, so the
existing drag, preview, accessible placement and "no room" behaviour apply unchanged; the request is recognised by its
reserved identity, never bound, and placed at the kind's default span (or smaller, never below its minimum).

## Data and lifecycle

Each widget observes its Stream section through the one `StreamContentProvider` (the Stream's shared sources: one
upstream per source, Hide rules and content levels already applied) only while it is composed (its Home page is showing)
and the activity is started, off the main thread. Content is never stored. States: loading, content, empty ("Nothing
playing", "No upcoming events", "No unread stories"), permission required (the Stream's "Allow access" button, compact),
off ("Turn on") and unavailable. A redacted item shows "Hidden content"; media keeps its transport, as in the Stream.

## Interaction

- Now playing: the transport buttons act through `StreamActionGateway`; tapping the title or any empty-state body opens
  the Stream on Now.
- Next event, Top story: tapping the card performs the content's own action (open the event, open the article) through
  the gateway, as the exception to "the body opens the Stream" allows; "Open Stream" in the widget's menu lands on
  Agenda or Feeds.
- Edit mode covers the widget with the usual gesture layer (drag, resize handles, remove). Outside it a long press
  still drags or opens the usual menu, and the release of that long press never also presses the control under it.

## Accessibility

Each widget is one labelled group ("Now playing: Midnight City by M83") with a single list of custom actions for its
controls (previous, play or pause, next, open, mark as read, Open Stream, Allow access). Transport buttons are 48 dp
targets from a width of three cells; below it a 48 dp "More actions" menu holds the same actions. Reduced transparency
and reduced motion come from `GlassSurface` and the Stream's own primitives.

## Not in this slice

A failed action shows no snackbar (the Stream's does); glance widgets in the dock or its panel; more widgets (a new one
is a new `GlanceWidgetKind` and a new class name, never a rename).
