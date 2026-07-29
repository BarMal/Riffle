# ADR 0001: RSS feed stages as a bounded TimeScape source

- Status: Accepted
- Date: 2026-07-29
- Supersedes: N/A
- Superseded by: N/A

## Context

Issue #995 defines the contract for a future RSS source. TimeScape already has a shared
static canvas and typed dynamic slots, including the reserved `FUTURE_FEED` source. The
source must therefore add feed content to the existing stage/template pipeline rather than
creating a second feed surface.

The source also crosses several sensitive boundaries: user-provided URLs and optional
credentials, network and refresh policy, offline storage, article text and images, profile
visibility, browser hand-off, read/dismiss state, and launcher backup/restore. A decision is
needed before adding a parser, network client, persistence schema, or worker.

## Decision

### Source and authentication contract

- The first implementation accepts explicitly entered feed URLs and supports RSS 2.x and
  Atom feeds. Discovery, directory search, OPML import, redirects to arbitrary schemes, and
  feed URL inference are separate follow-up work.
- Only `https` URLs are accepted by default. HTTP is rejected unless a later, explicit
  per-feed insecure-transport setting is introduced. URL validation rejects blank URLs,
  non-web schemes, embedded userinfo, and malformed hosts before any request is made.
- The MVP has no account login or credential scraping. Public feeds are the default. A later
  authenticated-feed slice may support user-supplied headers or a platform credential gateway,
  but credentials must be stored in a platform-protected store and must never enter launcher
  settings, logs, diagnostics, or backups.
- Requests follow redirects only to `https` and remain bounded by a response-size limit and
  connect/read timeouts. The adapter exposes normalized source errors rather than leaking
  response bodies or credentials to the domain or renderer.

### Refresh, cache, and resource policy

- Refresh is user-triggered initially, with an optional system-scheduled refresh only after
  the refresh policy is represented in settings and the platform scheduling boundary is
  tested. No refresh occurs merely because a feed stage is rendered.
- A feed has a configurable minimum refresh interval, with a conservative default and
  exponential retry/backoff for failures. Conditional requests may use ETag and Last-Modified
  metadata, but stale content remains usable when refresh fails.
- The cache is bounded per feed and globally by item count, serialized text size, and image
  bytes. Cache eviction is deterministic and removes oldest read items before unread items.
  Image downloads are optional, lazy, cancellable, and independently size-bounded; article
  text must remain useful when an image is unavailable.
- Metered-network and battery-saver state suppress non-user-triggered refreshes. The source
  must not require a new polling or caching framework; a future worker, if needed, sits behind
  a small platform scheduler interface.

### Article, card, and stage semantics

- Each configured feed is one dynamic source instance. Its articles are cards in a single
  feed stage, ordered by normalized publication time, then stable item identity. Missing or
  invalid dates fall back to source order without inventing a current timestamp.
- Stable item identity is derived from a canonical item URL when present, otherwise a stable
  feed/item identifier and finally a deterministic hash of normalized source fields. The
  source must deduplicate items and cap the number of rendered cards.
- A card contains only normalized presentation data: feed title, article title, optional
  author/date/summary, optional image reference, source identity, and browser-open action.
  Full HTML, scripts, tracking pixels, arbitrary enclosures, and executable content are not
  rendered in the launcher card.
- Focusing a card changes focus only. Opening details presents sanitized article text and an
  explicit **Open in browser** action; it never embeds a third-party page or silently launches
  an app. Back returns from detail to the originating feed stage.
- Read state and dismissal are launcher-owned intent keyed by a fixed-length opaque SHA-256
  digest of the normalized feed identity and normalized item identity. The persisted key never
  contains a URL, query string, tracking parameter, title, article text, or other source field;
  the source identity is used only while computing the digest. Dismissal removes an item from
  the active projection but does not send a destructive request to the publisher. Refresh must
  preserve valid read/dismiss intent across reordered entries.
- A feed stage can be pinned and can remain visible when empty. Feed content is transient in
  the same sense as notification/media content; stage identity, pin order, selected stage,
  and source configuration are durable intent.

### Template binding and coexistence

- Feed stages bind through `TimeScapeDynamicSlot(source = FUTURE_FEED)` and the existing
  responsive template variants. The static canvas remains shared across feed stages.
- Feed stages coexist with pinned app stages, notification stages, and the optional recent or
  frequently-used stack. They do not change selected app-stage focus or reorder pinned app
  stages. Each source owns its stable identity and ordering within its source projection.
- Missing permissions, unavailable network, empty cache, locked profiles, malformed feeds,
  and removed configurations map to honest loading/empty/unavailable states. They must not
  expose stale private content or leave an unreachable slot.
- The renderer consumes framework-independent card/state models. Android networking, XML
  parsing, image decoding, browser intents, and scheduling remain behind platform-facing
  gateways.

### Privacy and backup/restore

