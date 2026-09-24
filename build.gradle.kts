plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

tasks.register("verify") {
    group = "verification"
    description = "Runs headless checks expected before opening a pull request."
    val checkableProjects = subprojects.filter { it.buildFile.exists() }
    val appProjects = subprojects.filter { it.plugins.hasPlugin("com.android.application") }
    dependsOn(checkableProjects.map { "${it.path}:check" })
    dependsOn(checkableProjects.map { "${it.path}:ktlintCheck" })
    dependsOn(checkableProjects.map { "${it.path}:detekt" })
    dependsOn(appProjects.map { "${it.path}:assembleDebug" })
    // Screenshot comparison is opt-in until goldens are committed and CI switches it on; see
    // docs/development/screenshot-testing.md. Having the Roborazzi verify task in the graph is what
    // puts the debug unit-test run into compare-against-goldens mode.
    if (providers.gradleProperty("riffle.screenshots.verify").map { it.toBoolean() }.getOrElse(false)) {
        dependsOn(appProjects.map { "${it.path}:verifyRoborazziDebug" })
    }
}

tasks.register("deviceVerify") {
    group = "verification"
    description = "Runs connected-device checks in an emulator-backed environment."
    val appProjects = subprojects.filter { it.plugins.hasPlugin("com.android.application") }
    dependsOn(appProjects.map { "${it.path}:connectedDebugAndroidTest" })
}
