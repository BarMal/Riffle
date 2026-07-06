# Changelog

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
