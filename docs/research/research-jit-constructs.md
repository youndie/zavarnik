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

Three of the brief's premises did not survive contact with the JDK it pins. The toggle RQ1 asks for
does not exist on a product VM (§1.2). The instrument its steady-state gate is defined against
reports no compilation at all on the settings it ships with, so that gate cannot fail (§1.3). And
the relevance threshold the whole method rests on — 2 % of request CPU — is below the resolution of
the macro instrument on this host (§1.7). Each is marked *deviation from the brief* where it
appears.

Four of its questions also changed shape before any stand ran, which is the more useful result.
RQ1's suspicion is right and aimed at the wrong code: the oversized suspend bodies on every request
belong to Ktor and the Netty engine, not to the application, and nothing on the path is within a
factor of four of the huge-method limit (§1.8). RQ2 does not need to ask whether a megamorphic site
exists — one does by construction, 580 receivers on one call site with a type profile two wide
(§1.6). RQ5, scoped as the brief scopes it, is green by arithmetic before it is measured, because
application code is 0.9–4.0 % of a real service's CPU (§1.1). And RQ6 has two answers separated by
a single line of application code (§1.5). None of this required a measurement the brief planned;
all of it changes what the measurements should be.

Runs of 2026-09-19 are on the mac (Darwin aarch64, OpenJDK 25.0.2+10-69) and are committed under
`experiments/jfr-compiler-events/` — `run.sh` on a short workload, `long-run.sh` on one that keeps
the compiler busy, with the raw output of the second instrument beside every count, because a count
without its derivation cannot be argued with. The second script exists because the first one's
answer was wrong, which §1.3 records rather than quietly replaces. The figures quoted from
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
| `JobCancellationException` declares `public Throwable fillInStackTrace()` | `javap -p` on `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/JobCancellationException.class` |
| `TimeoutCancellationException` declares no such override — its only additions are `coroutine` and `createCopy` | `javap -p` on `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/TimeoutCancellationException.class` |

### 1.3 JFR reports no compilation on the settings it ships with, and a census on any other

The brief's Tooling table puts JFR down for "compiler behaviour on the live service **without
diagnostic flags**", and its measurement protocol starts only "after JFR shows no C2 compilations
for 60 seconds". Both are claims about an instrument, so they were tested against a second one: the
same runs also print every compilation under `-XX:+PrintCompilation`, and that count is what JFR is
held against. Two workloads — a single hot loop, which stops compiling almost immediately, and 3000
methods made hot one after another, which keeps the compiler busy for the whole run.

| Fact | Where verified |
|---|---|
| `profile.jfc` ships `jdk.Compilation` with **`threshold` 100 ms** and `jdk.CompilerInlining` with **`enabled` false**; `default.jfc` disables the inlining event too | `openjdk-25.0.2!/lib/jfr/profile.jfc` and `openjdk-25.0.2!/lib/jfr/default.jfc`, quoted at the head of each result log |
| **On the settings it ships with, JFR reports no compilation at all.** `settings=profile`, short workload: 1258 and 1275 compile tasks by `-XX:+PrintCompilation` against **0** `jdk.Compilation`. Long workload, two sweeps: **7268 and 7300** tasks over about 4.5 seconds, still **0** — and 0 `jdk.CompilerInlining`, 0 `jdk.CompilationFailure` | `experiments/jfr-compiler-events/results/`, the `shipped` arm of both scripts |
| `jdk.Deoptimization` is the one compiler event that does report on stock settings: 1–5 events per run, in every arm | the same logs |
| **Once `jdk.Compilation` is enabled explicitly, it is a census.** Long workload, `settings=none` with the event on and its threshold at 0: **6022 events against 6052 compile tasks** in the same id range in one sweep and **6015 against 6024** in the other — 99.5 % and 99.9 % | `results/*.long.log`, arm `bare` |
| What it misses is startup, not compilation: **886 and 933 tasks compiled before the recording was live**, all of them below the first id JFR reports | the same logs |
| **`jdk.CompilerInlining` is the event that truncates.** Eight recordings: it covers the **first 8 to 96 compile ids** of the recording and then stops, while `jdk.Compilation` in the very same recording runs on past 7000. Its event *count* is not the quantity to quote — 19, 33, 42, 80, 82, 87, 90, 342 — because one compilation makes many inlining decisions; the id span is what repeats | `results/*.long.log`, the `bare` arm plus three repeats, in each of two sweeps |
| The content is right where it arrives: `jdk.CompilerInlining` carries `succeeded` and the same refusal vocabulary as `-XX:+PrintInlining` — "callee is too large", "callee uses too much stack", "too big", "no static binding" — alongside the successes | `jfr print --events jdk.CompilerInlining` |
| **Watching costs something, and JFR costs more than the flag the brief calls diagnostic.** Medians of three interleaved repeats, two sweeps: no instrument 4.18 and 4.23 s; `jdk.Compilation` via JFR 4.40 and 4.47 s (**+5.3 %**, **+5.7 %**); `-XX:+PrintCompilation` 4.29 and 4.31 s (**+2.6 %**, **+1.9 %**). Within-variant spread is at most 1.4 %, so the ordering is not noise | `results/*.long.log`, section `cost` |
| **Between sweeps the absolute seconds moved and the ordering did not.** An earlier sweep of the same script on this machine ran the workload in about 6.7 s rather than 4.2. Only the ratios are compared across sweeps, and this row exists so that a reader adding a later number to this table knows that | the two committed `*.long.log` files |

