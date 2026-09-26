import io.github.takahirom.roborazzi.RoborazziExtension

/*
 * JVM screenshot tests (Roborazzi on Robolectric native graphics) for an Android module.
 *
 * Screenshot tests live under the `screenshots` package of the unit-test source set and are kept out
 * of ordinary unit-test runs -- and so out of `./gradlew verify` -- unless screenshots were asked
 * for. They are rendering-heavy, and until goldens are committed there is nothing to compare them
 * against. They run when any of these is true:
 *
 * - a Roborazzi task is on the command line (`recordRoborazziDebug`, `verifyRoborazziDebug`,
 *   `compareRoborazziDebug`, ...);
 * - a `roborazzi.test.record|verify|compare=true` Gradle property is set;
 * - `-Priffle.screenshots.verify=true` is set, which also makes the root `verify` task compare
 *   against the committed goldens (see the root build script).
 *
 * Only the debug unit-test task ever runs them: the Compose test activity they render into comes
 * from `ui-test-manifest`, which is a debug-only dependency.
 *
 * See docs/development/screenshot-testing.md for the record / verify / update workflow.
 */

plugins {
    id("io.github.takahirom.roborazzi")
}

val screenshotTestClasses = "**/screenshots/**"
val screenshotTestTaskName = "testDebugUnitTest"

val verifyScreenshots =
    providers
        .gradleProperty("riffle.screenshots.verify")
        .map { value -> value.toBoolean() }
        .getOrElse(false)
val roborazziTaskRequested =
    gradle.startParameter.taskNames.any { taskName -> taskName.contains("Roborazzi") }
val roborazziModeRequested =
    listOf("record", "verify", "compare").any { mode ->
        providers.gradleProperty("roborazzi.test.$mode").orNull == "true"
    }
val runScreenshotTests = verifyScreenshots || roborazziTaskRequested || roborazziModeRequested

extensions.configure<RoborazziExtension> {
    // Goldens are source: recorded here, committed, and compared against in verify mode. Diff
    // images from a failed verify still land in build/outputs/roborazzi.
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

tasks.withType<Test>().configureEach {
    if (runScreenshotTests && name == screenshotTestTaskName) {
        // Robolectric's hardware renderer draws shadows, elevation and blur the way a device does.
        systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        maxHeapSize = "2g"
    } else {
        exclude(screenshotTestClasses)
    }
}
