---
id: research-jit-constructs
title: "zavarnik, fifth phase — what C2 does to a Ktor + Exposed request path"
type: research
status: active
date: 2026-09-19
---

# Research: which Kotlin constructs C2 handles badly, and whether this stand can tell

The fifth phase — [brief](source-brief-jit-constructs.md): rank the Kotlin/JVM constructs on the
request path of a Ktor + Exposed service by how well HotSpot C2 optimises them, and price each
ranking as a share of request CPU. The brief gates the detailed work behind a macro check (RQ0)
and stops the study if the share is too small to matter.

This document is written in **English**, unlike the four phases before it — see
[docs/README.md](../README.md) for what that costs and why.

It records **verified facts** (read in artefacts and JDK configuration files, or measured by the
runs named beside them), **decisions**, and **risks**. Anything not verified is called a
hypothesis and says where it gets settled.

The phase does not start from zero, and that is the first thing to say about it. The second phase
([research-optimizer](research-optimizer.md)) profiled this exact stack to answer a different
question, and the fourth ([research-engines](research-engines.md)) rebuilt the stand and found
that the third phase's protocol had never pinned anything. Between them they already measured most
of the denominators this brief's thresholds are divided by. §1.1 collects them, because a study
that re-derives them will spend its budget arriving where the repository already is.

Three of the brief's premises did not survive contact with the JDK it pins: the toggle RQ1 asks
for does not exist on a product VM (§1.2), the instrument RQ7's steady-state gate is defined
against reports nothing on the settings it ships with (§1.3), and the relevance threshold the
whole method rests on is below the resolution of the macro instrument on this host (§1.7). Each is
marked *deviation from the brief* where it appears.

Runs of 2026-09-19 are on the mac (Darwin aarch64, OpenJDK 25.0.2+10-69) and are committed under
`experiments/jfr-compiler-events/` — two repeats, with the raw output of the second instrument
beside the counts, because a count without its derivation cannot be argued with. The figures quoted from
earlier phases were taken on the Linux box (Ubuntu 24.04 in WSL2, Core Ultra 7 255HX, 20 threads,
OpenJDK 25.0.4+7-1-24.04-Ubuntu). Which machine a number came from is stated with the number,
because §1.7 is about the machines.

---

## 1. Verified facts

### 1.1 The denominators this brief divides by are already measured

Nothing in this section is new work. It is here because the brief sets two thresholds — "2 % of
request CPU" for macro relevance and "10 % of p50 latency" for the RQ0 gate — and both are shares
of quantities this repository has already profiled twice.

| Fact | Where verified |
|---|---|
| On the stand's most business-like endpoint, application code owns **2.1 % of CPU and 9.9 % of allocated bytes** by owner; the rest by owner is kotlinx 73.9 %, Ktor 15.5 %, stdlib 8.4 % of CPU | [research-optimizer](research-optimizer.md) §1.4, `bench/profile/results/baseline/business.cpu.collapsed` |
| On a real service — konekt, Ktor CIO with Exposed and Postgres — application code owns **4.0 % of CPU and 3.4 % of bytes** at 50 rps, and 0.9 % / 5.4 % at 200 rps. By owner at 50 rps: kotlinx 21.7 %, Postgres driver 18.2 %, Ktor 13.0 %, stdlib 8.8 %, Exposed 4.7 % of CPU; stdlib 26.6 %, Ktor 24.8 %, kotlinx 13.6 %, Exposed 13.5 % of bytes | [research-optimizer](research-optimizer.md) §1.8, `bench/profile/results/konekt-screens-50/` |
| Swapping the engine moves the application's share more than anything the application does: handlers own **6.1 % of CPU on Netty against 3.1 % on CIO** — the same handlers, on an engine that does less | [research-engines](research-engines.md) §1.4, `bench/profile/results/engine-{cio,netty}/` |
| Netty costs **82 µs of CPU per request** on that endpoint against CIO's 166; on Netty the engine category itself is 43.1 % of CPU and the "hand-off machinery" 1–2.5 %, against CIO's 33–48 % | [research-engines](research-engines.md) §1.3, §1.7 |
| Statically, **10 of 283** application methods exceed `FreqInlineSize`; none exceeds 8000 bytes. Dynamically, of 858 "too big" inlining refusals under load, five name application methods and all five read "hot method too big": `Pricing::quote` 1827 b twice, an `invokeSuspend` 816 b twice, a generated `deserialize` 374 b once | [research-optimizer](research-optimizer.md) §1.4, `bench-results/inlining2/service.log` |
| On a container-limited real service at 50 rps, **61 % of self CPU is the JVM's own threads**, and the frames are C2's (`PhaseChaitin::Split`, `IndexSetIterator`) | [research-optimizer](research-optimizer.md) §1.8 |
| Run-to-run spread on this stand: **±13–15 % on rps**, 2–9 % on µs of CPU per request, for the same variant | [research-engines](research-engines.md) §1.5 |

