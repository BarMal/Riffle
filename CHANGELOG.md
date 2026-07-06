# Changelog

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