**Correction found while controlling this section.** It first read that JFR's compiler view "is not
a census", on the strength of the short workload: some 60 events of 1340 compile tasks, inside a
window of about twenty milliseconds. That was the subject, not the instrument. A one-method loop
finishes compiling while the JVM is still starting, so nearly everything it compiles happens before
the recording's settings take effect, and what was left looked like truncation. The long workload
removes that explanation and the number goes to 99.5 %. The lesson is the one the phase is built
on: a negative result about an instrument needs a positive control, and a control that is too small
to contain the effect controls nothing.

**Consequence — the gate can be built, and the brief's reason for it is still wrong.** "No
compilations for 60 seconds" is a usable warm-up criterion on `jdk.Compilation`, provided the event
is switched on and its threshold lowered. On the settings the brief assumes, it is satisfied while
the JVM compiles at full tilt — the failure mode where a check that cannot find its subject scores
as a pass. And "without diagnostic flags" does not survive either: the event needs an explicit
setting on the command line exactly as `-XX:+PrintCompilation` does, and costs three times more on
a compile-heavy workload. D3.

**Consequence — inlining evidence cannot come from JFR, and the positive control sizes each event
separately.** `jdk.Compilation` switched on is a census and can carry a gate. `jdk.CompilerInlining`
switched on covers the first few dozen compile ids of the recording and nothing after, in eight
recordings out of eight. The two sit in the same recording and only one of them can be believed, so
every inlining refusal this study quotes has to come from `-XX:+PrintInlining` or `LogCompilation` —
the invasive instrument the brief's own Threats section says changes timing. The inlining runs and
the timing runs are therefore different runs, which is a cost of the method rather than an oversight
in it.

**Precedent, one phase earlier.** The second phase's first inlining measurement returned zero
mentions of application code in 4744 inlining decisions, and the zero was the instrument: without
`-XX:+PrintCompilation` the log does not name compilation roots ([research-optimizer](research-optimizer.md)
§1.4). The same error, the same repository, a different instrument — and this section made a
version of it again before the control caught it.

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

### 1.5 A JSON-only service loads one encoder, and one call anywhere loads three

RQ6's green condition is that the `Encoder` calls in a generated serialiser inline. The jar ships
six encoder implementations, so whether the call sites are monomorphic is a question about which of
them the process *loads* — a runtime fact, not a count of classes in an artifact. Measured with a
program shaped like the brief's list endpoint, compiled by the Kotlin version the stand pins and run
under `-verbose:class`.

