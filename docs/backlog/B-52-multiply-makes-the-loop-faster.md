---
id: B-52
title: "A loop that multiplies is five times faster than the same loop without the multiply"
status: open
priority: P2
size: S
stage: stage-8-jit-constructs
---

# B-52 — An impossible ordering, reproduced, and unexplained

Summing 1024 ints:

| benchmark | ns/op |
|---|---|
| `for (v in ints) acc += v` | 406.7 |
| `for (i in ints.indices) acc += ints[i]` | 396.1 |
| `for (i in 0 until ints.size) acc += ints[i]` | 396.9 |
| **`for (v in ints) acc += v * 2`** | **77.3** |

The arm that does strictly more work is five times faster, and it reproduced in two independent
benchmark classes ([research-jit-constructs](../research/research-jit-constructs.md) §1.14).
0.073 ns an element is a fraction of a cycle, so the fast arm is vectorised and the others are not.

- **Indexing is not the variable**, though it looked like it first: direct and indexed iteration are
  within 3 % of each other. That reading is retracted.
- **By D8's rule this is a stand problem until shown otherwise.** An arm doing more work for less
  time is the fastest detector of a benchmark measuring itself, and it fires before the spread does.
  So this is an anomaly, not a finding, and it does not enter any verdict.
- **What it needs is the brief's own step 3**: `-prof perfasm` with hsdis on both arms, to see
  whether the fast one is vectorised and why the slow one is not. If the difference is real codegen,
  it is an RQ5-shaped result about a construct nobody would suspect; if it is the benchmark, the
  benchmark is wrong and so is anything built on that pair.
- Does **not** cover: the controls that used the fast arm. `handWrittenLoop` was one half of the
  inline-lambda control and its partner matched it to 1.5 %, so that control stands either way —
  both arms are the same shape.

- AC: `perfasm` output for both arms, committed, and a sentence saying which of the two explanations
  it supports.
- AC: if it is codegen, a minimal reproducer that does not depend on JMH.
- Anchors: `microbench/src/jmh/kotlin/micro/Controls.kt`, `microbench/results-candidates.md`.
