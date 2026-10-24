# Privacy controls (S11, as built)

Slice S11 of #1363 (design section 6 of [`workspaces-configuration.md`](workspaces-configuration.md), Settings IA in
sections 2.1 and 2.6, owner decision Q8). Two controls, both additive and both defaulting to today's behaviour: a
**content level per source**, applied at the lens projection step, and a **screenshot and recents** switch that marks
the launcher window secure. They live on a new **Settings > Privacy** page (Permissions, privacy & backup group), and
the content level is also on each privacy-sensitive source's own page. No new permission, prompt, background work or
dependency.

`./gradlew verify` was **not** run locally (the sandbox cannot resolve the Android Gradle plugin); CI is its first run.
What was run here: all `:core:domain` tests (JVM, 1798 tests, 0 failures, including the 26 new ones), ktlint over the
domain sources and every changed app file, and detekt with the repo config over the changed directories. The app-side
code (Compose page, activity sync, wiring and its JVM and Robolectric tests) was only linted and reviewed by eye; it
has **never been compiled, run or screenshotted**.

## What it does

### Content level per source

`SourceContentLevel` (`core/domain/.../workspace/lens/SourceContentLevel.kt`):

| Level | Stored id | What any page may show of the source's items |
| --- | --- | --- |
| Show everything (`SHOW`, default) | `show` | Whatever the lens projects (today's behaviour). |
| Titles only (`HIDE_BODY`) | `hide_body` | Title, icon, time, group, actions, ext. No body, subtitle (a media artist, an event's location) or image. |
| App icons only (`HIDE_TEXT`) | `hide_text` | Icon, time and group only: the same fields a `SENSITIVE` item keeps. Drawn as "Hidden content" with its app icon; counts per app remain. |

- **One redaction point.** The level is a per-source ceiling (`SourceContentLevels`, carried by
  `LensEvaluationContext.contentLevels`) intersected with the lens's `project` inside `LensProjection`, which stays the
  only place an item is redacted. It can only remove fields, never widen a lens, and `SENSITIVE_ITEM_FIELDS` still win.
- **Sort and group keys cannot leak.** `LensSorting` (title sort, pinned-first) and `LensGrouping` (`ByExt`) read the
  item through the same view, so a hidden title does not order results and a hidden ext does not form groups.
- **Filters, limits and exclusions are unchanged**: the level redacts output only; the same items appear at every level.
- **Every lens shape and every source.** Flat, grouped (by group key, day, ext), sorted, limited; search results follow
  the Search source's level. The UI offers the control only for sources whose descriptor is `PRIVACY_SENSITIVE`
  (Notifications, Media, Calendar, Calendar feeds); the domain accepts any source id.
- **Applies at once.** `StoredSourceContentPrivacy` is also a `ContextChanges`; the lens provider watches it (merged with
  the exclusion repository through `mergedContextChanges`), so changing a level re-evaluates every observed page.

### Screenshots and recents

`PrivacySettings.protectScreenContent` in `LauncherSettings` (JSON key `privacy.protectScreenContent`, absent means off).
`protectsWindow(destination)` is true while the switch is on and the launcher is on any destination except Settings:
Home (and the workspace preview drawn on it), the drawer, search and notifications can all show items; Settings never
does (design 6.3), so it stays capturable. `startScreenContentProtectionSync` (activity, `CREATED` lifecycle) applies the
distinct decisions through `ScreenContentProtectionPort`; `WindowScreenContentProtectionPort` sets or clears
`FLAG_SECURE` and, on Android 13 and later, `setRecentsScreenshotEnabled(!protected)`. The switch text says plainly that
it also blocks the person's own screenshots of Home.

### Settings > Privacy

A main Settings page (`SettingsPage.PRIVACY`), between Permissions and Backup, found by search (privacy, screenshots,
screen recording, recents, secure, redact, content level, hide content, titles only, work profile) and by launcher
search. Sections, in TalkBack order:

1. **Screenshots and recents**: the switch (a Material 3 switch row; the whole row toggles it).
2. **How much pages show**: one radio group per privacy-sensitive source (heading, then three 48 dp rows, each spoken as
   "Notifications, Titles only. The title and icon; ..." with "Selected" as its state). With Workspaces (preview) off
   this section only says where the levels are, because they apply to preview pages.
3. **Hidden items** (preview on only): links to Hidden items and rules and to Sources.
4. **Work and private profiles**: what paused, locked and unavailable profiles show, and that nothing a page shows is
   stored or backed up.

The Notifications, Media, Calendar and Calendar feeds source pages show the same radio group (same state, same
controller) where the "Coming later" note used to be. Nothing animates beyond the standard radio and switch
indicators, so reduced motion needs no special case.

## Decisions

- **Levels are device-local and not in Riffle's backup document**, consistent with source enablement as built (the
  disabled-sources set is device-local too, although design 2.4 once proposed putting it in the workspace blob). They
  are stored as `id=level` entries under `content_levels` in the same `riffle_workspace_sources` preferences file as the
  disabled sources (source ids and level names only). Android's own Auto Backup copies that file like any other
  preferences file; a restored level can only hide more, never less.
- **The screenshot switch is part of `LauncherSettings`** (design 6.2 item 3), so it travels with the settings section of
  a backup. Restoring it on another device only turns on a protection.
- **Default `SHOW` for every source**, so nothing changes until the person picks a level.
- **`HIDE_BODY` keeps ext.** Today's ext values are structure (notification app, category and priority; calendar end and
  all-day; RSS author), not message content. `HIDE_TEXT` drops ext with everything else.