| Fact | Where verified |
|---|---|
| `kotlinx-serialization-json-jvm` 1.11.0 ships six encoder implementations — `StreamingJsonEncoder`, `AbstractJsonTreeEncoder`, `JsonTreeEncoder`, `JsonTreeListEncoder`, `JsonTreeMapEncoder`, `JsonPrimitiveEncoder` — and the core artifact adds `AbstractEncoder`, `TaggedEncoder`, `NamedValueEncoder`, `NoOpEncoder` | `unzip -l` on `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/` |
| **Encoding and decoding through strings loads exactly one concrete encoder and one concrete decoder** — `StreamingJsonEncoder` and `StreamingJsonDecoder`. The other thirteen classes named Encoder or Decoder that the JVM loads are the interfaces and abstract supertypes | `experiments/json-encoder-census/results/`, arm `string` |
| **One `encodeToJsonElement`, anywhere in the process, adds three more concrete encoders** — `JsonTreeEncoder`, `JsonTreeListEncoder` and their `AbstractJsonTreeEncoder` base — plus `TaggedEncoder` and `NamedValueEncoder` in core: 15 loaded classes become 23 | the same log, arm `element`, and the diff it prints |
| The streaming and the sink paths share the one encoder class: `JsonStreamsKt`, the sink entry point, constructs `StreamingJsonEncoder` like `encodeToString` does | `javap` over `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/JsonStreamsKt.class` |
| **Ktor does not cross that line itself.** Across `ktor-serialization-kotlinx-jvm` and `ktor-serialization-kotlinx-json-jvm` 3.5.2, **zero** classes reference `JsonElement` or `encodeToJsonElement`; the converter calls `encodeToString`, `encodeToSink`, `decodeFromString` and `decodeFromSource` | constant-pool search and `javap` over both jars |

**Consequence — RQ6 has two answers and the threshold between them is two.** `TypeProfileWidth` is
**2** (§1.2): C2 records at most two receiver types per call site and treats a third as megamorphic.
A service that only encodes and decodes JSON bodies sees **one** receiver at the shared `Encoder`
sites, which is the best case there is. A service that calls `encodeToJsonElement` once — in a
health endpoint, in a log line, in a test fixture that runs in the same process — puts **three**
concrete encoders on those sites and pushes them over the line in one step. The interesting number
is not the verdict, it is the gap between the two arms, and the study should measure both.

**Consequence — this is the profile pollution RQ7 names, with a name.** The brief lists "profile
pollution in shared generic code" as an RQ7 suspect and gives no example. Here is one that costs a
single line of application code and is invisible in review, because the polluting call need not be
anywhere near the endpoint it slows down.

### 1.6 The continuation machinery, as compiled

| Fact | Where verified |
|---|---|
| `BaseContinuationImpl.resumeWith` is `public final` and calls `protected abstract invokeSuspend(Object)` — one call site whose receivers are every `invokeSuspend` in the process | `javap -p` on `org.jetbrains.kotlin:kotlin-stdlib:2.4.20!/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class` |
| `startCoroutineUninterceptedOrReturn` appears in the Kotlin metadata of `IntrinsicsKt__IntrinsicsJvmKt` but has **no JVM member** there: it is inline-only and compiled into its caller. `createCoroutineUnintercepted` and `intercepted` are ordinary static methods | `javap` and a binary search of `org.jetbrains.kotlin:kotlin-stdlib:2.4.20!/kotlin/coroutines/intrinsics/IntrinsicsKt__IntrinsicsJvmKt.class` |
| `CoroutineSingletons` is an enum with `COROUTINE_SUSPENDED`, `UNDECIDED`, `RESUMED` | the same jar |
| **The pinned classpath carries 580 `invokeSuspend` implementations** before a line of application code — coroutines 263, `ktor-server-core` 134, `ktor-io` 68, `ktor-utils` 27, stdlib 21, the Netty engine 18, `ktor-http` 15, `exposed-jdbc` 12 | `experiments/method-sizes/results/`, the scan of §1.8 |

