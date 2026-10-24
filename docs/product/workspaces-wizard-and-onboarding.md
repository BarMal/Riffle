# Workspaces: content wizard and first-time walkthrough (domain, as built)

Status: **domain built and tested; the wizard UI is built (section 6).** Package
`core/domain/.../launcher/workspace/wizard/`. Two UI slices build on this API: the wizard screens (section 6) and the
walkthrough screens. Refs #1363. Builds on the model in
[`workspaces-sources-lenses.md`](workspaces-sources-lenses.md), the editor in [`workspaces-editor.md`](workspaces-editor.md),
saved lenses in [`workspaces-lens-library.md`](workspaces-lens-library.md), the pool in
[`workspaces-pool.md`](workspaces-pool.md), and the default home in [`workspaces-default.md`](workspaces-default.md). It
replaces the "Choose your home style" sheet of `workspaces-configuration.md` 1.3.

## Owner requirements

- **A wizard** for configuring sources and lenses, opened from: the workspace editor's "Add page or widget"; Settings >
  Saved lenses > New; a source detail page's "Use this source" (source pre-selected); and, in a compact form, the
  first-time walkthrough. The advanced lens builder stays reachable ("Open advanced builder").
- **A first-time walkthrough, goal-first only** (no preset picker). The user says what they want on their home screens
  and the walkthrough builds it. It runs by itself on a fresh install; an existing user gets a one-time, dismissible
  "Set up your home" offer after updating; it can always be run again from Settings. Permissions are asked in-flow, on
  one step after the content is chosen, with an explanation before each system dialog, and can be skipped: skipped
  content stays in place and shows "Needs access".

## Principles

- **Goals, not sources.** The user picks "Notifications" or "My apps"; a goal is a recipe (sources, default lens, the
  valid looks and where they can go). Sources stay an implementation detail until the advanced builder.
- **Only valid choices.** Every option comes from `LensExpressionValidity` (through `ContentRecipes`) and from the
  editor's `ContainerOptions` (so from `WorkspaceEditor`): an option is enabled exactly when applying it would be
  accepted. Disabled options stay visible with a reason.
- **Pure reducers.** `(state, action, context) -> state`, as the editor's `BindingFlowReducer`. A stale or off-step
  action returns the state unchanged. Applying is a separate pure function that re-validates.
- **Never destroy data.** The walkthrough adds a workspace (or updates its own) and keeps everything else; only a fresh
  install's untouched seeded default is replaced. Every apply returns what the host needs for Undo.
- **No new permission behaviour.** The domain never requests anything. The walkthrough asks the host to open the same
  explicit flows Settings > Sources uses, and only after the explanation is on screen.

## Files

| File | What |
| --- | --- |
| `ContentGoal.kt` | `ContentGoalId`, `Destination`, `GoalPicker`, `GoalFilter`, `GoalLook`, `ContentGoal`, `AppPick`, `FeedOption` |
| `ContentGoalCatalog.kt` | The goals (data) |
| `ContentRecipes.kt` | `GoalSetup`, `SetupProblem`, lens building, fit checks, setup edits |
| `SourcePermission.kt` | `SourcePermission` and the source -> permission map |
| `WizardModel.kt`, `WizardFlow.kt`, `WizardReducer.kt`, `WizardApply.kt` | The wizard |
| `PlacedApps.kt` | Picked apps into the pool (shared by both) |
| `WalkthroughModel.kt`, `WalkthroughFlow.kt`, `WalkthroughReducer.kt`, `WalkthroughBuilder.kt`, `WalkthroughApply.kt` | The walkthrough |
| `Onboarding.kt` | `OnboardingState`, the launch rules (`Onboarding`) and `OnboardingCodec` |

## 1. Content goals (`ContentGoalCatalog`)

A `ContentGoal` declares: `sources`, `baseLens` (default sort and limit), `filters` (first is the default), `looks`
(first is the default; each an expression, the lens shaping it needs, and the `placements` it suits, best first),
`pickers`, `placedItems`, a `label` and a `description`. `permissions` is derived from the sources. `key` is stable
(use it for string resources); goals are not persisted.

| Goal (`ContentGoalId`) | Sources | Needs | Filters | Looks (destinations) | Pickers |
| --- | --- | --- | --- | --- | --- |
| My apps (`MY_APPS`) | `apps.all`, A to Z | none | All apps, Personal only, Work only | Grid (page, widget); A–Z list (page, widget); Categories (page, widget) | |
| Favourite apps (`FAVOURITE_APPS`) | `home.grid` (placed) | none | | Icons (page) | Apps to place |
| Recent apps (`RECENT_APPS`) | `apps.recent`, newest | Usage access | | Row of icons, 8 (widget, dock, page); List (page, widget); Grid, 12 (page, widget) | |
| Notifications (`NOTIFICATIONS`) | `notifications`, newest | Notification access | Everything, Last 24 hours, Personal, Work | By app: card stacks, a page per app (page-set); List; Cards; Grouped list (page, widget); App icons, 5 (dock, widget) | Only these apps |
| Media controls (`MEDIA`) | `media`, limit 1 | Notification access | | Card (widget, page) | |
| Calendar (`CALENDAR`) | `calendar`, soonest | Calendar | | Next event (widget, page); Upcoming (widget, page); By day (page, widget) | |
| Calendar feeds (`CALENDAR_FEEDS`) | `ics`, soonest | none | | By day; Upcoming (page, widget); Next event (widget); A page per calendar (page-set) | Feeds |
| News and feeds (`NEWS_FEEDS`) | `rss`, newest | none | Everything, Last 24 hours | Cards; Headlines (page, widget); A page per feed (page-set); Top story (widget) | Feeds |
| Search (`SEARCH`) | `search` | none | | List; Cards (page, widget) | Search text |
| Quick actions (`QUICK_ACTIONS`) | `shortcuts` | none | | Row of icons, 8 (widget, dock); List (page, widget) | |
| Widgets from my home (`HOME_ITEMS`) | `home.grid` (placed) | none | | As it is (page) | |

Every listed destination also allows **Save as a lens only** (`Destination.LENS_ONLY`), except for placed goals.

`ContentRecipes`:

- `defaultSetup(goal, apps)`; `lens(setup, look, pageKey)`, `binding(...)`: base lens, then the filter AND the picker
  filter (`GroupKeyIs` per app, `package:profile`, or per feed key; `AnyOf` for several), the look's group, sort and
  limit, and the search query as the lens's `SourceParameter`. A placed goal's lens is `home.grid` page `pageKey`.
