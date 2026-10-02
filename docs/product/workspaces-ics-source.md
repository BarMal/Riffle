# Workspaces: ICS calendar feed source

Status: built for the Workspaces (preview), user-triggered refresh only. Issue #1409 (part of #1363; follows the
recurrence spike #1395, see [`workspaces-ics-recurrence.md`](workspaces-ics-recurrence.md)). Design source:
[`workspaces-external-sources.md`](workspaces-external-sources.md) section 15.3 and
[`workspaces-configuration.md`](workspaces-configuration.md) section 15.

Evidence labels: **MEASURED** was run (JVM scratch project, Maven Central only, or CI); **NOT DONE** needs a device.
`./gradlew verify` was **not** run locally (the sandbox cannot resolve the Android Gradle plugin); CI is its first run.

## What it is

A read-only calendar source for subscribed `https` `.ics` links ("Calendar feeds (ICS)"). Events appear in
Workspaces lenses through the source id `ics`, like the device calendar source, but need no calendar permission.
With no feeds configured (the default) the source is an empty `Ready` list and nothing is fetched or stored.
Nothing runs in the background: the network is touched only when the user taps **Refresh**.

## As built

### Domain (`core/domain`, package `...workspace.sources.ics`, `java.time` only)

| Piece | Role |
| --- | --- |
| `IcsEngine` / `DefaultIcsEngine` | `parse(text)` and `expand(events, window)`. The library sits behind `RecurrenceExpander`, the seam from the spike. Without an expander (or when it refuses a rule) a recurring event shows only its own first instance: degraded, never wrong. |
| `IcsParser` (+ `IcsContentLines`, `IcsValues`, `IcsEventBuilder`) | A bounded in-repo RFC 5545 reader for untrusted text: unfolding, `VEVENT`, `RRULE`, `RDATE`, `EXDATE`, `RECURRENCE-ID` overrides (moved or cancelled, with their own title, location and length), `TZID` (IANA names, prefixed names such as `/mozilla.org/.../Europe/Berlin`, about 30 common Windows names, `X-WR-TIMEZONE`), all-day values with an exclusive `DTEND`, `CLASS` private or confidential, `STATUS:CANCELLED`. An unknown `TZID` reads as floating (device zone) and is counted. Other components, `VALARM` and `VTIMEZONE` definitions are ignored. It never throws and puts no feed text in a result. |
| `IcsLimits` | 4,000,000 input characters, 8,192 per logical line, 5,000 events, 256 characters of text, 1,024 of rule, 2,000 `EXDATE` and 500 `RDATE` values and 1,000 overrides per event, component depth 8, event length capped at 366 days. |
| `IcsFeed`, `IcsFeedSettings`, `IcsFeedUrls` | Feed settings model (id, name, url, enabled; at most 10 feeds). URLs are validated like RSS (`FeedUrl`: https only, no credentials, default port) plus `FeedHostSafety`; `webcal://` is read as `https://`. `IcsFeed.host` is the only part of a URL that may be displayed and `toString()` redacts the URL. |
| `IcsEventPruner` | What a refresh keeps: recurring events, and single events that ended no more than a day ago, soonest first, at most 1,000 per feed. |
| `IcsSourceReader`, `IcsItemMapper` | Expand the cached events over the next 14 days (60 maximum) and map to items, soonest first, at most 50. Items mirror `CalendarItemMapper`: the same `calendar.end` and `calendar.all_day` extras (plus `ics.recurring`), subtitle is the location (else the feed name), grouped by feed. **Privacy mirrors the device calendar: an event the feed marks `CLASS:PRIVATE` or `CONFIDENTIAL` is `SENSITIVE` (text stripped at projection); others are `VISIBLE`. The source itself declares `PRIVACY_SENSITIVE`.** Items have no tap target: a read-only feed has nothing to open. |
| `SourceIds.ICS` | `"ics"`, appended to `BUILT_IN` (a stored contract: never rename). |

### Library

`core/recurrence-ical4j` (`Ical4jRecurrenceExpander`, `Recur` only) expands recurrence rules. The ical4j **parser,
`TimeZoneRegistry` and `VTIMEZONE` handling are not used** (see decisions). `:app` now depends on the module.

### App