**Consequence 1 — RQ0 asks about a bucket whose inside is already known, and the gate will pass.**
The brief's RQ0 counts application, Ktor, Exposed, serialisation and the JDBC driver as one
quantity and asks whether it clears 10 % of p50 latency. On konekt that bucket is essentially the
whole process outside the driver's socket wait, so the gate passes and tells nobody anything. The
question with an answer in it is the one the second phase asked instead: *which owner inside the
bucket*. That answer exists, and it says the mass is kotlinx, then the driver, then Ktor — with
application code a rounding error.

**Consequence 2 — the 2 % macro threshold is unreachable for any construct confined to application
code.** All of application code is 0.9–4.0 % of CPU on a real service. A construct that occurs
only there cannot reach 2 % of request CPU unless it is *half of everything the application does*.
RQ5 as written — value classes, `$default` methods, delegated properties, capturing lambdas — is
therefore a green verdict by arithmetic before a single measurement, and a green verdict reached
that way is the brief's own "grey" in disguise. This is a *deviation from the brief*: the
constructs have to be counted wherever they occur, and kotlinx.serialization, Ktor and Exposed are
Kotlin too. See D5.

**Consequence 3 — pinning Netty raises the ceiling, and not for the reason the brief gives.** The
brief chose Netty as "the most common production configuration". Measured, it is also the choice
that doubles the application's share of CPU and removes a third of the profile that belongs to the
dispatcher queue. That is the difference between a study whose subject is 3 % of the profile and
one whose subject is 6 %, and it should be stated as the reason rather than discovered later.

### 1.2 The C2 thresholds, and which of the brief's toggles a product JVM actually has

| Fact | Where verified |
|---|---|
| `FreqInlineSize=325` (a `C2 pd product` flag), `MaxInlineSize=35`, `InlineSmallCode=2500`, `MaxInlineLevel=15`, `C1MaxInlineSize=35`, `C1MaxInlineLevel=9`, `DontCompileHugeMethods=true`, `DoEscapeAnalysis=true`, `EliminateAllocations=true`, `TypeProfileWidth=2`, `PerMethodRecompilationCutoff=400`, `OmitStackTraceInFastThrow=true` | `java -XX:+PrintFlagsFinal -version`, OpenJDK 25.0.2+10-69, macOS/aarch64, 2026-09-19 |
| The same values on Linux/x86-64 and JDK 25.0.4, and `FreqInlineSize` is platform-dependent *by declaration* and equal on both platforms only *in fact* | sborka, `docs/research/research-perf-lint.md` §1.2 |
| **`HugeMethodLimit` is a `develop` flag and does not exist on a product VM**: `-XX:HugeMethodLimit=9000` is refused with "VM option 'HugeMethodLimit' is develop and is available only in debug version of VM", and `-XX:+UnlockDiagnosticVMOptions` does not change that | both commands, mac, 2026-09-19 |

