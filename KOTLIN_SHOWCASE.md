# Kotlin and Spring Boot: From Messy Public Data to a Robust Application

Companion notes for the talk. The running example is this project: a Spring Boot app in Kotlin
that reads public Berlin demonstration data, parses semi-structured HTML, converts raw rows into a
typed domain model, classifies events with a small rule DSL, geocodes locations where possible, and
serves the result through a Spring MVC API, a map, and a timeline.

**The framing (from the abstract):** this is *not* a rewrite pitch. Spring Boot stays the familiar
foundation — `@RestController`, `@Service`, `@Scheduled`, `JdbcClient`, `@ConfigurationProperties`,
Actuator. Kotlin earns its place in the parts where enterprise code usually turns defensive and
noisy: **nullable input, parsing results, rejected rows, domain identifiers, explicit states, and
readable transformation pipelines**. Every code block below is real code from the repo.

---

## 0. The pipeline (the whole talk on one slide)

```
Berlin Police HTML  →  BerlinPolicePageClient        (fetch, force UTF-8)
                    →  BerlinPolicePageParser (Jsoup) →  List<RawDemonstrationRow>
                    →  DemonstrationNormalizer        →  List<NormalizedDemonstration> + rejected rows
                    →  ImpactClassifier (Kotlin DSL)  →  ImpactAssessment (score + reasons)
                    →  GeocoderService + Nominatim    →  EventGeometry (Point | Unknown)
                    →  DemonstrationReadModel          (immutable AtomicReference snapshot)
                    →  Spring MVC API  →  Thymeleaf + vanilla JS + MapLibre
```

Each arrow is a place where input is messy and Kotlin keeps the code honest instead of noisy. The
sections below walk the arrows.

---

## 1. Messy input, without the null-check noise

Source rows are semi-structured: fields missing, German date/time formats, time strings that look
like locations. The parser's only job is to extract raw rows and **preserve `rawText`** — every
field is nullable and stays that way (`parser/RawDemonstrationRow.kt`):

```kotlin
data class RawDemonstrationRow(
    val rowIndex: Int,
    val rawText: String,
    val titleRaw: String?,
    val dateRaw: String?,
    val timeRaw: String?,
    val locationRaw: String?,
    val routeRaw: String?,
    val districtRaw: String?,
    val participantCountRaw: String?,
)
```

Kotlin's type-level nullability (`String?`, `?.`, `?:`, smart casts) means the "field might be
missing" case is in the type, not in a pile of defensive `if (x != null)` blocks. The source page
being down is a typed failure too (`parser/BerlinPolicePageClient.kt` throws
`SourcePageUnavailableException`), not a random `NullPointerException` deep in the pipeline.

---

## 2. Parsing results and rejected rows as explicit states

A bad row must not fail the whole import. The normalizer returns a **sealed result**, and the caller
collects successes and rejections side by side (`normalizer/DemonstrationNormalizer.kt`):

```kotlin
sealed interface NormalizationResult {
    data class Success(val value: NormalizedDemonstration) : NormalizationResult
    data class Rejected(val row: RawDemonstrationRow, val reason: String) : NormalizationResult
}

fun normalizeAll(rows: List<RawDemonstrationRow>): Pair<List<NormalizedDemonstration>, List<NormalizationResult.Rejected>> {
    // date that won't parse → Rejected(row, "Could not parse date: ..."), import continues
}
```

Rejected rows are surfaced, not swallowed — they land in `ImportDiagnostics.rejectedRows` and are
visible on `/api/snapshot/status`. "Some rows are bad" is an expected, reported outcome, not an
exception.

---

## 3. Domain identifiers and value classes (zero runtime cost)

`domain/DomainTypes.kt` — stop passing bare `String`/`Int` around, and put invariants where they
can't be bypassed:

```kotlin
@JvmInline value class DemonstrationId(val value: String)
@JvmInline value class SourceHash(val value: String)

@JvmInline
value class ImpactScore(val value: Int) {
    init { require(value in 0..100) { "Impact score must be in 0..100, got $value" } }
}
```

`@JvmInline` compiles to the underlying `String`/`int` — the type safety and the `0..100` invariant
cost nothing at runtime. In Java this is a wrapper class (allocation) or a lost invariant.

---

## 4. Explicit states with sealed types + exhaustive `when`

"Missing coordinates" is a first-class state, not a null or a magic value
(`domain/EventGeometry.kt`):

```kotlin
sealed interface EventGeometry {
    data class Point(val lat: Double, val lon: Double, val confidence: Double) : EventGeometry
    data object Unknown : EventGeometry
}
```

```kotlin
// query/GeoJsonService.kt — no `else`; add a subtype and this stops compiling
when (val g = demo.geometry) {
    is EventGeometry.Point -> feature(g)
    EventGeometry.Unknown  -> null   // stays in the timeline, absent from the map
}
```

The same pattern models every outcome the pipeline cares about, so the caller is forced to handle
each case: `ImportResult` (`Success` / `Failure` / `AlreadyRunning`), `NormalizationResult`,
`ParserRunHandle` (`Persisted` / `NotPersisted` — diagnostics DB may be down).

---

## 5. The rule DSL — explainable, not a black box

The classifier is the centerpiece: **type-safe Kotlin that reads like configuration**, lives in the
IDE with full navigation and refactoring, and can explain *why* a score was assigned. No AI, no
annotation processing, no reflection — just a builder with `Builder.() -> Unit` receivers
(`classifier/ImpactRuleDsl.kt`, `classifier/ImpactClassifier.kt`):

