# Per-lens search queries (as built)

Slice S10 of the Workspaces configuration plan (section 13 of [workspaces-configuration.md](workspaces-configuration.md)),
issue #1363 follow-up. Background: [workspaces-sources-rss-search.md](workspaces-sources-rss-search.md) (the Search source
and its single `SearchQueryHolder`), [workspaces-sources-lenses.md](workspaces-sources-lenses.md),
[workspaces-lens-library.md](workspaces-lens-library.md), [workspaces-editor.md](workspaces-editor.md).
Preview-only: nothing here is reachable with the Workspaces preview off, and no stored schema version changes
(`CURRENT_WORKSPACE_SET_SCHEMA_VERSION` stays 2).

## What it does

Two lenses over the Search source can now carry different query texts, so two pages or widgets can show two
different searches at once. A lens without a query behaves exactly as before: it reads the shared
`SearchQueryHolder`.

## Decision that differs from section 13 (please confirm)

Section 13 sketched `ParameterBinding` slots with the query text never persisted. This slice follows the newer
instruction instead: **the query text is user-authored lens configuration and is stored in the lens**; search
**results are never stored**. Consequences:

- The text is in the workspace set document and therefore in backup and restore. It is bounded (128 characters, one
  per source per lens) and is not echoed into items.
- It is not secret-like, but it can be personal. Every string form that is under this code's control is redacted:
  `SourceParameter.toString()` is the constant `SourceParameter`, so `Lens`, `LensBinding` and registry keys never
  print it. The codec output and the in-memory per-query stream key still hold it by necessity.
- Slots, `ParameterStore`, `SearchBoxContainer` and the "same slot shared by two containers" idea from section 13 are
  **not built**. A shared text box that drives several lenses is still the `SearchQueryHolder` default.

## Domain

- `SourceParameter` (`workspace/ParameterizedSource.kt`): a normalized, never-blank query (trim, cut at
  `MAX_SEARCH_QUERY_LENGTH` = 128). `SourceParameter.query(raw)` returns null for blank text.
- `ParameterizedItemSource : ItemSource` adds `subscribe(parameter, observer)`. `ItemSource.subscribe(observer)` is
  unchanged and stays the default (for Search, the holder). Sources that do not implement it never get a parameter.
- `Lens.parameters: Map<SourceId, SourceParameter>` (last constructor field, default empty). `Lens.parameterFor(id)`
  ignores a parameter whose source is not in the lens.
- `LensCodec`: key `"params"`, an array of `{source, query}`, written only when non-empty, so a lens without
  parameters encodes byte-for-byte as before and old data decodes to an empty map. Decoding is safe: non-array,
  non-object entries, missing or blank fields, sources the lens does not read and duplicates are skipped (first wins);
  queries are re-normalized.
- No schema bump: the key is optional and additive. A reader older than this change drops the key and shows the
  lens without a query (the default).

### Evaluation and sharing

`SharedSourceRegistry.source(id, parameter)` (the existing `source(id)` is unchanged):

- No parameter, or a source that is not a `ParameterizedItemSource`: the one shared stream for the source, as before.
- Otherwise one `SharedSourceStream` per distinct `(source, parameter)`, created on the first observer and dropped
  after the last. Two lenses with the same query share one upstream; different queries get different ones.
- At most `maxParameterStreams` (default 4) are live at once. An observer that would exceed the cap reads
  `SourceState.Unavailable` immediately (never silent, no upstream is started) and a retry after another stream is
  released succeeds. An observer of an already live query is never refused. `liveParameterStreams()` reports the
  count, never the queries.
- Cancelling twice is safe; results are held only as the stream's latest state and are never persisted.

`SourceBackedLensResultProvider` passes `lens.parameterFor(id)` when it attaches to each source, so every consumer of
the real provider (pages, widgets, page-sets, previews) gets per-lens queries with no other change.

An empty query is not an error: with no lens parameter the source reads the holder, whose empty text yields
`Ready(empty)`, the same as `LauncherSearchProvider.search`. A lens never stores a blank query, so a stored parameter
is always runnable. There is no separate "needs input" `SourceState`: adding a variant would break every exhaustive
`when` over it. A UI that wants a "Type to search" hint can show it when the lens has no query and the holder is empty.

## App

`searchSource` (`FeedAndSearchSources.kt`) now returns `ParameterizedSearchSource`: `subscribe(observer)` is the
unchanged holder-driven stream (still `SEARCHABLE` and `LIVE`); `subscribe(parameter, observer)` runs the same
`LauncherSearchProvider` query for the lens text over the same hidden-app-aware snapshot, so ranking and result mapping
are identical. A per-query stream is `SEARCHABLE` and re-queries on app changes (`deps.apps.changes`); the registry
shares and bounds these streams. Descriptor and registry membership are unchanged.

## Editor

All through existing validation and Undo; no new `WorkspaceEdit` type.

- `LensQueryEdits.withQuery(lens, source, text)` / `clearQuery(lens, source)`: pure lens updates (blank clears).
- `LensQueryEdits.setQuery(session, target, source, text, context)` / `clearQuery(...)`: builds the ordinary
  `SetPageBinding` / `SetWidgetBinding` / `SetDockSection` edit and applies it to a `WorkspaceEditSession`, so
  `WorkspaceEditor`'s gate applies and Undo/Redo work. Refusals: `NoExistingBinding` (including `FlowMode.Add`),
  `UnsupportedSource`, `SourceNotInLens`. Setting the same text is not recorded in history.
- A binding that references a saved lens is **detached** when its query changes (the query is part of the lens
  definition). To change a saved lens's own query, use `LensLibraryEditor.editSavedLens` with `withQuery`; the existing
  break policy applies.
- Which sources take a query is `LensQueryEdits.supportsQuery`, currently `SourceIds.SEARCH` only (a stored contract:
  ids are only added). The apps source is `SEARCHABLE` too but has no per-lens query, so it is not offered.
- Flow: `BindingFlowAction.SetQuery(source, text)` and `LensDraft.parameters` (carried by `toLens` and `from`,
  dropped when the source is unselected). Only applies at the Source step for a selected source. No Compose UI field is
  added.
- `LensExpressionValidity` is unchanged: a query changes neither projected fields nor result shapes.

## Tests

Pure JVM tests (in a scratch Gradle project on Maven Central, as `./gradlew verify` cannot download Android
dependencies here; CI runs the real `verify`): codec round trip, absent-when-empty, malformed input, normalization and a
seeded round-trip property; registry per-query results, sharing, release, double cancel, non-parameterized fallback,
unknown source, the cap, the provider end to end, and a seeded subscribe/cancel property (never above the cap, one
upstream per live query, nothing live at the end); redaction sentinel tests; editor set/clear/undo/redo, refusals,
saved-lens detach, the flow action and a seeded edit/undo property; app adapter tests for the lens query, independence
from the holder, no work after the last observer leaves and redaction. ktlint and detekt (repository config) pass on
the same sources.

## Not done

- A Compose text field in the editor Source step (the flow action and draft are ready).
- A visible "Type to search" hint / `NeedsInput` state.
- Slot bindings, `ParameterStore`, `SearchBoxContainer` (section 13); a text box that drives several lenses.
- Source exclusion rules and parameters together are untested beyond ordering (exclusions run on a source's output).
- Debouncing typed edits: each applied query is its own edit and its own stream, so a UI should apply on
  commit or debounce.
- Manual validation on a device: with the preview on, give two pages the Search source, set different queries and
  confirm each lists its own results, then clear one and confirm it falls back to the shared query.