- `fits(setup, look, destination, sources)`: `LensExpressionValidity.check` (per group, and not sideways-scrolling, for
  a page-set; either for a lens-only save); `looksFor`, `destinationsFor`, `problems` (`PICK_AN_APP`,
  `LOOK_DOES_NOT_FIT`).
- Edits that keep a setup consistent: `withFilter`, `withLook`, `toggleApp`, `toggleFeed` (only offered feeds),
  `withQuery` (normalized like `SourceParameter.query`), `reconcile(setup, destination)`.

`SourcePermission`: `NOTIFICATION_ACCESS` (notifications, media), `CALENDAR`, `USAGE_ACCESS` (recent apps), the same
mapping as the app's `sourceAccessRouteFor`. `accessOf(permission, accessMap)`.

## 2. The wizard

### Context and start

```kotlin
val context = WizardContext(
    entry = WizardEntry.Editor(workspaceId, pageId = gridPageOrNull), // or NewSavedLens, or FromSource(sourceId, activeWorkspaceId)
    sources = SourceChoices.build(registry.descriptors(), access),   // exactly as the editor builds it
    layout = layoutWorkspaces,                                        // the layout shown (N10: phone for landscape)
    capabilities = layoutCapabilities,
    feeds = rssAndIcsFeeds.map { FeedOption(sourceId, groupKey, label) },
)
var state = WizardFlow.start(context)
state = WizardReducer.reduce(state, action, context)
```

`home.grid` is never in the registry; `context.descriptors` adds it, so placed goals validate.

### Steps (`WizardFlow.steps(state, context)` is the visible sequence; show "Step n of m" from it)

