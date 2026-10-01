# Workspaces screenshot matrix

Audit for WS9 (issue #1357). The plan (workspaces-sources-lenses.md, WS9) requires "screenshot tests for
every expression and preset in compact and unfolded". This page records what exists, what was filled in,
what is still missing, and how to review the images.

Tests: `app/src/test/java/com/riffle/app/screenshots/expressions/` (one class per expression, shared
`ExpressionFixtures`, `renderExpression`) and `.../screenshots/containers/ContainersScreenshotTest.kt`
(shared `ContainerFixtures`, `StaticLensResultProvider`). Devices come from `ScreenshotDevices`:
compact = `COMPACT_PHONE` (411x914dp), unfolded = `UNFOLDED_FOLDABLE` (840x900dp), dark = `+night`, large
font = `fontScale 2.0`. Golden PNGs are named `ClassName.method.png`.

Legend: `x` exists on main before this PR; `+` added by this PR; `-` missing (a gap, see notes);
`n/a` not meaningful for that surface.

## Expressions

State columns use `ExpressionState`: `Ready` with an empty result is "empty", `Loading`, and
`Unavailable(message)` which is how a host shows a missing permission or an unavailable source (there is
no separate permission state at expression level; the container host maps `LensOutput.PermissionRequired`
to it, covered under Containers).

| Expression | Compact | Unfolded | Dark | Large font | Empty | Loading | Unavailable |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Card | x | x | x | x | x | + | + |
| CardStack | x | x | x | x | x | + | + |
| Categories | x | x | x | x | x | + | + |
| IconGrid | x | x | x | x | x | + | + |
| IconRow | x | x | x | x | x | + | + |
| AlphaList | x | x | x | x | x | + | + |
| Index (grouped) | x | x | x | x | x (grouped) | + | x |
| Index (flat) | x | - | - | - | - | n/a | n/a |
| List | x | x | x | x | x | x | + |

Remaining gaps, deliberately not filled:

- Dark and large font in the unfolded width (cross product). The plan asks for compact and unfolded;
  each axis is covered independently. Adding the product would double the image count for little signal.
- Tabletop and tablet landscape for expressions. Expressions are single-pane width-driven composables
  (they cap readable width at 640dp), so unfolded covers the wide case; the Home and Cards classes
  already cover tablet and tabletop.
- Reduced motion (CardStack, expressions with transitions) and RTL are not in any screenshot test.
  Reduced motion changes motion, not static pixels, so a screenshot would not differ; cover it with
  behaviour tests. RTL is a candidate follow-up if a preset needs it.
- Index (flat) only has a compact image; flat is a fallback shape, grouped is the primary.

## Containers

Hosts: `WidgetContainerHost`, `PageContainerHost` (bound page and widget-grid page), `PageSetContainerHost`.

| Container | Compact | Unfolded | Dark | Large font | Empty | Loading | Permission | Unavailable |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Widget | x | x | + | - | - | x | x | + |
| Bound page | x | x | - | - | - | - | - | - |
| Widget-grid page | x | x | x | - | - | n/a | n/a | n/a |
| Page set | x | x | + | x | - | - | + | x |

Notes:

- Widget-grid pages host several widgets, each with its own state, so a whole-page loading, permission or
  unavailable image is not meaningful; those states are covered by the single-widget rows.
- Still missing for a later pass if a preset needs them: the bound page's non-ready states, empty
  results at every container, and large font for widget, bound page and widget-grid page. Each is a
  one-line test using `ContainerFixtures` plus `StaticLensResultProvider(LensOutput.X)`.

## Presets (pending, WS8 in flight)

Preset screenshot tests are not possible yet: the preset definitions (iOS, Nova, TimeScape, Niagara,
Kvaesitso) do not exist on main. Pending entries, one row per preset and workspace page:

| Preset | Compact | Unfolded | Dark | Large font | Empty / loading / permission / unavailable |
| --- | --- | --- | --- | --- | --- |
| iOS | pending | pending | pending | pending | pending |
| Nova | pending | pending | pending | pending | pending |
| TimeScape (folded Now / Notifications / Recents) | pending | pending (separate unfolded workspace) | pending | pending | pending |
| Niagara | pending | pending | pending | pending | pending |
| Kvaesitso | pending | pending | pending | pending | pending |

Intended harness shape (describe only; no code until WS8 lands):

- A `PresetsScreenshotTest` (or one class per preset) that iterates the preset's workspaces and pages
  and renders each page's container through the existing WS4 hosts (`PageContainerHost`,
  `PageSetContainerHost`, `WidgetContainerHost`) inside `renderExpression`.
- Data from a `StaticLensResultProvider` whose lambda maps a lens's first source id to the shared
  `ExpressionFixtures` results (same trick as `ContainerFixtures.resultFor`), so no source subscribes and
  nothing depends on the clock or network.
- Variants per preset: compact, `UNFOLDED_FOLDABLE` (pick the preset's unfolded workspace where one
  exists), `NIGHT`, `LARGE_FONT_SCALE`, plus one image with every lens forced to
  `LensOutput.PermissionRequired` and one with `Unavailable`.
- Test names `<preset><Page>Compact|Unfolded|CompactDark|CompactLargeFont`; goldens follow the same
  `ClassName.method.png` rule so reviewers see them in the same artifact.
- Rendering through the real hosts keeps presets honest: if a preset references an expression/container
  combination the hosts cannot draw, the test fails rather than the preset silently degrading.

## How CI publishes the images and how to review them

Workflow `.github/workflows/ci.yml`, job **Screenshots** (runs on every pull request and on pushes to
`main`):

1. Runs `./gradlew :app:testDebugUnitTest --tests 'com.riffle.app.screenshots.*' :app:recordRoborazziDebug`
   (record-only; not a merge gate while goldens are not committed).
2. Uploads `app/src/test/screenshots/` as the artifact `screenshots` (14 days). If rendering fails,
   it uploads the HTML test report as `screenshot-test-report`.

To review: open the PR, Checks tab, the CI run, Artifacts, download `screenshots`, or
`gh run download <run-id> --name screenshots --dir app/src/test/screenshots`. Compare image names
against the tables above (`<Class>.<method>.png`). `./gradlew verify` compiles and lints screenshot tests
but does not render them, so a green headless verify says nothing about the images; check the Screenshots
job. Full details in `docs/development/screenshot-testing.md`.

Proposed doc edit (not made here): that guide's "What is covered" table lists only the Cards, Home, Dock,
DockPull and AppearanceTuning classes. Add rows for the expression classes and `ContainersScreenshotTest`,
or point to this matrix.
