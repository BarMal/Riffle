# RSS feed refresh (user-triggered)

Issue #1374. Companion to [rss-feed-behavior.md](rss-feed-behavior.md) and
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

## Policy

| Rule | Behaviour |
| --- | --- |
| Trigger | Only an explicit user action calls `refresh`. Opening a stage, the settings page or the source never fetches. |
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
- Validators, backoff counters and last errors are in memory only (reset on process death). Article content is
  stored only in the existing device-local cache, which stays excluded from backups.
- No new permissions: `INTERNET` was already declared; no prompts.

## Manual validation

1. Settings > RSS feeds: add a public https feed; the row shows "Not refreshed yet".
2. Tap Refresh: row shows "Refreshing...", then "N new articles"; the feed row shows "Updated ...". With the
   workspace preview on, the RSS source shows the articles without restarting.
3. Tap Refresh again immediately: "Already up to date" or nothing fetched (minimum interval).
4. Airplane mode, Refresh again after 15 s: "Refresh failed: Network error"; cached articles remain.
5. Disable a feed: it is not fetched on Refresh.

## Not done

- Background / periodic refresh (the refresh interval setting still does not start network activity).
- Persisting validators or last errors across restarts.
- DNS-level private address checks; JSON Feed; refresh from the Sources page or an in-stage pull gesture.