**Consequence — RQ1 has one toggle, not two, and they measure different things** *(deviation from
the brief)*. The brief writes the 8000-byte limit as `DontCompileHugeMethods`, which conflates the
switch with the constant. `-XX:FreqInlineSize=<n>` is a real dial: it can be raised to find where
a refusal stops costing. The 8000 has no dial on a release build — the only available toggle is
`-XX:-DontCompileHugeMethods`, which removes the refusal entirely. That answers "what does not
compiling this method cost", which is a bound, not a knee, and it is worth having for exactly the
methods the second phase found (`Pricing::quote` at 1827 b is nowhere near 8000, so on this stand
the second toggle has no subject at all).

**Consequence — RQ7's exception arm has to name which exception.** `OmitStackTraceInFastThrow` is
on, but it applies to exceptions the JVM throws implicitly, not to ones library code constructs.
For the two the brief names: `kotlinx.coroutines.JobCancellationException` **overrides
`fillInStackTrace`** and is already stackless; `TimeoutCancellationException` **does not**, so
every `withTimeout` expiry walks the stack. "Exceptions used as control flow" is two different
prices, and the brief's toggle — "stackless exceptions" — is already applied upstream to one of
them.

| Fact | Where verified |
|---|---|
| `JobCancellationException` declares `public Throwable fillInStackTrace()`; `TimeoutCancellationException` declares no such override | `javap -p` on `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/{JobCancellationException,TimeoutCancellationException}.class` |

### 1.3 JFR's compiler events report nothing on the settings they ship with

The brief's Tooling table puts JFR down for "compiler behaviour on the live service **without
diagnostic flags**", and its measurement protocol starts only "after JFR shows no C2 compilations
for 60 seconds". Both claims were tested on a program whose compilations can be counted
independently: a hot loop, run under `-XX:+PrintCompilation` and a JFR recording at the same time.

| Fact | Where verified |
|---|---|
| `profile.jfc` ships `jdk.Compilation` with **`threshold` 100 ms** and `jdk.CompilerInlining` with **`enabled` false**; `default.jfc` disables the inlining event too | `$JAVA_HOME/lib/jfr/{profile,default}.jfc`, JDK 25.0.2, quoted in full at the head of each result log |
| **On the settings it ships with, JFR reports no compilation at all.** Two runs, `settings=profile`: 1258 and 1275 compile tasks printed by `-XX:+PrintCompilation`, against **0** `jdk.Compilation`, **0** `jdk.CompilerInlining` and **0** `jdk.CompilationFailure` in the recording of the same run. `jdk.Deoptimization` is the one compiler event that does report: 2 events in each | `experiments/jfr-compiler-events/results/`, arm `shipped` |
| Forced on — `jdk.Compilation#threshold=0ms` plus `+jdk.CompilerInlining#enabled=true` — the events appear but do not cover the run: 57 and 64 events against 1347 and 1339 compile tasks, over event windows of 24 and 21 ms | the same logs, arm `forced` |
| With `settings=none` and every event enabled explicitly, so that no `.jfc` control can override them, the picture does not improve: 28 events against 1028 tasks, and 28 against 1011 | the same logs, arm `bare` |
| The event count is repeatable and the window is not: 57 and 64 events, but compile-id ranges 1264–1386 in one run and 885–1374 in the other, holding 117 and 470 `-XX:+PrintCompilation` tasks respectively | the same logs |
| The content is right when it arrives: `jdk.CompilerInlining` carries `succeeded` and the same refusal vocabulary as `-XX:+PrintInlining` — "callee is too large", "callee uses too much stack", "too big", "no static binding" — alongside the successes | `jfr print --events jdk.CompilerInlining` on a recording from the `forced` arm |

**Consequence — a gate phrased as "no compilations for 60 seconds" is satisfied by an instrument
that reports no compilations at all** *(deviation from the brief)*. On the shipped settings the
answer is zero whether or not the JVM is compiling, which is the failure mode where a check that
cannot find its subject scores as a pass. The steady-state gate has to be read off
`-XX:+PrintCompilation` or `LogCompilation`, and the brief's own Threats section then applies:
those flags change timing, so the gate run and the timing run are different runs. That tension is
real and the brief does not resolve it; D3 does.

