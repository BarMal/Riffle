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
| Backup import validation | Validate before applying persisted state | New backup sections or persisted models |
| APK size | Follow the release APK size budget | New libraries, assets, generated code, or native dependencies |

## PR Expectations

- Mention the relevant target when touching startup, search, notification, widget, backup, animation, or layout code.
- Prefer domain-level unit tests for ranking, grouping, placement, migration, and validation work.
- Avoid repeated filtering or sorting directly from Compose recomposition paths.
- Do not add a central manager that coordinates unrelated launcher domains only to share state.
- Document any platform-only manual performance checks in the PR when local tests cannot cover them.

## Benchmark Roadmap

The first benchmark suite should cover:

- cold launch to first drawn home surface;
- app drawer search with a synthetic catalog;
- notification grouping with a synthetic notification set;
- page switching on phone, unfolded, and tablet layouts;
- backup import validation for representative documents.

Until that suite exists, reviewers should treat this document as the baseline performance contract and ask for focused tests around pure logic that can be measured locally.
