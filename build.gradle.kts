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
    description = "Runs the checks expected before opening a pull request."
    val checkableProjects = subprojects.filter { it.buildFile.exists() }
    dependsOn(checkableProjects.map { "${it.path}:check" })
    dependsOn(checkableProjects.map { "${it.path}:ktlintCheck" })
    dependsOn(checkableProjects.map { "${it.path}:detekt" })
}
