# Architecture Decision Records

Architecture Decision Records (ADRs) capture the project decisions that should outlive a pull request.

## When to Add One

Add or update an ADR before implementing a significant architecture change, including:

- module boundaries;
- persistence formats, migrations, and backup compatibility;
- state management and navigation patterns;
- rendering, template, animation, and gesture systems;
- major platform gateways or third-party dependencies.

Small implementation choices, bug fixes, and local refactors do not need an ADR unless they establish a new pattern other contributors are expected to follow.

## Process

1. Copy `0000-template.md` to the next numbered file, for example `0001-launcher-state-boundaries.md`.
2. Set the status to `Proposed` while the decision is under review.
3. Link the ADR from the pull request description when the PR implements or changes that decision.
4. Change the status to `Accepted` when the PR lands.
5. When replacing a previous decision, create a new ADR and reference the superseded record instead of rewriting history.

## Status Values

- `Proposed`: under discussion or in a draft PR.
- `Accepted`: current project direction.
- `Superseded`: replaced by a later ADR.
- `Deprecated`: no longer recommended, but not replaced by one specific ADR.
