# APK Size Budget

This release budget is part of the broader
[`Performance Budgets`](../development/performance-budgets.md).

Riffle tracks signed release APK size to catch dependency and asset growth early.

## Current Budget

- APK budget: 32 MiB (33,554,432 bytes), enforced in alpha and stable release workflows.
- Baseline: Alpha 71 `riffle-alpha.apk` was 18,922,889 bytes, approximately 18.05 MiB.
- Current: alpha build 1506 (`4b3b95687`) measured 26,912,347 bytes, approximately 25.67 MiB. The
  release build does not enable R8 minification or resource shrinking
  (`isMinifyEnabled = false`), so this is organic growth from feature work.

The 32 MiB cap (raised from 25 MiB after the 25.67 MiB alpha build failed the old cap, blocking
alpha publishing) leaves room for normal launcher work while keeping large dependencies, bundled
assets, and build configuration changes visible before they become expensive to unwind.

## Updating The Budget

Only raise the budget with an explicit pull request that explains:

- the new APK size;
- what changed;
- why the extra size is acceptable;
- whether shrinking alternatives were considered.

Release notes continue to include APK and AAB sizes for every published build.

## Follow-up

Evaluating R8 minification and resource shrinking for release builds is the preferred way to
reduce size before raising the budget again. It needs on-device validation and keep rules, so it is
tracked separately from the budget change.

The ICS feed source (#1409) added `:core:recurrence-ical4j` to the app: the shipped (unminified) configuration
measured 27.88 MiB in the Minified Release Check on that PR (about +2.2 MiB over 25.66 MiB), still under the
32 MiB cap with about 4.1 MiB of headroom; the minified build measured 4.68 MiB.

An opt-in minified build measured 25.66 MiB down to 4.38 MiB on the same commit (CI run
36896105589). See [`R8 Minification`](r8-minification.md) for the measured composition, keep-rule
review, the `riffle.minify` build, and the staged plan to enable it.
