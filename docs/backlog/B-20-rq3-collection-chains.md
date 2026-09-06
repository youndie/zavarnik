---
id: B-20
title: "RQ3 — промежуточные коллекции цепочек: ≈ 4,6 % байт на /business"
status: open
priority: P2
size: L
stage: stage-5-optimizer
blocked_by: [B-16]
---

# B-20 — RQ3: слияние цепочек коллекций

Ресёрч §1.4: `Object[]` 2,06 %, `ArrayList` 0,93 %, `LinkedHashMap$Entry` 0,89 %, `ArrayList$Itr`
0,71 % с владельцем `Pricing.quote` — около 4,6 % байт, выше красного порога 2 %. Это самый
дорогой в реализации кандидат (IR-проход по цепочкам `Iterable`), и самый спорный: то же даёт
`asSequence()` руками.

- **Решение: как в B-19 — сначала ручная замена на `Sequence` и A/B**, потом решение о проходе.
- Не покрывает: `groupBy`/`sortedBy` — они не сливаются в один проход по определению.

- AC: строка в ресёрче §1.7 с дельтой ручного варианта; решение «проход / рекомендация в доке».
- Якоря: `bench/src/main/kotlin/bench/Pricing.kt`, `bench/profile/ab.sh`.
