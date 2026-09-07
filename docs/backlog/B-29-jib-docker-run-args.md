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
