## Release rules for :app. Only applied when R8 runs, i.e. when built with -Priffle.minify=true
## (docs/release/r8-minification.md). The shipped release does not minify yet.
##
## Deliberately small: the manifest components (MainActivity, the notification listener and the
## overlay service) are kept by AAPT2-generated rules, Compose/DataStore/coroutines/emoji2/lifecycle
## ship their own consumer rules, and the app has no reflection, serialization framework or
## ServiceLoader use. Enums persisted by name use valueOf()/enumValues()/.name, which R8 preserves.
## Add a rule only with a justification comment, and prefer fixing the call site over a broad keep.

# Readable crash stack traces once the mapping file is retained next to the build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The system binds this service by component name and checks it against the enabled notification
# listeners setting. Already kept via the manifest rule; stated here so a future manifest or
# class-name refactor cannot silently drop it. (AndroidNotificationAccessGateway,
# RiffleNotificationListenerService.)
-keep class com.riffle.app.launcher.notifications.RiffleNotificationListenerService { *; }

# androidx.window loads its extensions implementation reflectively from the device and compiles
# against stubs. Not expected to be required (the library ships consumer rules); present so a
# missing-class report from R8 does not fail the build for classes that only exist on-device.
# VERIFY in the first CI run: delete these two lines if R8 does not need them.
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

# ical4j (core/recurrence-ical4j, used only for its Recur rule engine by the ICS feed source). Its jar
# references optional Groovy extension classes (the Groovy runtime is not a dependency) and the JDK-only
# java.beans.Transient annotation (absent on Android); neither is on the Recur path. Safe to ignore, and
# no keep rule is needed: there is no reflection on that path (the calendar parser and its ServiceLoader
# factories are never used). See docs/release/r8-minification.md.
-dontwarn groovy.**
-dontwarn java.beans.Transient
# DefaultZoneRulesProvider extends java.time.zone.ZoneRulesProvider, which the Android SDK does not expose.
# ical4j only loads it through its (excluded) service entry and its zone registry, neither used here.
-dontwarn java.time.zone.ZoneRulesProvider
# threeten-extra (an ical4j dependency) names the optional Joda-Convert annotations.
-dontwarn org.joda.convert.**
