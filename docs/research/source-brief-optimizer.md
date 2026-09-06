---
id: source-brief-optimizer
title: Исходный бриф второй фазы — плагин оптимизации байткода Kotlin/JVM для сервисов на Ktor CIO
type: research
status: active
date: 2026-09-06
---

> Бриф второй фазы, как он был написан 06.09.2026, до ресёрча, **без правок**. Бриф просил отдельный
> репозиторий; владелец решил вести фазу здесь же — стенд вырастает из `samples/ktor`, а ресёрч
> живёт рядом с ресёрчем AOT-кэша: [research-optimizer](research-optimizer.md) отмечает, какие
> посылки подтвердились, какие перевернулись и какие остались гипотезами.

# Research Brief: Kotlin/JVM bytecode optimization plugin for Ktor CIO services

**Status:** draft, pre-research  
**Date:** 2026-09-06  
**Owner:** Pavel  
**Repository:** new, separate from zavarnik  
**Time-box:** 1 week RQ0 gate → decision; 3 weeks build if green

---

## 1. Problem statement

Kotlin emits JVM bytecode with overheads the JIT does not reliably remove: boxing at `suspend` boundaries and in generics, intermediate collections in stdlib chains, eager string building for disabled log levels, per-call construction of expensive constants, and inline-induced bytecode bloat that pushes methods past C2's inlining and compilation thresholds. Nothing in the Kotlin toolchain fixes these. R8/ProGuard could fix some of them but is almost never applied to server jars, and it is blind to Kotlin semantics.

**Hypothesis:** a Gradle plugin combining a K2 IR compiler plugin (semantic rewrites on user code) and an ASM bytecode transform (peephole rewrites on the whole runtime classpath) yields a measurable throughput and/or allocation improvement on a realistic Ktor CIO service, beyond what R8 already provides.

## 2. Goals / non-goals

**Goals**
- Quantify where the hot path of a Ktor CIO service actually is — user code vs. `ktor-*`/`kotlinx-*` — because that bounds the value of an IR-only plugin.
- Establish an R8 baseline so the plugin is judged on its delta over existing tooling.
- Implement each candidate optimization behind its own flag, measure it in isolation, keep only those that clear a pre-declared threshold.

**Non-goals**
- Anything C2 already does reliably: devirtualization, inlining, escape analysis, loop optimizations.
- Rewriting `kotlinx.coroutines` internals (dispatch, `Job`, `CancellableContinuationImpl`). Out of reach for a plugin over user code; diagnostics only.
- Public ABI changes. Signature-altering rewrites are restricted to `private`/`internal` declarations.
- Startup/warmup via AOT caching — that is zavarnik's job. This plugin is measured on steady-state throughput and alloc/req first; warmup deltas are reported separately (see §5).

## 3. Architecture decision (made by RQ0)

| | K2 IR compiler plugin | ASM transform (Gradle `TransformAction` on `runtimeClasspath`) |
|---|---|---|
| Scope | user modules only | entire classpath incl. Ktor, kotlinx |
| Available info | full Kotlin types (`Int?`, `suspend`, inline context) | JVM bytecode + `@kotlin.Metadata` |
| Suited for | semantic rewrites: fusion, specialization, lazy logging | call stripping, constant hoisting, peephole |
| Ceiling on a CIO service | ≈ user code's share of the profile | whole process |

Likely outcome: both layers in one plugin. But the IR layer alone cannot move end-to-end numbers if user code is a small share of the profile, so RQ0 decides which layer is built first — or whether either is worth building.

## 4. Research questions

Pre-declared red/green outcomes. Results and retractions go into `docs/research/`, one file per RQ, each claim with a path to the log that supports it (same convention as zavarnik).

### RQ0 — Profile split and R8 baseline (gate)
*What share of CPU and allocations on the benchmark service is attributable to user-compiled code? What does R8 with a sensible server config already deliver?*
- **Method:** async-profiler (`cpu`, `alloc`) on the benchmark service (§5), samples attributed by jar of origin. Then the same benchmark on the jar processed by R8 (`-assumenosideeffects` on `Intrinsics`, inlining, enum unboxing, no obfuscation, no shrinking of reflection-reachable classes).
- **Green:** user code ≥ 25% of allocations, *or* R8 delta < 3%.
- **Red:** user code < 10% *and* R8 delta ≥ 8% → an IR plugin has no room; either build only the ASM layer or stop.

### RQ1 — Intrinsics removal (user code + dependencies)
- **Method:** `-Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions` for user modules; ASM transform stripping `Intrinsics.check*` from dependencies.
- **Expected:** 1–3%, mostly in the C1/interpreter window. Report bytecode-size reduction and JIT inlining changes (JITWatch) next to throughput.
- **Red:** < 1% on every metric → keep as documented compiler flags, drop the ASM part.

### RQ2 — Boxing elimination
Kotlin-specific boxing sites the JIT does not remove:
- `suspend fun f(): Int` — the return value is always boxed (`Object invokeSuspend`, `Boxing.boxInt`); same for `Deferred<Int>.await()` and `Flow<Int>` emissions.
- `value class` in generic, nullable, or interface positions.
- `Map<String, Int>`, `List<Long>` on private fields (candidate for primitive-specialized collections).
- **Method:** count `Integer.valueOf` / `Boxing.box*` allocation samples before/after; IR rewrite specializes `private`/`internal` suspend functions only.
- **Red:** boxing < 3% of allocations on the benchmark → demote to diagnostic.

