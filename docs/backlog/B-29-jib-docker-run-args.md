---
id: B-29
title: "Jib-режим: сеть и окружение стенда для контейнера тренировки — zavarnik { jib { dockerRunArgs() } }"
status: done
priority: P1
size: S
stage: stage-3-packaging
---

# B-29 — `docker run` с сетью и окружением стенда

`jibAotTrain`/`jibAotVerify` (B-26) запускают образ голым `docker run`: без сети и переменных.
Образцу этого хватает, konekt — нет: без Postgres и брокера он не стартует (B-27, эксперимент).
Чтобы применить Jib-режим на konekt, контейнеру тренировки нужны `--network` стенда и `-e` с
адресами.

- **Решение:** `zavarnik { jib { dockerRunArgs("--network", "konekt_default", "-e", "DB_URL=…") } }`
  — список идёт в `docker run` после `--rm`, до `--user` и образа, и в тренировку, и в проверку.
  Значения — дело сборки пользователя; konekt берёт их из свойства Gradle, которое ставит его
  измерительный скрипт.
- AC: команда `docker run` собирается в этом порядке. **Automated:** `JibImageTest`.
- AC: konekt тренируется через `jibAotTrain` на сети своего стенда — см. konekt B-123 (Jib).
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikExtension.kt`
  (`JibSpec`), `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/JibAotTrainTask.kt`.

**Проверено на konekt 07.09.2026** (ветка `feat/aot-cache-experiment`, `scripts/measure/aot-coldstart-jib.sh`):
`jibAotTrain` с `--network konekt_default -e DB_URL=… -e BROKER_HOST=…` натренировал кэш внутри
Jib-образа на сети стенда, `jibAotVerify` — 4727 из 4730 классов приложения из кэша; entrypoint
несёт `-XX:AOTCache=/app/zavarnik/app.aot`. Готовность по медиане десяти рестартов 6244 → 2242 мс
(второй круг без кэша, без выбросов первого, — 4253 мс), первый запрос 309 → 168 мс; кэш 61 МБ,
образ 537 → 614 МБ. Две грабли konekt: Jib 3.5.4 не дружит с configuration cache
(`--no-configuration-cache` на его задачах), и у Jib-образа нет `HEALTHCHECK` — `compose up --wait`
возвращается на «running», ждать `/health` надо самому.