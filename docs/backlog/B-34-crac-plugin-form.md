---
id: B-34
title: "Форма плагина для CRaC: cracCheckpoint / cracVerify / слой снимка поверх того же образа"
status: open
priority: P1
size: L
stage: stage-6-crac
blocked_by: [B-32]
---

# B-34 — Форма плагина для CRaC

Если ворота B-32 зелёные, плагин получает третью тройку задач по D2
[research-crac](../research/research-crac.md): тренировка = checkpoint после `readyWhen` и
`workload` внутри контейнера образа, проверка = restore + workload + два restore для D4,
упаковка = слой снимка поверх образа с тем же digest. Из AOT-фазы переносится раннер, workload
с заголовками и захватом, `dockerRunArgs`; не переносится `distTar` и стартовый скрипт как
лаунчер (§1.5, D2).

- **Решение:** DSL `zavarnik { crac { … } }` рядом с `training { }`; задачи через тот же Jib-путь
  (`jibDockerBuild` → контейнер → `jcmd`) и через Dockerfile-путь раннером (`Main checkpoint`,
  `Main restore-verify`). Политика для слушающего сокета генерируется плагином (D3). Требование
  на конфигурации: базовый образ — Zulu с CRaC ≥ 25 (по `java -version` внутри образа, не по
  имени тега).
- Гипотезы для проверки в задаче: Netty-движок Ktor под тем же правилом (PR netty#13308 закрыт
  без слияния — §1.4); `CRaCHeapErgonomics` и размер снимка (открытый вопрос 1); окружение
  restore-контейнера (риск 3).
- Не покрывает: чарт и k8s-раскатку (открытый вопрос 2) — отдельной задачей после первого
  применения.

- AC: `samples/ktor-jib` с `crac { }`: `./gradlew cracCheckpoint cracVerify` зелёные, restore
  образца отвечает 200 на обе маршрута, отчёт печатает готовность restore против обычного
  старта — те же ~50 мс против ~640 (`experiments/crac-ktor/results/`).
- AC: `cracVerify` красный на снимке, восстановленном не в том образе (§1.5), с текстом отказа
  warp в сообщении.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/JibSupport.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/JibAotTrainTask.kt`,
  `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/Main.kt`,
  `experiments/crac-ktor/warm.sh`.