| Step | Render with | Actions | Shown when |
| --- | --- | --- | --- |
| `SOURCE` | `goalChoices(context)`: `GoalChoice(goal, availability, needs)`. `READY`, `NEEDS_ACCESS` (choosable, badge), `UNAVAILABLE`, `ALREADY_THERE` (disabled, with reason) | `ChooseGoal(id)` | not pre-selected |
| `CONTENT` | the goal's `filters` (`state.setup.filterId`), and per picker: apps, feeds (`context.feeds` for the goal's source), search text | `ChooseFilter`, `ToggleApp`, `ToggleFeed`, `SetQuery` | the goal has more than one filter or a picker |
| `LOOK` | `lookChoices(state, context)`: `LookChoice(look, enabled, destinations)`; live preview `preview(state)` | `ChooseLook(id)` | always |
| `PLACE` | `destinationChoices(state, context)`: `DestinationChoice(destination, enabled, rejection, widgetTargets, replaces)` | `ChooseDestination(destination, target)` | not `NewSavedLens` |
| `SAVE` | `saves(state)`, `name(state, context)` (the user's, or a unique suggestion), `problems` | `SetSaveToLibrary`, `SetName` | not a placed goal |
| `DONE` | (closing) | | always last |

Navigation on every step: `Next` (advances only when `problems(state, context)` is empty; on the last step it finishes),
`Back` (one step; on the first step it cancels), `Cancel`, `OpenAdvanced`.

- **Entry rules.** `Editor`: save is on by default; with `pageId`, Place starts on a widget there. `FromSource`: the goal
  reading that source is pre-selected and the Source step is skipped (Back on the first step cancels); save is off by
  default. `NewSavedLens`: no Place step, the destination is `LENS_ONLY`, saving is required, placed goals are unavailable.
- **Preview.** `WizardPreview(binding, perGroup, placed, apps)`: draw `binding` with the existing hosts
  (`LensBoundExpression`; `PageSetContainerHost` when `perGroup`) over the caller's `ContainerServices`, as the editor's
  preview does. `placed`: draw `apps` as icons. Transient, never stored.
- **Problems** (`WizardProblem`): `NoGoal`, `GoalUnavailable`, `Setup(PICK_AN_APP | LOOK_DOES_NOT_FIT)`,
  `NoDestination`, `DestinationRejected(EditRejection?)`, `Name(LibraryProblem)`, `NoWorkspace`. Show the current step's
  problems next to the control and disable Next; announce them as a polite live region.
- **Exit** (`state.exit`): `Cancelled` (close, nothing changed); `Advanced(handoff)` (open the editor's lens builder:
  `BindingFlowState(draft = handoff.draft, expression = handoff.expression)` and the container kind, or the Saved lenses
  page's draft for `NewSavedLens`); `Finished` (apply, below).

### Applying (`WizardApply.apply(state, context, target)`)

```kotlin
val target = WizardTarget(
    layout = storedLayoutNow, pool = storedPoolNow,
    candidates = PoolHomeView.candidates(workspaceId, layoutClass, shownMode), ids = WorkspaceIdFactory.Random,
)
when (val result = WizardApply.apply(state, context, target)) {
    is WizardResult.Applied -> save(result.layout) and, if result.poolChanged, result.pool, in one write
    is WizardResult.Rejected -> show result.problems (stale state: the layout changed while the wizard was open)
}
```

- A saved lens is added with `LensLibrary.tryAdd` and the binding references it (`LensBinding.ref`), so a later edit in
  Settings > Saved lenses reaches the container.
- The container goes in with `ContainerEdits.build` and `WorkspaceEditor.apply` (re-validated). `containerId` names the
  new page or widget; `edit` is that one `WorkspaceEdit`.
- **From the editor**, apply `result.edit` to the editor session (`session.apply(edit, context)`) and take the library
  from `result.layout.library` into the session's `LensScope`, so Undo, Redo, Revert and the discard prompt cover it;
  the editor's Done then saves as today (`LensScopeChange.applyTo`). Picked apps (`result.pool`) are saved at once
  (the pool has its own store and Undo).
- **Placed goals** (Favourite apps): the apps go into the pool with `PlacedApps.place` on a new arrangement page. A
  workspace that already shows a placed home keeps it: it is forked first if it borrowed (`PoolArrangements.fork`), then
  the page is added; it gets no second `home.grid` container. A workspace without one gets a `home.grid` page first.
  An app it already shows is not added twice.

## 3. The walkthrough

### Context and start

```kotlin
val context = WalkthroughContext(
    origin = onboarding.origin,                // FRESH_INSTALL or EXISTING_USER
    sources = SourceChoices.build(registry.descriptors(), access),
    layout = layoutWorkspaces, pool = poolForLayout,
    suggestedApps = mostUsedOrDockApps,        // pre-picked for Favourite apps
    suggestedDock = defaultDockApps,           // pre-picked dock on a fresh install
    feeds = ..., globalGestureActions = DockMenuReachability.globalActions(homeGestures),
    capabilities = layoutCapabilities,
    firstRun = onboarding.walkthrough != WalkthroughProgress.COMPLETED,
)
var state = WalkthroughFlow.start(context)
state = WalkthroughReducer.reduce(state, action, context)
```

Rebuild the context (fresh `sources`) after each permission result: access is read from it.

### Steps (`WalkthroughFlow.steps`; Options and Permissions are skipped when empty)

| Step | Render with | Actions |
| --- | --- | --- |
| `WELCOME` | static | `Next`, `Cancel` |
| `GOALS` | `goalChoices(context)`, ticked = `state.chosen`. Preselection: fresh install: Favourite apps (when apps are suggested), My apps, Notifications, Calendar; existing user: Widgets from my home, My apps, Notifications | `ToggleGoal` |
| `ARRANGE` | `state.goals` in page order with `role` (`PAGE`/`WIDGET`), `rolesFor(setup, context)` for the segmented control, `state.startGoal` | `SetRole`, `MoveGoal(goal, toIndex)` (button form, placed pages stay first), `SetStartGoal` |
| `OPTIONS` | one goal at a time: `currentOptionGoal`, its filters, `looksFor(state, goal, context)` (looks that fit its role), its pickers; "n of m" from `optionGoals` | `SetFilter`, `SetLook`, `ToggleApp`, `ToggleFeed`, `SetQuery` (each takes the goal) |
| `DOCK` | `state.dockPins` (max `context.dockCapacity`), `canHideDock(context)` | `ToggleDockPin`, `SetDockHidden` (refused unless a gesture opens the workspace menu: the S7 rule) |
| `PERMISSIONS` | `neededPermissions(state, context)`, each with `permissionStatus` (`PENDING`, `EXPLAINING`, `REQUESTED`, `GRANTED`, `DENIED`, `SKIPPED`) and the goals that need it | `ExplainPermission`, `RequestPermission`, `PermissionResult`, `SkipPermission` |
| `REVIEW` | `review(state, context)`: `WalkthroughReview(workspaceName, pages, dockPins, dockHidden, needsAccess, replacesDefault, updatesExisting)` | `Next` builds |
| `DONE` | (closing) | |

Until the user arranges by hand (`state.arranged`), the arrangement follows the default proposal: placed pages first
(one page), then widgets (one widgets page), then the other pages, each in catalog order; a goal's role follows its
default look. Placed pages always come first because the home draws its placed pages before its lens pages (S9).

**Permission flow** (per permission, never two at once): "Allow" sends `ExplainPermission` (the rationale is shown);
"Continue" sends `RequestPermission`, which sets `state.pendingRequest`; the host then runs the existing explicit flow
(`SourceAccessLaunchers.request` for a source of that permission: calendar rationale and dialog, the notification
listener settings, the Usage Access settings) and reports `PermissionResult(permission, granted)`. While a request is
pending, Next and Back do nothing. "Not now" sends `SkipPermission`; leaving the step skips the rest. `RequestPermission`
without the explanation first is ignored.

**Exit**: `Cancelled` (call `Onboarding.skipped`; nothing changed) or `Finished` (apply).

### Applying (`WalkthroughApply.apply(state, context, target)`)

```kotlin
val target = WalkthroughTarget(layout, pool, candidates = PoolHomeView.candidates(WalkthroughFlow.WORKSPACE_ID, layoutClass, shownMode))
val result = WalkthroughApply.apply(state, context, target) as WalkthroughResult.Applied
// one write: result.layout (workspace store) and result.pool (pool store); then Onboarding.completed;
// snackbar "Your home is ready" with Undo -> write result.undo.layout and result.undo.pool back.
```

- Builds the workspace "My home" (`WalkthroughFlow.WORKSPACE_ID` = `walkthrough:home`, deterministic container ids
  `walkthrough:home:<goal>`), validated before anything is written.
- **Fresh install, first run** (`context.replacesDefault`): the seeded default is replaced (a Nova preset workspace
  with no arrangement of its own, so never edited) and "My home" becomes the default; the dock pins become the picked
  apps when any were picked. **Otherwise** (existing user, or any re-run): every workspace is kept, "My home" is added
  (or, from an earlier run, updated in place) and activated; the default does not move; the dock only gains picked apps
  it lacks, up to its capacity.
- Favourite apps go into the pool on page `walkthrough-apps` (`PlacedApps`); with "Widgets from my home" the current
  home is kept (borrowed, or forked when apps are added). A dock that is not in the pool yet is left alone
  (`dockSkipped`).
- Skipped permissions change nothing in the build: the content is there and its source reports `PermissionRequired`,
  which the existing hosts draw as "Needs access" with "Review access".
- Applying the same state again gives the same layout and pool (idempotent).

## 4. Onboarding state

`OnboardingState(origin, walkthrough, offer, completedRuns)`; the rules are `Onboarding`:

- `atLaunch(state, freshInstall)` once per process start. `freshInstall` = nothing was stored before this launch
  (`WorkspaceBootstrap` had to seed every layout). The origin is decided the first time only. Returns
  `RUN_WALKTHROUGH` (fresh install, not completed or skipped: an interrupted run comes back), `SHOW_OFFER` (existing
  user, once: showing it records `SHOWN`, so it never comes back), or `NONE`. Persist the returned state.
- `started`, `completed` (counts runs, settles the offer), `skipped` (keeps an earlier completion), `offerDismissed`,
  `offerAccepted`, `rerun` (Settings, always allowed).

**Storage: device-local, not in backup.** `OnboardingCodec` encodes it over `StoredValue` (version 1; decode never
throws; unknown values read as defaults; no value reads as "not decided", so the app decides from `freshInstall`). Store
it in its own preference (suggested `riffle_onboarding`), not in the workspace blob: it describes this install, and a
restore onto a new phone brings the workspaces the walkthrough made, so asking again there would only duplicate them.

## As built (UI): the walkthrough and onboarding

Status: **walkthrough UI, offer card, onboarding store and Settings entry built; not compiled here and never run on a
device** (Compose and the Android wiring were reviewed by hand; the Android-free parts were compiled and tested, see
Verification below). Package `app/.../launcher/onboarding/`. It uses only the walkthrough's own `WalkthroughFlow` /
`WalkthroughReducer` APIs (per-goal options included) and nothing from the content wizard UI (`launcher/wizard/`); a
later pass can share rows between them.

| File | What |
| --- | --- |
| `OnboardingStore.kt`, `SharedPreferencesOnboardingStore.kt` | `OnboardingStorePort`; `OnboardingCodec` as JSON in the `riffle_onboarding` preferences file (key `state`), read lazily; an in-memory store for tests |
| `OnboardingController.kt` | `OnboardingSurface` (`None`, `Offer`, `Walkthrough(origin, firstRun)`) and the state holder over `Onboarding`: `decideAtLaunch`, `dismissOffer`, `acceptOffer`, `rerun`, `walkthroughCancelled`, `walkthroughCompleted` |
| `WalkthroughSession.kt` | One run: `WalkthroughUi(state, context)`, `dispatch`, the permission round trip, `reopenReview` |
| `WalkthroughContexts.kt` | `WalkthroughInputs` -> `WalkthroughContext` (suggested apps and dock, feeds, gestures); the picker app list; `SourcePermission.flowSource()` |
| `WalkthroughApplier.kt`, `RepositoryWalkthroughStores.kt` | `WalkthroughApply` over the workspace and pool repositories, the two home guards, Undo |
| `WalkthroughScreen.kt`, `WalkthroughSteps.kt`, `WalkthroughComponents.kt` | The stateless screens (`WalkthroughScreen(model, onAction)`) |
| `OnboardingOfferCard.kt` | The existing-user card |
| `OnboardingLayer.kt`, `OnboardingHost.kt` | The shell glue (`LauncherShell(onboarding = ...)`), the process-wide controller, the platform wiring |
| `WalkthroughText.kt` | The copy of section 5 |

Shared files, additive only: `LauncherShell.kt` (an `onboarding` parameter, the layer after the workspace editor, the
Settings launcher local), `MainActivity.kt` (builds the host), `SettingsWorkspacesSection.kt` (the row),
`SettingsLauncherSearchEntries.kt` (the search entry), `CachedWorkspaceRepository.kt` (`foundStored`),
`pool/PoolHomeStartup.kt` (`HomeLayoutOpening.nothingStored`), `DataStoreHomeLayoutStore.kt`
(`HomeLayoutRuntime.nothingStoredAtStart`), `res/xml/backup_rules.xml` and `data_extraction_rules.xml` (exclude
`riffle_onboarding.xml`).

### At launch, and the order with the first-run flow

1. The process opens the home (`openHomeLayouts`) and reads the workspaces with S9's bounded startup read. Nothing about
   onboarding is read before the first frame.
2. **The Home-role card first** ("Set up Riffle", `shouldShowSetupCard`). Onboarding waits while it is shown and while a
   Home-role request is in flight (`FirstRunStatus.REQUESTING_HOME_ROLE`), so the two never stack and the Home-role
   request is never delayed. The card goes when Riffle is the Home app or the user taps "Not now".
3. Then, once per process (`OnboardingProcess` holds one controller), `decideAtLaunch` reads `riffle_onboarding` on
   `Dispatchers.IO` and calls `Onboarding.atLaunch`. **Fresh install** = the home layout store held nothing and had no
   recovery snapshot at this start (`HomeLayoutOpening.nothingStored`, and the start was neither read-only nor damaged)
   **and** the workspace store was readable and empty (`CachedWorkspaceRepository.foundStored == false`). A failed read
   never counts as fresh. It is asked only while the origin is undecided.
4. `RUN_WALKTHROUGH`: the walkthrough opens full screen over whatever is shown (`Onboarding.started` is stored, so an
   interrupted run comes back at the next launch). `SHOW_OFFER`: the card at the top of the home, on the home only; it
   was recorded as shown at this launch, so it is not shown at another one. `NONE`: nothing.
5. Settings > Workspaces > **Set up your home again** (`Onboarding.rerun`, `firstRun = false`) opens it at any time over
   Settings; launcher search finds it under "walkthrough", "setup", "set up" and "onboarding" (the entry opens the main
   Settings page, whose Workspaces section holds the row). A walkthrough opened from Settings is never replaced by the
   launch decision.

### The screens

- **Layout.** Full-screen `Surface` inside `WindowInsets.safeDrawing`. Top: "Step n of m" (from `WalkthroughFlow.steps`,
  so Options and Permissions count only when shown), "Skip set-up" (every step; it keeps the home as it is), a linear
  progress bar and the step's name. Bottom: Back ("Skip for now" on Welcome) and the primary action ("Get started",
  "Next", "Build my home"). The step body scrolls on its own (`LazyColumn`), so the bars stay reachable at any font
  size. At 600 dp and wider (an unfolded foldable, a tablet) a step list sits beside the step (done steps ticked, the
  current one selected) and the content is at most 720 dp wide.
- **Steps.** Welcome; Goals (`goalChoices`: multi-select cards, "Needs access" badge, disabled ones visible with their
  reason); Arrange (per goal: Move up / Move down within its block, a Page / Widget segmented button when
  `rolesFor` offers both, "Open on this page", and "Open on the first page"); Options (one goal at a time with "n of m":
  its filters, apps / feeds / search text pickers, `looksFor` looks, the setup problem as a polite notice); Dock (pins up
  to `dockCapacity`, "Hide the dock" only when `canHideDock`, otherwise with the reason); Permissions (below); Review
  (`review`: pages, widgets, dock, "Needs access: ...", replaces / keeps / updates).
- **Accessibility.** Every choice is one target of at least 56 dp that reads title, description and state as one
  sentence, with its role (checkbox, radio button, switch) and selected state; disabled options stay focusable; Move up
  and Move down name their goal; problems and statuses are polite live regions; the step name is a polite live region
  outside the cross-fade, so every step change is announced. Back is the system Back too (one step; on Welcome it
  leaves). Step changes cross-fade with `RiffleMotion.standard` and snap under reduced motion.

### Permissions

"Allow" sends `ExplainPermission` (the rationale and what Android will show next are on screen); "Continue" sends
`RequestPermission`, and the session then calls the host, from that tap, with the permission. The host runs the
existing explicit flow through `SourceAccessLaunchers.request` for the permission's source (`flowSource()`: the calendar
dialog via `CalendarAccessCoordinator`, the notification listener settings, the Usage access settings). Nothing is ever
requested at startup or without the tap.

