# Screenshot Testing

Riffle renders its main surfaces as JVM screenshot tests with
[Roborazzi](https://github.com/takahirom/roborazzi) on Robolectric's native graphics. They need no
device or emulator: CI renders every one on each pull request and attaches the PNGs, so a reviewer
can see a UI change without installing the build.

## What is covered

The tests live in `app/src/test/java/com/riffle/app/screenshots/`.

| Test class | Surface | Variants |
| --- | --- | --- |
| `CardsScreenshotTest` | Cards mode through the real `HomeDestination` (stage surface and its dock): per-app stack, the merged "All notifications" page, the empty state | compact phone (plus dark and large font), unfolded foldable, tabletop, tablet landscape |
| `HomeScreenshotTest` | Standard grid Home through `HomeDestination` with a filled dock on the bottom edge, a side edge, and the device template's default edge | compact phone (plus dark and large font), unfolded foldable, tablet landscape |
| `DockScreenshotTest` | The `Dock` composable with pinned apps and a dynamic section (stage entries and the All-notifications entry) | bottom and side edge, dark, large font, unfolded |
| `AppearanceTuningScreenshotTest` | The Cards appearance tuning overlay with its sheet expanded over the preview surface | compact phone (plus dark and large font), unfolded foldable |

The postures come from Robolectric device qualifiers in `ScreenshotDevices`:

| Name | Window | Notes |
| --- | --- | --- |
| `COMPACT_PHONE` | 411 x 914 dp, 420 dpi | Default for every class. Dark adds `+night`; large font uses `fontScale = 2.0`. |
| `UNFOLDED_FOLDABLE` | 840 x 900 dp | Cards get a flat `UNFOLDED` posture. |
| `TABLETOP_FOLDABLE` | 900 x 840 dp | Cards get a `TABLETOP` posture and a horizontal hinge across the middle. |
| `TABLET_LANDSCAPE` | 1280 x 800 dp | |

Robolectric can't report a folding feature. So for foldable postures the Cards tests pass the
`AdaptiveStageWindowLayout` (posture and hinge) that `MainActivity` would get from Jetpack
WindowManager on a real device.

### Determinism

- **No clock.** Notification timestamps are fixed offsets from a constant. The UI only shows the
  pre-computed age bucket, never time relative to now.
- **No network.** No feeds are configured and no notification carries artwork.
- **Bundled artwork.** App icons come from `SolidColorAppIconLoader`, which draws a flat circle in
  a fixed colour for each app. The wallpaper is a fixed gradient drawn by `ScreenshotBackdrop`.
- **Pinned SDK.** Every class runs on SDK 34 (`ScreenshotDevices.SDK`), so a `targetSdk` bump
  doesn't change the rendering. It also keeps the tests working on the CI JDK 17 toolchain.
- Rendering uses Robolectric's hardware pixel-copy mode (`robolectric.pixelCopyRenderMode=hardware`),
  so shadows, elevation and blur look the way they do on a device.

When one of these surfaces can't be built from fake state, render the nearest composable that can
and note it in the test's KDoc.

## How the tests are gated

Screenshot tests are **not run** in an ordinary unit-test run. `./gradlew verify` compiles and
lints them but doesn't render them or compare their output. They are rendering-heavy, and until
goldens are committed there is nothing to compare against. The `riffle.android.screenshots` convention
plugin (`build-logic/`) excludes the `screenshots` package from every unit-test task unless one of
these is true:

- a Roborazzi task is named on the command line (`recordRoborazziDebug`, `verifyRoborazziDebug`,
  `compareRoborazziDebug`, `verifyAndRecordRoborazziDebug`)
- a `-Proborazzi.test.record|verify|compare=true` property is set
- `-Priffle.screenshots.verify=true` is set

Even then, only `testDebugUnitTest` runs them, because the Compose test activity comes from the
debug-only `ui-test-manifest` dependency.

Use the full task names, not Gradle's abbreviations (`recRD`). The plugin decides whether to include
the tests by looking for `Roborazzi` in the requested task names.

## Workflow

Goldens live in `app/src/test/screenshots/` and are named `ClassName.method.png`. They are recorded
at half resolution (`roborazzi.record.resizeScale=0.5` in `gradle.properties`) to keep the repository
small.

### Record (anyone, locally with an Android SDK)

```bash
./gradlew :app:recordRoborazziDebug
# faster: run only the screenshot tests
./gradlew :app:testDebugUnitTest --tests 'com.riffle.app.screenshots.*' :app:recordRoborazziDebug
```

The images are written to `app/src/test/screenshots/`.

### Record (no local SDK: use CI)

Every pull request runs the **Screenshots** job in `.github/workflows/ci.yml`. It records every
screenshot test and uploads the images as an artifact named `screenshots`, kept for 14 days. The
job isn't a merge gate while goldens don't exist. If a test fails to render, the job fails and
uploads the test report as `screenshot-test-report`.

To review or adopt the images:

1. Open the pull request's **Checks** tab, choose the **CI** workflow run, and scroll to
   **Artifacts**.
2. Download `screenshots`, or use the CLI:
   `gh run download <run-id> --name screenshots --dir app/src/test/screenshots`.
3. Look through the PNGs. When they're right, commit them on the pull request's branch:

   ```bash
   gh run download <run-id> --name screenshots --dir app/src/test/screenshots
   git add app/src/test/screenshots
   git commit -m "Record screenshot goldens"
   ```

The artifact holds the contents of `app/src/test/screenshots/`, so it unpacks straight into that
directory.

### Verify (compare against committed goldens)

```bash
./gradlew verify -Priffle.screenshots.verify=true
# or just the screenshots
./gradlew :app:verifyRoborazziDebug
```

With the property set, the root `verify` task also depends on `:app:verifyRoborazziDebug`. That
runs the debug unit tests in compare mode and fails on any difference from a golden, or on a
missing golden. Diff images are written to `app/build/outputs/roborazzi/`. `compareRoborazziDebug`
writes the same diffs without failing, and a report is written to
`app/build/reports/roborazzi/index.html`.

### Update goldens after an intended UI change

Re-record, either locally with `recordRoborazziDebug` or from the pull request's `screenshots`
artifact as above. Then commit the changed PNGs in the same pull request as the UI change, so the
diff shows before and after.

## Turning comparison on in CI

Once a maintainer has committed a first full set of goldens:

1. In `.github/workflows/ci.yml`, change the **Headless verify** step to
   `./gradlew verify -Priffle.screenshots.verify=true`.
2. Keep the **Screenshots** job. It still gives reviewers the rendered images when a comparison
   fails.

Until then, a pull request that changes a covered surface should mention in its description that
the images are in the `screenshots` artifact.

## Adding a screenshot test

1. Put it in `com.riffle.app.screenshots` (anything outside that package runs as an ordinary unit
   test).
2. Annotate the class with `@RunWith(RobolectricTestRunner::class)`,
   `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and
   `@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)`. Override
   `qualifiers` or `fontScale` per method for other variants.
3. Build state from `ScreenshotFixtures`. Wrap content in `ScreenshotBackdrop`, pass a
   `SolidColorAppIconLoader`, and finish with `composeRule.captureScreen()`.
4. Record it, and commit the new golden once comparison is on.