**The positive control also sizes the green.** Enabling the events is not enough. Roughly 60 of
1340 arrive, and they arrive inside a window of about twenty milliseconds somewhere near startup;
the count repeats between runs while the window moves, so the two cannot both be describing the
compiler. Whatever the mechanism, JFR's compiler view is not a census, and no share computed from
it is a share of anything. Why the window is tens of milliseconds is open question 1.

**Precedent, one phase earlier.** The second phase's first inlining measurement returned zero
mentions of application code in 4744 inlining decisions, and the zero was the instrument: without
`-XX:+PrintCompilation` the log does not name compilation roots ([research-optimizer](research-optimizer.md)
§1.4). The same error, the same repository, a different instrument. That is what makes it worth a
decision rather than a footnote.

### 1.4 RQ4's subject is not the array

The brief suspects `ResultRow` "as `Array<Any?>`". Read in the artefact, the array is the cheap
half of what stands between a caller and a column value.

| Fact | Where verified |
|---|---|
| `ResultRow` holds `Object[] data`, a `Map<Expression<?>, Integer> fieldIndex`, and a `ResultRow$ResultRowCache lookUpCache`; a read goes `get → getInternal → getRaw → getExpressionIndex → data[i] → rawToColumnValue` | `javap -p` on `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/ResultRow.class` |
| `IColumnType<T>` has exactly **one abstract method**, `valueFromDB(Object)`, and twelve default methods, two of which are on the read path: `readObject(RowApi, int)` and `setParameter(...)` | `javap` on `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/IColumnType.class` |
| The package is `org.jetbrains.exposed.v1.core`; JDBC lives in a separate artifact, and the DAO layer the brief excludes is a third | the same jar; `exposed-jdbc`, `exposed-dao` in the Gradle cache |
| The versions present in this portfolio are **1.4.0** and 1.3.1 | `~/.gradle/caches/modules-2/files-2.1/org.jetbrains.exposed/` |

**Consequence — the brief's RQ4 toggle does not isolate what the brief says it isolates.**
Replacing `Array<Any?>` with a typed row holder removes the array *and* a hash lookup keyed by an
`Expression<?>` per column per row *and* the cache object. A two-arm comparison against
hand-written JDBC therefore prices all three at once and attributes the total to the array. Three
arms are needed: Exposed as shipped, an array without the map, and the typed holder. The
megamorphism the brief suspects is real in shape — after erasure the site calls
`Object valueFromDB(Object)` on an interface with one abstract method and many implementations —
but how many receivers a request actually sees is a runtime question, not a static one.

**Consequence — the setup table pins no version for the data layer**, while the brief's own rule
is that every version is pinned before the first measurement. "Ktor 3.x" is not a pin either. D7.

### 1.5 "JSON only" does not mean one encoder

| Fact | Where verified |
|---|---|
| `kotlinx-serialization-json-jvm` 1.11.0 ships six encoder implementations — `StreamingJsonEncoder`, `AbstractJsonTreeEncoder`, `JsonTreeEncoder`, `JsonTreeListEncoder`, `JsonTreeMapEncoder`, `JsonPrimitiveEncoder` — and the mirror set of decoders | `unzip -l` on `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/` |
| `kotlinx-serialization-core-jvm` 1.11.0 adds `AbstractEncoder`, `TaggedEncoder`, `NamedValueEncoder`, `NoOpEncoder` | the corresponding core jar |

**Consequence.** RQ6's green condition — "the encoder methods inline into generated serialisers" —
depends on how many of these classes are *loaded*, which depends on whether anything in the
service touches `JsonElement` at all. A service that only calls `encodeToString` may well be
monomorphic; one that uses `JsonTransformingSerializer`, polymorphic serialisation or
`encodeToJsonElement` anywhere on any path pollutes the profile for every path. That is a
hypothesis with an address: open question 2.

### 1.6 The continuation machinery, as compiled