```kotlin
private val rules = impactRules {
    topic(DemonstrationCategory.CLIMATE_ENVIRONMENT) {
        keywords("klima", "umwelt", "fridays", "co2")
        topicWeight(10)
        publicLabel("Climate or environment assembly")
        reason("Climate or environment-related topic")
    }
    signal("large crowd") {
        whenParticipantsAtLeast(2_000)
        addImpact(20)
        reason("Large expected crowd")
    }
    signal("moving route") {
        whenHasRoute()
        addImpact(25)
        reason("Moving route affects multiple streets")
    }
}
```

The result carries its own justification, so the API/UI can show the "why":

```kotlin
data class ImpactAssessment(
    val category: DemonstrationCategory,
    val publicLabel: String,          // neutral, never judges the cause
    val level: ImpactLevel,
    val score: ImpactScore,
    val reasons: List<String>,        // human-readable sentences
    val evidence: List<String>,       // which topic/signals fired
)
```

Because the rule set is built once (singleton), keyword normalization and regex compilation are
hoisted out of the hot path into `KeywordMatcher` — a nice "make it correct, then make it cheap"
aside.

---

## 6. Readable transformation pipelines

Filtering reads top-to-bottom as a sequence, defined once and reused by the query, timeline, and
GeoJSON services (`query/DemonstrationFilterExt.kt`):

```kotlin
fun Sequence<Demonstration>.applyFilter(f: DemonstrationFilter): Sequence<Demonstration> =
    filter { f.dateFrom == null || it.date >= f.dateFrom }
        .filter { f.district == null || it.district == f.district }
        .filter { f.category == null || it.category == f.category }
        .filter { f.impactLevel == null || it.impact.level == f.impactLevel }
        .filter { f.minImpactScore == null || it.impact.score.value >= f.minImpactScore }
```

Extension functions + sequences turn "loop with accumulators and flags" into a declarative
pipeline — the transformation *is* the code.

---

## 7. Robust in practice — expected problems are states, not HTTP 500

This is the talk's payoff: the things that always go wrong with real data are modeled as ordinary
outcomes, so they never reach the generic error path.

- **Unstable external service (Nominatim):** rate-limited to 1 req/s, and a failure returns
  `EventGeometry.Unknown` + a warning — the event still shows in the timeline. Failed lookups are
  negatively cached (`confidence = -1.0`) so they are never re-queried
  (`geocoder/GeocoderService.kt`, `geocoder/NominatimClient.kt`).
- **Infrastructure degradation:** `GeocodeCacheRepository` catches `DataAccessException` and treats
  the cache as a miss; `ParserRunHandle.NotPersisted` lets the import run even when the diagnostics
  table is unavailable. The import degrades; it doesn't crash.
- **Partial results:** the import publishes a **skeleton snapshot immediately**, then geocodes
  progressively, republishing as it goes; `geocodingComplete` flips only when the loop finishes, so
  the UI shows honest progress instead of a spinner or a wrong 100%.
- **Never replace good data with a failure:** `DemonstrationImportService` captures the last-good
  snapshot and rolls back to it if the import throws — a failed refresh never blanks the map:
  ```kotlin
  val previous = readModel.current()
  try { /* build + publish new snapshot */ }
  catch (e: Exception) { if (previous != null) readModel.replace(previous); /* report failure */ }
  ```
- **Expected client mistakes → 4xx, not 5xx:** query params carry constraints
  (`@Min(0) @Max(100) minImpactScore`) validated by Spring's built-in method validation → **400**;
  `dateFrom > dateTo` throws `ResponseStatusException(BAD_REQUEST)`. A concurrent manual import gets
  a truthful **409**, not a 500 (`tryStartImportAsync()` claims an `AtomicBoolean` guard before
  handing work to the executor).

The theme: with sealed results, nullable types, and a snapshot model, "the input was messy" and
"the dependency was down" are normal branches — the generic 500 is reserved for genuinely
unexpected bugs.

---

## 8. Spring stays the foundation — Kotlin just makes it terser

Same Spring you know, less ceremony:

- **Constructor injection** as the primary constructor — no `@Autowired`, no field injection,
  everything `val`:
  ```kotlin
  @Service
  class DemonstrationImportService(
      private val pageClient: BerlinPolicePageClient,
      private val classifier: ImpactClassifier,
      @Qualifier("importExecutor") private val importExecutor: TaskExecutor,
      props: AppProperties,
  )
  ```
- **Typed, validated config** instead of scattered `@Value` (`config/AppProperties.kt`):
  ```kotlin
  @Validated @ConfigurationProperties(prefix = "app")
  data class AppProperties(val source: Source, val importJob: ImportJob, /* … */) {
      data class Geocoder(@field:NotBlank val baseUrl: String, @field:Positive val minDelayMs: Long = 1100, /* … */)
  }
  ```
- **Boot 4 `RestClient`** built from an injected `RestClient.Builder`, and unit-tested with
  `MockRestServiceServer` — no live network in tests (`geocoder/NominatimClient.kt` + its test).
- **Scheduling / async / actuator** are the plain Spring annotations (`@Scheduled`, `@Async`,
  `/actuator/health`); the immutable read model is one `AtomicReference` swap.

---

## Suggested demo order (matches the abstract)

1. Show a messy source row → `RawDemonstrationRow` (all nullable) → `NormalizationResult` with a
   rejected row surfaced in `/api/snapshot/status`.
2. Open `ImpactRuleDsl` / `ImpactClassifier` — read a rule aloud, then show `reasons`/`evidence` on
   an event card. "Not AI, and it can explain itself."
3. Kill the network / point Nominatim at nothing → events still load, map degrades to `Unknown`,
   timeline intact, no 500.
4. Trigger two imports at once → one `202`, one `409`; force a mid-import failure → the previous
   snapshot is still served.
