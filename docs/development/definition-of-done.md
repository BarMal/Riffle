# Definition of Done

This checklist applies to every Riffle pull request. A PR can skip an item only when the reason is written in the PR description.

## Required Checks

- Link the GitHub issue or state that no issue exists.
- Keep the scope to one issue or one coherent slice.
- Run `./gradlew verify` before marking the PR ready.
- Add or update tests for changed domain logic, persistence, settings defaults, migrations, placement rules, and user-visible behaviour.
- Document manual validation steps for Android platform behaviour that cannot be fully covered locally.
- Keep platform APIs behind app-layer gateways or interfaces where practical.
- Add or update an Architecture Decision Record for significant architecture decisions, or state why none is needed.
- Avoid committing generated APKs, AABs, keystores, signing files, `local.properties`, logs, or credentials.

## Product Review

- User-facing behaviour has settings exposure when configuration is expected.
- Permission, accessibility, reduced-motion, and system-inset impacts are considered.
- Defaults are explicit, polished, and tested when persisted or user-visible.
- Foldables, tablets, rotation, and multi-window behaviour are considered for shared layout primitives.

## Engineering Review

- Domain models stay independent from Compose UI and Android framework types where practical.
- Major architecture changes reference an ADR from `docs/architecture/adr/`.
- Persisted models are versioned or have a migration plan before release.
- Performance risks are noted for startup, search, notification refresh, widget hosting, layout editing, and animations.
- New abstractions reduce coupling or match an established local pattern.
- Known limitations are listed in the PR description.
