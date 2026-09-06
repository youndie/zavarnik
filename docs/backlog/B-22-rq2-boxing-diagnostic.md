---
id: B-22
title: "RQ2 — боксинг: 1,26 % байт, ниже красного порога — только диагностика"
status: dropped
priority: P3
size: XS
stage: stage-5-optimizer
---

# B-22 — RQ2: боксинг примитивов

Ресёрч §1.4: `Integer`/`Long`/`Double`/… как лист аллокации — 1,26 % байт `/business`, при том
что стенд нарочно содержит `suspend fun (): Long`, `Deferred<Long>.await()` и `value class` в
generic-позиции. Красный порог брифа — 3 %. Снята как проход; остаётся строкой в диагностике
(B-18), если та переживёт ворота.

- Якоря: `bench/src/main/kotlin/bench/Pricing.kt`, `bench/profile/results/baseline/business.alloc.collapsed`.
