---
id: B-06
title: "aotVerify: запуск с -XX:AOTMode=on, подсчёт источников классов, сверка манифеста — сборка падает с причиной"
status: open
priority: P0
size: M
stage: stage-2-mvp
blocked_by: [B-05]
---

# B-06 — `aotVerify`

Главная ценность плагина по брифу — не кэш, а красная сборка, когда кэш не будет принят.
Ресёрч показал, что одного признака мало: по умолчанию JVM отвергает кэш **молча, с кодом 0**
(R16), `-XX:AOTMode=on` делает отказ фатальным (R13–R15), но на JDK 25.0.2 стёртый jar
принимается и в этом режиме (R14 в журнале 25.0.2: `exit=0`).

- **Решение: три независимых проверки, любая красная — задача красная, и текст называет
  которая.** (1) Запуск скрипта с `JAVA_OPTS=-XX:AOTMode=on -Xlog:aot -Xlog:class+load` до
  готовности и SIGTERM: ненулевой код — причина берётся из строк `[aot]` (`Unable to use`,
  `shared class paths mismatch`, `cannot be used with JDWP agent`, …). (2) Доля классов приложения с
  `source: shared objects file` ниже порога (умолчание — 90 %, из брифа RQ4) — красный, с числами.
  (3) SHA-256 jar-ов не совпадает с `app.aot.jars` — красный с именем jar-а.
- Отвергнуто: только код выхода. На части JDK он лжёт (§1.2).
- Отвергнуто: только парсинг журнала. Формат строк не контракт; проверка (2) считает, а не ищет
  фразы.
- Не покрывает: отчёт о выигрыше — [B-11](B-11-aot-report-task.md).

- AC: после `aotTrain` → `aotVerify` зелёный, в журнале — доля классов из кэша.
- AC: `touch lib/<any>.jar` → красный с именем jar-а по проверке (3) на любом JDK, а на
  25.0.4 — ещё и по (1) с «timestamp has changed».
- AC: `jvmArgs("--add-modules", "jdk.httpserver")` только в `aotVerify` → красный с «Mismatched
  values for property jdk.module.addmods» (R8).
- AC: `aotVerify` в `check` (умолчание) — сборка библиотеки без `application` не трогается.
- Якоря: `experiments/aot-validation/run.sh` (R8, R9, R13–R16, функция `summ`),
  `experiments/aot-validation/results/2026-09-06-macos-aarch64-openjdk-25.0.2.log` (R14 с
  `exit=0` — почему одного кода мало), `docs/research/research-architecture.md` (§1.1, §1.2, D4).
