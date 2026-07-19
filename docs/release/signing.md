# Release Signing

Riffle signs alpha release artifacts through GitHub Actions secrets. Do not commit keystores,
passwords, generated APKs, generated AABs, or `local.properties`.

## Required Repository Secrets

- `RIFFLE_SIGNING_KEYSTORE_BASE64`: base64-encoded Java keystore.
- `RIFFLE_SIGNING_STORE_PASSWORD`: keystore password.
- `RIFFLE_SIGNING_KEY_ALIAS`: release key alias.
- `RIFFLE_SIGNING_KEY_PASSWORD`: release key password.

## Creating A Keystore

Run this outside the repository, then store the generated file in a password manager or another
secure location:

```bash
keytool -genkeypair \
  -v \
  -keystore riffle-release.jks \
  -alias riffle-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Encode the keystore for GitHub Actions:

```bash
base64 -w 0 riffle-release.jks
```

On macOS, use:

```bash
base64 -i riffle-release.jks
```

## Local Release Builds

Set these environment variables before running `./gradlew assembleRelease bundleRelease`:

```bash
export RIFFLE_SIGNING_STORE_FILE=/absolute/path/to/riffle-release.jks
export RIFFLE_SIGNING_STORE_PASSWORD=...
export RIFFLE_SIGNING_KEY_ALIAS=riffle-release
export RIFFLE_SIGNING_KEY_PASSWORD=...
```

## GitHub Releases

Alpha releases follow successful `main` CI runs; use the Actions tab only to retry or override one:

- `Alpha Release` publishes a GitHub prerelease with `riffle-alpha.apk` and `riffle-alpha.aab`.
- `Stable Release` publishes a normal GitHub release with `riffle-stable.apk` and `riffle-stable.aab`.

Both workflows also keep the raw build outputs as GitHub Actions artifacts for traceability.
Release notes include APK and AAB sizes so growth is visible before a hard budget is enforced.

### Publishing an Alpha Candidate

Every successful `CI` run for a push to `main` publishes that exact commit. The workflow derives
the candidate SHA, changes since the previous alpha, and verification link from GitHub metadata.

Manual dispatch remains available for retries. `candidate_sha` defaults to the selected ref and
`release_notes` defaults to generated notes; an override must contain non-blank `Changed`,
`Verification`, and `Known Limitations` headings. The workflow rejects commits that are not on
`main` or do not have successful exact-SHA push CI. A SHA-derived tag makes retries idempotent.
