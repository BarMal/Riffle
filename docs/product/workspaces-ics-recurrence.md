# Workspaces: ICS recurrence spike (S12, decision N8)

Status: spike complete, library chosen, seam and tests merged-ready; the ICS feed source itself is not built.
Issue: #1395 (part of #1363). Decision N8 and the selection criteria are in
[`workspaces-configuration.md`](workspaces-configuration.md) section 15.2; the source it feeds is in
[`workspaces-external-sources.md`](workspaces-external-sources.md) (ICS subscribe-by-URL).

Evidence labels: **MEASURED** was run in the spike sandbox on 2026-10-01 (JVM 17, Gradle 8, Maven Central only);
**ESTIMATED** is derived from measured artifacts but not from an Android build (the sandbox cannot resolve AGP or
run D8/R8); **NOT DONE** needs a device or CI.

## 1. Does the calendar source need this? No.

`AndroidCalendarEventRepository` reads `CalendarContract.Instances`, which the calendar provider has already expanded:
`RRULE`, `RDATE`, `EXDATE` and `RECURRENCE-ID` exceptions are the provider's job, and every recurring occurrence arrives as
its own row (`CalendarInstanceRow`, one per instance; the item id is `eventId:begin` for that reason). All-day rows are
already moved from UTC midnight to local midnight. So the **device calendar source is not changed** and the library is not
wired into it. Re-expanding in the launcher would duplicate the provider's logic and could disagree with the user's calendar app.

The library is needed only where Riffle receives raw iCalendar text with no provider: the future **ICS feed source**
(`ICS_URL`). That source does not exist yet, so nothing in this slice ships the library in the app.

## 2. Evaluation

Candidates are the shortlist of section 15.2. biweekly was not evaluated further: its last release is older than
lib-recur's and its recurrence iterator was never verified.

### 2.1 Facts

| Criterion | ical4j 4.3.0 | lib-recur 0.17.1 |
| --- | --- | --- |
| Licence | BSD-3-Clause | Apache-2.0 (MIT-compatible either way) |
| minSdk fit (app minSdk is 31) | Class files are Java 11 (major 55; MEASURED); D8 handles it, `java.time` is native on API 31 so no back-port. No `threetenbp` back-port: its `org.threeten:threeten-extra` dependency extends `java.time` itself (Java 8 bytecode, MEASURED). | Java 8 bytecode (major 52, MEASURED), own `rfc5545-datetime` types, no `java.time` |
| Transitive dependencies (MEASURED, Gradle resolution) | `slf4j-api` 2.0.12, `commons-codec` 1.17.0, `commons-lang3` 3.18.0, `threeten-extra` 1.8.0 | `rfc5545-datetime` 0.3, `jems2` 2.23.1 |
| Jar bytes, library plus its transitives (MEASURED) | 1,675,672 + 68,115 + 372,608 + 702,952 + 280,525 = **3,099,872** | 164,789 + 32,721 + 143,173 = **340,683** |
| Uncompressed class/resource bytes (MEASURED) | ical4j 4.1 MB (of which about 2.2 MB are bundled `zoneinfo` resources), plus 2.9 MB for its dependencies | 0.31 MB plus 0.32 MB |
| Android-hostile references (MEASURED, class-file string scan) | `groovy.*` (optional Groovy extension classes, dependency absent), `java.beans.Transient` (annotation, absent on Android), `javax.xml.xpath` and `javax.xml.parsers` (present on Android). A `META-INF/services/java.time.zone.ZoneRulesProvider` entry. None is touched by the `Recur` rule engine. | None found |
| Maintenance | Release 2026-06-27 (4.3.0); active | Release 2024-04-07; README says the API "is not finalized yet and subject to change" |
| Scope | Parser, `VTIMEZONE`, recurrence | Recurrence only; a parser is a separate piece |
| Main risk | Large surface, optional-dependency classes | Stale; one maintainer |

### 2.2 Correctness corpus (MEASURED)

Both libraries were driven through the same `RecurrenceExpander` seam (section 3) with the same abstract corpus
(`RecurrenceCorpus`, 52 cases). **Both pass all 52.** For lib-recur the spike used a throw-away adapter (not committed)
that converted the same rule text; for ical4j it is the committed implementation.

