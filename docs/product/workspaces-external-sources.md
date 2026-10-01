# Workspaces: external and third-party sources (exploration)

Status: exploration and recommendation, no product code. Tracking issue: #1366. Builds on
[`workspaces-sources-lenses.md`](workspaces-sources-lenses.md) (Source/Item/Lens model, WS1 adapters,
Calendar access policy) and [`standard-launcher-mode.md`](standard-launcher-mode.md).

## Summary

- Today every `ItemSource` is built in. The universal `Item` already carries what an outside producer
  needs, but three of its fields are unsafe for untrusted producers as shipped: `ItemTarget.Intent`
  (source-owned intent token), `ItemAction.Custom`, and free-form `ext` keys. An external surface needs a
  stricter "external profile" of `Item` enforced at the boundary, not a new model.
- Recommended build order: (1) user-supplied feeds (RSS already planned, then JSON Feed and ICS) because
  they need no new permission and no other app; (2) system status sources (battery, connectivity) as
  cheap proof of the "source without permission" path; (3) a **pull-based, user-approved ContentProvider
  extension contract** for third-party apps; (4) SAF-backed local files. Explicitly do not build:
  AccessibilityService sources, Slices, `QUERY_ALL_PACKAGES`-based discovery of extensions, widget
  scraping, bound-service push, smart-home protocol clients inside the launcher.
- Nothing here changes Standard launcher mode: all external sources are additive, off by default, and
  never prompt.

## How claims are marked

