# Changelog

## Alpha 183

Release notes for the next alpha after Alpha 182.

### Changed

- Settings > Appearance now includes a Wallpaper motion control with Static and Scroll modes.
- System wallpapers can now follow the selected home page using Android wallpaper offset APIs when Wallpaper motion is set to Scroll.
- Launcher search web results now use a distinct "Search web" button and show clearer generated example searches for images, news, and videos.
- Dock render metrics now apply the existing compaction policy on narrow widths, so five-icon folded-mode docks shrink spacing and icon size before falling back to overflow navigation.

### Verification

- Full GitHub CI `Verify` passed for PRs #551, #552, and #553.
- Local verification included targeted wallpaper controller, search grid/web launcher, dock metrics, detekt, and full `./gradlew verify` runs.

### Known Limitations

- Wallpaper scroll support depends on the active wallpaper and OEM honoring Android wallpaper offset APIs, and it updates settled page offsets rather than drag-frame parallax.
- Web search examples are generated delegated searches, not live-fetched online result snippets.
- Dock folded-width behavior still needs manual validation on physical foldable hardware.

## Alpha 182

Release notes for the next alpha after Alpha 181.

### Changed

- Launcher search settings results now open Settings directly on the matched top-level settings page instead of always opening the Settings index.
- Generic Settings entry points still open the main Settings page, and unknown settings search IDs fall back to the main Settings page.

### Verification

- Full GitHub CI `Verify` passed for PR #549.
- Local verification included targeted search result, settings page mapping, action routing, and settings page navigation tests plus full `./gradlew verify`.

### Known Limitations

- Settings search deep links target top-level settings pages only; row-level highlighting or automatic scrolling within a settings page remains future work under #20.

## Alpha 181

Release notes for the next alpha after Alpha 180.

### Changed

- Launcher search now presents web search as a dedicated full-width Google search panel instead of mixing it into the app icon grid as a square pseudo-icon.
- Web search now shows compact local preview refinements for images, news, and videos, each delegating to the existing Android web search flow.
- Launcher search result counts now reflect local launcher results only: apps, shortcuts, and settings.

### Verification

- Full GitHub CI `Verify` passed for PR #547.
- Local verification included targeted search grid and search surface text tests plus full `./gradlew verify`.

### Known Limitations

- Web previews are local delegated Google searches, not network-fetched result snippets. Real online provider prefetch remains a separate provider/privacy/loading-state slice under #20.

## Alpha 180

Release notes for the next alpha after Alpha 179.

### Changed

- Settings > Appearance now launches the Android wallpaper picker through a standard chooser titled "Change wallpaper" while still checking the base wallpaper intent for availability.
- Launcher search now includes a web result for non-blank queries, labeled as a Google search, and delegates to Android web search with a Google URL fallback.
- Dock folder items are now actionable: tapping a dock folder opens the existing folder surface instead of leaving the placeholder inert.

### Verification

- Full GitHub CI `Verify` passed for PRs #543, #544, and #545.
- Local verification included targeted tests for wallpaper picker chooser behavior, web search intent fallback and action routing, launcher search grid results, dock folder item state, dock folder resolution, and full `./gradlew verify`.

### Known Limitations

- Web search has no provider settings UI yet; the fallback URL is Google-specific, and on-device validation of installed search/browser handling is still useful.
- Riffle still delegates wallpaper selection to Android and does not receive or persist picker results.
- Dock widget items remain placeholders; dock widget hosting and extra dock folder edit/context affordances remain open under #6.

## Alpha 179

Release notes for the next alpha after Alpha 178.

### Changed

- Settings > Appearance now includes a Change wallpaper action that opens Android's wallpaper picker and reports unavailable or failed launches.
- Launcher search now shows matching Settings results alongside app and shortcut results, with settings results opening the Settings destination.
- Home gesture settings now have a domain conflict detector for duplicate non-disabled gesture actions.
- Dock rendering now handles non-app dock items explicitly; folders and widgets render as placeholder dock items instead of disappearing.
- Page overview now supports selected-page grid dimension changes without changing sibling pages or the layout default grid.
- Hosted widget IDs are now deleted when a widget bind succeeds but placement into the selected page is rejected.

### Verification

- Full GitHub CI `Verify` passed for PRs #536, #537, #538, #539, #540, and #541.
- Local verification included targeted tests for wallpaper picker routing/gateway behavior, settings search state and grid rendering, gesture conflict detection, non-app dock item state and JSON round trips, selected-page grid edits, widget placement rejection cleanup, and full `./gradlew verify`.