| Piece | Role |
| --- | --- |
| `CachedIcsFeedRepository` (+ `IcsStorePort`, `DataStoreIcsStore`) | Synchronous view over a suspend store, loaded once when the preview runtime is created; edits before the load are refused; a failed read disables writes so an existing list is never overwritten. Own DataStore file `riffle_ics_feeds`, two keys (feed list, parsed-event cache) so a corrupt cache can never cost the feed list. |
| `IcsJsonCodecs`, `IcsEventJson` | Versioned JSON. Decoding is defensive: unknown version reads as nothing stored, bad entries are dropped, every URL is revalidated, bounds re-applied. The cache document has no URL. |
| `IcsRefreshCoordinator` | Same rules as RSS refresh by reusing `FeedRefreshPlanner`/`FeedRefreshState`: 15 s minimum interval, backoff, conditional requests (validators in memory, honoured only while the cache holds the content), one refresh at a time, typed failures only. Fetch via the existing `AndroidFeedTransport` (https only, redirects re-validated, 5 redirects, timeouts) with a **2 MiB** body cap, on a single background thread. The body is parsed, pruned and dropped; an empty parse never wipes existing content. |
| `IcsFeedsController`, `SettingsIcsFeedsPage` | Settings > Calendar feeds (ICS): refresh row, feed rows (name, host, switch, remove, status), add form (name, link). Removing or disabling a feed clears its cached events. |
| Sources page | A "Calendar feeds (ICS)" section with the same Refresh row and a link to the page, shown only when the runtime provides the controller. The source itself is a normal Sources row (enable or disable, status chip) through `EnablementSourceRegistry`. |
| `icsSource` | Reads the cache only (never the network); `LIVE` through the repository's change signal; `Loading` until the stores have loaded. |

Preview only: the Developer page and the Sources section exist only while Workspaces (preview) is on, and the
runtime (and so the DataStore load) is created only then. With the preview off nothing about the app differs
except the APK contents.

## Privacy and backup

* Feed URLs can carry secret tokens. They are never logged, never in an error or status text, never shown (host
  only), and never in a backup: the in-app JSON backup has no field for them (`IcsBackupExclusionTest`) and
  `riffle_ics_feeds.preferences_pb` is excluded from Auto Backup and device transfer in `backup_rules.xml` and
  `data_extraction_rules.xml`. The sentence "backup agent already excludes feeds without tokens" in the task brief
  describes RSS; ICS feeds are excluded wholesale.
* `AndroidFeedTransport` sends the RSS `Accept` header; calendar hosts ignore it in practice (**NOT DONE**: confirm
  against Google, Outlook and Nextcloud feeds on a device).
* Only `INTERNET`, already declared. No permission prompt.

## Decisions

1. **In-repo bounded parser, ical4j only for `Recur`.** ical4j's `CalendarBuilder` needs its `TimeZoneRegistry`
   (about 2.2 MB of bundled zone files), pulls Groovy and `java.beans` references, parses leniently with unbounded
   work, and its exceptions can echo feed text. A 350-line reader with hard bounds is fully testable on the JVM here
   and keeps the library confined to the engine that the spike measured. **This triggers the flip condition recorded
   in `workspaces-ics-recurrence.md` section 3** (an in-repo parser means lib-recur would be about a tenth of the
   size). It is kept on ical4j because the brief and decision N8 name it, the module and corpus exist, and the size
   gate below is the check; if the delta is unacceptable the swap is one class plus one dependency line.
2. **Privacy mirrors the device calendar** (per-event `CLASS`), not "everything sensitive". A private-by-default
   source would hide every title on a locked screen even for public holiday calendars; the source still declares
   `PRIVACY_SENSITIVE` so lenses treat it like Calendar.
3. **Cache parsed events, expand on read.** Caching expanded occurrences would go stale as the window slides;
   events are small and expansion over 14 days is cheap. Raw bodies are never stored.
4. **Reuse RSS refresh policy and transport unchanged** (no edits to RSS files, which other work is changing).
5. **Two DataStore keys in one file**, excluded from backup wholesale.
6. **No `Item` tap target.** There is nothing to open for a read-only feed; the actions list is empty.
7. **Hide-this wording** for `ics` items uses the generic defaults; the contextual Hide actions are owned by other
   work in flight and were not edited.

## Size, R8 and packaging (preconditions of `workspaces-ics-recurrence.md` section 5)

MEASURED by the non-publishing Minified Release Check on PR #1414 (run 36989803449, same commit for both builds):
unminified 29,238,738 bytes (27.88 MiB, dex 27.16 MiB over 3 files), R8-minified 4,907,657 bytes (4.68 MiB, dex
4.26 MiB), emulator smoke passed. The baseline is the same check's last published figure (PR #1407: unminified
25.66 MiB, minified 4.38 MiB), so the delta is **about +2.2 MiB unminified and +0.3 MiB minified**: under the 3 MiB
stop condition and well under the 32 MiB cap (4.1 MiB of headroom). Caveat: that baseline is from an earlier
commit, other merges since may have moved it either way; a `workflow_dispatch` run on `main` (run 36990674651) was
started to get a same-day figure, and its minified artifact was 6,841,246 bytes against 7,122,012 for the PR
(about +0.28 MB zipped), consistent with the minified delta above.