**Consequence — RQ2's site is megamorphic by construction, and nothing the application does can
change that.** `resumeWith` is one call site; its receivers are those 580 plus every suspend body
the service adds; `TypeProfileWidth` is 2 (§1.2). There is no configuration, no rewrite and no
volume of application code under which C2 sees a bimorphic profile there. That makes RQ2 a question
about what a *known* megamorphic site costs rather than about whether one exists — and it means the
brief's 1, 2 and 8 receiver arms are a calibration of the microbenchmark, not the measurement. The
service sits at the far end of that gradient and never moves along it.

The brief's benchmark method works — the intrinsic is callable from Kotlin — with the caveat that,
being inline-only, its machinery lands inside the benchmark method's own bytecode and counts against
that method's size.

### 1.7 What this host cannot deliver, measured

The brief's Fixed setup names "one Linux x86-64 machine, fixed CPU governor, load generator on
separate pinned cores". Three of those four were tried here before.

| Fact | Where verified |
|---|---|
| The Linux box is WSL2 and has neither `intel_pstate` nor `cpufreq` under `/sys`: frequency and turbo belong to the Windows host and cannot be fixed from the guest | [research-optimizer](research-optimizer.md) §1.4 |
| **Neither does the dedicated pair.** `bench-a`/`bench-b` are KVM guests with no `cpufreq` and no `intel_pstate` either, so the brief's "fixed CPU governor" is unavailable on every host this study can reach. What the pair buys is the generator being off-host, not a steadier clock (§1.10) | `bench/profile/results/pair-stand-notes.md` |
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

### 1.8 The size shortlist, for the whole request path rather than for application code

Phase 1 of the brief. The second phase scanned 283 application methods; this scans the 56 471
methods with a body across the 25 artifacts of the pinned stack — Ktor 3.5.2 on Netty,
kotlinx.serialization and coroutines 1.11.0, Exposed 1.4.0 over the PostgreSQL driver 42.7.13 and
HikariCP 7.0.2 — because the request path is mostly framework and a scan of application code alone
prices a twentieth of it.

| Fact | Where verified |
|---|---|
| **691 of 56 471 methods exceed `FreqInlineSize` — 1.22 %** — and 53 of those are class initialisers, which run once and are never candidates for inlining into a request | `experiments/method-sizes/results/`, threshold read from the JVM at report time |
| **Three methods on the whole classpath exceed 8000 bytes, and all three are cold**: two Unicode tables in the driver's shaded `stringprep` (SCRAM authentication, once per connection) and Netty's SPDY class initialiser | the same log's shortlist |
| The densest artifact is the serialisation glue: `ktor-serialization-kotlinx-jvm` has **4 of 36** methods over the threshold — 11.1 % — and content negotiation 4 of 75. By count the leaders are coroutines 104, the driver 101, `ktor-server-core` 71, Exposed 79 across its two artifacts | the per-artifact table |
| **52 `invokeSuspend` bodies exceed the threshold**, and four of them are on the request path by construction: `DefaultEnginePipelineKt$defaultEnginePipeline$1` 1080 b, `DefaultTransformKt$installDefaultTransformations$2` 1044 b, `ResponseConverterKt$convertResponseBody$1$2` 939 b, and Netty's `RequestBodyHandler$job$1` 1122 b | the shortlist, filtered to `invokeSuspend` |
| Exposed's `TransactionsKt.inTopLevelSuspendTransaction` is **1549 bytes** | the same |
| Coroutines' largest bodies are `BufferedChannel.toString`, `toStringDebug` and `checkSegmentStructureInvariants` — debug and invariant code, cold in a service | the same |

**Consequence — the brief's RQ1 suspicion is right and pointed at the wrong code.** It expects
`invokeSuspend` bodies "inflated by inline calls such as `transaction {}`", and names application
code. `transaction {}` does produce one: 1549 bytes in Exposed. But the four suspend bodies that run
on *every* request belong to Ktor and to the Netty engine, and the largest application method the
second phase found was 816 bytes. Under D5 the shortlist is the framework's, and the study's first
RQ1 arm is `DefaultTransformKt$installDefaultTransformations$2` — which is also the method the
second phase already caught building a `KClassImpl.toString` on every `call.receive<T>()`
([research-optimizer](research-optimizer.md) §1.4). Two phases, two instruments, one method.