| Fact | Where verified |
|---|---|
| `BaseContinuationImpl.resumeWith` is `public final` and calls `protected abstract invokeSuspend(Object)` — one call site whose receivers are every `invokeSuspend` in the process | `javap -p` on `org.jetbrains.kotlin:kotlin-stdlib:2.4.20!/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class` |
| `startCoroutineUninterceptedOrReturn` appears in the Kotlin metadata of `IntrinsicsKt__IntrinsicsJvmKt` but has **no JVM member** there: it is inline-only and compiled into its caller. `createCoroutineUnintercepted` and `intercepted` are ordinary static methods | `javap` and a binary search of `org.jetbrains.kotlin:kotlin-stdlib:2.4.20!/kotlin/coroutines/intrinsics/IntrinsicsKt__IntrinsicsJvmKt.class` |
| `CoroutineSingletons` is an enum with `COROUTINE_SUSPENDED`, `UNDECIDED`, `RESUMED` | the same jar |

**Consequence.** RQ2's premise holds by construction: the `resumeWith → invokeSuspend` edge is a
single site with as many receivers as the application has suspend bodies. And the brief's
benchmark method works — the intrinsic is callable from Kotlin — with the caveat that, being
inline-only, its machinery lands inside the benchmark method's own bytecode and counts against
that method's size.

### 1.7 What this host cannot deliver, measured

The brief's Fixed setup names "one Linux x86-64 machine, fixed CPU governor, load generator on
separate pinned cores". Three of those four were tried here before.

| Fact | Where verified |
|---|---|
| The Linux box is WSL2 and has neither `intel_pstate` nor `cpufreq` under `/sys`: frequency and turbo belong to the Windows host and cannot be fixed from the guest | [research-optimizer](research-optimizer.md) §1.4 |
| `taskset -pc <pid>` moves one thread's affinity, not the process's: 49 of 50 JVM threads stayed on all 20 cores, and every number of the second phase was taken on an unpinned JVM despite a protocol that said otherwise. Pinning on `exec` works | [research-engines](research-engines.md) §1.1, `bench/profile/run.sh` |
| A load generator on the subject's own machine does not merely add noise — it under-feeds the subject. Same binaries, same limits: on a shared 20-core box with the generator on it, a service looked as though it fitted a 256 MiB limit; on a dedicated pair with the generator off-host it survives **1 run in 6** | sborka, §8 of research-memory-limit — see the note below |
| Run-to-run spread for one variant on this stand: ±13 % on rps in the engine A/B, ±15 % in the second phase's, against 2–9 % on µs of CPU per request | [research-engines](research-engines.md) §1.5, [research-optimizer](research-optimizer.md) §1.6 |

The sborka row has no clickable address, and the reason is worth a sentence rather than a
permanently red anchor: docs/research/research-memory-limit.md exists only on the branch
`worktree-brief-c-memory-limit`, which is neither merged into that repository's `main` nor
pushed. Checked on 2026-09-19 — `main` holds only `source-brief-memory-limit.md`. The numbers
above are read out of the branch copy, and §8 there marks itself superseded by §2.5 of the
same document, which is where the six repetitions live.

**Consequence — the macro relevance threshold is below the macro instrument's resolution**
*(deviation from the brief)*. The brief allows a construct's share to be established either by
async-profiler samples or "by the toggle's effect on CPU per request". The second route asks for a
2 % effect from an instrument that moves 13–15 % between identical runs, and the brief's own kill
criterion 3 would end the study over a 5 % spread. The first route survives, because shares by
owner are proportions inside one process and were stable across dirty runs where absolute CPU
differed by 13–17 %. D2.

---

## 2. Decisions

### D1. The gate is not RQ0 as written *(deviation from the brief)*

Brief: run RQ0 first, stop the study if JVM CPU is under 10 % of p50 latency on all three database
endpoints.

Decision: keep the measurement, drop its use as a gate, and gate on §1.1's numbers instead — the
share of the bucket that belongs to each owner. Why: RQ0's bucket is nearly the whole process, so
it passes by construction and its passing carries no information; the split inside it is already
measured on this stack twice, and it is the split that decides which RQ can ever be red. The
price: the study loses the cheap early stop the brief wanted. It is replaced by a cheaper one —
§1.1 costs nothing to read.

