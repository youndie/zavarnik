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
- Заодно закрыт вопрос про wildcard-classpath `lib/*` (konekt ставит его в стартовый скрипт из-за
  двух `agent-jvm-*.jar` с одним именем): проверено прогоном 07.09.2026 — кэш тренируется,
  принимается в том же каталоге и после переноса, порядок раскрытия в одном каталоге стабилен —
  и закреплено TestKit-тестом.

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
- Дальше — эксперимент в konekt: тренировка на стенде, `coldstart.sh 5` против образа с кэшем и без;
  порог тот же, что был у самого проекта, — меньше 20 % по готовности означает отрицательный результат.