- Normalized public HTTPS feed URLs are user configuration and are backed up when the user
  includes launcher settings. URL normalization removes tracking-only query parameters before
  storage and backup; embedded userinfo, credentials, and unsupported schemes are rejected.
  Cached article text, summaries, images, response headers, auth material, and network error
  bodies are never backed up.
- Backup may include the normalized feed URL, feed identity, enabled state, refresh policy,
  selected template/slot binding, and opaque fixed-length read/dismiss digests. It never
  includes raw tracking-bearing URLs, credentials, article content, or any reversible item
  identifier. Restore validates each feed URL and schema version, drops invalid or unsupported
  entries, restores valid configuration with an empty cache, and never triggers a network
  request automatically. A restored feed refreshes only after the normal user/scheduler policy
  permits it.
- Work/private profile feed configuration and state remain profile-scoped. A locked or removed
  profile cannot contribute content to a visible feed stage. Diagnostics expose counts and
  failure categories only, not URLs, titles, article text, or account data.

### Dependency decision

- No RSS-specific dependency is added in this spike. The future parser should first evaluate
  Android's existing pull-parser capability behind a domain-independent parser interface,
  because the MVP needs bounded RSS/Atom field extraction rather than a general HTML engine.
- A dedicated maintained parser may be proposed in the parser issue only if conformance tests
  show that the platform parser cannot safely handle the required RSS/Atom variants. Any such
  dependency needs an explicit review of size, license, update cadence, and resource limits.
- Networking similarly remains an interface in the core/domain boundary; the Android adapter
  may use existing platform APIs. No third-party network stack is justified until requirements
  such as authenticated feeds or advanced HTTP caching exceed those APIs.

## Follow-up implementation issue series

The following slices are intentionally separate and should retain the contracts above:

1. [**Feed model and validation**](https://github.com/BarMal/Riffle/issues/1008) — define feed
   configuration, stable item identity, opaque SHA-256 read/dismiss digests, RSS/Atom normalized
   item models, profile ownership, URL validation, and domain conformance tests.
2. [**Platform source and refresh boundary**](https://github.com/BarMal/Riffle/issues/1009) — implement HTTPS transport, conditional requests,
   timeout/size limits, metered/battery policy, foreground refresh, and parser adapter tests.
3. [**Persistence and cache**](https://github.com/BarMal/Riffle/issues/1010) — add bounded offline item/image cache, migrations, opaque read/dismiss
   intent, eviction, and settings/backup exclusions with round-trip tests.
4. [**Template and stage projection**](https://github.com/BarMal/Riffle/issues/1011) — bind `FUTURE_FEED` to the existing dynamic-slot model,
   reconcile empty/unavailable/profile states, and verify coexistence/order with app and recent
   stages.
5. [**Renderer and detail flow**](https://github.com/BarMal/Riffle/issues/1012) — render feed cards in the existing TimeScape stack, expose
   sanitized details and browser-open behavior, and validate focus, Back, accessibility, and
   reduced-motion behavior on phone and foldable variants.
6. [**Settings and validation**](https://github.com/BarMal/Riffle/issues/1013) — add feed management, refresh/cache controls, privacy copy,
   manual device scenarios, and end-to-end verification without exposing sensitive fixture data.

## Consequences

This preserves one TimeScape stage/template model and makes offline use a first-class fallback.
It limits the initial feature to public HTTPS feeds and launcher-owned article presentation,
which avoids credential and embedded-webview risks. The trade-off is that discovery,
authenticated feeds, rich HTML, and background refresh require deliberate follow-up decisions.
The implementation slices can be tested independently at domain, adapter, persistence, and
Compose boundaries, and the existing standard launcher remains unaffected.

## Alternatives Considered

- **A full embedded web feed reader:** rejected because it expands the trust boundary, makes
  privacy and offline behavior opaque, and conflicts with the launcher-rendered card contract.
- **A separate RSS tab or full-screen feed UI:** rejected because it duplicates navigation and
  ignores the existing `FUTURE_FEED` dynamic-slot design.
- **Unbounded live network content in cards:** rejected because it is unpredictable offline,
  battery, metered-network, privacy, and performance behavior.
- **Persisting the complete feed cache in launcher backups:** rejected because backups can be
  shared and should carry user intent/configuration, not potentially sensitive source content.

## References

- [Issue #995](https://github.com/BarMal/Riffle/issues/995)
- [Issue #991](https://github.com/BarMal/Riffle/issues/991) — shared static canvas and dynamic slots
- [Issue #888](https://github.com/BarMal/Riffle/issues/888) — compact TimeScape app-stage experience
- [Issue #993](https://github.com/BarMal/Riffle/issues/993) — foldable Stage Manager transition
- [Issue #994](https://github.com/BarMal/Riffle/issues/994) — recent/frequently used virtual app stack
- [TimeScape accessibility validation](../../development/timescape-accessibility-validation.md)
