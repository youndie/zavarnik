---
id: B-28
title: "Пинить mtime jar-ов уже в installDist, а не только в aotTrain — образ, обученный в контейнере, иначе отвергает кэш"
status: done
priority: P1
size: S
stage: stage-3-packaging
---

# B-28 — mtime jar-ов пинуется в `installDist`

Нашлось на konekt (B-27, эксперимент): сервис не стартует без базы, тренировка идёт раннером
**внутри контейнера** образа, собранного из `installDist`, а кэш докладывается в образ вторым слоем.
Раннер пинит mtime jar-ов у себя в контейнере — образ этого не видит, во втором образе jar-ы несут
время копирования `installDist`, и JVM отвергла бы кэш «timestamp has changed». В скрипте
эксперимента это обходилось `touch -d @86400` на хосте до `docker build` — то есть пользователь
должен знать константу плагина. Не должен.

- **Решение:** `installDist` получает `doLast`, который ставит всем `lib/*.jar` константу
  `Training.JAR_MTIME` (та же, что у `distTar` и у тренировки). `aotTrain` пинит по-прежнему —
  идемпотентно. Docker `COPY` сохраняет mtime (D3), значит образ из `installDist` готов к
  тренировке в контейнере без единой строки у пользователя.
- Не покрывает: Jib (свой mtime, B-26) и `distZip` (DOS-время).
- AC: `installDist` без тренировки даёт `lib/*.jar` с `JAR_MTIME`, кэша при этом нет.
  **Automated:** `DistributionFunctionalTest`.
- AC: в konekt строка `touch` из `scripts/measure/aot-coldstart.sh` убрана, кэш во втором образе
  проходит `verify` под `-XX:AOTMode=on`.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikPlugin.kt`,
  `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/Training.kt`,
  `docs/research/research-architecture.md` (D3).