The cases include the RFC 5545 section 3.8.5.3 examples for week start (`WKST=MO` and `WKST=SU`), US election day,
`BYWEEKNO`, `BYYEARDAY`, `BYSETPOS` (last weekday, second-to-last weekday, first and last), ordinal `BYDAY`
(`2TU`, `-1FR`), `BYMONTHDAY=31` and `-1`, leap days (yearly, and by month and day), `COUNT` and `UNTIL` as UTC,
floating and date-only, `EXDATE` and `RDATE` combinations, moved and cancelled overrides, DST gap and overlap in New York,
a zone that dropped DST (Sao Paulo, 2019), a non-DST zone (Tokyo), UTC, all-day across the spring-forward, a window far
after the start, `COUNT` counted from the start and not the window, the instance cap, a rule generating thousands of starts per day,
a `COUNT=2147483647` rule, and thirteen malformed or refused rules. The rule-text pre-processing (`UNTIL` restatement, frequency and duplicate-key checks) is shared by both runs; only the rule generator differs.

### 2.3 Speed and heap (MEASURED, JVM, warmed up, not Android)

Window of 28 years from a 2000 start, cap 10,000, America/New_York:

| Rule | ical4j (warm) | lib-recur (warm) | Instances |
| --- | --- | --- | --- |
| `FREQ=DAILY` | 6 to 12 ms (first call 63 ms) | 8 ms (first call 250 ms) | 10,000 |
| `FREQ=WEEKLY;BYDAY=MO,WE,FR` | 31 to 48 ms | 8 to 11 ms | 4,383 |
| `FREQ=MONTHLY;BYDAY=MO..FR;BYSETPOS=-1` | 129 to 204 ms | 2 ms | 336 |

Heap after the run: about 86 MB used for ical4j, 38 MB for lib-recur (JVM, includes garbage; not a leak measurement).
ical4j is clearly slower on `BYSETPOS`; both are far below anything that matters for a refresh run off the main thread, and
real feeds ask for a 7 to 30 day window, not 28 years.

### 2.4 APK size (ESTIMATED)

