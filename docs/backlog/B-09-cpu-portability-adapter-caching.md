---
id: B-09
title: "Переносимость кэша между CPU: AOTAdapterCaching включается сам, и кэш несёт машинный код"
status: open
priority: P1
size: M
stage: stage-3-packaging
---

# B-09 — Переносимость кэша между CPU

`AOTAdapterCaching` — диагностический флаг, по `PrintFlagsFinal` без кэша он `false`, но с
`-XX:AOTCache` и на втором шаге `-XX:AOTCacheOutput` становится `true {ergonomic}` на обоих
JDK 25 (ресёрч §1.6); кэш маппит регион `(Code)`, и JVM пишет «Loaded 326 AOT code entries». Этот
код собран под CPU тренировочной машины: кэш с GitHub-раннера с AVX-512 падает `SIGILL` в
`~AdapterBlob` на CPU без него (Quarkus на Red Hat 25.0.3; Nucleus #400). Тренировка в CI и
запуск на «каком-нибудь» узле — ровно сценарий плагина.

- **Гипотеза-решение: по умолчанию `-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching` и на
  тренировке, и в прод-скрипте** (Nucleus называет это `COMPATIBILITY` и делает умолчанием),
  опция `portability = false` возвращает нативный режим. Отвергнуто: `-XX:UseAVX=2` — только x86
  и только потолок, а не отсутствие машинного кода.
- Проверить: падение воспроизводится только на двух разных CPU. Linux-машина без AVX-512
  (`/proc/cpuinfo`, §1.6) — кандидат на **прод**-сторону; тренировочная сторона с AVX-512 — вопрос
  (GitHub-hosted раннер или другая машина).
- Не покрывает: `AOTStubCaching` — на 25 остаётся `false`; посмотреть на 26 в
  [B-03](B-03-jdk26-on-linux-box.md).

- AC: кэш, натренированный на машине с AVX-512 без флага, падает на Linux-машине с `SIGILL` в
  `~AdapterBlob`; с флагом — стартует с `shared>0`. Оба журнала — в `experiments/`.
- AC: ресёрч §3, риск 1, переведён из гипотезы в факт с адресом журнала; умолчание плагина
  зафиксировано в D6.
- Якоря: `experiments/aot-validation/results/2026-09-06-linux-x86_64-openjdk-25.0.4.log`,
  `docs/research/research-architecture.md` (§1.6, D6, риск 1).
