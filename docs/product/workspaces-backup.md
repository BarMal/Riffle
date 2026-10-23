# Workspaces: backup and restore (WS10 S8)

Issue #1394, refs #1363 section 5. This is what is built, why, and what is not.

## As built

The existing launcher backup (`riffleLauncherBackup`, document version 1) now also carries three optional sections.
The document version stays 1, so an older build still reads the file and ignores the keys it does not know.

| Key | Content | Versioned by |
| --- | --- | --- |
| `"workspaces"` | The whole `WorkspaceSet` through `WorkspaceSetCodec`: per layout the workspaces and the saved-lens library. | The codec's schema number inside the object (`version`, currently 2). |
| `"exclusions"` | The per-layout source exclusion rules through `ExclusionRulesCodec` (rule ids, source ids, matcher text the user chose, flags). | The codec's schema number inside the object (`version`, currently 1). |
| `"pool"` | The placed-items pool store (`riffle_pool`) through `PoolStoreCodec`: per layout the apps, folders and widget providers with every arrangement, plus the one-time import flag. | The codec's schema number inside the object (`version`, currently 1). |

Placed items are their own section because they are their own store (owner decision 2026-10-23, #1363): restoring
workspaces never touches placed items and the other way round. Backups made before this change could carry a `pool`
key inside each `"workspaces"` layout; it was never populated at runtime and is ignored.

Pure mapping lives in `core/domain/.../workspace/backup/WorkspaceBackup.kt`; the app only moves it to and from JSON
(`LauncherBackupDocument`) and to the stores (`WorkspaceBackupPort`, `AndroidWorkspaceBackupPort`).

### Export

* A section is written only when data exists: nothing stored, an empty set, or an empty unmigrated rule value is
  omitted. **With the workspace preview never used, the backup is byte-for-byte what it was before** (test
  `nothingStoredMeansTheBackupIsByteForByteTheSame`).
* Data comes from the in-memory repository when it is initialised in this process, otherwise from what is stored.
  The preview and the backup share one pool repository, so an export taken while the preview is open includes unsaved
  pool edits that are still waiting for their debounced write.
  Tapping Export calls `prepareExport()`, which loads the stored values on a background scope while the file picker
  is open (no blocking read; the repo forbids `runBlocking`).
* Hosted widget ids are never written (they are live platform instances on this device). Pool widgets keep provider,
  size constraints and their cell and span.
* Never written: item content, feed articles, notification text, events, search queries, tokens or credentials.
  The sections hold only structure and user-chosen rule text.

### Restore

* Replace semantics, like layouts, settings and hidden apps. A section absent from the file leaves the current data
  untouched (an older backup restores exactly as before).
* Decoding never throws: a section that is not an object, or decodes to nothing, reads as absent. Unknown device
  classes, undecodable workspaces and rules, and arrangements of unknown workspaces are dropped by the codecs.
* Every restored widget is an **unbound placeholder** (`hostedId = null`), keeping its cell and span. Binding is the
  later "Set up widget" flow (pool doc).
* The pool's import flag is restored as recorded, so the next start does not import `HomeLayoutSet` over a restored
  pool. Arrangements are not pruned against the restored workspaces: migrated arrangements are keyed by migrated workspace
  ids (`ws:<class>:<mode>`) that preset workspaces borrow (`PoolHomeView`).
* Dangling lens refs are repaired with `LensLibraryOps`: refs that resolve take the library's lens
  (`rehydrate`), refs that do not become the inline lens they already drew (`detachDangling`).
* A failing store never fails the rest of the import: layouts, settings and hidden apps are applied regardless.
* While the workspace system is not initialised in the process, the restored value is only written to its store; the
  next start reads it and fills gaps through `ensureMigrated`. When initialised, the in-memory copy is replaced and
  observers refresh.
* Exclusions restore leaves the legacy hidden-app / hide-rule migration flag as the backup recorded it, so a deleted
  rule is not re-created.

## Decisions to confirm

1. **Workspace-source enablement is not backed up.** PR #1392 (file `riffle_workspace_sources`, disabled source ids)
   had not merged when this branched, and the setting is device-local (it follows permissions and what the device
   offers). Revisit after #1392 merges if you want it to travel; the pure mapping would be one more optional key.
2. **Document version stays 1**, sections versioned by their codec schema (matches section 5 of the configuration doc).
3. **Replace, not merge**, no Undo yet (see not done).
4. **Feed URLs:** the existing backup already carries them inside `settings`; this slice adds none and does not change
   that. The tokenised-URL omission rule of section 5 is not part of this slice.

## Tests

* `WorkspaceBackupTest` (domain): round trip with library, refs and pool; host ids never written; placeholders and
  counts; dangling refs repaired; resolving refs rehydrated; absent/empty maps to null; exclusions round trip; future
  and older schema; 300 seeds of hostile trees and 300 mutations of a valid blob never throw.
* `LauncherBackupWorkspaceDataTest` (app JVM): byte-identical backup with nothing stored, sections written without a
  host id (and no pool inside `"workspaces"`), widgets come back unbound, older backup leaves data untouched, only
  present sections restored (including a pool-only backup), failing store never fails import or export, malformed and
  hostile sections drop to absent.
* `CachedPoolRepositoryTest` (app JVM): a restore before initialise is only written and the next start does not import
  over it; after initialise it replaces the in-memory pool and persists; a failed write stays pending; export reads the
  store until initialised.

## Not done

* Import summary dialog ("3 workspaces, 5 saved lenses"): `WorkspaceRestore` already returns the counts but the
  decoded document does not carry them to the UI.
* Undo after restore and the `workspaces_prev` generation (configuration doc 1.5).
* An open pool edit session in the preview is not cancelled by a restore; restore with the preview closed (or leave edit
  mode first), or the next edit applies on top of the pre-restore pool.
* Releasing the replaced set's host ids (`deleteHostedWidgetId`) on restore; they stay allocated until the startup
  reconciliation of leaked host ids (pool doc "Not done").
* Restore-time rebinding (`isHostedWidgetBoundTo`) is not attempted: all widgets come back unbound.
* The pre-S4 rebuild from `WorkspaceMigration.migrate(homeLayoutSet)` when a document has no usable `"workspaces"`:
  not needed while `HomeLayoutSet` is still the source of truth and absent data leaves current data alone.
* Workspace-source enablement preferences (see decision 1) and the tokenised-URL omission rule.
* `./gradlew verify` was not run locally (Android Gradle plugin blocked in the authoring sandbox); the domain and the
  Compose-free backup logic were compiled and tested in a scratch Gradle project (see the PR).

## Owner device checklist

1. Settings, Workspaces: turn the preview on, change something visible (add a workspace, save a lens, hide an app
   so a rule exists). Settings, Backup: **Export**, save the file somewhere safe.
2. Optionally open the file: it should contain `"workspaces"`, `"exclusions"` and `"pool"`, no `"pool"` inside
   `"workspaces"`, no `"host"` field and no article or notification text.
3. Wipe: Settings, Apps, Riffle, Storage, Clear data (or reinstall). Set Riffle as Home again.
4. Settings, Backup: **Import** the file. Expect "Backup imported".
5. Turn the preview on. Your workspaces, saved lenses, hidden-item rules and the preview's placed home items (including
   any edits made in the preview) are back; the layout, settings and hidden apps are as before. Any widget shows as a
   placeholder to set up again.
6. Import an **older** backup (one made before this build): your current workspaces and rules are untouched.
7. With the preview never turned on, export again and confirm the file has none of the three keys.
