# Codespaces + Codex Workflow

This repository includes a GitHub Codespaces configuration for Android launcher development with the Codex CLI available in the terminal.

## Create the Codespace

1. Open the repository in GitHub.
2. Select **Code** -> **Codespaces** -> **Create codespace on branch**.
3. Choose a setup branch or `main` after this configuration has merged.
4. Wait for the post-create script to finish.

The devcontainer installs:

- JDK 17.
- Node.js LTS.
- GitHub CLI.
- Android command-line tools.
- Android platform tools.
- Android 35 platform and build tools.
- The official scoped OpenAI Codex CLI npm package: `@openai/codex`.

## Authentication

Check GitHub authentication:

```bash
gh auth status
```

Start Codex authentication:

```bash
codex
```

Use Codespaces secrets for any required tokens. Do not commit secrets or write them to repository files.

## Verify the Android project

Run:

```bash
./gradlew verify
```

CI runs the same headless checks plus `deviceVerify` on its configured emulator.

## Work from GitHub Issues

List open issues:

```bash
gh issue list --state open --limit 50
```

Inspect an issue:

```bash
gh issue view <issue-number> --comments
```

Create a work branch:

```bash
git checkout main
git pull --ff-only
git checkout -b issue/<issue-number>-short-description
```

Give Codex a constrained prompt, for example:

```text
Work on BarMal/Riffle issue #<issue-number> only.
Follow AGENTS.md.
Keep changes small and modular.
Do not commit directly to main.
Run ./gradlew verify and report the result.
Open a PR when ready.
```

## PR checklist

Before opening or marking a PR ready:

- Link the issue.
- Summarise the implementation.
- Include `./gradlew verify` output or failure details.
- Document manual validation steps for Android UI/platform behaviour.
- Document limitations or follow-up issues.

## Security notes

- Install Codex only from trusted OpenAI sources or the official scoped package.
- Avoid similarly named third-party Codex packages.
- Do not put release signing material in Codespaces unless it is explicitly required for a release task.
- Release signing secrets belong in GitHub Actions secrets, as documented in `docs/release/signing.md`.
