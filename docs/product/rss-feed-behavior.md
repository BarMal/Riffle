# RSS feed permission, network, and profile behaviour

This documents the observable behaviour of RSS feed settings and refresh (issues #995, #1008-#1013).
It complements [ADR 0001](../architecture/adr/0001-rss-feed-stages.md), which is the authoritative
contract; this page summarizes the parts relevant to manual device validation.

## Permissions

- Adding, removing, enabling, or disabling a feed never triggers an Android permission prompt.
  Feeds are configured entirely through **Settings > RSS feeds**; there is no notification-access,
  overlay, or storage permission tied to this feature.
- Only public HTTPS feed URLs are accepted. Entering an `http://` URL, a URL with embedded
  credentials (`user:pass@host`), or an otherwise malformed URL shows a short, generic inline
  error ("Enter a valid https feed URL") and the feed is not added.

## Network and refresh

- Refresh is user-triggered; opening or rendering a feed stage never performs a network request
  on its own. The refresh interval setting only bounds how soon a future scheduled/allowed refresh
  may run once a scheduler ships -- it does not itself start network activity today.
- Metered-network and battery-saver state are expected to suppress any non-user-triggered refresh
  once scheduled refresh ships; this settings slice adds no exception to that rule.

## Offline behaviour

- Cached articles remain available and readable when the device is offline or a refresh fails;
  stale content is shown rather than an empty state.
- Removing a feed, or disabling it, immediately clears that feed's offline article cache
  (`FeedArticleCacheRepository.clearFeed`), so cached content never outlives its configuration.

## Backup, restore, and privacy

- Backups include only normalized feed URLs, feed identity, enabled state, and refresh interval.
  Tracking-only query parameters are stripped before a URL is ever stored or backed up.
- Backups never include cached article text, images, response headers, credentials, raw
  tracking-bearing URLs, or read/dismiss digests. Restoring a backup validates each feed URL and
  schema version, drops invalid or unsupported entries, restores valid configuration with an
  empty cache, and never triggers a network refresh automatically.
- The in-settings privacy copy (Settings > RSS feeds) explains this data handling in user-facing
  language.

## Profile behaviour

- Feed configuration is scoped to the profile it was added under (personal, work, or private).
  A locked or removed profile's feeds do not contribute content to a visible feed stage, matching
  the same profile-availability rules used for app stages.

## Manual validation checklist

- Add a valid `https://` feed URL; confirm it appears enabled in the list.
- Attempt to add an `http://` URL and a URL with embedded credentials; confirm both are rejected
  with the generic error text and neither is added.
- Disable a feed; confirm its cached articles are cleared and it stops contributing rendered cards.
- Remove a feed; confirm its cache is cleared and it disappears from the configured list.
- Export a backup, inspect the JSON, and confirm it contains only `settings.rss` configuration --
  no article text, images, or cache-specific keys.
- Import that backup on a clean install; confirm feeds restore correctly with an empty cache and
  no network request fires as part of import.
