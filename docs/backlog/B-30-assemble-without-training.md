---
id: B-30
title: "training { onAssemble = false }: assemble и build не тренируют, если приложению негде стартовать"
status: done
priority: P1
size: S
stage: stage-3-packaging
---

# B-30 — `assemble` без тренировки

Нашлось на CI ветки konekt: `./gradlew build` → `assemble` → `distTar`, а `distTar` у плагина
зависит от выходов `aotTrain` — и `aotTrain` падает, потому что приложение без Postgres не
стартует. `verify { onCheck = false }` снимал тренировку с `check`, но не с `assemble`: сборка,
которая не может тренироваться, не могла и собираться.

- **Решение:** `training { onAssemble = false }` (умолчание `true`). При `false` `distTar` берёт
  кэш и манифест из `installDist` как обычные файлы, без зависимости от `aotTrain`: если тренировка
  их оставила — уезжают, если нет — tar без кэша, `assemble` зелёный. Тренировка идёт там, где
  приложение живёт: на стенде, внутри образа, раннером (B-25, B-29).
- AC: с `onAssemble = false` `assemble` не запускает `aotTrain` и zip/tar без кэша; после `aotTrain`
  `distTar` несёт кэш. **Automated:** `DistributionFunctionalTest`.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikPlugin.kt`
  (`shipInArchives`), `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikExtension.kt`.