- **Settings is not protected**: it never shows items, and a person can still capture it to report a problem.
- **Privacy is a main page, not a Developer page**: the screenshot switch works for the standard home too. The level
  controls and links appear only with the preview on, since every page they control is a preview page today.
- No locked-device rule (Q8): no code, setting or test for it.

## Tests

Domain (`core/domain`, JVM): `SourceContentLevelTest` (stored ids and ceilings; `SHOW` equals today's result; each level's
fields; a **leak sentinel** over every level x grouping (none, group key, day, ext) x sort (every field, direction and
pinned-first) x limit, asserting no hidden marker text appears anywhere in the result; never widens a lens;
`SENSITIVE` still wins at `SHOW`; other sources untouched; title sort and pinned-first cannot reveal hidden data; `ByExt`
cannot group on hidden ext; per-app counts and labels kept; search results follow the Search level; filters and limits
see the same items). `SourceContentPrivacyTest` (defaults, persistence and change notification, restart, unknown or
blank stored values, failing store, codec round trip, the planner's source list, `mergedContextChanges`, and the
screen-protection policy per destination).

App (JVM): `ContentLevelProviderTest` (the real lens provider applies a level at once when it changes; without privacy
nothing changes; stored entries round trip and malformed ones drop), `SourceContentLevelsControllerTest`,
`LauncherScreenProtectionTest` (policy per destination; the sync applies only changed decisions), `PrivacySettingsTextTest`,
`LauncherSettingsJsonCodecTest` and `LauncherSettingsStateReducerTest` (the switch round-trips, defaults off, persists),
`LauncherActionDomainTest` (route table row), `SettingsPagesTest` (the entry, its group, search aliases, launcher search
id, Back target), `SourceDetailTextTest`.

Screenshots (Roborazzi, fakes only, no items): `PrivacySettingsScreenshotTest` (compact, dark with the switch on, large
font, unfolded, preview off; selection, taps, spoken labels, links) and the Notifications case of
`SourceDetailScreenshotTest` now draws and taps the real control.

## Not done

- Compile and run on CI and a device (see top).
- An instrumented test that a screenshot of a protected window is blank (needs `deviceVerify`; `FLAG_SECURE` cannot be
  observed under Robolectric).
- Per-channel or per-app levels (one level per source only).
- The standard (non-workspace) home's notification cards and dock notification cards do not read the content level;
  they keep their existing rules (hide rules, profile visibility). The screenshot switch does cover them.
- Unfolded list-detail layout for the Privacy page (it is a single column capped like other settings pages).

## Checklist for the owner (on the device)

- [ ] Settings shows **Privacy** under Permissions, privacy & backup; searching "screenshots" or "content level" finds it.
- [ ] Switch off (default): a screenshot of Home shows Home. Turn it on: a screenshot of Home, the drawer and search is
      blank or refused, a screen recording shows black, and the recents thumbnail of the launcher is blank. Open
      Settings: a screenshot of Settings works. Turn it off again: screenshots of Home work at once.
- [ ] Preview off: the Privacy page shows the switch, the note about content levels and the profiles text; no links.
- [ ] Preview on: the Privacy page lists Notifications, Media, Calendar (and Calendar feeds if present), each on "Show
      everything". Pick "Titles only" for Notifications: notification pages and widgets lose their message text at once
      but keep titles and icons. Pick "App icons only": items read "Hidden content" with the app icon; grouped-by-app
      pages keep their app headings and counts; Dismiss buttons disappear; tapping an item still opens the app.
- [ ] Sort a notification lens by title with "App icons only": the order no longer follows the hidden titles.
- [ ] Media "Titles only": the track title shows, the artist and artwork do not. Calendar "Titles only": no location.
- [ ] The Notifications source page shows the same choice, in sync with the Privacy page; the "Coming later" text is gone.
- [ ] Restart the launcher: the levels and the switch are kept. Export a backup: it holds the switch, not the levels.
- [ ] TalkBack: each option reads the source, the option and what it shows, then "Selected"; headings are announced; the
      switch reads its state. 200% font: nothing is cut off.
