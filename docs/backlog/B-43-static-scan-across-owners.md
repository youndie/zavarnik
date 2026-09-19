---
id: B-43
title: "Static scan of the whole runtime classpath, not only of application code"
status: open
priority: P1
size: S
stage: stage-8-jit-constructs
---

# B-43 — Method sizes for every owner on the classpath

The second phase scanned 283 application methods and found 10 above `FreqInlineSize` and none above
8000 bytes. That scan answered a question about a plugin whose territory was application code.
This phase's territory is the whole request path (D5), and the mass of it is kotlinx, Ktor, Exposed
and the driver — all of them Kotlin or Java compiled by the same rules.

- **The scan covers the runtime classpath**, and every finding is labelled with its owner, so the
  shortlist for the size question is ordered by where the CPU is rather than by whose code it is.
- **It is a report, not a gate.** sborka already owns the gate-shaped version of this
  (`kapkanMethodSizes`); duplicating it here would produce two counters of one thing.
- **The threshold is read at report time**, not written into the output as a constant:
  `FreqInlineSize` is a platform-dependent flag that happens to agree on two platforms today
  ([research-jit-constructs](../research/research-jit-constructs.md) §1.2).
- Does **not** cover: deciding anything. A large method is a suspect; B-48 prices it.

- AC: a committed table of every method above the live `FreqInlineSize`, with owner, size and
  artefact, plus the count above 8000 bytes; the command that regenerates it is in the repository.
- AC: the table is cross-referenced against the CPU profile of B-41, so that a row nobody executes
  is visibly a row nobody executes.
- Anchors: `bench/profile/`, `bench/build.gradle.kts`.
