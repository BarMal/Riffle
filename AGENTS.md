# Agent Instructions

These instructions apply to coding-agent work in this repository.

## Project intent

Riffle is a polished standard Android launcher with four fixed Places: **Home** (the apps, folders and widgets the user placed), the **Dock** (pinned favourites on one side, live apps and the Stream button on the other; it is the handle for the other Places), the **Library** (every app, and search) and the **Stream** (the actions centre: now playing, notifications, recents, today, feeds, and the shortcuts of the Dock's pinned apps). Each Place has a short closed list of Views swapped in place with "Show as", and the Stream draws seven typed kinds of content. The look is one glass material at three weights (see `docs/product/design-language.md`) on top of the Material 3 component baseline; TimeScape and Stage Manager are interaction inspirations only. The decision and its rules are in [ADR 0003](docs/architecture/adr/0003-places-and-views.md).

## Operating rules

- Work one GitHub issue or one coherent slice at a time.
- Branch from latest `main`.
- Do not commit directly to `main`.
- Prefer small, reviewable pull requests.
- Include the issue number, scope, verification result, and known limitations in each PR description.
- Run `./gradlew verify` before marking work ready for review.
- `./gradlew verify` does not run the behaviour tests in the `com.riffle.app.screenshots` package (they need the Compose test activity) or any device test. Before pushing UI changes also run `./gradlew :app:testDebugUnitTest --tests 'com.riffle.app.screenshots.*' :app:recordRoborazziDebug`. Device tests (`deviceVerify`) only run in CI.
- CI builds on JDK 17; keep code and Gradle changes compatible with it even if you develop on a newer JDK.
- Connected-device tests run as `deviceVerify` in emulator-backed CI; a missing local device is not a blocker.
- Do not commit generated APKs, AABs, keystores, signing files, `local.properties`, logs, or credentials.
- Do not add secrets to source files, Gradle files, scripts, or documentation examples.

## Architecture rules

- Every model has one home in one Place. A new idea must be a new View for an existing Place or a new content type for the Stream; if it needs a new Place or a new layer, the answer is no.
- Views are swapped in place ("Show as", with Undo); there is no editor, wizard or query language, and workspaces, lenses and presets are not user-facing (stored workspaces stay decodable).
- Stream content is typed (`StreamContent`: notification, media, event, article, task, recent app, app shortcut), each with its own card, row and actions. Do not add a universal item type. The Today page's checklist, its summary of tomorrow and the notices are cards composed around that content (`TodayPlanner`, `StackEntry`), not content.
- Every translucent surface is drawn by `GlassSurface` at a weight (Thin, Regular, Thick); feature code contains no literal surface alpha or blur radius. Honour reduced transparency and reduced motion.
- Avoid large feature monoliths.
- Keep platform-facing code behind interfaces where practical.
- Keep launcher domain models independent from Compose UI and Android framework types where practical.
- Prefer platform gesture and infrastructure primitives over hand-rolled ones (ADR 0002).
- Stored data must keep decoding: removing a stored concept needs a codec test with old data, and item content is never persisted.
- Add tests for domain logic, model migrations, placement/collision rules, and settings/default behaviour.

## Product priorities

1. Standard Android launcher parity: home app registration, app discovery, app launch, the Library and its search, widgets, folders, wallpaper, grid/page editing, settings, backup/restore.
2. Polished defaults: clear permission flows, accessibility (48 dp targets, spoken labels, a non-gesture equivalent for every gesture), reduced motion and reduced transparency support, the glass material on every surface, and Material 3 baseline components where appropriate.
3. The Dock as the handle, and the Stream's typed content, pages and stacks, with motion profiles.
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
