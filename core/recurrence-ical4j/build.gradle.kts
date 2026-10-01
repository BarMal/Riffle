plugins {
    id("riffle.kotlin.jvm")
}

// The only module that may depend on the recurrence library. It is deliberately not a dependency of :app
// yet: nothing ships it until the ICS feed source exists (docs/product/workspaces-ics-recurrence.md).
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.ical4j)

    testImplementation(kotlin("test"))
}