**Consequence — RQ1's huge-method half is green, and now for a reason.** Nothing on the request
path is within a factor of four of 8000 bytes; the three that exceed it authenticate connections
and speak SPDY. That is a verdict reached by looking rather than by assuming, and it retires half
of RQ1 before the stand exists.

**What this scan cannot say.** Size is static. A method over the threshold is a suspect, not a
cost: only the profile says whether anything calls it, and only the inlining log says whether C2
was ever asked to inline it and refused. `BufferedChannel.toStringDebug` is 941 bytes and will
never appear in a request. The shortlist exists to be intersected with the profile, which is
[B-43](../backlog/B-43-static-scan-across-owners.md)'s second half and B-48's input.

### 1.9 Nine tenths of the size shortlist never runs

§1.8 produced 638 suspects. This intersects them with a CPU profile of the stand, on the engine the
brief pins, so that a suspect becomes a cost or stops being anything. Run on the Linux box —
`bench/` on Netty, `/business`, 45 s of warm-up and 90 s of measurement at 64 connections, JVM
pinned to cores 0–7 and `oha` to 8–15 — 134 870 samples, 91 463 rps, 71 µs of CPU per request.

Two files, and which is committed follows the rule this repository already set for profiles: the
run's `bench/profile/results/netty-jit/summary.md` is versioned, and the 19 MB of collapsed stacks
it was computed from stays on the machine that produced it. What carries the numbers below is
therefore the **join**, `experiments/method-sizes/results/2026-09-19-netty-jit-intersection.log`,
which is committed and which `intersect.py` rebuilds from any later profile of the same shape.

| Fact | Where verified |
|---|---|
| The run reproduces the fourth phase's owner split on Netty: application code **6.0 %** of CPU by owner here against 6.1 % there, on a different build and a different day | `bench/profile/results/netty-jit/summary.md` against [research-engines](research-engines.md) §1.4 |
| **Of 638 oversized methods, 49 appear in the profile.** 192 belong to five artifacts that cannot appear at all, because this stand has no database — Exposed, HikariCP, the driver. Of the 446 that could have run, **397 never do: 89.0 %** | `experiments/method-sizes/results/2026-09-19-netty-jit-intersection.log` |
| **All 49 together own 3.86 % of self samples, and the largest single one owns 0.56 %** — `AbstractChannelHandlerContext.write` at 330 bytes | the same log |
| The artifact where every oversized method runs is the serialisation glue: content negotiation **4 of 4**, `ktor-serialization-kotlinx` 2 of 4 — the same two artifacts §1.8 found densest by share | the same log's per-artifact counts |
| The four "every request" suspend bodies of §1.8 do run, which confirms the shortlist was reading the right code, and their own bodies are cheap: `RequestBodyHandler$job$1` 0.15 % self, `convertResponseBody$1$2` 0.07 % | the same log |
| `NettyHttp1Handler$handleRequest$1$1.invokeSuspend` is 381 bytes, **0.05 % self and 43.85 % stack** — the request-handling root. Size plus a large stack share is a frame, not a cost | the same log |

**Consequence — RQ1's size question is grey by the brief's own vocabulary.** The threshold for red is
2 % of request CPU. No single oversized method on this path owns half a percent of self samples, and
the entire class of them owns 3.86 %. A study that had scanned, shortlisted and stopped would have
reported 638 findings, and 89 % of that list is code nothing executes.

**Consequence — and this is the limit of the intersection.** A method's `self` share is the time
spent in its own body. The cost of a *refused inline* is not that: it is call overhead plus the
optimisation C2 could not do across the boundary, which can be larger or smaller than the body.
So this join **locates**, it does not price. RQ1 survives as a toggle experiment — raise
`FreqInlineSize`, measure µs of CPU per request — and it is now a much smaller one: 49 methods
rather than 638, with four of them named.

