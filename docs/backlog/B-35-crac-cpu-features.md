---
id: B-35
title: "Снимок привязан к CPU тренировки: цена и семантика -XX:CPUFeatures на checkpoint"
status: open
priority: P2
size: S
stage: stage-6-crac
blocked_by: [B-32]
---

# B-35 — CPU тренировки ≠ CPU прода

Restore на CPU без части признаков отказывает явно: «You have to specify -XX:CPUFeatures=[...]
together with -XX:CRaCCheckpointTo» ([research-crac](../research/research-crac.md) §1.5); тот же
снимок под Zulu 26 упал на этой проверке раньше проверки файлов. Это та же переносимость, что в
[B-09](B-09-cpu-portability-adapter-caching.md) у AOT-кэша, но встроенная в инструмент и с
громким отказом.

- **Решение:** измерить на паре машин (Linux-машина, Core Ultra без AVX-512, и запасная машина
  с другим CPU — та же пара, что нужна B-09): снимок без флага, снимок с `-XX:CPUFeatures`,
  сузившим набор до общего, — restore на второй машине и цена в готовности и rps. Что означает
  `generic` и есть ли он у warp — по документации Azul, а не по памяти.
- Не покрывает: arm64 ↔ x86_64 (снимок непереносим по определению).

- AC: таблица «набор признаков — restore на второй машине — готовность — rps» в §1.5
  research-crac с журналом в `experiments/crac-cpu/`; рекомендация для README плагина.
- Якоря: `experiments/cpu-portability/`, `experiments/crac-ktor/run.sh`.
