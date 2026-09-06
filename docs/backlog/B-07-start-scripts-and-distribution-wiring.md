---
id: B-07
title: "Проводка: сторож в стартовых скриптах добавляет -XX:AOTCache=$APP_HOME/lib/app.aot, когда кэш есть; кэш и манифест в installDist и distZip"
status: open
priority: P0
size: S/M
stage: stage-2-mvp
blocked_by: [B-05]
---

# B-07 — Проводка в стартовые скрипты и дистрибутив

Путь к кэшу в `-XX:AOTCache` разрешается от текущего каталога (D6 эксперимента), а
`applicationDefaultJvmArgs` не умеет `$APP_HOME`: шаблон Gradle сам говорит, что единственный
способ — постобработка скрипта, и экранирует любые «shell-фрагменты» в `DEFAULT_JVM_OPTS`
(ресёрч §1.4). Безусловный флаг тоже не годится: с ним тренировочный запуск того же скрипта
падает на «Only one of AOTCache or AOTCacheOutput can be specified» (G5). Без этой задачи кэш
лежит в дистрибутиве и не используется.

- **Решение: `startScripts.doLast` вставляет в unix-скрипт перед сборкой аргументов сторож**
  `if [ -f "$APP_HOME/lib/app.aot" ]; then DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS \"-XX:AOTCache=$APP_HOME/lib/app.aot\""; fi`
  (в windows — `if exist "%APP_HOME%\lib\app.aot" …`). Один скрипт для тренировки, проверки и
  прода: до тренировки кэша нет — флага нет; после — есть. Проверено на настоящем скрипте
  Gradle 9.7.1, G1–G4 в `experiments/gradle-start-script/`. Отвергнуто: передавать флаг через
  `JAVA_OPTS` при развёртывании — перекладывает на каждый Dockerfile то, ради чего плагин
  существует. Отвергнуто: второй скрипт `bin/<app>-train` — два скрипта расходятся.
- **`installDist` и `distZip` включают `lib/app.aot` и `lib/app.aot.jars`** и зависят от
  `aotTrain`; `-XX:AOTMode=on` в прод-скрипт по умолчанию **не** ставится — отсутствие кэша
  должно замедлять, а не ронять сервис. Побочная выгода сторожа: без файла нет ни флага, ни трёх
  строк `[error][aot]` в stderr (R16).
- Вопрос: где стоять `-XX:-AOTAdapterCaching` — решается в
  [B-09](B-09-cpu-portability-adapter-caching.md), но место в скрипте резервируется здесь.
- Не покрывает: образ контейнера — [B-10](B-10-docker-and-jib-recipe.md).

- AC: `unzip -p build/distributions/<app>.zip '*/bin/<app>' | grep AOTCache` показывает сторож
  с `$APP_HOME`; распакованный в `/opt/<app>` дистрибутив стартует с `source: shared objects
  file` в `-Xlog:class+load` (G4).
- AC: тот же дистрибутив без `lib/app.aot` стартует без флага и без строк `[error][aot]`.
- AC: `JAVA_OPTS=-XX:AOTMode=on bin/<app>` поверх скрипта с кэшем — код 0 (G3); с
  `touch`-нутым jar на JDK 25.0.4 — код 1.
- Якоря: `experiments/gradle-start-script/build.gradle.kts` (сторож — то, что вставит плагин),
  `experiments/gradle-start-script/run.sh` (G1–G6), `experiments/aot-validation/run.sh` (D1, D6,
  R16), `docs/research/research-architecture.md` (§1.4, D2).
