---
id: B-34
title: "Форма плагина для CRaC: cracCheckpoint / cracVerify / слой снимка поверх того же образа"
status: done
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

**Сделана часть первая, 11.09.2026 — сторона раннера** ([research-crac](../research/research-crac.md) §1.8):
`Main checkpoint|restore-verify`, `Crac`, `CracPolicies` (генерация файла политик), `Launch.Restore`,
`Exercise` (общий шаг «готовность + нагрузка» для тренировки, снимка и проверки восстановленного),
ключи `cracIgnoredRemotePorts`/`cracImageDirName` в `RunnerConfig`. Проверено на образце в контейнере
CRaC-JDK: снимок 67 МиБ, restore обслуживает ту же нагрузку. По дороге закрыт дефект, которого не
было в плане: раннер ломал свой же checkpoint keep-alive соединениями пробы готовности и workload.
**Сделана 11.09.2026 целиком** ([research-crac](../research/research-crac.md) §1.9): `jibCracCheckpoint`
и `jibCracVerify`, снимок слоем и точка входа `java -XX:CRaCRestoreFrom=…` вместо флага, ключ
`--image` у раннера, образец `samples/ktor-jib` с `-Pcrac` и `crac-check.sh` в CI. По дороге
эксперимент `image-layer.sh` нашёл дефект, который иначе выстрелил бы только в кластере: путь к
файлу политик вморожен в снимок, и файл обязан лежать внутри образа. Не покрыто: чарт и k8s
(открытый вопрос 2), и `-XX:CPUFeatures` (B-35).

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
