# Places and Views: device checklist

This replaces the old workspaces dogfood checklist. [ADR 0003](../architecture/adr/0003-places-and-views.md) replaced the
workspace model with four Places (Home, Dock, Library, Stream) and one glass material, and its slices were built and
tested on the JVM, in screenshot tests and in emulator-backed CI. **Nothing in it has been run on a physical device
except what the owner reported** (glass over the wallpaper, Dock size, plain labels). Treat the first session as the
first real validation. Each item says what to do, the expected result, and what a failure looks like.

**Before you start: export a backup** (Settings > Backup > Export) and keep the file somewhere safe. Install the newest
alpha APK over the previous one (signed with the project key, so data is kept; see
[`signing.md`](../release/signing.md)). The debug build has the id suffix `.debug` and is a separate app.

Report problems as a GitHub issue with the alpha version (Settings > About), the device and its fold or window state,
the steps and a screen recording. Do not paste notification text, calendar titles or task titles.

## Glass and the wallpaper

Use a busy, bright wallpaper and then a dark one.

- [ ] **Dock at the default (72%) shows the wallpaper.** Expected: the wallpaper is visible through the Dock, tinted, text
      and icons readable. Failure: an opaque near-black bar (the bug fixed in #1450 returning).
- [ ] **Dock background 0% is clear, 100% is opaque** (Settings > Dock). Expected: exactly that, with the edge lights still drawn
      at 0%. Failure: 0% still looks tinted, or 100% still shows the wallpaper.
- [ ] **Search pill, folders (open one), page indicator, widget frames** are translucent over the wallpaper, not black.
- [ ] **Text over a bright wallpaper** on the Dock, folders and Settings sections stays readable.
- [ ] **Flat body, lit edge** (the owner's dislike of the old bevel). Look at the spine, a page header, a Stream
      card (Stack and List, and a tall one such as now playing), the Dock and the search pill. Expected: one even
      tint with no lighter band, trapezoid, horizontal seam or dark corner wedges; a thin bright edge along the top
      that fades down the sides, following the rounded corners exactly; a faint line just inside the top edge that
      fades out toward both ends. A tall card has the same edge as a pill. Failure: any bevel, or an edge that is
      thicker on tall surfaces.
- [ ] **Library sheet and Stream cards** are a flat Thick or Regular tint over what is behind them with the same edge
      lights. Failure: a visible tint seam.
- [ ] **Light, see-through glass.** Over a dark wallpaper the Dock, a Stream card and the search pill are a light
      blue-white veil you can see the wallpaper through (Thin about 12%, Regular about 16%); menus and the Library sheet
      are a darker, heavier tint. Check body text over a bright or busy wallpaper on a card: if it is hard to read, say so.
- [ ] **Contextual Dock row** (open the Stream). Expected: pinned icons, live apps and tiles are one row of equal
      48 dp items, at least six across a 360 dp phone; opening a page whose icon is off-screen scrolls the row so a whole
      item sits at the left edge, never a clipped sliver; the soft fade shows only at an edge with more beyond it.
- [ ] **Tint as light.** A card from a coloured app shows that colour as a thin glow along its edges, not as a
      coloured body; text stays readable.
- [ ] **Reduced transparency** (Settings > Appearance): every glass surface becomes an opaque tint, no wallpaper
      showing, edge lights faint. Turn it off again and the glass returns.
- [ ] **Spotlight follows the rounded Dock**: the colour glow under pinned icons hugs the corners and moves when the
      icons change and stops (no idle animation, check with a profiler or battery drain overnight if in doubt).
      Folders and the Library search results panel also glow from their icons.
- [ ] **Reduced motion** (system "Remove animations" or Settings > Motion): the spotlight snaps and every page,
      split and Library change is a cut or a fade, never a slide.

## Dock

- [ ] **Size.** Pinned icons are the same size as Home icons (default 44 dp) and the bar is one icon plus a little
      chrome tall, not a thin strip. Change Icon size (32 to 56): the pins follow exactly. Failure: pins smaller than
      Home icons or a short bar (the regression fixed in #1452).
- [ ] **Six pins plus the dynamic side.** Pin six or more apps. Expected: the run scrolls at full icon size with a fade
      at the edge that has more (none at an edge with nothing beyond it); at least three pins stay visible beside the
      dynamic side. Failure: pins shrink, or the dynamic side crowds the pins out.
- [ ] **Minimum gap.** On the narrowest width you have, icons never touch: the gap stops at 8 dp and the run scrolls.
- [ ] **Dynamic side is compact.** With no live apps and nothing playing there is no dynamic side (the pinned icons
      only; with no pins either, the Dock keeps a body one icon long); with a few notifications and media playing it
      shows live apps and now playing, each a 48 dp tile, and scrolls if it has more than the live-app limit (Settings >
      Dock > Live apps).
- [ ] **Edge.** Move the Dock to each of the four edges (Settings > Dock). Layout, fades and the pull below work
      on each; on a side edge the run scrolls vertically.
- [ ] **Pull opens the Library, on all four edges.** Pull away from the edge starting anywhere on the Dock (pinned side,
      live apps, the gap): the Library follows the finger. Release early: it springs back. Pull again while it settles:
      it catches. Pulling the same way with the Stream showing opens the Library over it.
- [ ] **Press toggles Home and the Stream, on all four edges.** Press the Dock down (toward its edge) about a thumb's
      width and lift: the Stream opens (a bottom Dock pressed down, a top Dock up, a left Dock left, a right Dock right),
      on the page it was last left on. Press again: Home returns. A press that stops short (under about 28 dp), or that is
      drawn back before the lift, does nothing. Failure: it opens while still pressed, it needs a long drag, or it launches
      a pinned app or live app it started on.
- [ ] **Taps (Home mode).** Pinned app launches. A live app launches the app. Now playing launches the player.
- [ ] **Press that starts on a live app.** Press down from a live app's icon and lift: the Stream opens on that app's
      page. From now playing: on Now. From anywhere else: where it opens today. A press that stops short leaves nothing
      behind (the next plain open is unaffected). Check a side Dock too.
- [ ] **Contextual mode.** With the Stream open the Dock shows the same icons in the same order, then a divider and a tile
      for each page that is not an app (Now unless it is playing, Recents, Today, Feeds). The open page's icon is
      ringed. With many pins and apps the row scrolls sideways with a fling (vertically on a side Dock), and the ringed icon is
      always in view, also after a swipe of the Stream. A tap on a tile or a live app switches the page and never launches.
      Failure: tiles that do not scroll into view, a ring that lags a swipe by a page, a tile under 48 dp.
- [ ] **Page options on a tile.** Long-press a tile (or a live app) in the contextual mode: Show as the other view, Move
      earlier and later, Hide section, Show for each hidden section. Each applies at once; Hide and Show as offer Undo.
- [ ] **Live app long-press.** In the Home mode the menu has Open in Stream, the app's shortcuts, App info and Hide app.
      With TalkBack: "Open <app>" and "Show <app> in Stream", and the Dock's actions "Show live pages" and "Back to Home".
- [ ] **Long-press and drag.** Long-press a pin for its menu; drag to reorder; drag out to Home.

## Home

- [ ] **Plain labels.** Labels are plain white text with a soft shadow, no pill, readable over the wallpaper. Settings >
      Home > Label background turns a pill on at your stored opacity and off again.
- [ ] **Edit, folders, widgets, pages** behave as the standard launcher: long-press to edit, drag, make a folder, add
      and resize a widget, add and remove pages.
- [ ] **Gestures.** Swipe down opens the Library with the keyboard up (Search); two-finger swipe up opens notifications;
      the Stream and Library actions can be bound in Settings > Gestures.

## Library

- [ ] **Pull tracking** from the Dock follows the finger, opens a Thick sheet with the Dock still visible and tappable
      under it, and closes with a pull down on its header, Back, Home or launching an app.
- [ ] **Three views.** Categories (the default), A-Z with the alphabet scrubber, Grid. Long-press the header (or its
      options button) for Show as; it redraws at once and Undo restores. Settings > Library > Show as matches.
- [ ] **Search with the keyboard.** The field is at the top; typing replaces the view with results (apps, shortcuts,
      settings); Enter launches the top hit; clearing restores the view; the keyboard does not cover the results.
- [ ] **Hidden apps after upgrade.** Apps you had hidden, or hidden by an exclusion rule, are still hidden; none is
      newly visible. Unhide one in Settings > Library > Hidden apps and restart: it stays unhidden.
- [ ] **Long-press an app**: Add to Home, Pin to Dock, shortcuts, App info, Hide, Uninstall. (There is no drag out to
      Home.)

## Stream

- [ ] **Sideways page swipe versus vertical stack scroll.** On a page, a sideways swipe changes page and the Dock's ring
      follows; a vertical swipe moves the stack. Failure: either one fighting the other, or a swipe that moves both.
- [ ] **Fling distance through a stack.** A hard fling moves a believable number of cards and settles on one (magnet).
      Failure: it overshoots wildly or stops dead.
- [ ] **Pages in the Dock's order, with many apps.** With ten or more apps with notifications the pager walks the Dock
      left to right (live apps newest first, then now playing, then the built-in pages); the pages of apps past the Dock's
      slots follow the live ones and have no icon. There is no spine or page header on any page.
- [ ] **A notification arriving while on a page** never moves the page you are on; a page that vanishes hands over to its
      neighbour. Reopening the Stream returns to the page you left.
- [ ] **A press on a live app lands on the app's page.** Press down from a live app in the Dock and lift: the Stream opens
      on that app's page. If its notifications were cleared meanwhile it opens on the first Notifications page.
- [ ] **Every page leaves Loading.** Open the Stream: each of the six pages shows its content, or its honest state (no
      access, nothing to show, not connected) within a few seconds; none stays on "Loading", and one that could not
      be read says so with Retry.
- [ ] **No flash on opening.** Open the Stream by the Dock press, by a tile or live app and with reduced motion on: Home fades as the
      Stream comes in, it never reappears at full strength over the Stream, and under reduced motion it is a cut.
- [ ] **Show as and Undo.** Long-press the page's tile in the Dock: Stack or List applies to every page of
      that section; Undo restores. Move and Hide section work. Settings > Stream shows the same values and edits live.
- [ ] **Media.** With a player running: artwork, title and artist, play or pause, next, previous, scrub, the queue
      (when the app exposes one), tapping opens the player. Pause from the card and check the app pauses. Failure:
      stale state, buttons that do nothing, a scrubber that jumps back.
- [ ] **Notifications.** Reply from an expanded conversation, snooze, dismiss, Hide. Check the app's notification is
      actually dismissed or replied to. A sensitive notification shows hidden content and offers no reply or history.
- [ ] **Today (slice 4b).** The Today page opens on the next event with a countdown that counts down by itself ("in 25 min",
      "now", "ends in 10 min"); the "To do today" card is behind it (in front when there are no events), ticking a task
      completes it (it is put back if Todoist refuses), the add field and refresh are on that card; later events and
      a Tomorrow card follow. Without Todoist a "Todoist is not connected" card follows the events. Show as List draws the
      agenda, then the tasks under "Tasks". A layout from before the change (Agenda and Tasks sections) shows one Today.
- [ ] **Pinned apps' shortcuts (slice 4b).** In the Dock's contextual mode every pinned app has a page of its shortcuts
      (long-press the app in the Home mode to compare), swipe the cards sideways through them, tap a card to launch the
      shortcut; tap a pinned icon to switch to its page and see it ringed; an app with no shortcuts is one "Open <app>"
      card; without the Home role a note says shortcuts need it. Hold-press the Dock and slide over a pinned icon: the
      label names it, lifting opens its page.
- [ ] **Agenda window.** Today and the next seven days show, by day; calendar feeds (Settings > Stream > Agenda) appear
      when included. Events past that window do not.
- [ ] **Images.** Notification pictures, article lead images and app icons load without jank.
- [ ] **Honest states.** Without notification, calendar or usage access each section says "Allow access to show this"
      with a working button; Tasks says "Connect Todoist" until a token is saved.
- [ ] **Recents and Feeds.** Recents shows recent then "Often used" apps as a pile of cards (icon, name, last used); Feeds has one page per feed with
      unread items; mark read works.

## Tasks (needs a real Todoist token)

The Todoist API paths, field names, a task's web address and app link, and the due-date formats are only partly
verified against the reference. A mismatch is fixed in `TodoistApiMapping.kt`.

- [ ] **Connect.** Paste a personal API token in Settings > Stream > Tasks. Expected: tasks due today and overdue
      appear (overdue first). A wrong token says it was rejected and offers to replace it.
- [ ] **Complete** a task: it ticks at once and is done in Todoist; with airplane mode on it comes back with a message.
- [ ] **Open** a task: the Todoist app if installed, otherwise the web.
- [ ] **Quick-add** a task: it appears, due today, and exists in Todoist.
- [ ] **Offline.** Airplane mode, refresh: the last tasks stay, marked stale. Remove the token: Connect shows.
- [ ] **Recurring tasks** move to their next date. **Tasks with no due date** are not shown (by design).
- [ ] **Token privacy.** The token is not in an exported backup file, and Riffle makes no Todoist request before you
      save one.

## Unfolded and tablet

- [ ] **Split.** Home and the Stream share the screen with the divider; drag it; Home only and Stream only are reachable.
      The Stream takes the side nearer the Dock's dynamic side for a bottom or top Dock.
- [ ] **Hinge snap.** Over a vertical hinge the divider snaps to it and no pane crosses it. **Tabletop** stacks the
      panes above and below it.
- [ ] **The Dock press** opens the last-used Stream state (the split or the Stream only) and returns to Home only; the
      divider's actions ("Home only", "Split", "Stream only") reach all three.
- [ ] **Pages two-up.** With the Stream on the whole wide screen (840 dp or more), two pages show side by side and the
      Dock's ring follows the first.
- [ ] **Stage rail and transition.** Show as Stage (Settings > Stream; the Stage's own bar has the menu back to Pages): the rail lists the
      other pages as live thumbnails, a tap brings one to the centre with the transition and sends the previous one back to
      the rail. In a narrow pane (the Stream beside Home) the rail shows icons only. Under reduced motion it switches
      without the transition.
- [ ] **Fold and unfold** with the Stream open: the selected page and the layout survive.
- [ ] **Rotation** keeps the page, the Library state and the Dock edge sensible, and nothing draws under the system bars
      or the cutout.

## Glance widgets

- [ ] **Add from the picker.** The "Riffle" group at the top lists Now playing, Next event and Top story; drag or "Add to
      Home" each.
- [ ] **Resize** in edit mode: a small widget shows the compact layout with a "More actions" menu; from three cells wide
      the transport buttons show.
- [ ] **Long-press and drag on a live widget** drags it or opens the usual menu, and releasing the long-press never also
      presses a control under the finger.
- [ ] **Behaviour.** Now playing controls act on the player; the next event and top story open in their own apps;
      "Open Stream" lands on the right section; empty states and "Allow access" are honest; a restored backup brings the
      widgets back.

## Settings

- [ ] **Search scroll-to-setting.** Search for a setting by name or keyword (try "label background"): its page opens
      scrolled to it, and the row is visible rather than hidden under the app bar.
- [ ] **Back** always goes to the parent page.
- [ ] **Stream page edits live.** Turn a section off, reorder it, change Show as: the Stream behind reflects it at once.
- [ ] **Privacy and access** holds every permission and the content level of each source, including Tasks.

## Data safety

**Export a backup first.**

- [ ] **Upgrade over real data.** Install over a build you used before this work. After a restart Home pages, the Dock
      pins, folders and widgets are unchanged, and the Dock size and edge you had are kept.
- [ ] **Old backup import.** Import a backup written by an earlier build: Home, Dock, settings, hidden apps and
      exclusions are restored. Export again and re-import: nothing is lost.
- [ ] **A layout saved in the old Cards mode** shows its placed items on the standard Home; a saved notification-cards
      page says the notifications are in the Stream and opens it.
- [ ] **Backup contents.** The file has calendar feeds (links can carry a private key: keep it private) and no
      notification, calendar, task or article text, and no Todoist token.
- [ ] **Onboarding.** A fresh install shows four steps (Home, Dock, Library, Stream) with permissions asked only in the
      Stream step, each skippable; an existing user is shown nothing; "Set up Riffle again" re-runs it and changes
      nothing you placed.

## Accessibility

- [ ] **TalkBack on the Dock**: the actions "Show live pages" (or "Back to Home") and "Open Library" exist and work;
      entries are spoken ("Messages, 3 new", "Now playing: title"); in the contextual mode each tile is spoken by its
      name ("Agenda"), says "Show Agenda" on a double tap, offers its page options on a long double tap, and the open
      page's tile is announced as selected.
- [ ] **Pager actions**: "Previous page" and "Next page" actions work; the focused card exposes its own actions.
- [ ] **Divider actions**: "Home only", "Split" and "Stream only" work from the divider's action list; the arrow keys
      move it with a keyboard.
- [ ] **Library header** offers "Close Library" and each Show as view as actions.
- [ ] **Targets** are at least 48 dp and the largest system font size does not clip Dock, Stream headers or Settings.
- [ ] **Keyboard**: Ctrl + the pull arrow opens the Stream or returns Home; Ctrl + Shift + the arrow opens the Library.
