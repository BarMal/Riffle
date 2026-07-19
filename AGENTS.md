# Agent Instructions

These instructions apply to coding-agent work in this repository.

## Project intent

Riffle is a polished standard Android launcher. Standard UI should use current Android and Material design language; TimeScape is an interaction inspiration only, expressed through focused card stacks with clear ordering and predictable accessible transitions.

## Operating rules

- Work one GitHub issue or one coherent slice at a time.
- Branch from latest `main`.
- Do not commit directly to `main`.
- Prefer small, reviewable pull requests.
- Include the issue number, scope, verification result, and known limitations in each PR description.
- Run `./gradlew verify` before marking work ready for review.
- Connected-device tests run as `deviceVerify` in emulator-backed CI; a missing local device is not a blocker.
- Do not commit generated APKs, AABs, keystores, signing files, `local.properties`, logs, or credentials.
- Do not add secrets to source files, Gradle files, scripts, or documentation examples.

## Architecture rules

- Avoid large feature monoliths.
- Keep platform-facing code behind interfaces where practical.
- Keep launcher domain models independent from Compose UI and Android framework types where practical.
- Prefer module boundaries that can support classic launcher behaviour and Riffle-specific dynamic/card behaviour independently.
- Add tests for domain logic, model migrations, placement/collision rules, and settings/default behaviour.
- Do not hard-code fixed layouts where configurable Riffle pages/templates are required.

## Product priorities

1. Standard Android launcher parity: home app registration, app discovery, app launch, app drawer/search, widgets, folders, wallpaper, grid/page editing, settings, backup/restore.
2. Polished defaults: clear permission flows, accessibility, reduced motion support, and Material 3 baseline components where appropriate.
3. Riffle-specific capabilities: dynamic pages, templates, cards, stack geometry, and motion profiles.
4. Advanced contextual behaviour after the foundations are robust.

## Android expectations

- Use Kotlin and Jetpack Compose consistently with the existing Gradle conventions.
- Respect lifecycle and permission boundaries.
- Keep launcher, notification, widget, shortcut, wallpaper, and profile APIs isolated enough to test core behaviour without a device.
- Consider foldables, tablets, rotation, system bars, and window insets from the start for shared layout primitives.

## Verification

Run:

```bash
./gradlew verify
```

For UI/platform features that cannot be fully covered locally, document manual validation steps in the PR.
CI runs `./gradlew verify deviceVerify` so connected-device coverage remains a merge gate.
