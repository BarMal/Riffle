# RSS feed refresh (user-triggered and opt-in background)

Issues #1374 (user-triggered) and #1393 (opt-in background). Companion to [rss-feed-behavior.md](rss-feed-behavior.md) and
[ADR 0001](../architecture/adr/0001-rss-feed-stages.md).

## What is built

- **Transport** (`AndroidFeedTransport`, pre-existing): `HttpURLConnection`, https only, redirects
  re-validated (https only, 5 max), 1 MiB response cap, 10 s connect / 15 s read timeouts, conditional
  requests (`If-None-Match`, `If-Modified-Since`). No new HTTP dependency.
- **Parser / normalizer** (`AndroidFeedParser`, `FeedItemNormalizer`, pre-existing): RSS 2.0 and Atom,
  capped input and output item counts and field lengths. Markup is reduced to plain text at render time
  (`stripHtmlMarkup`). Images stay lazy and are never fetched during refresh.
- **Policy** (core/domain, pure): `FeedRefreshPlanner` decides which feeds a refresh fetches. `FeedHostSafety`
  rejects non-public host literals.
- **Coordinator** (`FeedRefreshCoordinator`, app): plan, fetch on a background executor, parse, store into
  `FeedArticleCacheRepository`, report typed results, and signal cache changes.
- **RSS source**: `FeedSourceDependencies.changes` is wired to `FeedRefreshCoordinator.cacheChanges`, so the
  RSS source is `LIVE` and reloads after a refresh stores new articles.
- **UI**: Settings > RSS feeds has a "Refresh feeds" row (result summary, announced politely) and a per-feed
  status line (refreshing, last error, last updated).
