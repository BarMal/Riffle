# Performance Budgets

Riffle should stay responsive on mainstream Android devices, not only current flagships. These initial budgets are guardrails for feature design and PR review until dedicated benchmarks are added.

## Initial Targets

| Area | Target | Review trigger |
| --- | --- | --- |
| Cold launcher draw | First interactive home surface within 1,500 ms after process start on a mid-range device | New blocking startup work, synchronous app/widget scans, or migration work |
| Warm resume | Home surface visible within 300 ms | New work in `onResume` or destination changes |
| App drawer search | Query update reflected within 100 ms for 250 installed apps | New filtering, ranking, profile, or shortcut search logic |
| Page switch | No visible missed frames during normal home page changes | New layout passes, animations, or page state recomputation |
| Widget refresh | Provider refresh work kept off the main thread | New widget provider scans or binding flows |
| Notification refresh | Notification grouping update within 150 ms for 100 active notifications | New grouping, stale filtering, or counter logic |
| TimeScape card updates | No visible missed frames while settling a focused card through a 100-notification burst; compose only the configured visible stack depth | New card rendering, artwork, detail, or listener-refresh work |
| Backup import validation | Validate before applying persisted state | New backup sections or persisted models |
| APK size | Follow the release APK size budget | New libraries, assets, generated code, or native dependencies |

## PR Expectations

- Mention the relevant target when touching startup, search, notification, widget, backup, animation, or layout code.
- Prefer domain-level unit tests for ranking, grouping, placement, migration, and validation work.
- Avoid repeated filtering or sorting directly from Compose recomposition paths.
- Do not add a central manager that coordinates unrelated launcher domains only to share state.
- Document any platform-only manual performance checks in the PR when local tests cannot cover them.

## Benchmark Roadmap

The CI smoke benchmark suite currently covers:

- app drawer search against a synthetic 250-app catalog;
- notification grouping against a synthetic 500-notification active set.

The first benchmark suite should cover:

- cold launch to first drawn home surface;
- page switching on phone, unfolded, and tablet layouts;
- backup import validation for representative documents.

Until Android device benchmarks exist, reviewers should treat this document and the smoke tests as the baseline performance contract and ask for focused tests around pure logic that can be measured locally.

## TimeScape Manual Check

On a compact phone and an expanded/folded layout, grant notification access and create a burst of at
least 100 notifications with a mix of valid and invalid large icons. While the burst is arriving,
drag between cards, open details, dismiss the focused notification, background the launcher, and
return. Confirm that the focused surviving card remains selected, removed details close with the
recovery message, no duplicate refresh is visible after resume, and artwork falls back to the app
colour treatment when it cannot decode. Repeat with reduced motion and on a device/API level that
does not support blur; saved appearance settings must remain unchanged and card text legible.