### Known Limitations

- Wallpaper selection is delegated to Android; Riffle does not receive or persist picker results, and manual OEM/API validation remains pending under #7.
- Settings search results open Settings generally; deep-linking to the matched settings page remains follow-up work under #20.
- Dock folders/widgets are placeholder-only in this alpha; opening/hosting/add flows from the dock remain open under #6.
- Page overview grid controls are compact one-step chips; richer numeric controls and deeper page customisation remain open under #17.
- Gesture conflict detection is domain-only; conflict UI and broader gesture types remain open under #14.
- Widget configuration flows, host size option updates, provider-aware resize behavior, and dock widgets remain open under #5.

## Alpha 178

Release notes for the next alpha after Alpha 177.

### Changed

- Launcher search now has a domain-owned provider and extensible result model for mixed global app and settings results.
- Settings page entries can now be projected into launcher search results, including dynamic status text such as hidden-app counts.
- Over-capacity dock layouts now render enough slots for all persisted dock items when capacity is positive, so existing items stay reachable through dock scrolling instead of disappearing.

### Verification

- Full GitHub CI `Verify` passed for PRs #533 and #534.
- Local verification included targeted tests for mixed app/settings search ranking, settings search projection, hidden app exclusion, blank global searches, dock over-capacity slot rendering, and full `./gradlew verify`.

### Known Limitations

- The new global search provider is not yet wired into the Compose search UI; app drawer and existing search surfaces keep their current presentation for this alpha.
- Dock folders, widgets/static items, explicit paging controls, and future card dock modes remain open under #6.
- Wallpaper picker integration remains open under #7.

## Alpha 177

Release notes for the next alpha after Alpha 176.

### Changed

- Reduced-motion home page changes now use the same short animated settle path for external page selections instead of snapping abruptly, reducing visual stutter between the pager and page indicator.
- Wallpaper source application now returns explicit success/failure results behind the platform controller boundary.
- Direct Settings > Appearance wallpaper source changes now show fallback feedback when system wallpaper cannot be applied.
- If system wallpaper application fails and Riffle falls back to the solid background mode, launcher settings are synced to that fallback source so persisted state and rendered background stay aligned.

### Verification

- Full GitHub CI `Verify` passed for PRs #530 and #531.
- Local verification included targeted tests for reduced-motion home pager policy, wallpaper apply/fallback behavior, fallback user-facing copy, and full `./gradlew verify`.

### Known Limitations

- Wallpaper picking/setting APIs and OEM-specific wallpaper device validation remain open under #7.
- Reduced-motion polish is still page-pager specific; the broader shared motion system remains open under #24.

## Alpha 176

Release notes for the next alpha after Alpha 175.

### Changed

- Saved Home Screen Library layouts now remain available at cold start, so the launcher does not fall back to Standard until the user toggles layout mode away and back again.
- Settings > Dock now projects the selected folded/unfolded device-class layout through a testable state helper, keeping dock edits independent across device tabs.
- Missing dock fields in stored layout JSON now inherit the layout entry's device-class defaults instead of always borrowing phone dock sizing.
- Visible home docks now preserve configured capacity, icon size, and spacing; overflowing dock content scrolls inside the capped dock instead of compacting away the selected settings or hanging off-screen.

### Verification

- Full GitHub CI `Verify` passed for PRs #527 and #528.
- Local verification included targeted tests for launcher view-mode startup restoration, device-specific dock settings projection, dock JSON defaults, dock render metrics, and full `./gradlew verify`.

### Known Limitations

- Manual foldable device/emulator validation is still needed for exact folded/unfolded dock feel.
- Dock paging and explicit overflow controls remain future work; this alpha uses horizontal scrolling for overflow.
- Reduced-motion pager polish remains a known follow-up outside this urgent regression release.

## Alpha 175

Release notes for the next alpha after Alpha 174.

### Changed

- Main branch quality gates now run CI `Verify` on pushes to `main`, and root `./gradlew verify` explicitly includes app debug assembly.
- Startup home layout selection now preserves saved single-layout home pages when the current device class does not yet have its own stored layout.
- Folded, unfolded, and tablet home layouts now start with distinct dock icon sizes and spacing, and dock settings edits target the selected settings device class without mutating the active layout.
- The initial launcher shell has JVM coverage for a completed first run with no installed app data, closing the initial app-shell epic.
- Wallpaper validation evidence now documents current support, manual device checks, and remaining picker/scrolling/failure-state gaps.