**What this run cannot say.** There is no database behind it. Exposed's 79 oversized methods, the
driver's 101 and HikariCP's 11 were never given the chance to appear, and RQ4's territory is
untouched — the whole point of [B-41](../backlog/B-41-jit-stand-data-layer-and-endpoints.md). The
89 % is the honest miss rate; the 92.3 % over the whole shortlist is the one that would have
flattered this section.

### 1.10 The stand with a database, on a dedicated pair — and what its ruler turned out to be

B-41 built the data layer; this measures on it. Subject and generator are separate machines
(`bench-a`/`bench-b`, 4 cores and 7 GiB each, private link, RTT 0.74 ms), which is the arrangement
§1.7 says the single-host protocol cannot honestly replace. The service runs the **same
`installDist` bytes** as the WSL runs: the subject has no public IPv4, so it cannot resolve the
settings plugin, and building elsewhere and shipping the distribution turned out to be better
method than a fix — one artefact, two hosts.

| Fact | Where verified |
|---|---|
| The pair is genuinely idle: **steal 0** since boot, 99–100 % idle at rest, nothing but sshd listening | `bench/profile/results/pair-stand-notes.md` |
| **The governor cannot be fixed here either** — these are KVM guests with no `cpufreq` and no `intel_pstate`, exactly like WSL. The pair's advantage is the generator being off-host, not a steadier clock | the same notes |
| **CPU per request is a function of offered rate, not a constant of the code**: 210 µs at 5k rps, 113 at 10k, 92 at 20k, 67 at 40k, 53 at saturation, with the process burning **0.001 cores at idle** | the rate sweep in the same notes |
| **The control holds.** `/plaintext` never touches the data layer, so the two modes must agree there, and they do: **83 µs stub against 82 µs real**, saturation 56 739 against 58 194 rps | `results/pair-{stub,real}-plaintext/summary.md` |
| **The data layer costs about 2.2× the stub at the same offered rate**: `/db/items/{id}` 447 → 995 µs, `/db/items?limit=50` 561 → 1181 µs, `POST /db/items` 647 → 1108 µs | `results/pair-*-{dbitem,dblist,dbpost}/summary.md` |
| **The database is not the limit.** Postgres alone does **15 631 tps** on this box at 0.256 ms average (`pgbench -S`, 4 clients) while the stand's real mode saturates near 3–4.5k with the JVM at 2.2–2.5 of 4 cores | the same notes |
| **19.4 % of the request path's wall time is a spin in the connection pool**: `HikariPool.recycle → ConcurrentBag.requite → Thread.yield`, which is what HikariCP does while anyone is waiting for a connection. Acquisition *wait* is 0.16 %, so the pool's capacity is not the issue — the hand-off is | wall-clock profile, 25 s under load, same notes |
| The database round trip is visible and modest by comparison: `Net.poll` under `VisibleBufferedInputStream.readMore`, 14 % of all wall samples | the same profile |

**The ruler, and it is the most important number here.** Five repeats of one configuration, in one
process, back to back: 3109, 3389, 3952, 4138, 4292 rps — **30 % spread on the median**, and 462 to
613 µs per request. Nothing finer than about 1.5× can be claimed in real mode on this stand.

**Consequence — what survives and what does not.** The 2.2× between stub and real is far outside
that ruler and stands. The `/plaintext` control agreeing to 1 % is inside it, as a control should
be. But the pool sweep (8/16/32/64 → 3223/3099/4675/4410 rps) and the concurrency arms are **single
runs each**, and their differences sit inside the ruler: the same configuration measured 4675 rps in
one sweep and 2641 twenty minutes later. *So the ceiling is not explained.* A first reading of the
spin said "the pool sets it"; that claim is withdrawn — not refuted, unmeasured. The spin itself is
a share taken inside one run rather than a ratio between runs, which is why it survives while the
conclusion drawn from it does not.

**Consequence — the protocol needs one more discard.** The first measured window after a 40-second
warm-up is still the slowest of the five (609 µs against 462–496 for the middle three). Warm-up did
not end where the protocol declared it ended.

