---
id: B-50
title: "The verdict table and the article, with green, grey and stopped written up like red"
status: open
priority: P2
size: S
stage: stage-8-jit-constructs
blocked_by: [B-45, B-46, B-47, B-48, B-49]
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