### Verification

- Full GitHub CI `Verify` passed for PRs #521, #522, #523, #524, and #525.
- Local verification included targeted tests for startup device-class layout restoration, dock settings target selection, device-specific dock defaults, launcher empty-state evidence, wallpaper/settings support, and root `./gradlew verify`.

### Known Limitations

- Dock paging and explicit overflow navigation remain future work; current behaviour still relies on compaction and scrolling guards when users configure too many dock items.
- Wallpaper picker integration, static/scrolling wallpaper controls, and explicit wallpaper API/policy failure messages remain open under #7.
- Reduced-motion pager polish remains a known follow-up; this alpha focuses on layout loading, dock settings, and epic closure evidence.

## Alpha 174

Release notes for the next alpha after Alpha 173.

### Changed

- Contextual behaviour gained a domain signal planner for personal/work profile presence, notification activity, and day-start inputs.
- Generated-page content plans can now materialize app content into deterministic home shortcut items while safely skipping unsupported notification-group content.
- Dock render metrics now expose the domain overflow mode classification while preserving the existing compact/scroll visual behaviour.
- Widget add completion now fits oversized preferred widget spans to the selected grid before adding the widget to home.
- App, hidden-app, folder-add, widget-picker, and shortcut search paths now share a common search-token normalizer.

### Verification

- Full GitHub CI `Verify` passed for PRs #515, #516, #517, #518, and #519.
- Local worker verification included targeted tests for contextual signals, generated-page item materialization, dock render metrics, widget span fitting, and search token normalization.

### Known Limitations

- Contextual signal planning is domain-only and is not yet wired to platform observers or model/action execution.
- Generated notification-group content is still intentionally skipped until card-backed generated content is implemented.
- Dock overflow navigation remains reporting/scaffolding only; paging or explicit overflow controls are future work.

## Alpha 173

Release notes for the next alpha after Alpha 172.

### Changed

- Reduced-motion home page selection now snaps directly to the selected page instead of running the external settle animation, avoiding intermediate page indicator drift.
- Settings now includes a Contextual page with a persisted default-off toggle for contextual launcher behaviour.
- Built-in launcher template catalog defaults now provide stable domain templates for standard app-drawer and conservative generated-page layouts.
- Generated page content plans can now be applied to generated page descriptors with defensive rejection for unavailable or mismatched plans.
- Dock overflow handling gained a domain policy that classifies fitted, compacted, and overflow-navigation cases for future dock scrolling or paging work.

### Verification

- Full GitHub CI `Verify` passed for PRs #509, #510, #511, #512, and #513.
- Local worker verification included targeted tests for the reduced-motion pager policy, template catalog defaults, generated page plan application, dock overflow policy, and contextual settings reducer/UI routes.

### Known Limitations

- Reduced-motion pager polish has unit coverage but no device visual pass yet.
- Contextual behaviour remains opt-in and does not yet run platform observers, contextual signals, generated-page behaviour, or model/action execution.
- Template catalog, generated page application, and dock overflow policy are domain scaffolding; UI wiring and overflow navigation controls remain future work.

## Alpha 172

Release notes for the next alpha after Alpha 171.

### Changed

- Folded and narrow home docks now fit five app slots within the available width by reducing spacing first, then icon size, with clipped scrolling as a final guard.
- Launcher view-mode selection now respects the availability policy when restoring or switching layouts, falling back to the standard app drawer when experimental modes are unavailable.
- Notification grouping now has deterministic ordering for same-package notifications across personal, work, and private profiles.
- Contextual launcher behaviour settings are now persisted in launcher settings JSON and remain disabled by default for existing users.
- Template-based layouts gained domain support for planning and applying seed pages.
- Generated pages gained a domain content-planning layer for app-backed, profile-backed, and notification-backed page descriptors.

### Verification

- Full GitHub CI `Verify` passed for PRs #502, #503, #504, #505, #506, and #507.
- Local worker verification included targeted unit tests for view-mode availability, template seed application, notification grouping, settings JSON migration, generated page content planning, and dock fit metrics.

### Known Limitations

- Contextual behaviour is model/persistence only; no settings UI or platform signal observers are enabled yet.
- Template and generated-page work remains domain scaffolding; it is not wired into the launcher UI yet.
- The dock fix prevents visual overflow on narrow widths, but dock paging and richer overflow controls are still future work.
