# Riffle

Riffle is an Android launcher (Kotlin, Jetpack Compose) with four fixed Places:

- **Home**: the apps, folders and widgets you place, as in a standard launcher.
- **Dock**: pinned favourites on one side; live apps and a Stream button on the other. Pulling it away from its
  edge opens the Library (pinned side) or the Stream (dynamic side).
- **Library**: every installed app and search, shown as categories, an A-Z list or a grid.
- **Stream**: the actions centre. Now playing, notifications (one page per app), recents, agenda, tasks from
  Todoist and feeds, as pages you swipe sideways, each a vertical stack of cards or a list.

Every surface uses one glass material in three weights, over a Material 3 component baseline. Riffle also ships
three glance widgets (Now playing, Next event, Top story) for Home.

The decision behind this is [ADR 0003](docs/architecture/adr/0003-places-and-views.md), and its build plan is
[`places-views-plan.md`](docs/product/places-views-plan.md). Most of it has only been run in tests, not on a
physical device: [`places-views-device-checklist.md`](docs/development/places-views-device-checklist.md) is the list
of what still has to be checked by hand.

## Development

Run the local verification suite before opening a pull request:

```bash
./gradlew verify
```

The verification task runs unit tests, Android checks, detekt, and ktlint. It does not run the behaviour tests
in the `screenshots` package or the device tests; see [`AGENTS.md`](AGENTS.md). CI builds on JDK 17. A local
Android SDK is required; keep its path in `local.properties` or `ANDROID_HOME`, never in source control.

For a cloud development environment with Codex CLI, see
[`docs/development/codespaces-codex.md`](docs/development/codespaces-codex.md).

Pull requests should follow the project
[`Definition of Done`](docs/development/definition-of-done.md).
Performance-sensitive changes should also reference the
[`Performance Budgets`](docs/development/performance-budgets.md).
UI changes can be reviewed through the JVM screenshot tests described in
[`Screenshot Testing`](docs/development/screenshot-testing.md).

## Release Signing

Alpha release signing is automated through GitHub Actions. Required secrets are documented in
[`docs/release/signing.md`](docs/release/signing.md).

Release APK size is budgeted and tracked in
[`docs/release/apk-size-budget.md`](docs/release/apk-size-budget.md).
