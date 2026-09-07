---
id: B-13
title: "Выпуск v0.1: Plugin Portal / Central, README с измерением, объявление — и четыре недели наблюдения"
status: open
priority: P1
size: M
stage: stage-4-release
blocked_by: [B-12]
---

# B-13 — Выпуск v0.1 и четыре недели наблюдения

> **Снапшоты — 07.09.2026.** `publish-snapshot.yaml` зовёт `publish-wip.yaml` sborka с двумя
> именованными секретами; каждый пуш в `main` после `check` кладёт в reposilite `snapshots` модуль
> и маркер плагина (первый — `0.1.0.1`: jar, module, pom с описанием и лицензией, sources).
> Проверено настоящим путём: чужой проект с `pluginManagement` на reposilite резолвит
> `id("io.github.youndie.zavarnik") version "0.1.0.1"`, тренирует и проходит `aotVerify`. README
> показывает блок репозитория. Plugin Portal и Central (через `central.yaml` sborka) — впереди.

Четвёртый критерий остановки из брифа: ноль внешних реакций за четыре недели после v0.1 — проект
закрывается. Значит, у выпуска есть дата, у наблюдения — конец, и оба записываются здесь.

- **Решение: публиковать через `central.yaml` в sborka** (репозиторий / ref / версия), как bochka и
  viddik, — один путь для всего портфеля; на Plugin Portal — отдельная публикация, если id
  `io.github.…` там принимается без верификации домена (проверить, не помнить).
- README показывает таблицу `aotReport` ([B-11](B-11-aot-report-task.md)) с условиями замера, а
  не проценты; статья — по правилам скилла `blog-post` на kotlin.website.
- Не покрывает: Maven-плагин — сознательно вне MVP (бриф, §8).

- AC: `./gradlew` в чужом проекте с `id("io.github.youndie.zavarnik") version "0.1.0"` резолвит
  плагин с Central/Portal.
- AC: через четыре недели после даты выпуска здесь записано число реакций (звёзды, issue,
  упоминания, вопросы) и решение: продолжать или закрыть.
- Якоря: `docs/research/research-architecture.md` (§3, критерии остановки).
