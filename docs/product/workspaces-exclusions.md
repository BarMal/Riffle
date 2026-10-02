# Workspaces: source exclusion rules (as built, slice S6)

Design: section 14 of `workspaces-configuration.md`. This file records what is built (issue #1382; the management
UI is issue #1402, see "Management UI" below), the vocabulary, the differences from the design, and what is not
done.

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
Rules are read when a lens is evaluated; since the management UI (below) a rule change also re-evaluates live lenses.

## Management UI (as built, issue #1402)

Settings > Developer > Sources > **Hidden items and rules** (`SettingsPage.EXCLUSIONS`, reached from the row that
replaced the "coming soon" placeholder on the Sources page; same preview gate as Workspaces and Sources, so with
the preview off nothing exists and nothing runs). Details of the page are in
[`workspaces-settings.md`](workspaces-settings.md).

* **Layer split.** Domain (`core/domain/.../workspace/settings/`): `ExclusionsSettingsPlanner` (model: rules grouped by
  kind APPS, FEEDS_AND_GROUPS, ITEMS, TEXT, EMPTY_CONTENT, with a plain description, "only on this layout", match count),
  `ExclusionRuleDescriber`, `TextRuleValidator`, `ExclusionsSettingsAction` (`SetEnabled`, `Delete`, `AddText`,
  `ApplyToAllLayouts`) with `applyTo` returning an `ExclusionsChange` (whole `before`/`after` value, so Undo is putting
  `before` back), and `ExclusionHideActions` (the contextual Hide, below). App, Compose-free
  (`app/.../launcher/exclusions/`): `ExclusionsSettingsController` over `CachedExclusionRepository`,
  `ExclusionMatchCounter`, `ExclusionsSettingsText` / `ExclusionsAnnouncements`. Compose: `SettingsExclusionsPage`,
  `ExclusionsSettingsRows`, `ExclusionsSettingsDialogs`.
* **Change signal (the old known limitation).** `CachedExclusionRepository` now owns every change after load:
  `update(transform)` replaces the cache at once, persists the latest value in order on a scope (writes are disabled for
  the process if the initial read threw, as before), bumps `version`, and notifies listeners. It is the
  `ContextChanges` (new `fun interface` next to `SourceBackedLensResultProvider`) that `workspaceLensProvider(...,
  exclusionChanges = ...)` hands to the provider: each live observation subscribes and, on a change, re-runs its latest
  source states through the evaluator (fresh `LensEvaluationContext`, so the new rules apply) without re-reading or
  re-subscribing any source; cancelling the observation detaches it. The first `initialize` also notifies, so a lens
  that composed before the rules loaded fixes itself. With no signal passed (default) behaviour is unchanged.
  `SourceBackedLensResultProvider`'s constructor gained `contextChanges` before the trailing `context` lambda.
* **Per layout and N10.** Rules are viewed and edited for the layout selected in the layout tabs, keyed by the plain
  device class exactly as stored (N10, folding `PHONE_LANDSCAPE` into `PHONE`, is still **not** applied; the page shows the
  layout name, and a rule with no equal rule on another listed layout carries "Only on this layout"). "Apply to all
  layouts" adds a copy (fresh id, user origin, enabled state kept) to every other layout, de-duplicated by source and
  matcher, so it is idempotent; it is undoable.
* **Actions.** Switch per rule (the whole row; no Undo, it is its own inverse); Delete asks first, announces in the Settings
  snackbar (polite live region) with Undo. Add (text rules) and Apply to all layouts also offer Undo. Only the latest
  undoable change can be undone: any later change, dismissal, leaving the page, or a change to the rules from elsewhere
  (the repository version moved) ends the offer, so Undo never overwrites something newer.
* **Text rules.** Source (notifications, media, calendar, RSS, apps), field (title, subtitle, body), mode (exactly,
  contains, pattern with `{?}`) and the typed text; the value is stored normalised like the evaluator does. Validation is
  pure and shown live: at least 3 characters that are not wildcards (the same minimum as "hide ones like this"), at most
  120, a supported source, not a duplicate by source and matcher, the 500 user-rule cap. Optional app narrowing of a typed
  rule is not offered (migrated and contextual rules keep theirs).
* **Counts.** `ExclusionMatchCounter` reads (through the same shared, enablement-wrapped registry the containers and the
  Sources page read through, so no upstream of its own) only the sources the viewed layout's rules can hide from, while the
  page is open, and computes `SourceExclusionFilter.matchCounts` on a background thread (stale results dropped, at most
  2,000 items per source). A rule has a count only when it is enabled and its own source is Ready; otherwise the row says
  "Turned off" or "Source is off" or shows nothing (loading or no permission). Items are never rendered, logged or stored;
  they are dropped on close. A rule row shows only the generated description: an app is a package (with profile or launcher
  entry), a group is the feed or group key, an item rule is "One specific item" (its key is never shown), a text rule shows
  the pattern the user authored.
* **Contextual "Hide this" (as built, issue #1410).** `ExclusionHideActions.choicesFor(item)` lists what an item supports
  (app, feed or group, item, like this, empty content), with the design's per-source vocabulary ("Hide notifications from
  this app", "Hide this feed", "Hide this article", ...), built on the existing `ExclusionRuleBuilders` (so sensitive items
  offer only the structural ones and "like this" needs text of at least 3 characters). `menuChoicesFor(item)` is what the
  menu shows: the same list without empty-content (the four choices of the design's menu).
  `ExclusionHideActions.apply(rules, layout, item, kind, allLayouts)` returns an undoable `ExclusionsChange` whose message is
  "Hidden on <layout>: <fixed phrase>" (never item text, and the domain has no app label, so the phrase names the kind of
  thing hidden, for example "notifications from this app"); hiding something whose rule exists but is switched off turns it
  back on; an equal enabled rule is a no-op ("Already hidden").
  * **Where it is offered.** Only in the Workspaces preview, only when the runtime has exclusion rules (otherwise
    `PreviewHideHost` draws its content alone). Expressions read `LocalItemHider` (default null: nothing extra is drawn, so
    the editor's draft previews and every existing screenshot are unchanged). ADR 0002 prefers platform primitives over new
    gesture arbitration, so the primary affordance is an ordinary **"More options" button** (48 dp, a plain click) on List,
    Index, Alpha list, Card and the focused Card stack card, plus a **TalkBack custom action per choice** ("Hide this feed",
    ...) on the same rows and cards. Icon-only cells (Icon row, Icon grid, Categories icons) have no room for a button, so
    they get a **long press** (a labelled long-click action, the only other gesture there is a scroll) and the same custom
    actions. Cards behind the focused card in a stack carry neither button nor actions.
  * **Snackbar.** `PreviewHideHost` owns a `SnackbarHostState` above the dock bar (88 dp clearance, safe-drawing insets) and
    shows `ExclusionsSettingsController.feedback`: the polite live-region message, **Undo**, and, after a hide on one layout,
    **Hide on all layouts**. `ExclusionsSettingsController.hide(...)` keeps the hidden `Item` in memory only until the
    announcement ends (dismissed, undone, replaced, another change, leaving the preview) so `hideOnAllLayouts()` can repeat
    it for every layout (equal rules are not duplicated; it has its own Undo). Item content is never stored, logged or put in
    a message. The preview builds its own controller over the shared `CachedExclusionRepository` (`WorkspaceRuntime.hideController()`);
    the Undo guard (repository version) makes it safe next to the Settings page's controller.

* **Test note.** The add-text-rule dialog has no Roborazzi test: its text field's cursor-blink animation never lets the
  Compose test clock idle (idle timeout, and pausing the clock hung the job). Dialog interaction is validated manually
  (owner checklist); the logic is covered by `TextRuleValidator` (including `actionFor`, the button's draft-to-action
  mapping) and controller JVM tests. Page-level and delete-dialog screenshots remain.

## Not done

* Contextual Hide: the snackbar's "Manage" action (it needs a way to open Settings > Hidden items and rules from the preview)
  and the app-label wording of the TalkBack action ("Hide Slack notifications": the domain has no app label, so the actions
  say "Hide notifications from this app"); the Empty-content choice has no menu entry; hiding from the editor's draft
  previews is not offered on purpose. Not validated on a device (see the dogfood checklist).
* Editing a rule (change mode or value) and the live preview of matching items while typing; naming a rule (`label`).
* Optional app narrowing for a typed text rule; the "Copy these rules to the other layouts" bulk action and the
  "Also add the hiding rules" option of Copy from other layout.
* Channel, thread and calendar-id keys and the generic `SourceKey` matcher (owner decision N5: after the flip).
* Moving the classic launcher, drawer, search, badges and dock cards to the engine; `AppVisibilityRepository` as an
  adapter over the rules; removing `withHiddenApps` and `NotificationHideRuleFilter` from the adapters. Until then a rule
  edited here affects the preview and what feeds it; the classic launcher still applies the legacy hidden apps and hide rules.
* Backup (`"exclusions"` document key) and import of pre-S6 backups through the migration (S8).
* Notification variant of `isAppHidden` for non-lens consumers; the N10 landscape fold.