| Precondition | Status |
| --- | --- |
| APK size delta | Measured, about +2.2 MiB unminified, +0.3 MiB minified (above). Gate passed. |
| D8/R8 `-dontwarn` needs | `-dontwarn groovy.**` and `-dontwarn java.beans.Transient` in `app/proguard-rules.pro`, with a justification comment. R8 first failed on two more missing classes, `java.time.zone.ZoneRulesProvider` (the Android SDK does not expose it; ical4j's `DefaultZoneRulesProvider` extends it) and `org.joda.convert.*` (named by threeten-extra), so `-dontwarn java.time.zone.ZoneRulesProvider` and `-dontwarn org.joda.convert.**` were added; the second run succeeded and the emulator smoke passed. |
| `ZoneRulesProvider` service entry | Excluded from the APK with `packaging.resources.excludes` together with `zoneinfo/**`, `zoneinfo-global/**` and `META-INF/groovy/**` (none is read by `Recur`). Android's `ZoneRulesProvider` is the platform one and does not load service entries from the APK. |
| On-device corpus run | **NOT DONE** (needs a device or an instrumented run): see the owner checklist. |

## Tests (all run in CI; domain, library and Compose-free app logic also run in a JVM scratch project)

* Domain: `IcsParserTest` (multi-`VEVENT`, `TZID` forms, all-day, `RRULE` + `EXDATE` + `RDATE` + override,
  cancelled, orphan override, folding, escapes, quoting, nested components), `IcsParserHostileInputTest` (empty,
  oversize input and line, folded overrun, too many events and dates, malformed values and durations, deep nesting,
  seeded random and mutated feeds), `IcsEngineTest` (fake rule generator), `IcsEventPrunerTest`, `IcsItemMapperTest`,
  `IcsSourceReaderTest`, `IcsFeedSettingsTest`, `SourceIdsTest`.
* Library module: `Ical4jIcsEngineTest` runs a feed text through the parser and the real expander across the March
  clock change (weekly rule, `EXDATE`, moved override, all-day span); the 52-case corpus is unchanged.
* App (JVM): `IcsJsonCodecsTest`, `CachedIcsFeedRepositoryTest`, `IcsRefreshCoordinatorTest`,
  `IcsFeedsControllerTest`, `IcsSourceTest` (fakes), `IcsBackupExclusionTest`, `SettingsDeveloperPagesTest`.
* Roborazzi: `IcsFeedsSettingsScreenshotTest` renders only static, non-focused states (no text field, no dialog:
  a focused field's cursor blink hangs the run); the add form is covered by the controller test.

## Not done

* **Background or periodic refresh**, refresh-one-feed from the UI, and persisting validators or errors across restarts.
* **Real on-device checks**: corpus run, behaviour against real Google, Outlook and Nextcloud feeds, `Accept` header
  acceptance, TalkBack on the new page, large font and the unfolded two-pane layout.
* **`VTIMEZONE` definitions** are not read: a custom `TZID` that is not an IANA or common Windows name reads as
  floating. `RANGE=THISANDFUTURE` overrides are dropped. `RDATE;VALUE=PERIOD` uses the period start only.
  Sub-daily `RRULE` frequencies are refused by the expander (the event then shows its first instance only).
* **Exclusions wording** for `ics` (Hide this event) and the Hidden items text-rule source list: owned by other work.
* **DNS-level private address checks** (same limit as RSS), JSON Feed, and an in-stage pull-to-refresh.
* **Auto Backup wiring is verified by file content only**, not by a restore on a device.
* Calendar sharing links that need cookies or OAuth are out of scope (token-in-URL feeds only).

## Owner device checklist

1. Settings > Workspaces (preview) on, then Sources: a "Calendar feeds (ICS)" section and a "Calendar feeds" source
   row appear (Ready, empty). With the preview off neither exists.
2. Open Calendar feeds (ICS), add a real private ICS link (for example the secret address of a Google calendar).
   Only the host is shown; the field clears; no link is visible anywhere afterwards.
3. Refresh: row shows "Refreshing...", then "Updated 1 feed"; the feed row shows "Updated ...". An immediate second
   Refresh says "Already up to date" and sends no request.
4. A workspace lens over the Calendar feeds source shows the next 14 days: recurring events (weekly, with an
   exception and a moved instance), all-day events and a private event. Compare with the calendar app.
5. Airplane mode, wait 15 s, Refresh: "Refresh failed" and the old events stay.
6. Disable the feed: its events disappear; re-enable and refresh: they return. Remove the feed: nothing remains.
7. Sources page: switch the source off (status Off) and on; tap Refresh from the Sources page section.
8. Back up (Settings > Backup export) and inspect the file: no feed link. Reinstall or restore: the feed list is empty.
9. Force-stop the app and reopen: feeds and cached events are still there; nothing was fetched until Refresh.
10. TalkBack on the page (the refresh result is announced politely), a large font size, and a fold or tablet width.
11. Run the minified check artifact on the device and open the page, refresh, and view a recurring event (this
    exercises the R8 rules on the `Recur` path).
