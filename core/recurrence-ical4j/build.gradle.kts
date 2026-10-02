plugins {
    id("riffle.kotlin.jvm")
}

// The only module that may depend on the recurrence library. :app depends on it for the ICS feed source
// (docs/product/workspaces-ics-source.md); see docs/product/workspaces-ics-recurrence.md for the decision.
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.ical4j)

    testImplementation(kotlin("test"))
}
