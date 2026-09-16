---
id: B-40
title: "training { environment(…) }: окружение тренировочного стенда на сборочной машине"
status: done
priority: P1
size: S
stage: stage-3-packaging
---

# B-40 — окружение для тренировочного прогона

`AotTrainTask` запускает установленное приложение и отдаёт ему окружение процесса Gradle — своего
у прогона нет. Приложение, которое читает настройки из окружения и **отказывается стартовать**,
когда обязательной переменной нет (типизированная схема конфигурации; отказ — это и есть фича),
через `check` не тренируется вовсе: процесс выходит с ошибкой конфигурации, URL готовности никто
не отвечает, `aotTrain` ждёт до `readyTimeout`. У Jib-режима это уже есть — `jib { dockerRunArgs }`
([B-29](B-29-jib-docker-run-args.md)), «сеть и окружение стенда»; у обычного пути двери не было.

Обходной путь — `training { onAssemble = false }` ([B-30](B-30-assemble-without-training.md)) —
здесь не подходит: он отдаёт кэш на `check`, то есть ровно то, что делает ворота воротами. А
приложение стартовать на сборочной машине **может**, ему надо только сказать, куда положить файл.

- **Решение:** `training { environment("APP_STORE", "…") }` — `MapProperty<String, String>`,
  вход задач `aotTrain`, `aotVerify` и `aotReport`, применяется к запуску поверх окружения сборки.
  Проверка идёт тем же путём, что тренировка: приложение, отказавшееся без переменной, откажется и
  под `-XX:AOTMode=on`, а `aotVerify` — это и есть гейт на `check`.
- **Значения не уезжают в `zavarnik.properties`** и, стало быть, ни в дистрибутив, ни в образ.
  Это значения сборочной машины: путь, верный здесь, внутри образа неверен, а раннер в контейнере
  и так стоит в окружении, которое контейнеру дали. Отвергнутая альтернатива — писать их в
  properties «чтобы кэш был воспроизводим» — обменивает воспроизводимость на молча неверный путь
  в проде.
- `JAVA_HOME` и `JAVA_OPTS` **отказываются** в `ConfigurationChecks`: их ставит сам запуск через
  `Launch.Script`, значение пользователя было бы молча перезаписано. Флаги JVM живут в
  `zavarnik { jvmArgs(…) }`, который одинаково доходит до тренировки, проверки и прода.
- Задача не покрывает окружение шагов `workload { exec(...) }` — они и так наследуют окружение
  сборки, и своего им никто не просил.

- AC: приложение, падающее без `FIXTURE_STORE`, тренируется и проверяется, когда переменная задана
  в `training { environment(...) }`, и сообщает о своём отказе, когда нет. **Automated:**
  `TrainingEnvironmentFunctionalTest`.
- AC: значение переменной не встречается в `lib/zavarnik.properties` собранного дистрибутива.
  **Automated:** там же.
- AC: изменённое значение заставляет `aotTrain` пройти заново, а не считаться `UP-TO-DATE`.
  **Automated:** там же.
- AC: `training { environment("JAVA_OPTS", …) }` отказывается на конфигурации с указанием на
  `jvmArgs`. **Automated:** `ConfigurationChecksFunctionalTest`.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikExtension.kt`
  (`TrainingSpec.environment`),
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/AotTrainTask.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/AotVerifyTask.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/AotReportTask.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ConfigurationChecks.kt`,
  `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/ApplicationRun.kt`.

Заведена по [issue #13](https://github.com/youndie/zavarnik/issues/13): repro — `youndie/keel`,
сервис с обязательным `KEEL_DB_PATH` в схеме конфигурации.