The result is reported as `PermissionResult` when the launcher comes back: the session waits for a pause (the dialog or
the settings screen covers it) and then a resume, re-reads access (`WorkspaceRuntime.sourceChoices()`, never the flow's
own answer), rebuilds the context and reports granted or denied. For a flow that could not open (no pause), the row
shows "Check again", which reports what access is now. "Not now" skips; leaving the step skips the rest. A granted row
stays listed as "Allowed". Skipped and denied content is built anyway and shows "Needs access" with "Review access".

**Domain fix in this slice:** once every permission is granted the Permissions step leaves `WalkthroughFlow.steps`
while the user is still on it, and Next went back to Welcome. `WalkthroughNavigation.next` and `back` now move by step
order, not by index (identical for every listed step; `WalkthroughNavigationTest`).

### Applying and Undo

On "Build my home" the layer calls `WalkthroughApplier.apply` with the layout shown now (N10: the phone layout when
rotated):

- **Read-only home** (`HomeLayoutRuntime.readOnlyHome`): refused, nothing written; the Review step shows the existing
  notice (`READ_ONLY_HOME_NOTICE`) and "Build my home" is disabled.
- **Edits held while loading** (workspaces not loaded, or `PoolHomeLayoutRepository.awaitingWorkspaces`): "Build my
  home" is disabled with "Your home is still loading" and re-checked every 250 ms and on every workspace change; an apply
  that races it is refused and nothing is written.