### RQ3 — Collection-chain fusion
*`filter{}.map{}.first{}` inlines into one method, but intermediate `ArrayList`s survive; C2 does not scalar-replace them (Graal sometimes does).*
- **Method:** IR pass fusing adjacent stdlib chain calls on `Iterable`/`List` into one loop with one output list. Measure alloc/req and throughput on the "business logic" endpoint.
- **Red:** < 2% alloc/req → drop; recommend `Sequence` in docs instead.

### RQ4 — Lazy logging rewrite
*`logger.debug("id=$id body=$body")` builds the string even when debug is disabled.*
- **Method:** IR pass rewriting SLF4J / kotlin-logging calls with non-constant string arguments into level-guarded blocks (equivalent of `debug { }`). Measure alloc/req with debug disabled.
- **Expected:** frequently the single largest garbage source in user code of production services.
- **Red:** < 2% alloc/req at realistic log density → lint only.

### RQ5 — Constant hoisting
*`Regex("...")`, `DateTimeFormatter.ofPattern(...)`, `Json { }` constructed inside request handlers.*
- **Method:** hoist literal-argument constructions to static fields; implementable at both IR and bytecode level. Measure CPU/req on the endpoint with regex validation.
- **Red:** < 2% → lint only.

### RQ6 — Inline-bloat diagnostics
*Methods > 325 bytecode instructions are not inlined by C2 (`FreqInlineSize`); methods > 8000 are not compiled at all (`DontCompileHugeMethods`). Nested `inline` wrappers in Ktor handlers reach these easily.*
- **Method:** report per-method bytecode size after compilation, flag both thresholds, cross-check with `-XX:+PrintCompilation`.
- **Outcome:** always ships as a diagnostic. RQ only verifies whether any benchmark handler actually crosses a threshold.

### RQ7 — Coroutine diagnostics (no rewrites)
- `suspend` functions that contain suspend calls but never actually suspend at runtime (a state machine is generated regardless).
- `withContext` with the same dispatcher as the caller.
- **Method:** IR static analysis plus an optional runtime probe. Ships as lint.

### RQ8 — Low-cost extras (measure once, keep if non-zero)
- Capturing non-inline lambdas allocated per request.
- `enum.values()` → `entries`, `String.format` → template, `lateinit` access checks.
- Exceptions used for control flow (`runCatching` on hot paths) — diagnostic only.

## 5. Measurement protocol

- **Service:** Ktor CIO with three endpoints — `echo`, JSON CRUD over an in-memory store (kotlinx.serialization), "business logic" (collection chains, regex validation, debug logging disabled). Start from `zavarnik/samples/ktor` and extend; keep the benchmark in its own repo so both plugins can use it.
- **Load:** `oha` or `wrk`, 60 s warmup, 120 s measurement, fixed connection count, 3 repetitions, results committed under `experiments/`.
- **Metrics:** rps, p99 latency, **alloc/req** (JFR `ObjectAllocationSample`), CPU/req. Warmup (time-to-stable-rps) recorded but reported in a separate column.
- **Isolation:** every optimization is a plugin flag. One-at-a-time deltas, then all-on.
- **JIT attribution:** a delta present during warmup but absent in steady state is classified as *warmup*, not *throughput*. Warmup deltas are real but must be measured *with* the zavarnik AOT cache enabled, otherwise they overstate what a user would see.
- **Per-optimization kill criterion:** < 2% on every steady-state metric → removed or demoted to lint.

## 6. Order of work

0. RQ0 gate: profile split + R8 baseline. One week. Decides everything below.
1. RQ6 diagnostics — cheapest, always useful.
2. RQ4 lazy logging + RQ5 constant hoisting — cheapest rewrites, most likely non-zero.
3. RQ2 boxing at `suspend` boundaries.
4. RQ3 collection fusion.
5. RQ1 intrinsics via ASM on dependencies — only if RQ0 shows there is something to strip there.

## 7. Kill criteria (project level)

1. RQ0 red.
2. All-on delta on the benchmark < 5% throughput *and* < 10% alloc/req in steady state.
3. Four weeks after v0.1 release: zero external reactions.

## 8. Open questions

- One Gradle plugin with two engines vs. two plugins (IR, ASM) sharing a report format.
- Whether the ASM layer should depend on `kotlin-metadata-jvm` to recover Kotlin types from dependency bytecode (enables boxing analysis on Ktor jars).
- IR pass ordering relative to Compose compiler, kotlinx.serialization and atomicfu plugins.
- Name (`zavarnik` is taken by the AOT-cache plugin; this one needs its own).

## 9. References

- Kotlin compiler plugin API (K2, IR): https://kotlinlang.org/docs/compiler-plugins.html
- `kotlinx-atomicfu` as precedent for an IR optimization plugin: https://github.com/Kotlin/kotlinx-atomicfu
- HotSpot inlining thresholds (`FreqInlineSize`, `MaxInlineSize`, `DontCompileHugeMethods`): https://chriswhocodes.com/hotspot_options_openjdk21.html
- JITWatch: https://github.com/AdoptOpenJDK/jitwatch
- async-profiler: https://github.com/async-profiler/async-profiler
- R8: https://r8.googlesource.com/r8
- Ktor CIO engine: https://ktor.io/docs/server-engines.html
- zavarnik (AOT cache, prior art for the research/experiment conventions and the Ktor sample): github.com/youndie/zavarnik
