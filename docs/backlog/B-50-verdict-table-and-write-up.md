---
id: B-50
title: "The verdict table and the article, with green, grey and stopped written up like red"
status: open
priority: P2
size: S
stage: stage-8-jit-constructs
blocked_by: []
---

# B-50 — The output of the phase is a table, and most of it will be green

The brief's deliverable is one row per construct: verdict, evidence, cost as a share of request
CPU. Two phases of this repository ended in a measured "no", and both were worth more than the
plugin they cancelled. This one is likely to end the same way, and the write-up is where that value
either lands or evaporates.

- **Green, grey and stopped are written up with the same detail as red.** A green row states the
  control number that sizes it (B-44), so that "no effect" and "below what this chain resolves"
  stay distinguishable.
- **Every published number carries its methodology, its JMH JSON, its JFR file and its compilation
  log**, committed beside it, as the brief requires.
- **The stand is named**, not described as "a real service" — a reference stand called by its name
  costs the argument nothing and costs credibility everything when the first reader clicks.
- **Numbers from earlier phases are re-run in the same sweep if they are quoted beside new ones.**
  Absolute figures on this host do not survive between sweeps; shares do, and only shares are
  compared across them, with that said out loud.
- Does **not** cover: where the article is posted, or whether any finding goes to an upstream
  tracker. Both are the owner's call.

- AC: `docs/research/` holds one page per RQ with verdict, numbers, log excerpts and toggle result,
  and a single verdict table over all constructs.
- AC: every version quoted in the text is re-checked against the build files rather than the draft.
- Anchors: `docs/research/research-jit-constructs.md`, `bench/profile/results/`.

## Progress — 2026-09-20: the table is done, the article is not

**AC 2 is met, and finding it cost two corrections.** Every version named in the text was re-checked
against the build files rather than the draft:

* `kotlin-stdlib` was quoted as **2.4.20** in three artefact addresses while the stand ships
  **2.4.10** — the same skew the scans had, now guarded by `experiments/stack.sh` against the
  committed dist manifest. Addresses corrected and the facts re-verified on 2.4.10.
* Re-verifying turned up a wrong fact underneath a right conclusion: §1.6 said
  `IntrinsicsKt__IntrinsicsJvmKt` has **no JVM member** for `startCoroutineUninterceptedOrReturn`. It
  has three, `private static final`, which is exactly what `@InlineOnly` compiles to. The original
  `javap` ran without `-p`. Recorded in §2.2.
* ktor 3.5.2, Exposed 1.4.0, HikariCP 7.0.2, pgjdbc 42.7.13, netty 4.2.16.Final, kotlinx 1.11.0,
  JMH 1.37 and JDK 25.0.4 all check out against the manifest and `microbench/build.gradle.kts`.

**AC 1 is met in substance, and the deviation is worth naming.** The brief asks for "one page per RQ".
What exists is one research document with a section per question — §1.15 RQ3, §1.16 RQ6, §1.17 RQ1,
§1.18 RQ5, §1.19 and §1.20 RQ2, §1.21 RQ7, §1.22 RQ4 — each carrying its verdict, its numbers and the
address of its log, plus the single verdict table in §2 over all constructs. Splitting one document
into eight would break the layered format's rule of one document per entity and cost every
cross-reference; the sections are the pages.

**What is left is the article**, and where it goes is the owner's call, as this item already says.
The material is §2 for the table, §2.1 for the fourteen findings the brief's form did not ask for,
§2.2 for the fourteen withdrawn claims, and §2.3 for the review that caught four of them.