- **Background refresh** (#1393, opt-in, Off by default): see "Background refresh" below.

## Policy

| Rule | Behaviour |
| --- | --- |
| Trigger | An explicit user action calls `refresh`, or, only when the user chose a nonzero background interval, the periodic worker does. Opening a stage, the settings page or the source never fetches. |
| Scope | Refresh all, or refresh one feed (`FeedRefreshScope`). |
| Excluded | Disabled feeds, feeds of locked or removed profiles, non-public hosts. No request is made. |
| Minimum interval | 15 s between attempts per feed (repeat taps are skipped as `TOO_SOON`). |
| Backoff | After failures, "refresh all" waits 60 s doubling to 6 h; refresh-one ignores backoff but not the minimum interval. A backwards clock never freezes a feed. |
| Conditional requests | ETag / Last-Modified from the last successful response are sent while the cache still holds that content; a 304 keeps the cache. |
| Concurrency | One refresh at a time; a second request returns false. |
| Failure | Typed reason only: `NETWORK`, `TIMEOUT`, `BAD_STATUS`, `OVERSIZE`, `MALFORMED`, `UNSAFE_URL`. The cache is untouched; stale content stays visible (existing staleness rules). |
| Empty parse | A feed that parses to zero articles does not replace a non-empty cache (`MALFORMED`). |
| Caps | Response size, redirects and timeouts (transport); per-feed and global article caps (normalizer and cache). |

## Security and privacy notes

- All network I/O runs on the coordinator's single background thread. The coordinator is built lazily and does
  no I/O until asked.
- Only https URLs; credentials in URLs are rejected when a feed is added (`FeedUrl`); redirects to non-https
  are refused.
- `FeedHostSafety` is a literal-only guard (localhost, `.local`/`.internal`, private, loopback, link-local and
  CGNAT IPv4, any IPv6 literal, single-label hosts). It does not resolve DNS, so a public name pointing at a
  private address is not detected.
- URLs, response bodies and exception messages are never logged and never put in results or status text.
- Validators (ETag / Last-Modified), backoff counters, last error (typed reason only), timestamps and the last
  background run are persisted in the existing device-local article cache document, so they survive process
  death. They hold no URLs, no response bodies and no article content, are bounded (at most one entry per
  configurable feed, validators over 256 characters dropped, failure counts clamped), decode defensively (a
  corrupt entry is dropped, never a crash) and stay excluded from backups with the rest of that file.
- No new runtime permissions or prompts: `INTERNET` and `ACCESS_NETWORK_STATE` were already declared.
  WorkManager's merged manifest adds the normal permissions `RECEIVE_BOOT_COMPLETED` (re-schedule after reboot)
  and `WAKE_LOCK`, plus a `SystemForegroundService` entry that is never started (no `setForeground` use).

## Background refresh

Opt-in, Off by default (#1393). With the interval Off, Riffle schedules nothing and makes no automatic request.

| Setting | Values | Default |
| --- | --- | --- |
| Background refresh | Off, 1 h, 3 h, 6 h, 12 h, 24 h | Off |
| Only on Wi-Fi | on / off | on |
| Only while charging | on / off | off |

The two switches and the status line appear only once an interval is chosen. Settings written by earlier builds
(`MINUTES_30` .. `MINUTES_360`, which never did anything) decode to Off, so nobody is opted in by a setting they
never saw take effect. A backup restore carries the user's own choice; it never triggers a fetch by itself.

How it works:

- **Decision** (`FeedBackgroundScheduler`, core/domain, pure): settings to `Schedule(interval, unmetered,
  charging)` or `Cancel(INTERVAL_OFF | NO_FEEDS | NO_ENABLED_FEEDS)`.
- **Scheduler** (`FeedBackgroundRefreshScheduler`, app): one unique periodic work
  (`riffle.rss.background-refresh`), `ExistingPeriodicWorkPolicy.UPDATE`, first run one period after opting in.
  Constraints: network `UNMETERED` (Wi-Fi only on) or `CONNECTED`, battery not low, charging optional. Retry
  backoff is exponential from 30 minutes. `startFeedBackgroundSync` observes the saved settings and applies the
  decision on launch and on every change; Off, no feeds, or no enabled feed cancels the work.
- **Worker** (`FeedRefreshWorker`, app): a thin `Worker` adapter. All logic is in `FeedBackgroundRefreshRunner`
  (Compose-free, unit-tested): re-read the settings (stale queued work after switching Off does nothing),
  re-check Wi-Fi-only against the active network and refuse under battery saver (platform constraints are only
  a hint), then call `FeedRefreshCoordinator.refreshScheduledBlocking`. No new HTTP code: the same transport,
  parser, `FeedHostSafety`, size caps and cache as the user-triggered path.
- **Planner for scheduled runs**: always an "all" plan (so backoff applies and the refresh-one bypass is
  unavailable), per-feed minimum gap of half the interval since the last attempt (so a doubled-up or early run
  cannot fetch a feed much more often than asked, and a recent manual refresh counts), validators sent.
- **Retries**: a run where every attempted feed failed with `NETWORK` or `TIMEOUT` asks WorkManager for a
  backed-off retry, at most 3 attempts per period. Malformed, oversize or unsafe feeds never retry.
- **Status**: Settings shows "Last background refresh: <relative time> (new articles | nothing new | failed |
  skipped)", read off the main thread from the persisted record. No URLs or feed names.
- **Cost**: `androidx.work:work-runtime` 2.10.0, no Kotlin extensions or coroutines artifact needed (`Worker`,
  not `CoroutineWorker`). It brings Room and an `androidx.startup` initializer; measured against the 32 MiB cap
  by the Minified Release Check (the app was about 25 MiB unminified when R8 stage 1 landed). WorkManager
  initialises at process start through the default initializer; with the interval Off nothing is enqueued.
- **Cache refresh while the app is open**: the worker uses its own coordinator, so the in-app RSS source does not
  see a background update until it next reloads. Persisted state is merged by newest attempt so the two never
  fight over validators or backoff.


## Manual validation

1. Settings > RSS feeds: add a public https feed; the row shows "Not refreshed yet".
2. Tap Refresh: row shows "Refreshing...", then "N new articles"; the feed row shows "Updated ...". With the
   workspace preview on, the RSS source shows the articles without restarting.
3. Tap Refresh again immediately: "Already up to date" or nothing fetched (minimum interval).
4. Airplane mode, Refresh again after 15 s: "Refresh failed: Network error"; cached articles remain.
5. Disable a feed: it is not fetched on Refresh.
6. Background refresh (see below) is validated separately.

### Manual validation: background refresh

Needs a device or emulator with a debug build (`com.riffle.app.debug`) and a feed that responds.

1. Default state: Settings > RSS feeds shows "Background refresh: Off" with the text "Nothing runs in the
   background". `adb shell dumpsys jobscheduler | grep -c com.riffle.app` shows no WorkManager job for Riffle.
2. Add a feed, choose 1 hour. Two switches (Only on Wi-Fi on, Only while charging off) and
   "No background refresh has run yet." appear. Within seconds,
   `adb shell dumpsys jobscheduler | grep -A12 com.riffle.app` lists a `SystemJobService` job with the Wi-Fi
   (unmetered) and battery-not-low constraints. Turn on "Only while charging" and the job shows the charging
   constraint after the next sync.
3. Force a run: find the job id in the `dumpsys` output, then
   `adb shell cmd jobscheduler run -f com.riffle.app.debug <job id>`. Reopen Settings: the status line shows
   "Last background refresh: ... (new articles)" and the feed row shows a fresh "Updated" time. Run it again
   immediately: "(nothing new)", and no request is made for feeds inside the half-interval gap.
4. Failure path: enable airplane mode with Wi-Fi only off, force the run: status "(failed)", the job is
   rescheduled with backoff (`dumpsys jobscheduler` shows a later run time), cached articles stay.
5. Metered path: with Wi-Fi only on and the device on mobile data (or a Wi-Fi network marked metered), force the
   run: status "(skipped ...)", no request. Turn Wi-Fi only off and force again: the feeds are fetched.
6. Switch to Off: the unique work disappears from `dumpsys jobscheduler` within seconds. Remove or disable every
   feed while an interval is set: same.
7. Process death: add a feed, refresh, `adb shell am force-stop com.riffle.app.debug`, reopen, refresh again
   within a minute: the request is conditional (inspect with a logging proxy you control; Riffle never logs
   URLs) and a 304 keeps the cache.
8. Android Studio App Inspection > Background Task Inspector lists `riffle.rss.background-refresh` with its
   constraints, attempt count and result.

## Not done

- DNS-level private-address checks: `FeedHostSafety` is still literal-only, so a public hostname that resolves
  to a private address is not detected. Not built here.
- JSON Feed support. Not built here.
- Refresh from the Sources page or an in-stage pull gesture.
- A single shared coordinator between the activity and the worker (the in-app source reloads background
  updates only on its next load).
- WorkManager-level instrumented tests (`androidx.work:work-testing` was left out; the worker is a thin adapter
  over a unit-tested runner and the decisions are pure).