The app is about 18 MB (#53). Release builds currently set `isMinifyEnabled = false`, so **nothing would be shrunk**.
Taking the jars as an upper bound: ical4j and its dependencies add roughly **2 to 3 MB** (about 11 to 17 percent of the
current APK), of which roughly 1 MB compressed is `zoneinfo` timezone resources the rule engine never reads; lib-recur adds
roughly **0.3 MB** (under 2 percent). These are not APK measurements: confirm with `assembleRelease` size before and after
in CI when the library is first added to `:app`. Possible mitigations, none applied yet: enable R8 for release, a
`packaging.resources.excludes` for `zoneinfo*`, `-dontwarn groovy.**` / `-dontwarn java.beans.Transient`.

## 3. Decision

**ical4j 4.3.0, kept confined to its `Recur` rule engine; lib-recur is the proven drop-in fallback.**

Why, from the evidence above:

1. **Correctness is a tie.** Both pass the whole corpus, so the choice is about everything around recurrence.
2. **The ICS source needs more than recurrence.** It must parse `VEVENT`, `RRULE`, `EXDATE`, `RDATE`, `RECURRENCE-ID` and
   `VTIMEZONE` from untrusted text. ical4j is the only candidate with a maintained parser and time-zone handling; with
   lib-recur Riffle would write and own a bounded RFC 5545 parser (the larger correctness and security risk), or add a
   second library. That parser question is **not** answered by this spike (only the rule engine was exercised).
3. **Maintenance.** ical4j released three months before this spike; lib-recur's last release is two and a half years old with
   an explicitly unstable API.
4. **The cost is real and accepted conditionally:** about 2 to 3 MB estimated, four transitive dependencies, a few
   Android-missing optional classes that need `-dontwarn`, slower `BYSETPOS`. These do not affect correctness.

**Flip condition (recorded, not a hedge):** if the ICS source slice decides to parse feeds with an in-repo bounded parser
instead of ical4j's parser, use lib-recur: it is about a tenth of the size and passes the same corpus, and the swap is one
class in `core/recurrence-ical4j` (renamed) plus the dependency line in `gradle/libs.versions.toml`. If the size
budget in #53 is tightened below what ical4j allows, the same flip applies.

Nothing here is on-device evidence: see section 5.

## 4. As built

* `core/domain` (`com.riffle.core.domain.launcher.workspace.sources.ics`, no library, java.time only):
  * `RecurrenceExpander` (`fun interface`): `expand(RecurrenceRequest): RecurrenceResult`.
  * `RecurrenceRequest`: `rrule` (null for RDATE-only or single events), `dtStart`, `allDay`, `zone`, `exDates`, `rDates`,
    `overrides` (`RecurrenceOverride`, a moved or cancelled `RECURRENCE-ID`), `window` (half-open `TimeWindow`), `maxInstances`
    (default 500, hard maximum 10,000). Wall-clock times are in the event's zone, which is what RFC 5545 recurrence is
    defined on, so a daily 09:00 event keeps 09:00 across a DST change.
  * `RecurrenceResult.Ok(occurrences, truncated)` or `Failed(INVALID_RULE | UNSUPPORTED_RULE)`; no message, because rule text is
    user content. Each `Occurrence` has the instant and its `originalStart` (the `RECURRENCE-ID`).
  * `assemble` and `toInstantIn`: the library-independent half. `RDATE` union, `EXDATE` removal, override replacement,
    zone resolution (gap: forward by the gap; overlap: first occurrence, per RFC 5545 section 3.3.5), window filter,
    ordering, de-duplication and the cap. Tested without the library in `OccurrenceAssemblyTest`.
* `core/recurrence-ical4j` (new JVM module, `riffle.kotlin.jvm`): `Ical4jRecurrenceExpander`, the only code that imports
  ical4j. It uses only `Recur`: no calendar parser, no time-zone registry. It restates `UNTIL` as wall-clock time (UTC
  converted to the event zone; date-only is inclusive of the whole day), refuses sub-daily frequencies and non-Gregorian
  `RSCALE` as `UNSUPPORTED_RULE`, rejects duplicate keys, an unknown `FREQ` and rules over 1,024 characters as `INVALID_RULE`,
  catches every library failure as `INVALID_RULE`, and reports `truncated` when the library limit was reached.
* `RecurrenceCorpus` (abstract, 52 cases) and `Ical4jRecurrenceExpanderTest`: a replacement implementation subclasses the
  corpus and must pass the same table.
* `gradle/libs.versions.toml`: `ical4j = "4.3.0"`, `libs.ical4j`. The module is registered in `settings.gradle.kts`, so
  `verify` runs its `check`, `ktlintCheck` and `detekt` automatically.
* **`:app` does not depend on the module.** No APK change, no permission, no network, no background work.

The section 15.3 sketch named a broader `IcsEngine` (parse plus expand, one module `external-ics-ical4j`). This slice built
the narrower recurrence seam first, because that is what the spike could verify. `IcsEngine` can later be a thin layer over
`RecurrenceExpander` plus a parser; the module is named for what it contains today.

## 5. Not done

* **Dependency verification.** The repository has no `gradle/verification-metadata.xml` and no lockfiles, so there was nothing
  to regenerate; if one is added later the new artifacts (ical4j 4.3.0 and its four dependencies) need entries.
* **`./gradlew verify` was not run locally.** The sandbox cannot resolve the Android Gradle plugin. The domain code, the
  library implementation and all tests were run in a scratch Gradle project (Kotlin 2.0.21, Maven Central only) using the repo's
  detekt config and ktlint settings: 52 corpus-module tests and 10 domain tests pass, ktlint and detekt are clean.
  CI (`./gradlew verify deviceVerify`) is the first run of the real build; the new module's dependency resolution happens there.
* **No Android measurements:** APK size (section 2.4 is an estimate), D8/R8 behaviour, on-device run of the corpus, whether the
  `ZoneRulesProvider` service entry or the Groovy/`java.beans` classes cause R8 or runtime warnings. Do this before `:app`
  first depends on the module.
* **Parser not spiked:** `VEVENT`/`VTIMEZONE` parsing, `RECURRENCE-ID` extraction from a feed, `TZID` to `ZoneId` mapping
  (including unknown or Windows-style zone names), floating times, `RDATE;VALUE=PERIOD`, `RANGE=THISANDFUTURE`, `EXDATE`
  with a different value type than `DTSTART`, and the hostile-feed size limits. The seam takes already-parsed wall-clock values.
* **Sub-daily rules are refused** (`FREQ=HOURLY`/`MINUTELY`/`SECONDLY`), because iteration from a distant start is not bounded
  cheaply. If a feed needs them, expand them from a near start with an explicit iteration budget.
* **No iteration-time budget.** Wall-clock cost is bounded by the window and cap for `DAILY` and longer, but a `BYHOUR`/`BYMINUTE`
  rule from a very distant start is still generated from the start. Callers must run expansion off the main thread.
* **Errata of RFC 5545 section 3.8.5.3:** the corpus uses the RFC examples that are unaffected; no erratum-corrected cases
  were added.
* The device calendar source (`CalendarContract.Instances`) is unchanged by design (section 1). Its 7-day window and
  200-row cap are separate, existing limits.
