plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    // Keep in step with `roborazzi` in gradle/libs.versions.toml (the runtime library the tests use).
    implementation("io.github.takahirom.roborazzi:roborazzi-gradle-plugin:1.43.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.0.21")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
}
