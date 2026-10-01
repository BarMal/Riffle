# RSS and Search sources (as built)

Issue #1365. Completes the built-in source set: every id in `SourceIds.BUILT_IN` now has a registered
`ItemSource`. Background: [workspaces-sources-lenses.md](workspaces-sources-lenses.md),
[rss-feed-behavior.md](rss-feed-behavior.md), [ADR 0001](../architecture/adr/0001-rss-feed-stages.md).
No WS0 contract changed.

## RSS source (`SourceIds.RSS`, `rss`)

**Mapping** (`FeedItemMapper`, domain, framework-free). The mapper does not re-derive any feed rule. It takes
the `FeedStageSnapshot` produced by the existing `FeedStagePlanner` plus sanitized article content, so the
planner decides which feeds appear, their order (pinned, then configured), their lifecycle and each feed's
article order.

| Item field | Value |
| --- | --- |
| `id` | `rss:<feedId>:<article digest>` |
| `title` | article title (plain text) |
| `subtitle` / `groupLabel` | the feed's host without `www.` (a configured feed has no other name) |
| `groupKey` | the feed id |
| `body` | summary, cut to 280 characters with an ellipsis |
| `timeEpochMillis` | published time from the stage item, absent when undated |
| `image` | `ItemImageKeys.feedArtwork(digest)` when the article has an image URL; resolves from the offline image cache only |
| `target` | `DeepLink(canonicalUrl)`, or `None` when the article has no URL |
| `ext` | `rss.author` (text, when known), `rss.stale` (flag) |
| `privacy` | `VISIBLE` |

Rules inherited from the planner: profile-locked feeds expose nothing, removed-profile feeds are absent, a
stale feed keeps its cached articles (flagged `rss.stale`), loading and failed feeds contribute no items,
and an article whose cached content is gone (evicted, cleared) is skipped. Read and dismissed state is not
applied: no existing surface applies it yet, and the cache answers it one digest at a time by decoding the
whole cache document, which is too costly per article. Left for the slice that adds dismissal UI.

**Capabilities**: `GROUPABLE` only.

- Not `LIVE`: the article cache has no change notification and no refresh path exists to signal one yet.
- Not `PRIVACY_SENSITIVE`: feed content is public; the private-content rule that does exist (locked profile)
  is enforced by exclusion, not by redaction.

**Network**: the source performs none. It reads the offline cache (`FeedArticleCacheRepository.loadFeed`) on
the shared background executor, never on the main thread, and only when the first observer subscribes. At the
time of writing nothing in the app wires `FeedTransport` to the cache (refresh is "user-triggered" per
rss-feed-behavior.md and the trigger is not built), so the cache is what a future refresh path fills. When it
ships it should write through `replaceFeed` and report through a `SourceChangeSource`, which would make the
source `LIVE`.

**States**: no feeds configured is `Ready(empty)`: not an error and not a prompt. Configured but never
fetched is also `Ready(empty)`. A read failure is `Unavailable`. There is no permission, so
`PermissionRequired` never occurs.

## Search source (`SourceIds.SEARCH`, `search`)

**Passing a query.** `ItemSource.subscribe(observer)` has no input, and changing it would touch every adapter
and the persisted-lens contract. Instead the source observes a `SearchQueryHolder`, a small in-memory holder
that a search box UI or container writes with `set(text)`:

- The source is `SEARCHABLE` and `LIVE` (it re-queries whenever the holder changes, and not otherwise).
- There is exactly one query per process: every lens over `search` shares it, which is what the one
  subscription per source rule implies. Per-lens queries would need an additive WS0 hook (for example a
  parameterized source subscription) and are deliberately out of scope; this is the open question below.
- An empty or blank query is `Ready(empty)`, matching `LauncherSearchProvider.search`, which returns nothing
  for an empty query. There are no suggestions today.
- The query is trimmed and cut at 128 characters.

**Privacy of the query text.** It lives only in the holder's memory. It is not serialized, not part of any
state saved across process death, not logged, not in diagnostics or backup, and `SearchQueryHolder.toString()`
does not contain it. Item ids come from the result keys, so the text is not echoed into items either.

**Results** (`SearchItemMapper`) reuse `LauncherSearchProvider`, so ranking and matching are the existing
behaviour. Apps (including matches through app shortcuts, as the app search index already does) map to
`ItemTarget.App` with the app icon and group `apps`; launcher settings pages map to a source-owned
`ItemTarget.Intent("launcher-setting:<entry id>")` with group `settings` and a `search.section` extra. The
provider ranking is preserved; lenses may group by `groupKey`. Group labels are English constants for now
(`Apps`, `Settings`); a UI should localise by `groupKey`.

**States**: apps come from the same hidden-app-aware snapshot as the apps sources; a platform that cannot
answer is `Unavailable`. There is no permission.

## Registration and tests

Both are registered in `builtInSourceRegistry`. A pure test asserts that every `SourceIds.BUILT_IN` id has a
registered source and nothing else is registered.

## Open question

Do lenses need their own query (two search boxes on one page)? If so, the minimal additive hook is an
optional interface `ParameterizedItemSource { fun subscribe(parameters, observer) }` plus a lens source
parameter; the shared single-query holder would then be the default parameter value.