**What this does not yet say.** Whether the 2.2× is Exposed's mapping, the driver's round trip, the
`Dispatchers.IO` hop or the hand-off spin is exactly RQ4's question, and it needs the brief's own
control arm — the same query through hand-written JDBC — which is [B-45](../backlog/B-45-rq4-exposed-read-path-in-three-arms.md).
The ceiling has an item of its own now: [B-51](../backlog/B-51-real-mode-ceiling-and-its-ruler.md).

### 1.11 RQ4 answered: Exposed costs 1.3× hand-written JDBC, and the brief's line is 1.5×

Three arms behind one binary, sharing the pool, the dispatcher and the row shape, so that the only
difference between them is the layer: hand-written JDBC with autocommit; the same SQL inside an
explicit `BEGIN`/`COMMIT`; and the shipped Exposed path. All three return identical responses. Two
endpoints, three rounds, arms rotating within each round, the first window after warm-up discarded,
20-second measured windows at a fixed 2000 rps.

| Endpoint | jdbc | jdbc-tx | exposed | exposed / jdbc |
|---|---|---|---|---|
| `/db/items/{id}`, 1 row | 510 µs | 575 µs | 646 µs | **1.27×** |
| `/db/items?limit=50` | 595 µs | 658 µs | 768 µs | **1.29×** |

Spreads across the three rounds: 2–7 %. That is the protocol paying for itself — the same stand
measured a **30 %** spread the day before, on single windows at a floating rate (§1.10).

**The verdict. RQ4 is green**, by the brief's own threshold: green is "CPU per request within 1.5×
of hand-written JDBC for the same query", and the measured ratio is 1.27–1.29×. The red condition —
above 1.5×, with a third of the gap traceable to failed inlining, megamorphic dispatch or failed
scalar replacement — is not reached, so its second half never comes up.

**The decomposition is worth more than the verdict**, because it says where the 30 % sits:

| Layer | Cost | Shape |
|---|---|---|
| the transaction wrapper | **~64 µs per request** | flat — 65 µs on one row, 63 µs on fifty |
| Exposed's fixed part | **~70 µs per request** | query building, transaction manager, statement setup |
| Exposed's mapping | **0.76 µs per row** over hand-written, ≈ **0.151 µs per column** | grows with rows: +71 µs at one row, +110 µs at fifty |

**Consequence — §1.4's suspicion was right in mechanism and small in size.** The per-column
`HashMap` lookup in `getExpressionIndex` plus the `valueFromDB` dispatch are real and they are what
the per-row term measures, but they cost about 0.15 µs a column. On a fifty-row page with five
columns that is 38 µs against a 768 µs request — 5 %. A study that had found the mechanism and
stopped would have reported a defect; the number turns it into a footnote.

**Consequence — the biggest single line is not Exposed at all.** Wrapping the same SQL in an
explicit transaction costs 64 µs, as much as everything Exposed adds on a single-row read. That is
not a JIT question and not an Exposed question; by the brief's own distinction it is library cost
and is recorded and left alone.

**What this does not answer.** The brief also asks for a typed row holder against `Array<Any?>` in
a microbenchmark. That arm cannot be staged honestly at service level — Exposed exposes no
index-based row access — and it needs JMH, which this phase does not have yet. The per-row term
above bounds what it could ever show: 0.76 µs a row is the whole of what a perfect holder could win.

**A caution about reading these beside §1.10.** Those numbers were taken at a different pool size
and a floating rate; the same `exposed` arm reads 995 µs there and 646 µs here. Absolute
microseconds do not survive between sweeps on this stand, ratios do, and the ratios here were all
taken inside one sweep against each other.

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

### D3. The warm-up gate reads `jdk.Compilation`; the inlining evidence cannot *(deviation)*

Brief: start measuring after JFR shows no C2 compilations for 60 seconds; JFR gives compiler
behaviour without diagnostic flags.

Decision, in two halves, because the two events behave differently (§1.3):

* **The gate uses `jdk.Compilation`, switched on explicitly** — `+jdk.Compilation#enabled=true` and
  `#threshold=0ms` — and the runs it gates carry that setting too, since at 99.5 % coverage it is a
  census, and at +5.3 % and +5.7 % across two sweeps of a microbenchmark that does nothing but
  compile it is affordable on a service that spends most of its time waiting for I/O. On the settings the brief assumes, the same gate cannot fail.