- Otherwise `WalkthroughApply.apply` with `WorkspaceHome.borrowCandidates(WalkthroughFlow.WORKSPACE_ID, layout)` (S9's
  fixed borrow order). The pool goes first (`CachedPoolRepository.update`, in memory; the home sees it as an external
  change and re-projects; the pool-backed home's `flushNow` writes it, releases host ids and refreshes the recovery
  snapshot), then the workspace set (`CachedWorkspaceRepository.save`; the home re-targets to "My home"). Then
  `Onboarding.completed`, the workspace menu refreshes, the home is shown and a snackbar says "Your home is ready" with
  **Undo**, which writes `undo.pool` (only when the pool was written) and `undo.layout` back. A refused apply returns
  to Review with every choice kept.

### Decisions (UI, to confirm)

1. **The Home-role card comes first**; the walkthrough or the offer follows it in the same launch.
2. **Fresh install needs both stores empty** at start (home layouts and workspaces). An existing user who never had
   workspaces stored still has a home layout, so they get the offer, never a replacing walkthrough.
3. **The onboarding file is excluded from Auto Backup and device transfer** too (not only from Riffle's backup): a new
   phone restored from Auto Backup gets the workspaces and is treated as an existing user (the offer, once).
4. **Undo restores the layout's workspaces and pool as they were** before the walkthrough; an edit made to that layout
   in the seconds the snackbar shows is undone with it. Undo does not reopen the walkthrough or reset onboarding.
5. **A walkthrough interrupted by a process death** restarts from Welcome (its choices are not saved); a fresh install
   gets it again at the next launch.
6. The walkthrough draws in the global theme (not the active workspace's skin).

### Tests (app)

- JVM, Android-free (run here): `OnboardingControllerTest` (fresh install runs full screen and stores STARTED; once per
  process; an interrupted run comes back; completed or skipped is not asked again; an existing user gets the offer once;
  Not now and Set up; Settings re-runs never replace and a cancelled re-run keeps the completion; a re-run before the
  decision wins; freshness asked only while undecided; no write when nothing changed), `OnboardingStoreTest` (round
  trip, garbage and unknown values read as undecided, field names only, excluded from Auto Backup and transfer),
  `WalkthroughSessionTest` (forwards and back, Back on Welcome and Skip leave, goals toggle, explain then request
  through the host once, a resume that is not a return reports nothing, granted and denied returns, Check again, skip
  everything, granting everything still moves on to Review, a refused apply reopens Review),
  `WalkthroughApplierTest` (fresh install replaces the seed and Undo restores both stores, an existing user keeps every
  workspace, applying twice updates in place, read-only and loading refuse and write nothing, no pool, Undo refused on a
  read-only home), `WalkthroughContextsTest` (picker apps, dock suggestions, feeds, re-runs never replace, the
  Permissions step, each permission maps to the Settings > Sources flow), `FreshInstallSignalsTest` (both store signals),
  `SettingsPagesTest` (the search entry).
- Robolectric (CI): `WalkthroughScreenshotTest` (every step compact, light and dark; Review while loading and on a
  read-only home; the offer card light and dark; Goals at the largest font), `WalkthroughUnfoldedScreenshotTest` (every
  step unfolded, light and dark), `WalkthroughHappyPathTest` (Get started, toggle a goal, explain and skip access, build,
  apply and Undo; Skip set-up; Back; Continue opens the flow and Check again reports "Not allowed"; the offer's buttons).
- Domain: `WalkthroughNavigationTest`.

### Verification (UI slice)

Run here in a scratch Gradle project on Maven Central: the whole `:core:domain` suite (230 classes, 2014 tests, all
pass), the Android-free onboarding files with the pool and workspace repositories they use (6 classes, 43 tests, all
pass), ktlint on domain main and test and on every changed app file, detekt over domain and app (main and test), clean.
**Not run:** the Compose and Android code (`WalkthroughScreen`, `WalkthroughSteps`, `WalkthroughComponents`,
`OnboardingOfferCard`, `OnboardingLayer`, `OnboardingHost`, `SharedPreferencesOnboardingStore`, `LauncherShell`,
`MainActivity`, the Settings section) was not compiled, and the Robolectric screenshot and UI tests and
`SettingsPagesTest` did not run (dl.google.com is blocked here); CI runs `verify deviceVerify`.

### Not done (walkthrough UI)

- Sharing rows with the content wizard UI (`launcher/wizard/`); a live preview of each page beside the steps.
- Saving the walkthrough's choices across a process death.
- Drag to reorder on Arrange (Move up / Move down only).
- An instrumented test of the real permission flows (the system screens cannot be driven in a JVM test).

### Owner device checklist (walkthrough UI)

- [ ] Fresh install: Settings > Apps > Riffle > Clear storage (or install on a clean device), open Riffle. The "Set up
      Riffle" card shows first; set Riffle as the Home app. The walkthrough then opens by itself.
- [ ] Walk through: pick goals (TalkBack reads each card's state), arrange (Move up, Page / Widget, Open on this page),
      options, dock. On Allow access tap Allow then Continue for one permission and say no in the system screen: the row
      says "Not allowed". Tap "Not now" on the other. Review says "Needs access: ...". Build my home.
- [ ] The new home shows; the skipped content says "Needs access", and "Review access" opens the rationale first. Tap
      Undo on the snackbar: the starting home is back. Force-stop and reopen: no walkthrough (it was completed).
- [ ] Clear storage again, open Riffle, tap "Skip set-up": the starting home stays; reopen: no walkthrough.
- [ ] Clear storage again and force-stop Riffle mid-walkthrough: the next launch opens the walkthrough again.
- [ ] Update over an existing install: the "Set up your home" card appears once on the home. Restart Riffle: it does
      not come back (whether or not you tapped it). On another install tap "Set up": your other workspaces and your home
      are untouched after Build my home.
- [ ] Settings > Workspaces > Set up your home again runs it (also found by searching "walkthrough"); running it twice
      updates "My home" rather than adding a second one.
- [ ] Notification access from the walkthrough: Continue opens the system list; turn Riffle on, come back: "Allowed".
      Usage access the same. Calendar: the system dialog.
- [ ] TalkBack: every step change is announced; Remove animations: steps snap; rotate and fold/unfold mid-walkthrough:
      the step and choices stay; on the unfolded screen the step list is beside the step.

## 5. Copy (Material 3: sentence case, short, second person; one primary action per screen)

### Wizard

| Where | Text |
| --- | --- |
| Title (by entry) | Editor: "Add to your home"; Saved lenses: "New lens"; source page: "Use {source}" |
| Source | "What do you want to add?" Supporting: "You can change it later." Disabled: "Not available on this phone", "Already on this home"; badge "Needs access" |
| Content | "What should it show?" Apps (placed): "Pick apps for your home screen"; apps (notifications): "Only these apps" with "All apps" when none; feeds: "Feeds", "All feeds" when none; search: field "Search for", helper "Leave empty to use what you type in the search box" |
| Look | "How should it look?" Disabled: "Doesn't fit here". Preview label: "Preview" |
| Place | "Where should it go?" "New page", "A page for each app" (each feed, each calendar), "Widget on {page}" / "Widget on a new page", "In the dock" (+ "Replaces what the dock shows now"), "Save as a lens only" ("Use it later from Saved lenses") |
| Save | "Save to your lenses" switch; supporting: "Reuse it elsewhere. Editing it updates every place it's used." Field "Name" (40 characters, counter) |
| Problems | `PICK_AN_APP` "Pick at least one app." `LOOK_DOES_NOT_FIT` "This look doesn't fit here." `NoDestination` "Choose where it goes." `DestinationRejected` "It doesn't fit on this workspace." `BLANK_NAME` "Enter a name." `NAME_TAKEN` "You already have a lens with this name." `NAME_TOO_LONG` "Use 40 characters or fewer." `LIBRARY_FULL` "You have 100 lenses. Delete one to save another." `NoWorkspace` "This workspace was deleted." |
| Buttons | "Back", "Next", final "Add" ("Save" for a lens only), close "Cancel"; link "Open advanced builder" |
| Done | Snackbar "Added {name}" (editor: the editor's Undo covers it) |

### Walkthrough

| Where | Text |
| --- | --- |
| Welcome | "Set up your home". "Tell Riffle what you want on your home screens and it builds them for you." Buttons "Get started", "Skip for now" |
| Goals | "What do you want on your home screens?" "Pick as many as you like." |
| Arrange | "Arrange your home". "Choose what gets its own page and what becomes a widget." Segmented "Page" / "Widget"; "Move up" / "Move down"; radio "Open on this page". Note: "Your app icons always come first." |
| Options | "{Goal}" with "{n} of {m}"; same controls and copy as the wizard's Content and Look |
| Dock | "Your dock". "Pick up to {n} apps to keep at the bottom of the screen." Switch "Hide the dock"; when refused: "To hide the dock, first set a gesture to open the workspace menu in Settings > Gestures." |
| Permissions | "Allow access". "Some of what you picked needs your permission. You can skip this and allow it later in Settings > Sources." Rows: "Notifications and media: lets Riffle show your notifications and what's playing. They stay on this phone."; "Calendar: lets Riffle show your upcoming events. It never changes your calendar."; "Recent apps: lets Riffle show the apps you used last." Buttons "Allow" then "Continue", "Not now". Status: "Allowed", "Not allowed", "Skipped" |
| Review | "Review your home". Rows "Page {n}: {goals}" (+ "Opens here"), "Widgets: {goals}", "Dock: {apps}" / "Dock hidden". "Needs access: {goals}. They show Needs access until you allow it." Fresh: "This replaces the starting home." Otherwise: "Your other workspaces stay as they are." Button "Build my home" |
| Done | Snackbar "Your home is ready" with "Undo" |
| Offer (existing users, once) | Card "Set up your home". "Tell Riffle what you want on your home screens. Your current home stays." Buttons "Set up", "Not now" |
| Settings | Settings > Workspaces row "Set up your home", supporting "Build a workspace from what you want to see" |

Accessibility: every row is one 48 dp target that reads label, description, state and reason as one sentence; disabled
options stay focusable with their reason; problems and the permission status are polite live regions; Move up / Move
down replace drag; step changes cross-fade with `RiffleMotion.standard` and snap under reduced motion; content respects
`WindowInsets.safeDrawing`; at 600 dp and wider the preview sits beside the steps (as the editor).

## 6. Wizard UI (as built)

App package `app/.../launcher/wizard/` (Compose, Material 3), plus small additive hooks in the editor, Settings > Saved
lenses, the source detail pages and the workspace layer.

### Files

| File | What |
| --- | --- |
| `ContentWizard.kt` | `ContentWizard(context, onFinished, onCancelled, onAdvanced, ...)` (stateful, saveable) and the stateless `ContentWizardScreen` (screenshots), the preview pane |
| `WizardSteps.kt` | Source (with "Needs access" and Review access), Place, Save, and the Content and Look steps over the shared options |
| `WizardGoalOptions.kt` | **The compact per-goal options view for the walkthrough:** `GoalOptions(setup, looks, apps, feeds, callbacks)`, and its halves `GoalContentOptions` / `GoalLookOptions`, wired by `GoalOptionsCallbacks` (`wizardCallbacks` wires them to `WizardAction`s) |
| `WizardHolder.kt` | The pure state holder over `WizardReducer`: restore, refusal notice, visible steps, "Step n of m", the closing name |
| `WizardStateCodec.kt` | The state as nested `ArrayList`s for saved instance state (process death); never throws |
| `WizardCommit.kt` | Applying through `WizardApply` and writing: `commit` (Settings) and `forEditor` (an edit for the session); the `WizardStores` port |
| `HomeWizardStores.kt` | `WizardStores` over the cached workspace repository and the pool repository; a workspace-only fallback |
| `ContentWizardRequests.kt` | Which Settings-opened wizard shows (`WizardRequest.NewSavedLens`, `.FromSource`) and the advanced handoff |
| `WizardInputs.kt`, `WizardText.kt`, `WizardTags.kt` | Picker choices (visible apps, RSS feeds by host, calendar feeds by name), the copy (section 5), test tags |
| `editor/EditorWizard.kt`, `editor/EditorWizardSteps.kt` | The editor's wizard (`EditorContentWizard`), `EditorWizardSupport`, and the editor reducer's wizard steps |
| `workspace/WorkspaceWizardLayer.kt` | Draws a Settings-opened wizard over Settings, applies and announces it |

### Entry points

1. **Editor.** "Add page or widget" opens the wizard (`EditorAction.OpenWizard`); a widget page's "Add widget" opens it
   on that page (`WizardEntry.Editor(pageId)`). "Open advanced builder" sits next to "Add page or widget" (one tap) and
   in the wizard's top bar, which hands over to the Add flow on the wizard's draft (`EditorAction.StartAdvanced`). The
   wizard's context is the session (`LensScope.layoutWith(draft)`). Its result is `EditorAction.ApplyWizard(edit,
   library)`: one `session.apply(edit, ..., nextScope)` step, so **Undo, Redo, Revert and the discard prompt cover the
   page and its saved lens**; Done saves both as today. Picked apps are written to the pool at once (own store).
2. **Settings > Saved lenses > New** opens the wizard (`WorkspaceSettingsHost.onNewLensWizard`); "Open advanced
   builder" under it opens the builder empty, and the wizard's own link opens the builder on its draft
   (`LensBuilderSessions.startNew(layout, draft)`).
3. **A source detail page**: a new "Add to your home" section, row "Use this source on your home"
   (`WorkspaceSettingsHost.onUseSource`), opens the wizard with that source pre-selected for the layout's active
   workspace. Its advanced link opens the editor of that workspace on the Add flow with the draft
   (`ContentWizardRequests.takeEditorHandoff`, `EditorEntry(initialHandoff)`).
4. **Walkthrough**: `GoalOptions` (no frame, no buttons) for its Options step; `ContentWizard` itself can also be hosted.

### Behaviour

- **State.** `rememberSaveable` with `WizardStateCodec` (keyed by the entry): a run survives rotation and folding (the
  activity handles configuration changes; the composition stays) and is restored from saved instance state wherever
  the host composes it again; a restored state whose step this run no longer shows starts again. The hosts' own "open"
  flags (the editor, `ContentWizardRequests`) are in memory, as the editor's are today, so after process death the
  wizard is not reopened by itself.
  A refused finish reopens on the last step with the reason as a polite live region.
- **Steps.** "Step n of m" (from `WizardFlow.steps`), a heading, the step's controls, the current step's problems (a
  polite live region next to the buttons; Next disabled), Back (cancels on the first step), one primary button
  ("Next", then "Add", or "Save" for a lens only), close (Cancel) and "Open advanced builder" in the top bar. System
  Back steps back. Rows are the editor's `EditorChoiceRow`: one 48 dp selectable row with radio or checkbox role,
  selected state, and label, description, badge and reason read as one sentence; disabled options stay visible with
  their reason. The step change cross-fades with `RiffleMotion.standard` and snaps under reduced motion. Content
  respects `WindowInsets.safeDrawing`.
- **Layout.** At 600 dp and wider the live preview sits beside the steps on every step (as the editor); compact shows
  it above the looks on the Look step.
- **Preview.** `WizardFlow.preview` drawn by `EditorPreview` (so `LensBoundExpression`, or `PageSetContainerHost` per
  group) over the runtime's `ContainerServices`; a placed goal lists the picked apps.
- **Permissions.** A goal that needs access shows "Needs access"; once chosen (and on later steps of a source page's
  run) the explanation per permission and "Review access", which calls the existing explicit flow
  (`SourceAccessLaunchers.request`, calendar rationale first). Nothing is ever requested by the wizard itself; skipped
  content draws "Needs access" through the existing hosts.
- **Apply outside the editor** (`WizardCommit.commit`): re-validated against what is stored now; the workspace store is
  written only when the layout changed, the pool only when picked apps changed it. A pool change is **refused** with
  the existing `READ_ONLY_HOME_NOTICE` in a read-only home, with "Your home is still loading" while S9 holds item edits
  (`awaitingWorkspaces`), and while the pool is not loaded; nothing is written then. Success shows a toast "Added
  {name}" and refreshes the workspace menu.
- **Editor**: `EditorEnvironment.editContext` now always knows `home.grid` (as `WizardContext.descriptors` does), so a
  placed page the wizard adds never blocks Done as an unknown source.

### Tests (app)

- JVM (`app/src/test/.../launcher/wizard`, `.../editor/EditorWizardReducerTest`): `WizardHolderTest` (every step to
  Finished, Back, Cancel, refusal reopens on the last step, restore, "Use this source" skips Source, names),
  `WizardStateCodecTest` (round trip, Bundle-safe values, garbage), `WizardCommitTest` (source page, new lens, picked
  apps into the pool, read-only and held refusals with nothing written, stale layout, the editor edit),
  `ContentWizardRequestsTest`, `EditorWizardReducerTest` (one Undo step with its saved lens, Redo, Revert, Done saves
  page and lens together, the advanced handoff, a placed page does not block Done).
- Roborazzi: `screenshots/wizard/WizardScreenshotTest` (compact: each step light and dark, needs access, large font,
  "Use this source", a refused finish) and `WizardUnfoldedScreenshotTest` (unfolded two-pane: each step light and dark).
- Compose UI (Robolectric): `WizardEntryPointsTest`, a happy path from the editor (through Done), Saved lenses > New
  (wizard and advanced button, then a saved lens), a source page (the row, then a page on the active workspace),
  Cancel and the advanced handoff, and the walkthrough's `GoalOptions`.

### Not done (wizard UI)

- Calendar feeds in the feed picker come from the last state the ICS controller published (empty until Settings >
  Calendar feeds or Sources has been opened this process); RSS feeds always show.
- The placed-goal preview lists app names, not icons.
- The app picker is a plain list with a filter field above 12 apps (no icons, no sections).
- From Settings the announcement is a toast (the wizard closes over Settings); the editor uses its own notice.
- Reopening a wizard after process death (the editor and the Settings request are not persisted).

### Owner device checklist (wizard)

- [ ] Editor > Add page or widget: Notifications > A page for each app > Add. The page appears; Undo removes it and its
      saved lens; Done keeps both. A widget page's "Add widget" starts on a widget there.
- [ ] Editor > Open advanced builder (both places): the Add flow opens, from the wizard with its draft.
- [ ] Settings > Saved lenses > New: wizard, no Place step, Save adds the lens; "Open advanced builder" opens the builder.
- [ ] Settings > Sources > Calendar (access not granted) > Use this source on your home: "Needs access" with the
      explanation; Review access shows the rationale before the system dialog; Add places it; it says Needs access.
- [ ] Favourite apps from a source page or the editor: apps appear on a new home page; in a read-only home the wizard
      says "Your home screen can't be changed right now ..." and nothing changes.
- [ ] Rotate and unfold mid-run: choices kept, the preview beside the steps when unfolded.
- [ ] TalkBack: each row reads label, description, state and reason; problems are announced; Remove animations snaps
      the step change.

## Decisions (to confirm)

1. **One goal per wizard run.** "Choose source(s)" is a goal choice; a lens over several sources is the advanced
   builder's job.
2. **Favourite apps are placed apps** (pool, `home.grid`), not a lens: nothing sets `launcher.pinned` yet (design 12).
3. **No "today" or "unread" filters.** The lens model has no future time window (age filters are non-negative) and
   notifications carry no read state. Calendar offers "Next event", "Upcoming" and "By day" instead.
4. **Walkthrough lenses are inline**, not saved to the library (a goal-first home should not fill Saved lenses); the
   wizard saves by default from the editor and Settings.
5. **Walkthrough widgets share one widgets page** (lens widgets cannot sit on the placed home pages), and placed pages
   always come first.
6. **"My home" has a fixed id** (`walkthrough:home`), so a re-run updates it in place; Undo restores the previous values.
7. **Fresh install, first run** replaces the seeded Nova default only while it has no arrangement of its own, and sets
   the dock to the picked apps. Re-runs and existing users only ever add.
8. **The offer is shown at one launch**, whatever the user does with it.
9. **Onboarding state is device-local** and not backed up.

## Tests (domain, JVM)

- `ContentGoalCatalogTest`: every goal x filter x look x listed destination (with none, one and several picks, feeds, a
  query) fits and validates as a stored container (`ContainerValidation`, per group for page-sets) under the app's
  source descriptors (several hundred combinations); an unlisted destination is never offered; every built-in source
  (and `home.grid`) has a goal; permissions; placed goals are pages only and never saved; stable keys and labels.
- `ContentRecipesTest`: defaults, filter and picker composition, look shaping, normalized query, offered feeds only,
  unknown choices ignored, `reconcile`, placed goals need an app.
- `WizardFlowTest`: the editor entry through every step to Done; Back on every step and Cancel on every step; "Use this
  source" pre-selects and skips Source (and starts on Look for Media; an unavailable source starts on Source and can
  still hand over); New saved lens (no Place, save required, name problems); a taken name; invalid combinations
  rejected (no goal, off-step choices, unknown look, a page-set look as a widget or dock); unavailable versus "needs
  access" goals; "already there"; Favourite apps need an app and skip Save; a content change keeps the look valid; the
  dock section "replaces"; the advanced handoff; the preview.
- `WizardApplyTest`: a page-set with a referenced saved lens (and the replayable edit); a widget on the editor's page; a
  dock section from a source page, not saved; a lens only; Favourite apps added to a borrowed home (fork, no duplicates,
  migrated arrangement and dock untouched) and to a workspace without one; stale state rejected (deleted workspace,
  name taken meanwhile).
- `WalkthroughFlowTest`: preselection (fresh, existing, unavailable goals); the proposal; arrange (roles with look
  fitting, placed first, start page); options stepping forwards and back; dock pins and the hide rule; the permission
  flow (no request without explanation, pending blocks, denied, unrelated permission ignored, leaving skips, review
  "needs access"); no Permissions step when all is granted; Back and Cancel.
- `WalkthroughApplyTest`: fresh install replaces the seeded default, places favourites and sets the dock; an existing
  user loses nothing (every workspace, library and pool unchanged); favourites with the current home fork it and the
  dock only gains; applying twice is idempotent (both origins); a re-run updates in place and replaces nothing;
  skipped permissions leave the content in place; Undo values; a dock not in the pool; the widgets page and start page.
- `OnboardingTest`: fresh auto-run until completed or skipped (and after an interruption); the offer shown once,
  dismissed, accepted; the origin decided once; re-runs; codec round trip and safe decode (garbage, unknown values, a
  future version, a legacy flag).
- `WizardPropertyTest`: seeded random tap sequences over every entry (and over the walkthrough, both origins, with and
  without access): whatever finishes applies to a valid layout with no dangling saved-lens reference, and no existing
  workspace or arrangement changes.

## Verification

Run here in a scratch Gradle project on Maven Central: the whole `:core:domain` suite (229 classes, 2007 tests, 62 of
them new in 8 classes; all pass), ktlint on domain main and test, detekt over the new package (main and test).
`./gradlew verify` was not run (dl.google.com is blocked here); CI runs `verify deviceVerify`.

## Not done (next slices)

- All UI: the wizard screens and their four entry points, the walkthrough screens, the existing-user offer card, the
  Settings > Workspaces "Set up your home" row, the onboarding preference and its `atLaunch` call in the first-run flow
  (it must never delay the Home role request).
- A "today only" calendar filter and an "unread" notification filter (need lens model additions).
- Choosing which page a wizard widget goes on beyond the editor's page and the first free grid page (the targets are
  offered; the UI decides how to present them).
- Saving walkthrough lenses to the library, and multi-source goals.
- Removing a walkthrough page that a re-run no longer asks for from the pool: a re-run without Favourite apps keeps
  their arrangement page (data is never removed).

## Owner device checklist (once the UI lands)

- [ ] Fresh install (clear data): the walkthrough opens by itself; pick goals, arrange, skip Calendar access; review
      shows "Needs access: Calendar"; build; the home shows the pages, the calendar widget says "Needs access" and
      "Review access" opens the rationale first. Undo restores the starting home.
- [ ] Update over an existing install: the "Set up your home" card appears once; "Not now" and it never returns;
      Settings > Workspaces > Set up your home runs it; your other workspaces and your home are untouched; switch back.
- [ ] Run it again: "My home" is updated, not duplicated; the dock only gains apps.
- [ ] Wizard from the editor, Saved lenses > New and a source page; the advanced builder opens with the draft.
- [ ] TalkBack, Remove animations, rotation, unfolded.
