# Riffle

A first-principles rewrite and re-implementation of the Calm launcher prototype.

## Development

Riffle is an Android launcher built with Kotlin, Jetpack Compose, and a modular Gradle setup.

Run the local verification suite before opening a pull request:

```bash
./gradlew verify
```

The verification task runs unit tests, Android checks, detekt, and ktlint. A local Android SDK is
required; keep its path in `local.properties` or `ANDROID_HOME`, never in source control.

For a cloud development environment with Codex CLI, see
[`docs/development/codespaces-codex.md`](docs/development/codespaces-codex.md).

## Release Signing

Alpha release signing is automated through GitHub Actions. Required secrets are documented in
[`docs/release/signing.md`](docs/release/signing.md).

Release APK size is budgeted and tracked in
[`docs/release/apk-size-budget.md`](docs/release/apk-size-budget.md).
