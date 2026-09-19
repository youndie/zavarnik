---
id: B-46
title: "RQ6: count the encoders the service actually loads, then judge the call sites"
status: open
priority: P2
size: S
stage: stage-8-jit-constructs
blocked_by: [B-44]
---

# B-46 — "JSON only" is six encoder implementations, not one

`kotlinx-serialization-json-jvm` 1.11.0 ships `StreamingJsonEncoder`, `AbstractJsonTreeEncoder`,
`JsonTreeEncoder`, `JsonTreeListEncoder`, `JsonTreeMapEncoder` and `JsonPrimitiveEncoder`; the core
artifact adds four more ([research-jit-constructs](../research/research-jit-constructs.md) §1.5).
Whether the `Encoder` call sites in a generated serialiser are monomorphic is therefore a question
about which of those classes the service loads, and one `encodeToJsonElement` anywhere pollutes the
profile everywhere.

- **The census comes first** (open question 2): which encoder and decoder implementations are
  loaded at all under the list endpoint's load, and which appear as receivers at the generated
  serialiser's call sites.
- **Only then the verdict**, by the brief's chain, with `CompileCommand=dontinline` on the encoder
  methods as the lower bound and one-format against two-format as the pollution arm.
- **Polymorphic serialisation of a sealed hierarchy is reported separately**, as the brief asks —
  it is a different receiver set and a different answer.
- Does **not** cover: other formats. The brief's stack is JSON.

- AC: a committed list of loaded `Encoder`/`Decoder` implementations under load, and the receiver
  count per generated-serialiser call site from the inlining log.
- AC: a verdict per call site with the toggle's effect and the spread, and, if green, the control
  number from B-44 that sizes it.
- Anchors: `bench/src/main/kotlin/bench/`, `bench/profile/run.sh`.
