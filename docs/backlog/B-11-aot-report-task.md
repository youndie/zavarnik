---
id: B-11
title: "aotReport: таблица «холодный / с кэшем» по времени готовности — артефакт для публикации"
status: done
priority: P2
size: S/M
stage: stage-3-packaging
blocked_by: [B-06]
---

# B-11 — `aotReport`

> **Сделано 06.09.2026.** `AotReportTask`: N прогонов (умолчание 10, `-Pzavarnik.runs=N`) с
> `-XX:AOTMode=off` и с кэшем, время до первого `200` на `readyWhen.url` снаружи процесса
> (`StartScriptRun.awaitReady`), отсортированные ряды и медианы в
> `build/reports/zavarnik/aotReport.md` с версией JDK и размером кэша; без `readyWhen.url` —
> ошибка с объяснением. В `check` не входит. На образце Ktor (Linux 25.0.4, 5 прогонов):
> 619 мс без кэша, 238 мс с кэшем. TestKit-тест: две строки по два отсортированных числа.

Бриф хочет число, которое можно показать. Стенд [B-01](B-01-ktor-stand-and-readiness-timing.md)
получает его руками; `aotReport` делает то же самое для любого проекта с плагином — и тем же
методом, чтобы цифра в README не разошлась с измерением.

- **Решение: N запусков (умолчание 10) с `-XX:AOTMode=off` и N с кэшем, время до первого `200` на
  `readyWhen.url`, отсортированные ряды и медианы в markdown-таблицу в `build/reports/zavarnik/`.**
  Отвергнуто: среднее — первый запуск после перезапуска меряет прогрев, и один выброс уносит
  среднее (журнал Linux: `2107` мс среди `124–153`).
- Отвергнуто: считать «проценты ускорения» без абсолютных величин — отношение без абсолютной
  величины ничего не решает; в таблице обе колонки в миллисекундах.
- Не покрывает: пропускную способность и пиковую производительность — вне брифа.

- AC: `./gradlew aotReport` создаёт файл с двумя рядами по N чисел и медианами, с подписью JDK,
  GC и размера кэша; без `readyWhen.url` задача падает с понятным текстом.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/AotReportTask.kt`,
  `zavarnik-gradle-plugin/src/functionalTest/kotlin/io/github/youndie/zavarnik/AotReportFunctionalTest.kt`,
  `experiments/aot-validation/run.sh` (секция `M1`), `docs/research/research-architecture.md` (§1.9).