### D2. The macro unit is the share by owner and µs of CPU per request, never rps *(deviation)*

Brief: macro relevance is 2 % of request CPU, measured by samples or by the toggle's effect on CPU
per request.

Decision: shares by owner from async-profiler, plus `utime+stime` over the clean window divided by
responses, as the fourth phase established. rps is printed and never used for a verdict. Why:
§1.7 — rps moves ±13–15 % between identical runs on this host and the frequency cannot be fixed;
the ratio-shaped quantities survived that. The price: a share says where the CPU went, not what
removing it would save, so every red verdict still needs its toggle.

### D3. The steady-state gate reads `-XX:+PrintCompilation`, and the timing runs do not *(deviation)*

Brief: start measuring after JFR shows no C2 compilations for 60 seconds; JFR gives compiler
behaviour without diagnostic flags.

Decision: the warm-up gate is a separate run with `-XX:+PrintCompilation -XX:+PrintInlining`,
which establishes *how long* warm-up takes on this stand; the timing runs then use that duration
as a fixed warm-up and carry no compiler flags. Why: §1.3 — JFR reports zero on shipped settings
and a few tens of milliseconds when forced, so a gate built on it cannot fail; and the brief is
right that the flags change timing, so they cannot be in the run being timed. The price: warm-up
duration is assumed constant across variants, which is an assumption and gets re-checked whenever
the variant changes the code being compiled.

### D4. RQ1 keeps one dial and one bound

`-XX:FreqInlineSize` is the dial. `-XX:-DontCompileHugeMethods` is a bound and has no subject on
this stand, because the largest application method measured is 1827 bytes (§1.1). Splitting a
suspend function by hand stays, and is the only arm that tests the brief's actual suspicion — that
`transaction {}` and friends inflate `invokeSuspend` past the threshold.

### D5. Constructs are counted wherever they occur *(deviation from the brief)*

Brief: RQ5 lists Kotlin codegen patterns and prices each against 2 % of request CPU.

Decision: each pattern is counted across the whole process — kotlinx.serialization's generated
serialisers, Ktor's pipeline, Exposed's DSL and the application alike — and the report says which
owner each occurrence belongs to. Why: §1.1, consequence 2 — application code is 0.9–4.0 % of CPU,
so a pattern confined to it cannot clear 2 % and every RQ5 row would come out green by arithmetic.
The price: a red verdict then names a construct in somebody else's library, where the action is an
upstream ticket rather than a rewrite — which is what the brief's Non-goals already say happens to
findings.

### D6. The stand is `bench/` on Netty; no new repository

Follows the second phase's D3 and its reasoning. The four endpoints of this brief are added to the
existing service rather than replacing `/echo`, `/items`, `/business`, so that the numbers of
§1.1 stay comparable in the same tree. The data layer is added behind the brief's two modes, real
and stub. Netty is the default for this phase's runs and CIO stays available on `-Dbench.engine`,
because the engine turned out to move the application's share by a factor of two (§1.1).

### D7. Every version in the setup table is a version, not a range

Exposed is pinned at 1.4.0, Ktor at the version the stand already carries, and the JMH, JDBC
driver and HikariCP versions are written into the catalog before the first measurement. Why: the
brief's own rule, which its table does not keep — "Ktor 3.x" and an unpinned Exposed are not pins,
and §1.4's facts are true of 1.4.0 and of nothing else.

### D8. A pair with a known order runs beside the calibration controls

The brief's five controls are expected to be green and catch a harness that sees effects that are
not there. They do not catch a harness that has stopped measuring the subject. One extra pair is
added whose order follows from the code — a variant that does strictly everything another does and
one step more. If it comes out cheaper, the run is discarded and the stand is fixed, not the
number. Why: an impossible ordering is the fastest detector of a stand measuring itself, and it
fires before the spread does.

---

## 3. Risks and open questions

