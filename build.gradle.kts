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
}

tasks.register("deviceVerify") {
    group = "verification"
    description = "Runs connected-device checks in an emulator-backed environment."
    val appProjects = subprojects.filter { it.plugins.hasPlugin("com.android.application") }
    dependsOn(appProjects.map { "${it.path}:connectedDebugAndroidTest" })
}
