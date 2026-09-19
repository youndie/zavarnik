---
id: B-45
title: "RQ4: what Exposed's read path costs, in three arms rather than two"
status: open
priority: P1
size: M
stage: stage-8-jit-constructs
blocked_by: [B-41, B-44]
---

# B-45 — Separating the array from the map from the column type

The brief suspects `ResultRow` "as `Array<Any?>`" and proposes one toggle: a typed row holder
against Exposed. Read in the artefact, a column read goes
`get → getInternal → getRaw → getExpressionIndex → data[i] → rawToColumnValue`, and
`getExpressionIndex` is a hash lookup keyed by an `Expression<?>`, per column, per row
([research-jit-constructs](../research/research-jit-constructs.md) §1.4). A two-arm comparison
prices the array, the map and the cache object together and hands the total to the array.

- **Three arms**: Exposed as shipped; an array without the per-column map lookup; a typed row
  holder. Plus hand-written JDBC for the same query as the brief's outer bound.
- **`IColumnType` is the other suspect and is separate.** One abstract method after erasure,
  `Object valueFromDB(Object)`, on an interface with many implementations — how many receivers a
  request sees is a runtime measurement, not a count of classes in the jar.
- **The brief's distinction is the verdict's shape**: Exposed doing work is library cost and is
  recorded and left alone; only the part traceable to failed inlining, megamorphic dispatch or
  failed scalar replacement is a finding of this study.
- Does **not** cover: the DAO layer or R2DBC.

- AC: four numbers per query shape with spreads beside them, in stub mode, plus the share of the
  gap that the inlining log attributes to each of the three named causes.
- AC: the receiver count observed at the `valueFromDB` site on the list endpoint, from the
  compilation log — a number, not an adjective.
- Anchors: `bench/src/main/kotlin/bench/`, `bench/profile/ab.sh`.