* **Inlining refusals come from `-XX:+PrintInlining` or `LogCompilation`, in their own runs.**
  `jdk.CompilerInlining` stops after a few dozen compilations, reproducibly. The brief is right that
  those flags change timing, so no timing number is taken from a run that carries them.

Why the correction is recorded rather than the conclusion alone: the first version of this decision
sent the gate to `-XX:+PrintCompilation` on the strength of a control that was too short to compile
anything after startup. The price of the decision as it now stands: two runs instead of one
wherever a verdict needs both an inlining reason and a time.

### D4. RQ1 keeps one dial and one bound

`-XX:FreqInlineSize` is the dial. `-XX:-DontCompileHugeMethods` is a bound with no subject: not one
method on the whole request path comes within a factor of four of 8000 bytes, and the three that
exceed it across 56 471 are cold (§1.8). Splitting a suspend function by hand stays, and §1.8 says
which one to split first — the four `invokeSuspend` bodies that run on every request belong to Ktor
and the Netty engine, not to the application.

The dial is now pointed at a list of 49 rather than 638 (§1.9), and the arm is the toggle rather
than the scan: the scan says which methods are large and which of them run, and neither of those is
the price of a refused inline.

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

**Open question 1 — closed 2026-09-19, and the hypothesis was wrong.** It asked why JFR's compiler
events covered only tens of milliseconds, and guessed at per-thread buffers that the compiler
threads stop flushing. The answer is that the question was about the test program: on a workload
that keeps compiling, `jdk.Compilation` covers 99.5 % of compile tasks (§1.3). What does truncate is
`jdk.CompilerInlining`, reproducibly and always at the start of the recording; why is not known, and
it is not worth knowing here, because the study needs that evidence at a fidelity JFR does not offer
anyway. If it is ever worth reporting upstream, the four repeats in
`experiments/jfr-compiler-events/results/` are the material.

**Open question 2 — closed 2026-09-19.** A JSON-only process loads one concrete encoder; one
`encodeToJsonElement` anywhere loads three, and `TypeProfileWidth` is 2 (§1.5). Ktor's own converter
never touches the tree API, so the polluting call, if there is one, is always application code. What
is still open is not the census but the price: the receiver counts the compilation log shows at
those sites under load, which is [B-46](../backlog/B-46-rq6-encoder-receiver-census.md).

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
| experiment | `experiments/jfr-compiler-events/run.sh` — §1.3, the short workload |
| experiment | `experiments/jfr-compiler-events/long-run.sh` — §1.3, the control that corrected it, and the price of each instrument |
| experiment | `experiments/json-encoder-census/run.sh`, `experiments/json-encoder-census/Probe.kt` — §1.5 |
| experiment | `experiments/method-sizes/run.sh`, `experiments/method-sizes/scan.py` — §1.8 |
| experiment | `experiments/method-sizes/intersect.py` — §1.9, the join with the profile |
| stand | `bench/profile/jit-pair.sh` — §1.10, the two-host protocol with a shared rate per endpoint |
| stand | `bench/src/main/kotlin/bench/Data.kt` — §1.10, the two data modes |
| measurement | `bench/profile/results/pair-stand-notes.md` — §1.10, the stand's ruler and every probe behind it |
| measurement | `bench/profile/results/pair-rq4-arms.md` — §1.11, the three arms with every round |
| profile | `bench/profile/results/netty-jit/` — §1.9, the run the join reads |
| JDK configuration | `openjdk-25.0.2!/lib/jfr/profile.jfc`, `openjdk-25.0.2!/lib/jfr/default.jfc` — §1.3 |
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
warm-up gate has to be wired to an instrument that reports ([B-42](../backlog/B-42-warmup-gate-on-printcompilation.md)),
and the calibration controls have to run through the whole chain before any candidate does
([B-44](../backlog/B-44-calibration-controls-and-the-known-order-pair.md)). The RQs are scheduled
after that, in the order §1.1 gives rather than the order the brief numbers them.
