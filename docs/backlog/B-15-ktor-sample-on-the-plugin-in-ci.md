---
id: B-15
title: "samples/ktor на плагине, прогоняемый в CI: сквозная проверка train → verify → dist"
status: done
priority: P1
size: M
stage: stage-2-mvp
blocked_by: [B-07]
---

# B-15 — `samples/ktor` на плагине в CI

> **Сделано 06.09.2026.** `samples/ktor` — отдельная сборка, плагин по id через
> `pluginManagement { includeBuild("../..") }`; DSL — порт в `jvmArgs`, `readyWhen.url`, два
> `curl` в `workload`. `./gradlew -p samples/ktor check distTar` на Linux 25.0.4: готовность
> 537 мс, кэш 31,7 МБ, 2262 из 2262 классов приложения из кэша, `lib/app.aot` и `.jars` в tar.
> CI (`check.yaml`): задания `plugin` и `sample` на `ubuntu-latest` с `setup-kotlin` sborka —
> как у bochka; self-hosted не понадобился, репозиторий публичный. Не проверено: CI ещё не
> запускался — у репозитория нет remote (B-13).

TestKit ([B-08](B-08-testkit-invalidation-cases.md)) проверяет плагин на игрушечных проектах.
Пример на Ktor — то, что увидит первый пользователь, и то, что стенд
[B-01](B-01-ktor-stand-and-readiness-timing.md) мерил руками; когда он собран плагином, стенд
и продукт перестают быть двумя разными вещами.

- **Решение: `samples/ktor` — отдельная сборка с `includeBuild("..")`** (как `stand/` в sborka:
  просит плагин по id, а не через `pluginManagement`), с `readyWhen.url` на `/health` и
  `workload` из `curl` по двум маршрутам. CI гоняет `aotTrain aotVerify distZip` на self-hosted
  раннере (там JDK 25.0.4 и Docker) — не на GitHub-hosted.
- Не покрывает: образ — [B-10](B-10-docker-and-jib-recipe.md).

- AC: зелёный `check.yaml` с шагом `samples/ktor`, в журнале — доля классов из кэша и
  `distZip` в артефактах.
- Якоря: `samples/ktor/build.gradle.kts`, `samples/ktor/settings.gradle.kts`, `.github/workflows/check.yaml`,
  `docs/research/research-architecture.md` (§1.3 — хук Ktor, §1.7 — образец sborka `stand/`).