- **VERIFIED**: stated in the cited official page, fetched while writing this doc (2026-10-01).
- **UNVERIFIED**: not confirmable from docs available here, or behaviour that varies by OEM/OS build; needs
  a device test or a Play Console policy read before relying on it. The Play Console help pages
  (`support.google.com`) were blocked by the sandbox egress policy, so Play policy statements are
  VERIFIED only where a search result quoted the policy; otherwise UNVERIFIED and listed in
  [Verification backlog](#verification-backlog).
- Codebase claims cite the file read.

## 1. Existing model, and what an external producer touches

Read from `core/domain/.../launcher/workspace/Source.kt`, `Item.kt`, `SourceIds.kt`,
`sources/SourceAccess.kt`, and `app/.../launcher/sources/`:

- `ItemSource` has `descriptor: SourceDescriptor(id, capabilities)` and `subscribe(observer)`. Capabilities
  are `LIVE, GROUPABLE, ACTIONABLE, SEARCHABLE, PRIVACY_SENSITIVE`. `SourceState` is `Loading`,
  `Ready(items)`, `PermissionRequired` ("never a prompt") and `Unavailable`.
- `SourceRegistry.source(id): ItemSource?` resolves persisted lens `SourceRef`s. Persisted lenses reference
  sources by id string only; built-in ids (`SourceIds`) are "a stored contract: never rename".
- `Item` is "transient and non-durable": never written to layout, settings, backup or diagnostics.
  Redaction happens only at the lens `project` step (`SENSITIVE_ITEM_FIELDS` = title, subtitle, body, image,
  actions, ext).
- Lens evaluation dedupes by `ItemId` across sources, "first in merged order wins". An external source
  that could emit the id of a built-in item could therefore hide or impersonate it if ordered first.
- `sourceStateFor(access, items)` reads gated data only when access is `GRANTED`. This is the pattern an
  external source must follow: consent state first, query second.
- `ItemTarget` has `App`, `Shortcut`, `DeepLink(uri)`, `Intent(token)` ("a source-owned intent, referenced
  by an opaque token the source can resolve") and `None`. `ItemAction.Custom(id, label)` is open-ended.
- `ItemExtKey` must match `namespace.name` (lowercase); `ItemExtValue` is `Text | Number | Flag` only.
- The manifest declares `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_CALENDAR` (requested only from Settings),
  `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `ACCESS_HIDDEN_PROFILES`, and a `<queries>` entry for
  `MAIN`/`LAUNCHER` intents only (`app/src/main/AndroidManifest.xml`). It does not declare
  `QUERY_ALL_PACKAGES`.
- RSS is already planned/implemented as a settings-configured, https-only, user-triggered-refresh feature
  (`docs/product/rss-feed-behavior.md`): useful precedent for URL sources (https only, no credentials in
  URLs, no auto-refresh network, cache cleared on removal, backup holds config only).

Consequence: an external source needs (a) a source id namespace that cannot collide with built-ins,
(b) an item-id prefix rule, (c) a restricted target/action/ext profile, and (d) a lifecycle that degrades
to `Unavailable`. None of that requires changing the `Item` shape.

## 2. Android constraints that apply to every candidate

| Constraint | Claim | API level | Status | Source |
| --- | --- | --- | --- | --- |
| Package visibility | Apps see a filtered package list by default; launchers are auto-visible for display; use `<queries>` for packages, intents or provider authorities | 30+ | VERIFIED | [package visibility](https://developer.android.com/training/package-visibility) |
| `QUERY_ALL_PACKAGES` | Needs Play approval; requires core-purpose justification and a Permissions Declaration form; launchers and device search are among cited permitted uses | 30+ (Play policy) | VERIFIED via the Play help page snippet; full text not fetched | [Play help](https://support.google.com/googleplay/android-developer/answer/10158779) |
| Provider export default | `android:exported` defaults to false when targeting API 17+; a provider can require `readPermission` and can use URI grants | 17+ | VERIFIED | [`<provider>`](https://developer.android.com/guide/topics/manifest/provider-element) |
| Signature permissions | `signature` is granted only to apps signed with the same certificate as the declarer; `knownSigner` allows listed certificates | `knownSigner` newer (UNVERIFIED exact level) | VERIFIED for semantics | [`<permission>`](https://developer.android.com/guide/topics/manifest/permission-element) |
| Untrusted IPC data | All data from other apps via IPC, files and network is to be treated as untrusted and validated; use explicit intents to bind services (implicit bind throws) | 21+ for bind | VERIFIED | [security tips](https://developer.android.com/training/articles/security-tips) |
| Implicit broadcasts | Manifest receivers for most implicit broadcasts are not delivered; use context-registered receivers | 26+ | VERIFIED | [broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts) |
| Periodic background work | WorkManager periodic minimum interval is 15 minutes; constraints available (network type, battery not low, idle) | all | VERIFIED | [define work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) |
| Background FGS start | Apps targeting 12+ cannot start foreground services from the background except listed exemptions | 31+ | VERIFIED | [FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) |
| Standby bucket penalty | Frequently timing-out work can land an app in the restricted bucket, limiting background execution and network | 14+ (as documented) | VERIFIED | [optimize battery](https://developer.android.com/develop/background-work/background-tasks/optimize-battery) |
| Persistent SAF grants | `takePersistableUriPermission` keeps a picked document/tree across restarts; access is lost if the document is moved or deleted; no storage permission needed | tree picker 21+ | VERIFIED | [SAF](https://developer.android.com/training/data-storage/shared/documents-files) |
| Slices | The Slice framework is deprecated and gets no updates from Android 15 (VANILLA_ICE_CREAM) on; AppSearch is suggested for sharing displayable data | 35 | VERIFIED via platform reference text quoted in search (not a direct page fetch) | [androidx.slice](https://developer.android.com/reference/androidx/slice/Slice) |
| AppWidgetHost | Hosts get widget UI as `RemoteViews` inside `AppWidgetHostView`, not raw data; binding needs `BIND_APPWIDGET` plus a user-approved bind dialog; at most 16 distinct RemoteViews per widget from 12 | 12+ for the cap | VERIFIED | [widget host](https://developer.android.com/guide/topics/appwidgets/host) |
| Notification listener | Needs the user to enable Notification access in Settings; the service must hold `BIND_NOTIFICATION_LISTENER_SERVICE` | 18+ | VERIFIED | [reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| Battery | `ACTION_BATTERY_CHANGED` needs no permission, is sticky, and is not manifest-declarable (register at runtime) | all | VERIFIED | [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager) |
| Connectivity | `registerDefaultNetworkCallback`, `ACCESS_NETWORK_STATE` is a normal permission (already in the manifest) | 24+ for the default callback (UNVERIFIED exact level) | VERIFIED for permission | [network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) |
| Health Connect | Per-data-type read permissions; Android 9 (API 28) and higher; a separate background-read permission exists | 28+ | VERIFIED | [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) |

Play policy points that are not fully verified here (see backlog): the data-safety form entries for any
new data class read, "sensitive permission" declaration thresholds for health and calendar data, and
whether reading another app's ContentProvider via a documented extension contract triggers any extra
review.

## 3. Candidate catalogue

Ratings: Feasibility (F), user Value (V), Risk (R: privacy, policy, maintenance), each High/Med/Low. "New
permission" means a new manifest permission or a new special-access settings toggle in Riffle.

| # | Candidate | New permission | F | V | R | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | User-supplied feed URLs: RSS/Atom, JSON Feed, ICS calendars, simple JSON | none (`INTERNET` already declared) | High | High | Low-Med | Build first |
| 2 | System status: battery, connectivity (and metered) | none | High | Med | Low | Build early |
| 3 | Third-party extension contract (provider-pull, user approved) | none for Riffle; provider app gates itself | Med | High long term | Med | Build third, narrow |
| 4 | Local files via Storage Access Framework (picked folder/files) | none (user picks) | Med | Med | Low-Med | Build fourth |
| 5 | Other apps' public ContentProviders: contacts, MediaStore, tasks | dangerous per provider | Med | Med | High | Only via existing first-class adapters, not generic |
| 6 | Do-not-disturb state | maybe `ACCESS_NOTIFICATION_POLICY` | Med | Low | Med | Defer; reading may need the policy-access grant (UNVERIFIED) |
| 7 | App-hosted widgets as data | `BIND_APPWIDGET` user grant | Low (as data) | Low | Med | Do not build as a source; widget hosting stays a container concern |
| 8 | Bound-service / broadcast / Intent push from apps | none or custom | Low-Med | Med | High | Do not build |
| 9 | Health Connect | per data type, sensitive | Med | Low-Med | High | Defer; reject for now |
| 10 | Smart-home / IoT | network, sometimes Bluetooth/location | Low | Med | High | Do not build in the launcher; reachable only through an extension app |
| 11 | Wear / companion | companion API | Low | Low | Med | Out of scope |
| 12 | AccessibilityService | special access | n/a | n/a | Reject | Policy and trust |
| 13 | Android Slices | n/a | n/a | n/a | Reject | Deprecated |
| 14 | Weather | none (via feed/JSON URL) or provider | Med | Med | Low-Med | Fold into #1 (JSON endpoint) or #3 |

### 3.1 User-supplied URLs and feeds (candidate 1)

Why first: no new permission, no other app, user-explicit, and the privacy posture is already established
by the RSS behaviour doc. Formats:

- **RSS/Atom**: already planned (`SourceIds.RSS`).
- **JSON Feed** (a published open format; mapping to `Item` is near one-to-one: title, summary/content_text,
  url, date_published, image).
- **ICS (iCalendar) subscribe-by-URL**: read-only events mapped like `CalendarItemMapper`. No
  `READ_CALENDAR` involved, so it is the permission-free alternative for users who decline calendar
  access. Needs a bounded RFC 5545 parser; recurrence expansion (RRULE) is the real cost, so ship
  "single events and simple RRULE or ignore RRULE with a visible note" as an explicit choice.
- **Simple JSON endpoints** (e.g. weather): requires a user-authored mapping (JSON path to title/subtitle/
  time). Powerful but a UX and security cost; defer behind a fixed templates approach (named templates for
  known shapes) rather than a general mapping editor.

Constraints and rules (reuse the RSS ones): https only; no credentials in URL; user-triggered refresh at
first, scheduled refresh only through WorkManager with network and battery-not-low constraints at 15
minutes or longer (VERIFIED floor above); response size and item count caps; never follow redirects to
non-https; cache is item content, so it must not outlive configuration (RSS precedent) and must not enter
backup. Authenticated feeds (private ICS with token in URL) are a privacy hazard: treat the URL itself as a
secret, never log it, store it in the same place RSS URLs live but flag it `PRIVACY_SENSITIVE`. Whether
tokenised URLs are in backup is an open question (see section 8).

Play: only `INTERNET`; data-safety form must already state network use for RSS. Data collected: none leaves
the device except the request to the user's chosen host (UNVERIFIED how the form wants this phrased).

### 3.2 System sources (candidate 2)

- **Battery**: sticky `ACTION_BATTERY_CHANGED`, no permission, runtime-registered while observed (VERIFIED).
  Matches the existing "stream starts on first observer, stops on last" `SharedSourceStream` rule, so battery
  cost is zero when no lens shows it.
- **Connectivity / metered**: `ACCESS_NETWORK_STATE` already declared (VERIFIED normal permission).
- **Do not disturb**: the fetched reference summary says reading the interruption filter needs
  `ACCESS_NOTIFICATION_POLICY`; this contradicts common understanding that reading needs no grant, so it is
  UNVERIFIED. Test on a device (API 29 and 34) before deciding; until then do not plan on it.
- Value is as card and header content ("battery 18%, charging"), best as a single-item `Card`. Items are
  `VISIBLE`; no privacy issue. Capability `LIVE`.

### 3.3 ContentProviders of other apps (candidate 5)

- Contacts and MediaStore are platform providers behind dangerous permissions (`READ_CONTACTS`,
  `READ_MEDIA_*`, UNVERIFIED exact set per API). Each would be a new permission and a new data-safety
  entry. Recommend: no generic "pick any provider" feature. If ever wanted, each is a dedicated built-in
  adapter with its own explicit-grant affordance, exactly like Calendar, and a separate decision.
- "Tasks": there is no standard cross-app tasks provider on Android (UNVERIFIED that none is system-level);
  vendor providers differ (some are exported, some require vendor permissions or are not exported). A
  launcher cannot depend on them. This is the use case the extension contract (3.5) exists for.
- Package visibility (VERIFIED): to query a provider by authority Riffle needs a `<queries><provider>`
  entry or an intent filter match, which implies enumerating known authorities at build time.
  `QUERY_ALL_PACKAGES` would be the only way to find arbitrary providers; do not request it for this.

### 3.4 App-hosted widgets as sources (candidate 7)

`AppWidgetHost` yields `RemoteViews` rendering, not data (VERIFIED), and binding needs user consent. Parsing
`RemoteViews` for text is not a supported API (UNVERIFIED, brittle, view hierarchy is private to the
provider). Widgets are better expressed as a **container** (hosted widget area) than a `Source`. The
existing workspace doc lists "an app's hosted widget" under Source examples; this doc recommends
treating that as an `Item.target`-free hosted container, not an item stream. No new Android permission
beyond what widget hosting already needs.

### 3.5 Extension API for third-party apps (candidate 3)

Options compared:

| Mechanism | Direction | Pros | Cons | Verdict |
| --- | --- | --- | --- | --- |
| **ContentProvider (pull)**, provider app exports a documented contract, Riffle queries | Riffle to app | Synchronous, bounded, no process start side effects beyond provider launch, supports `readPermission` and `grantUriPermissions`, change notifications via `ContentObserver`, easy size/row caps, Riffle controls rate | Provider launch wakes the other app; discovery needs `<queries>` or a known authority list | Recommended |
| **Bound service (AIDL/Messenger)** | Riffle to app | Streaming possible | Needs explicit-intent binding (VERIFIED), service launch from background has start restrictions, lifetime and crash handling is heavier | Not now |
| **Broadcast / Intent push** | App to Riffle | Simple for app authors | Implicit broadcasts restricted (API 26, VERIFIED); anyone can spoof a sender unless permission-protected; background wake costs; unbounded rate | Reject |
| **Signature-permission gating** | n/a | Strong; only same-signer apps | Third parties cannot get it unless signed alike, so it only suits first-party companion apps | Use only for an optional first-party tier |
| **Slices** | n/a | n/a | Deprecated from API 35 (VERIFIED) | Reject |
| **App Actions / Shortcuts** | Platform | Shortcuts already feed the Quick actions source | Not a data stream; App Actions is Assistant-driven (UNVERIFIED Riffle can consume) | Keep shortcuts only |
| **Widgets** | n/a | Already supported as containers | No data (3.4) | Not a source |
| **Health Connect** | Platform | Well-defined | Sensitive, per-type permissions, Play health review (UNVERIFIED details) | Defer |
| **Notification listener** | Platform | Already used by the Notifications source | Same grant, already exists | Nothing new |
| **AccessibilityService** | Platform | Could read any screen | Play policy restricts use to accessibility tools / requires prominent disclosure and core functionality; far beyond launcher scope (VERIFIED via search quoting the policy; full text UNVERIFIED) | Reject |

Why pull via provider: the user's consent step and Riffle's caps live in one process, one query shape, one
failure mode (provider missing, throws, times out, returns garbage). It also matches the existing
`ItemSource.subscribe` seam: observer registration on a `ContentObserver`, refresh by re-query.

Discovery without `QUERY_ALL_PACKAGES`: an extension app declares a `<provider>` carrying a manifest
`<meta-data>` pointing to an XML descriptor (see section 7) and an intent-filter-free discovery action
is not available for providers. Practical discovery options, in order of preference:
1. **Explicit user entry**: the user chooses "Add source > From an app", Riffle opens a system
   picker flow through an `Intent` the extension app handles (e.g. an action like
   `com.riffle.action.PICK_SOURCE`, resolved with a `<queries><intent>` entry in Riffle). The
   extension returns its authority and a one-time grant. This keeps discovery user-initiated and
   avoids broad package visibility. VERIFIED that `<queries><intent>` makes matching activities
   visible (API 30+); the whole handshake is a design proposal, UNVERIFIED on devices.
2. A `<queries>` list of known first-party/partner authorities.
3. `QUERY_ALL_PACKAGES`: reject for this purpose (Play review burden, and Riffle likely already has a
   launcher-based justification only for app listing; do not widen it).

## 4. Trust and safety model for an extension surface

Principle: **an external item is data supplied by an untrusted party, shown to the user, and nothing more.**

| Rule | Detail |
| --- | --- |
| Never executable | No scripts, HTML, WebView, or deep-link-triggered code paths run on item content. Text is rendered as plain text; no markup parsing except a closed set Riffle controls |
| No auto-launch | An item target is launched only on explicit user tap. No target resolution at ingestion |
| Target allowlist | External items may only use `ItemTarget.App` / `ItemTarget.Shortcut` with the provider's own package, or `ItemTarget.DeepLink` with scheme allowlist `https` (and optionally the provider's own registered scheme). Reject `intent:`, `javascript:`, `file:`, `content:` (Riffle's own provider) and `android-app:` unless explicit. `ItemTarget.Intent(token)` is not allowed for external sources: it is "source-owned" and has no external meaning |
| Actions | External items get `Open` only at first (and `Dismiss` only as an in-Riffle hide, never a callback to the provider). `ItemAction.Custom` would imply a callback channel; do not allow in v1 |
| Item id | Must be prefixed by the source's id (`ext.<source>.<n>`), validated at ingestion; prevents shadowing built-in items in lens dedupe |
| `ext` keys | Only keys in the source's own declared namespace; reserved namespaces (`launcher`, `media`, `calendar`, `rss`, `notifications`) rejected. Values stay `Text`/`Number`/`Flag`. Length caps apply |
| Size and rate | Caps (proposals, not measured): max 200 items per refresh, title 200 chars, subtitle 300, body 2,000, ext 8 entries and 128 chars each, image handles resolved lazily with a byte cap and downsampling; minimum refresh interval of 15 min via WorkManager, one in-flight query per source, query timeout |
| Images | Provider-supplied image URIs are resolved through the injected `ExpressionImageLoader` with decode limits; `content://` image URIs only from the provider's own authority; `http` rejected |
| Consent | Per-source, explicit, revocable; shows provider app name, icon, and what it will read; no auto-enable on install or update; permission or grant loss returns `PermissionRequired`, not a crash |
| Provenance | Every external item shows its source ("From <app name>") wherever the expression has room, and the editor and Sources settings always show it. Provenance cannot be spoofed because Riffle supplies the label from the package manager, not from the item |
| Privacy | External items default to `SENSITIVE` when the descriptor says `privacySensitive`, and the provider may mark items `SENSITIVE`, but never downgrade a source-level sensitive flag. Redaction stays at the lens `project` step. Content is never persisted, backed up, or logged (same rule as `Item`) |
| Exfiltration | The surface is read-only in v1: no data flows from Riffle to the extension except a source/consumer-neutral query. No Riffle state, other sources, notification content or app list is ever passed to a provider. Riffle never grants a provider URI access to its own storage. Network access remains Riffle's own; extensions cannot cause Riffle to fetch arbitrary URLs except image URIs under the rules above |
| Sandboxing | Parse and validate in Riffle's process using plain data classes; no reflection, no deserialising arbitrary classes (use primitives/`Cursor` columns only, no `Bundle` Parcelables from a provider, which are a known hazard; UNVERIFIED specifics, avoid by design) |
| Crash / uninstall | Provider missing or throws: state becomes `Unavailable` ("<app> is not available"), last items are dropped (content is transient), the source stays in the user's list and lens, and recovers on next refresh or reinstall. Never a stack trace to the UI, never an empty-but-ready lie |
| Ordering | Lens order puts built-ins ahead of external sources by default so a spoof cannot win dedupe |

### Cost of the `ext` extras contract and how much structure to allow

The existing decision (`ext` is namespaced primitives only; promote to a first-class field if an expression
needs richer structure) is the right limit for untrusted producers. Costs and recommendation:

- Allowing arbitrary keys means expressions cannot rely on them, and lens `ByExt`/sort/filter on a
  third-party key makes persisted lenses depend on an app that may disappear. Recommend: persisted lenses
  may reference an external source's ext keys, but a missing source or key yields no match (not an error).
- Structure beyond primitives (nested objects, lists) is rejected. Needs like "progress bar" or "list of
  checklist items" become new first-class optional `Item` fields (e.g. `progress`), added by Riffle, with
  the provider mapping onto them. This puts the evolution burden on Riffle (deliberately) and keeps
  expressions testable against fakes.
- Version the contract with an integer `contractVersion` in the descriptor; Riffle rejects higher
  majors and ignores unknown columns.

## 5. Per-constraint rollup for the recommended set

| Source | Permissions | Background | Play | Battery | Third-party data privacy |
| --- | --- | --- | --- | --- | --- |
| URL feeds (JSON Feed, ICS) | `INTERNET` (declared) | WorkManager periodic, 15 min floor, network + battery-not-low constraints (VERIFIED) | Network use already declared for RSS; data-safety wording UNVERIFIED | Low if refresh is rate-limited and only while a lens uses it | URL may embed a token: treat as secret |
| Battery/connectivity | none / normal (declared) | Runtime-registered receiver/callback only while observed (VERIFIED for battery) | None | Negligible | None |
| Extension provider | none in Riffle; user approval inside Riffle; provider may gate itself with its own `readPermission` | Query on observe and on `ContentObserver` change; no persistent service | Needs `<queries>` (intent or provider) rather than `QUERY_ALL_PACKAGES` (VERIFIED visibility rules); declaring "reads data from user-approved apps" in data-safety UNVERIFIED | Wakes provider process per query; cap rate | Provider data can be sensitive; default to Sensitive-capable, never persisted |
| SAF files | none (user picks), persisted grant (VERIFIED) | Scan on demand; no always-on watcher | None for SAF (UNVERIFIED data-safety text) | Low if bounded | File names and content may be sensitive: render as `SENSITIVE` by default, no content indexing |

Standard launcher mode: none of the above are required by Standard surfaces; with all external sources
absent or removed nothing is empty, blocked, or prompted (same guarantee as the calendar policy).

## 6. Product: discover, add, remove, degrade

Goals: explicit, user-initiated, never an auto-prompt, progressive disclosure (per
`standard-launcher-mode.md` guardrails), Material 3 components.

- **Where.** Settings > Sources (the home for source state, mirroring Settings > Permissions for access).
  Lists each source with status chips: Ready, Needs permission, Unavailable, Not set up. Built-ins first,
  then "Added sources". Each row has provenance (app name or host), last refresh time, and Remove.
- **Add.** One "Add source" action with three choices (progressive disclosure, collapsed under it):
  Feed or calendar URL (RSS, JSON Feed, ICS), From an app (the extension handshake, section 3.5), Folder
  (SAF picker). Each opens a short rationale sheet saying what is read and where it stays (on device), then
  the action. Nothing is requested before the user taps the final confirm.
- **Permissions.** Reuse the Calendar pattern: statuses `GRANTED / NOT_GRANTED / DENIED_PERMANENTLY` mapped
  to `SourceAccess`, decision by a pure "next step" function (rationale, request, open settings). Surfaces
  that need a grant show the rationale beside the button and never trigger the request themselves.
- **Editor (WS7).** The source picker lists only sources that are set up, plus a "Set up a source" entry that
  deep-links to Settings > Sources. Choosing a source whose state is `PermissionRequired` or `Unavailable`
  is allowed but shows the state inline in the preview, so the editor never hides why a lens is empty.
  Lens/expression validity filtering (WS7) is unchanged because `Item` is unchanged.
- **Remove.** Removal drops the item cache and any grant record (RSS precedent), then lens references show
  "source removed" and evaluate to empty; the user can edit the lens or re-add. Config is exported in
  backup, items are not.
- **Uninstalled/crashed provider.** The source remains listed as Unavailable with the missing app named; a
  link opens the app's Play page only on tap (UNVERIFIED whether a Play link needs `<queries>`; avoid
  automatic resolution).
- **Accessibility and motion.** Status is text plus icon, never colour only; list rows expose state in the
  accessibility label; reduced motion applies to any status transitions.
- **Backup/restore.** Source config (ids, URLs, enabled state, approved package + authority) round-trips;
  restore re-validates each entry and never auto-queries or auto-fetches; approvals for apps require
  re-confirmation on the new device (grants are device state).

## 7. Minimal external-source contract sketch

Sketch only (Kotlin, compatible with the existing `ItemSource`/`SourceDescriptor`). No new `Item` fields.

```kotlin
// core/domain (framework-free)
@JvmInline value class ExternalSourceId(val value: String) // "ext.<kind>.<stable-hash>"; maps to SourceId

enum class ExternalSourceKind { FEED_URL, ICS_URL, APP_PROVIDER, FOLDER }

/** Persisted config only. Never contains item content. */
data class ExternalSourceConfig(
    val id: ExternalSourceId,
    val kind: ExternalSourceKind,
    val displayName: String,          // user-visible label, Riffle-chosen or user-edited
    val enabled: Boolean,
    val locator: String,              // https URL, or "package/authority", or SAF tree URI string
    val contractVersion: Int = 1,
    val declaredCapabilities: Set<SourceCapability> = emptySet(),
    val sensitive: Boolean = true,    // default conservative; maps to PRIVACY_SENSITIVE
)

/** The profile every external item must satisfy; applied at ingestion, before Items reach a stream. */
data class ExternalItemPolicy(
    val idPrefix: String,             // "ext.<id>."
    val extNamespace: String,         // the source's own ext namespace
    val allowedTargetSchemes: Set<String> = setOf("https"),
    val maxItems: Int = 200,
    val maxTitle: Int = 200, val maxSubtitle: Int = 300, val maxBody: Int = 2_000,
    val maxExtEntries: Int = 8,
)

fun interface ExternalItemSanitizer {
    /** Returns null for an item that violates the policy; never throws on hostile input. */
    fun sanitize(raw: RawExternalItem, policy: ExternalItemPolicy): Item?
}

/** Platform seam; the app layer implements one per kind. Blocking: call off the main thread. */
interface ExternalSourceClient {
    fun fetch(config: ExternalSourceConfig): ExternalFetchResult
}

sealed interface ExternalFetchResult {
    data class Ok(val items: List<RawExternalItem>) : ExternalFetchResult
    data object ConsentRequired : ExternalFetchResult   // -> SourceState.PermissionRequired
    data object Unavailable : ExternalFetchResult       // missing app, offline, parse failure, timeout
}
```

Mapping to the existing model: an `ExternalSource : ItemSource` wraps a `SharedSourceStream` (one upstream,
replayed, started on first observer), calls the client off the main thread, runs the sanitizer, then emits
`SourceState.Ready(items)`; `ConsentRequired` becomes `PermissionRequired`, everything else `Unavailable`.
`SourceRegistry` gains external ids alongside `SourceIds.BUILT_IN`; built-in ids remain reserved.

Provider contract (third-party app side), one URI, columns only:

```
content://<authority>/riffle/items      (cursor)
  id TEXT NOT NULL, title TEXT, subtitle TEXT, body TEXT, time_ms INTEGER, group_key TEXT,
  group_label TEXT, image_uri TEXT, target_kind TEXT ('app'|'deeplink'), target TEXT, sensitive INTEGER,
  ext_<name> TEXT|INTEGER   -- optional, becomes "<namespace>.<name>"
```

Descriptor (manifest meta-data on the provider, read with `PackageManager`; shape, not final):

```xml
<provider android:name=".RiffleItemsProvider" android:authorities="com.example.tasks.riffle"
          android:exported="true" android:readPermission="com.example.tasks.permission.RIFFLE_READ">
  <meta-data android:name="com.riffle.source" android:resource="@xml/riffle_source" />
</provider>
<!-- res/xml/riffle_source.xml: contractVersion, label, ext namespace, sensitive default, refresh hint -->
```

Gating: for first-party/same-signer apps use a `signature` permission (VERIFIED semantics); for
third-party apps, the provider sets its own `readPermission` (normal/dangerous), and Riffle's user
approval is the real gate. Riffle never relies on the permission alone for trust: the sanitizer always runs.

## 8. Recommendation

Build order:

1. **JSON Feed and ICS URL sources**, after RSS lands. Same security profile and UI as RSS; ICS gives a
   permission-free calendar. Reuse RSS URL validation, cache-clearing and backup rules. Start with ICS
   single events plus a documented RRULE subset; show unsupported recurrence honestly.
2. **System status** (battery, connectivity): proves "source without permission", smallest surface. Defer DND
   until a device test settles whether reading it needs `ACCESS_NOTIFICATION_POLICY`.
3. **External-source scaffolding**: `ExternalSourceConfig`, sanitizer, Settings > Sources list with status
   and removal. Ship it supporting the URL kinds first, so the add/remove/degrade product loop is proven
   without any third-party code.
4. **Provider-pull extension contract** with user-initiated handshake and one reference extension app in
   the repo (a sample, not a product). Version 1 read-only, `https`/`App`/`Shortcut` targets, no custom
   actions.
5. **SAF folder source**: file names and modified times only, `SENSITIVE` by default, no content indexing.

Explicitly not building (and why):

- **AccessibilityService source**: Play policy restricts it to accessibility tools and prominent disclosure;
  it grants far more than any source needs; trust cost is unacceptable.
- **Slices**: deprecated from API 35 (VERIFIED).
- **Widget scraping into Items**: not an API (`RemoteViews` is UI), brittle, privacy-opaque.
- **Bound-service or broadcast push**: spoofing, background-start restrictions, unbounded rate.
- **`QUERY_ALL_PACKAGES` for extension discovery**: do not widen the permission justification.
- **Generic "any ContentProvider" browser** and **arbitrary JSON path mapping editor**: too powerful for
  the safety model.
- **Smart-home/IoT clients, Health Connect, Wear**: reach only via future extension apps; no direct
  integration in the launcher.

### Owner decisions (2026-10-01)

Answers recorded on #1363; the design consequences for configuration are in
[`workspaces-configuration.md`](workspaces-configuration.md) (sections 14 and 15).

1. Third-party extension API: **not yet**, only on demand. The handshake, signing and sample-app questions
   (4, 6, 7) are parked; stop at step 3 of the build order.
2. Tokenised ICS/feed URLs in backup: **no**; re-enter after restore. A tokenised URL is omitted from the
   backup (a pure rule with tests in the configuration doc, section 5).
3. External items in the dock's dynamic section: **allowed** (this differs from the recommendation below,
   which was "containers only at first"). Trust-model and dock-budget implications, to be enforced when the
   first external source ships:
   - the dock is always visible, so external items are ordered **after** built-in items (the "built-ins
     ahead" ordering rule becomes mandatory in the dock) and draw as icon plus count only;
   - external items **share** the dynamic section's slot budget (`notificationSlotCount`, 1 to 5) and never
     extend it; proposal: external sources together occupy at most half the slots and at least one slot is
     kept for built-ins when any exist;
   - provenance ("From <app name>") must be reachable from a dock icon (long-press and a TalkBack action);
   - source exclusion rules and the `OFF` status apply; `PRIVACY_SENSITIVE` items show the icon only.
4. ICS recurrence: **full recurrence support** (this differs from the "documented RRULE subset" assumed in
   the build order and in question 5). Scope: RFC 5545 `RRULE` expansion, `EXDATE`/`RDATE`,
   `RECURRENCE-ID` overrides, time zones including DST, bounded expansion windows and an instance cap.
   **N8 (2026-10-01): use a third-party library**, isolated behind a domain `IcsEngine` interface so it can be
   swapped. Selection criteria, a web-verified shortlist (ical4j, lib-recur, biweekly), the recommended pick
   (ical4j 4.x, lib-recur as fallback) and the spike plan with the RFC 5545 acceptance corpus are in
   [`workspaces-configuration.md`](workspaces-configuration.md) section 15.2 and 15.3. Raises the ICS slice
   from small to medium.
5. Play data-safety and privacy declarations: the **owner** updates them; every PR that adds a permission or
   a data class carries a checklist line (configuration doc, 7.2).
6. Per-lens search queries are in scope now (configuration doc, section 13); this does not affect the
   external-source contract except that a future searchable external source would use the same parameter
   binding.

### Open questions for the owner

1. Is a third-party extension API in scope for v1 of workspaces, or should the roadmap stop at URL sources and
   system status until demand exists? (Recommendation: stop at step 3, ship step 4 only on demand.)
2. May tokenised ICS/feed URLs be included in backup? Default recommended: no (re-enter after restore),
   because the URL is a bearer secret.
3. Are external-source items allowed in the dock's dynamic section, or only inside workspace containers?
   (Recommendation: containers only at first, to limit exposure.)
4. Handshake UX: is an "add from app" Intent flow acceptable, or must discovery be passive? Passive
   discovery needs `QUERY_ALL_PACKAGES` or a known-authority list.
5. Does the project accept ICS recurrence limits (subset) in v1?
6. Should unsigned third-party providers be allowed at all, or first-party/same-signer only initially?
7. Is a sample extension app in this repo acceptable maintenance cost?
8. Who owns the Play Console data-safety and sensitive-permission declarations updates for each new source
   class?

## Verification backlog

UNVERIFIED items needing a device test or a Play policy read before building:

- Reading the DND interruption filter: required permission (API 29, 34 devices).
- Play data-safety wording for "reads data from user-approved apps" and for user-supplied URL fetching.
- Full text of the `QUERY_ALL_PACKAGES` and AccessibilityService Play policies (only summarised by search
  results here; `support.google.com` was blocked).
- Exact API level for `knownSigner` and for `registerDefaultNetworkCallback`.
- That `<queries><intent>` makes the extension handshake activity visible on API 30+ devices and OEM builds
  (documented in general; the handshake itself is a proposal).
- Whether `ContentResolver` queries to a provider in a stopped or restricted-bucket app behave acceptably
  (latency, failure) on API 31+ and Android 14 restricted bucket.
- Whether a Play-store link from an Unavailable source needs a `<queries>` entry.
- Whether any vendor ships a cross-app tasks provider usable without vendor permissions.
