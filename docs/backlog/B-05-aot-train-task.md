---
id: B-05
title: "aotTrain: тренировочный прогон через настоящий стартовый скрипт, готовность, нагрузка, SIGTERM, манифест"
status: open
priority: P0
size: L
stage: stage-2-mvp
blocked_by: [B-04]
---

# B-05 — `aotTrain`

Сердце плагина. Ресёрч закрыл пять вопросов, от которых зависит форма задачи: classpath из
каталогов кэш не даёт (E4), поэтому тренируется раскладка `installDist`, а не `run`; кэш пишется
при любом завершении, кроме `SIGKILL` (E1–E3), поэтому SIGTERM достаточен и хук в приложении не
нужен; проверка jar-ов идёт по mtime и размеру (§1.1), поэтому mtime нормализуются **до**
тренировки (D2 эксперимента); на части JDK JVM не проверяет jar-ы вовсе (§1.2), поэтому плагин
пишет манифест сам; `-XX:AOTCache` и `-XX:AOTCacheOutput` вместе — «Only one of AOTCache or
AOTCacheOutput can be specified» (G5), поэтому кэша в момент старта тренировки быть не должно.

- **Решение: запускать `build/install/<app>/bin/<app>` — тот скрипт, который поедет в прод, —
  с `JAVA_OPTS=-XX:AOTCacheOutput=<abs>/lib/app.aot`** (§1.4: скрипт складывает
  `DEFAULT_JVM_OPTS $JAVA_OPTS $<APP>_OPTS`). Скрипт добавляет `-XX:AOTCache` сам и только когда
  `lib/app.aot` существует ([B-07](B-07-start-scripts-and-distribution-wiring.md)), поэтому
  задача сначала **удаляет** старый кэш и манифест, потом стартует. Проверено сквозным прогоном
  G1–G4. Отвергнуто: `JavaExec` с собранным вручную classpath — другая строка classpath, другой
  порядок, и «работает у меня» на стенде, которого нет в проде.
- **Ждать завершения процесса-лаунчера, а не закрытия порта:** одношаговый режим — два запуска
  JVM подряд («Launching child process … to assemble AOT cache», JEP 514), и кэш появляется
  после второго; на `hello world` это 0,4–1,1 с (M2), на приложении — секунды.
- Порядок: удалить `lib/app.aot` и `lib/app.aot.jars` → нормализовать mtime jar-ов в `lib/` к
  `1970-01-01T00:00:01Z` (умолчание Jib — `EPOCH_PLUS_SECOND`, D3 ресёрча) → старт → ждать
  `readyWhen.url` → выполнить `workload` → SIGTERM → ждать лаунчер → записать `lib/app.aot.jars`
  (SHA-256 каждого jar в порядке classpath).
- Режим `exitAfter` — запасной для приложений без HTTP; таймаут безопасности с `destroyForcibly`
  и **ошибкой** (Nucleus делает так же, §1.7): SIGKILL кэша не даёт, значит, задача провалена.
- Память: одношаговый режим удваивает потребность в `-Xmx` (JEP 514) — задача задаёт heap
  тренировки явно и пишет это в документации.
- Не покрывает: Windows (нет SIGTERM) — [B-14](B-14-windows-training.md); переносимость между
  CPU — [B-09](B-09-cpu-portability-adapter-caching.md).

- AC: после `./gradlew aotTrain` в `build/install/<app>/lib/` лежат `app.aot` и `app.aot.jars`;
  журнал задачи называет время готовности, длительность нагрузки и размер кэша.
- AC: повторный `aotTrain` при существующем кэше проходит (старый кэш удалён до старта), а не
  падает с «Only one of AOTCache or AOTCacheOutput».
- AC: приложение, не открывшее порт за таймаут, — задача падает с текстом, в котором есть
  «SIGKILL» и «кэш не записан»; файла `app.aot` нет.
- AC: `touch` любого jar в `lib/` после `aotTrain` делает `aotVerify` красным (проверка
  [B-06](B-06-aot-verify-task.md)), а повторный `aotTrain` — снова зелёным.
- Якоря: `experiments/aot-validation/run.sh` (E1–E4, D1–D2, M2),
  `experiments/gradle-start-script/run.sh` (G1–G5), `docs/research/research-architecture.md`
  (§1.3–§1.5, D1–D3).
