# Workspaces: source exclusion rules (as built, slice S6)

Design: section 14 of `workspaces-configuration.md`. This file records what is built (issue #1382), the
vocabulary, the differences from the design, and what is not done. Management UI is a separate slice.

## Model (`core/domain/.../workspace/exclusions/`)

* `SourceExclusionRule(id, source, matcher, enabled, label, origin, createdAtEpochMillis)`; `origin` is
  `USER`, `MIGRATED_HIDDEN_APP` or `MIGRATED_HIDE_RULE`.
* `ExclusionMatcher`: `App(package, profile?, activity?)`, `Group(groupKey)`, `ItemKey(itemId)`,
  `Text(field TITLE|SUBTITLE|BODY, value, mode EXACT|CONTAINS|WILDCARD, app?)`, `EmptyContent(app?)`.
  Text normalisation and the `{?}` wildcard are the legacy `NotificationHideRule` ones (shared code, not a copy).
  Generic `SourceKey` ext matchers are not built (see below).
* `ExclusionRuleSet`: the rules of one layout. Pure ops `add`, `edit`, `setEnabled`, `remove`, `merge`
  (union de-duplicated by source and matcher), `forSource`, `isAppHidden(AppIdentity)`. User rules are capped at 500,
  migrated rules are exempt. Undo is restoring the previous value (the set is immutable).
* `ExclusionRuleBuilders`: contextual "hide" rules from an `Item`: `hideApp`, `hideGroup` (feed or category),
  `hideItem`, `hideEmptyContent`, `hideLike(field, generalizeNumbers)`. They return null when the item cannot
  support the rule (no app or group, sensitive item, text shorter than 3 characters). Ids and clock are injected.
* `LayoutExclusionRules(layouts: Map<HomeLayoutDeviceClass, ExclusionRuleSet>, legacyMigrated)`: one set per layout,
  keyed by the plain device class (all five). Owner decision N10 (fold `PHONE_LANDSCAPE` into `PHONE`) is not applied:
  the key is a plain `HomeLayoutDeviceClass`, so folding is a one-line change in the consumer when decided.

## Evaluation

`SourceExclusionFilter.apply(rules, items)` is step 0 of `DefaultLensEvaluator`, before merge, dedupe, the input bound,
the lens filter, sort, limit, group and project. It reads `LensEvaluationContext.exclusions`, which defaults to
`ExclusionRuleSet.EMPTY`, so existing evaluation is identical when nothing is passed (tested). It only removes items.
`matchCounts` gives the "hides N items" numbers (runtime only, never stored).

* A rule applies to items of its own source, and `App`, `Text` and `EmptyContent` rules also apply across a source
  family: `apps.all` / `apps.recent` / `shortcuts`, and `notifications` / `media`. This reproduces the legacy behaviour
  (a hidden app disappears from recents and shortcuts; a notification hide rule also hid media). `Group` and `ItemKey`
  are source-specific.
* Redaction: `Text` and `EmptyContent` never match a `SENSITIVE` item (no content to confirm, so a rule cannot probe
  redacted text). `App`, `Group` and `ItemKey` still apply. Nothing is logged or stored from item content.
* Media items carry the notification text as the subtitle, so `BODY` reads the subtitle for the `media` source.
* The activity of an app or shortcut item is read from its id (`<source>:<profile>:<package>/<activity>`,
  `shortcuts:<profile>:<package>:<activity>:<id>`); `Item` has no activity field.

## Equivalence with the legacy paths (tested on seeded corpora)

* Notification and media items: mapper with `NotificationHideRule`s equals mapper without rules plus the filter over
  the migrated rules (`ExclusionEquivalenceTest`).
* App and shortcut items: `withHiddenApps` equals the filter over migrated hidden-app rules; `isAppHidden` agrees.
* Known differences: (1) a notification from a quiet (redacted) profile is hidden by the legacy `TITLE`/`BODY`/
  `EMPTY_CONTENT` rules on the raw notification, but the new filter sees only the redacted item and cannot match
  content (`APP` rules still apply); this is deliberate. (2) Recent apps resolve a package to one visible activity
  before hiding in the legacy code, so hiding the personal profile's app falls back to the work one; the filter
  hides the resolved item instead (equal when a package has one launcher activity per profile).
  Neither matters until adapters stop applying the legacy filters, which this slice does not change.

## Storage and migration

* Codec: `ExclusionRulesCodec` over `StoredValue`, mirroring the workspace codec's safe decode. It never throws; drops
  unknown device classes, unknown matcher kinds, rules without id or source and repeated ids; a present but
  undecodable `app` narrowing drops the rule rather than widening it. A rule holds only identifiers and the text
  the user chose.
* App layer (`app/.../launcher/exclusions/`): `DataStoreExclusionStore` (own DataStore file `riffle_exclusions`, key
  `source_exclusions`, not in the workspace blob), `ExclusionRulesJsonCodec` (org.json bridge), and
  `CachedExclusionRepository` (synchronous `rules(deviceClass)`, empty until `initialize`).
* `ExclusionMigration.migrate(existing, hiddenApps, hideRules)` copies the global hidden apps and
  `NotificationHideRule`s into every layout: ids `mig:<LAYOUT>:app:<profile>:<package>/<activity>` (source `apps.all`)
  and `mig:<LAYOUT>:notif:<ruleId>` (source `notifications`, always narrowed to the rule's app). It is deterministic
  and idempotent: it sets `legacyMigrated`, after which it returns its input unchanged, so a rule the user later
  deleted, edited or disabled is never re-created or overwritten. Legacy stores are only read.

## Runtime wiring (preview only)

`workspaceLensProvider(..., exclusions = { ... })` puts the current layout's rules in each evaluation's context;
`WorkspaceRuntime.prepareExclusions()` (called when the preview layer composes) loads and migrates them. The layout is
derived from the configuration at evaluation time. With the preview off nothing runs. The classic launcher still applies
hidden apps and hide rules itself; migrated rules are therefore redundant with the adapters' own filtering today.
Rules are read when a lens is evaluated; a lens is not re-evaluated by a rule change alone yet (no management UI exists).

## Not done

* Management and contextual-creation UI, snackbar with Undo, "Hide on all layouts", layout tabs (separate slice).
* Channel, thread and calendar-id keys and the generic `SourceKey` matcher (owner decision N5: after the flip).
* Moving the classic launcher, drawer, search, badges and dock cards to the engine; `AppVisibilityRepository` as an
  adapter over the rules; removing `withHiddenApps` and `NotificationHideRuleFilter` from the adapters.
* Backup (`"exclusions"` document key) and import of pre-S6 backups through the migration (S8).
* Notification variant of `isAppHidden` for non-lens consumers; re-evaluation of observers when rules change; the
  N10 landscape fold.