**Risk 1. The host cannot fix its own clock (§1.7).** Mitigation: µs of CPU per request as the
reported unit, variants interleaved, three repeats, and the within-variant spread printed beside
every between-variant difference. A difference smaller than the spread is not a result, and rows
whose spread exceeds ~1.3× are marked unusable in the output itself rather than in a footnote.

**Risk 2. The generator shares the subject's machine (§1.7).** It errs safely — the subject looks
healthier than it is — which is the dangerous direction for a study whose findings are all
"something costs less than you think". Mitigation: every row carries `gen=local`, and such rows are
not quoted as measurements of a limit; the dedicated pair used for sborka's brief C is the escape
when a number has to be defended.

**Risk 3. A green or grey verdict is the comfortable one and costs nothing to publish.** The brief
already has calibration controls for this. Mitigation on top: every green and grey verdict states
the control number that sizes it — what the smallest effect this chain demonstrably detects was, in
the same units — so that "no effect" and "not resolvable here" stay distinguishable in the write-up.

**Risk 4. The study re-derives §1.1 and arrives where the repository already stands.** Mitigation:
a candidate already priced by phase two starts from that number and the work is the toggle, not the
profile. The per-candidate budget of one working day is spent on causation.

**Open question 1. Why JFR's compiler events cover only tens of milliseconds (§1.3).** Hypothesis:
the events sit in per-thread buffers that the compiler threads stop filling once compilation tails
off, so nothing flushes them. Address: the stand under load, where compilation continues for
minutes, with `jcmd JFR.dump` taken mid-run instead of at exit. If the hypothesis holds, JFR is
usable on a service and unusable on a short program, which is the opposite of what §1.3 suggests
in isolation.

**Open question 2. How many `Encoder` receivers the list endpoint actually sees (§1.5).** Address:
`-XX:+PrintInlining` on the stand, counting loaded implementations, before RQ6 is scheduled.

**Open question 3. What C2 itself costs on a container-limited service.** At 50 rps under a
one-core limit, 61 % of self CPU was the JVM's own threads and the frames were C2's (§1.1). The
brief does not ask this, and on the class of service it is about — small containers, modest rates —
it may be the largest JIT-related number in the study. It is not a construct verdict, so it does
not fit the brief's output shape; it fits an article. Address: a dedicated run on konekt's own
stand, deciding nothing about the construct list.

---

## 4. Code anchors

| Kind | Code |
|---|---|
| stand | `bench/src/main/kotlin/bench/Main.kt` — the service and the engine switch |
| stand | `bench/profile/run.sh` — pinning, warm-up, clean window, CPU and alloc profiles |
| stand | `bench/profile/ab.sh`, `bench/profile/engines-ab.sh` — interleaved variants, medians |
| stand | `bench/profile/attribute.py` — self/owner attribution of collapsed stacks |
| experiment | `experiments/jfr-compiler-events/run.sh` — §1.3, three arms and two repeats |
| JDK configuration | `lib/jfr/profile.jfc`, `lib/jfr/default.jfc` in the pinned JDK — §1.3 |
| artefact | `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/ResultRow.class` |
| artefact | `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/IColumnType.class` |
| artefact | `org.jetbrains.kotlin:kotlin-stdlib:2.4.20!/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class` |
| artefact | `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/JobCancellationException.class` |
| artefact | `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/StreamingJsonEncoder.class` |

---

## 5. What happens next

The order of work and its acceptance criteria are in the backlog, stage `stage-8-jit-constructs`.
The first items are the ones everything else depends on: the stand has to grow a database and the
brief's four endpoints ([B-41](../backlog/B-41-jit-stand-data-layer-and-endpoints.md)), the
warm-up gate has to be rebuilt on an instrument that reports ([B-42](../backlog/B-42-warmup-gate-on-printcompilation.md)),
and the calibration controls have to run through the whole chain before any candidate does
([B-44](../backlog/B-44-calibration-controls-and-the-known-order-pair.md)). The RQs are scheduled
after that, in the order §1.1 gives rather than the order the brief numbers them.
