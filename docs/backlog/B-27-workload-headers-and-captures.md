---
id: B-27
title: "Workload: заголовки запроса и захват значения из ответа — чтобы тренировать за авторизацией"
status: done
priority: P1
size: S
stage: stage-3-packaging
---

# B-27 — Заголовки и захват в workload

Первый настоящий кандидат на плагин — konekt (Ktor CIO, `installDist`, `eclipse-temurin:25-jre`,
`/health`, старт до готовности 5,3–6,7 с на одном ядре по его же `research-measurements.md`).
Его горячий путь — экраны под bearer-токеном, а токен добывается тремя запросами: запрос OTP,
dev-код, verify. У workload не было ни заголовков, ни способа передать значение из одного ответа
в следующий запрос; `exec("curl", …)` в JRE-образе невозможен (curl там нет), а скрипт — второй
язык в конфигурации.

- **Решение:** у `get`/`post` появляется блок `{ header(name, value); capture(variable, path) }`;
  захваченное подставляется в следующие шаги как `{{name}}` — в URL, заголовках, теле и словах
  команды. `{{…}}`, а не `${…}`: в Kotlin-скрипте не требует экранирования. Путь — через точку,
  целочисленный сегмент индексирует массив (`user.id`, `items.0.sku`). JSON читает свой
  восьмидесятистрочный разбор в раннере (`Json.kt`): раннер по-прежнему без зависимостей.
- Незахваченный `{{name}}` — ошибка до отправки запроса с перечнем захваченного; ответ не JSON при
  наличии `capture` — ошибка, а не пустая строка.
- Конфигурация в `zavarnik.properties`: `workload.N.header.<имя>`, `workload.N.capture.<переменная>`.
- ~~Заодно закрыт вопрос про wildcard-classpath `lib/*`~~ **Опровергнуто 08.09.2026 (B-31):**
  прогон 07.09 (W1–W4) показывал стабильный порядок раскрытия на одной файловой системе; в кластере
  k0s containerd раскрыл `lib/*` иначе, чем Docker overlay2 в CI, и JVM отвергла кэш. Теперь
  wildcard в стартовом скрипте — отказ.

- AC: фикстура с `/login` (JSON с токеном) и `/private` (401 без `Authorization` и `X-User`):
  workload с `capture` и `header` тренирует кэш; без заголовка `aotTrain` падает с `answered 401`
  и именем шага. **Automated:** `AotTrainFunctionalTest`.
- AC: `RunnerConfig` переживает раунд-трип с заголовками и захватами; `Workload` подставляет
  значения в URL, заголовок и команду. **Automated:** `RunnerConfigTest`, `WorkloadTest`, `JsonTest`.
- AC: `startScripts { classpath = files("lib/*") }` — `aotTrain` и `aotVerify` зелёные.
  **Automated:** `DistributionFunctionalTest`.
- Якоря: `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/Workload.kt`,
  `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/Json.kt`,
  `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/WorkloadStep.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikExtension.kt`.
- **Эксперимент в konekt сделан 07.09.2026** (ветка `feat/aot-cache-experiment`, задача konekt B-123,
  запись `docs/research/measurements-2026-09-07/aot/` и §6a `research-measurements.md` там же):
  тренировка раннером внутри контейнера стендового образа (приложение не стартует без Postgres),
  кэш вторым слоем, `verify` под `-XX:AOTMode=on` — 5329 из 5332 классов приложения из кэша;
  `coldstart.sh` попеременно по двум образам, по 10 рестартов: готовность **4380 → 2042 мс**,
  первый запрос **510 → 240 мс** по медиане, следующая сотня без изменений; кэш 65 МиБ,
  образ 602 → 685 МБ. Порог 20 % взят с запасом; выпускать ли — решение владельца konekt.
