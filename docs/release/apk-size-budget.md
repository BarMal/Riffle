# APK Size Budget

Riffle tracks signed release APK size to catch dependency and asset growth early.

## Current Budget

- APK budget: 25 MiB, enforced in alpha and stable release workflows.
- Baseline: Alpha 71 `riffle-alpha.apk` was 18,922,889 bytes, approximately 18.05 MiB.

The 25 MiB cap leaves room for normal launcher work while keeping large dependencies, bundled
assets, and build configuration changes visible before they become expensive to unwind.

## Updating The Budget

Only raise the budget with an explicit pull request that explains:

- the new APK size;
- what changed;
- why the extra size is acceptable;
- whether shrinking alternatives were considered.

Release notes continue to include APK and AAB sizes for every published build.
