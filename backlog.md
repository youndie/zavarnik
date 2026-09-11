# Бэклог: zavarnik

> Роль документа: продуктовый бэклог. **Один файл на задачу в [`docs/backlog/`](docs/backlog/)** —
> `B-NN-<slug>.md`. Здесь живёт индекс (генерируется) и всё, что не задача: цель, этапы, решения.
>
> Новая задача: скопировать [`docs/templates/backlog-item.md`](docs/templates/backlog-item.md),
> взять следующий свободный `B-NN`, после правки выполнить `python3 scripts/backlog_index.py`.

## Цель

Сделать так, чтобы обычное JVM-приложение на плагине `application` — Ktor, http4k, CLI —
получало AOT-кэш Project Leyden одной строкой `plugins { id(...) }` и блоком тренировки, и чтобы
сборка **падала**, а не молчала, когда кэш не будет принят в проде. Порядок этапов выведен из
[ресёрча](docs/research/research-architecture.md), §4: сначала стенд, который может закрыть
проект (критерий остановки по RQ6), потом плагин, потом упаковка и выпуск.

## Этапы

Этап — поле задачи, а не каталог. На задачи ссылаются по id из документов всех слоёв, поэтому
перестановка приоритета не должна двигать файл.

| Id этапа | Этап | Что это |
|---|---|---|
| `stage-0-research` | Ресёрч | Что JVM проверяет на самом деле; чем тренировка должна заканчиваться; кто это уже сделал. Закрыт 06.09.2026 — [research-architecture](docs/research/research-architecture.md). |
| `stage-1-stand` | Стенд | Ktor-приложение и замер, который решает, есть ли что публиковать (RQ4, RQ6, JDK 26). Гейт RQ6 пройден 06.09.2026: −59…−63 % ([B-01](docs/backlog/B-01-ktor-stand-and-readiness-timing.md)). |
| `stage-2-mvp` | Плагин | `aotTrain` → `aotVerify` → проводка в дистрибутив; TestKit на каждое условие инвалидации. |
| `stage-3-packaging` | Упаковка | Docker, Jib, переносимость между CPU, отчёт; Windows. |
| `stage-4-release` | Выпуск | Имя, координаты, публикация, четыре недели наблюдения. |
| `stage-5-optimizer` | Вторая фаза | Плагин оптимизации байткода ([бриф](docs/research/source-brief-optimizer.md)): ворота RQ0, потом только то, что прошло порог. |
| `stage-6-crac` | Третья фаза | CRaC — та же тройка задач, снимок прогретого процесса вместо кэша ([бриф](docs/research/source-brief-crac.md), [research-crac](docs/research/research-crac.md)): ворота B-32 на konekt, потом плагин. |
| `stage-7-engines` | Четвёртая фаза | Диспетчер CIO: три движка Ktor под одним протоколом ([research-engines](docs/research/research-engines.md)). Выросла из [B-23](docs/backlog/B-23-dispatcher-spin-hypothesis.md): вывод «свойство CIO» был сделан на единственном движке. |

## Отметки

`[ ]` открыта · `[~]` в работе · `[x]` сделана · `[?]` открытый вопрос · `[-]` снята

<!-- BEGIN INDEX -->

## Open (4)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-33](docs/backlog/B-33-random-after-restore.md) `[ ]` | Одинаковые Random во всех репликах после restore: что затронуто в Kotlin-сервисе и как это ловить | P1 | S | - |
| [B-39](docs/backlog/B-39-verify-before-filing.md) `[ ]` | Перед issue в Ktor: подтвердить механизм без Ktor и перепроверить прибор, которым меряли CPU | P1 | S | B-37 |
| [B-09](docs/backlog/B-09-cpu-portability-adapter-caching.md) `[ ]` | Переносимость кэша между CPU: AOTAdapterCaching включается сам, и кэш несёт машинный код | P3 | M | - |
| [B-13](docs/backlog/B-13-release-v0-1-and-four-week-watch.md) `[ ]` | Выпуск v0.1: Plugin Portal / Central, README с измерением, объявление — и четыре недели наблюдения | P3 | M | B-12 |

## Closed (35)

**Стенд**

- [B-01](docs/backlog/B-01-ktor-stand-and-readiness-timing.md) `[x]` - Стенд: Ktor-приложение и замер времени готовности с кэшем и без (RQ6)
- [B-02](docs/backlog/B-02-kotlin-classes-archived-share.md) `[x]` - Доля классов Kotlin-приложения, пришедших из кэша: indy-лямбды, корутины, сериализация (RQ4)
- [B-03](docs/backlog/B-03-jdk26-on-linux-box.md) `[x]` - JDK 26 на Linux-машине: ZGC (JEP 516) и состояние JDK-8377932 — прогоном, а не чтением

**Плагин**

- [B-04](docs/backlog/B-04-plugin-skeleton-and-toolchain-checks.md) `[x]` - Каркас плагина: расширение zavarnik, проверки тулчейна до первой задачи
- [B-05](docs/backlog/B-05-aot-train-task.md) `[x]` - aotTrain: тренировочный прогон через настоящий стартовый скрипт, готовность, нагрузка, SIGTERM, манифест
- [B-06](docs/backlog/B-06-aot-verify-task.md) `[x]` - aotVerify: запуск с -XX:AOTMode=on, подсчёт источников классов, сверка манифеста — сборка падает с причиной
- [B-07](docs/backlog/B-07-start-scripts-and-distribution-wiring.md) `[x]` - Проводка: сторож в стартовых скриптах добавляет -XX:AOTCache=$APP_HOME/lib/app.aot, когда кэш есть; кэш и манифест в installDist и distZip
- [B-08](docs/backlog/B-08-testkit-invalidation-cases.md) `[x]` - TestKit: каждый случай инвалидации из журнала эксперимента становится тестом плагина
- [B-15](docs/backlog/B-15-ktor-sample-on-the-plugin-in-ci.md) `[x]` - samples/ktor на плагине, прогоняемый в CI: сквозная проверка train → verify → dist

**Упаковка**

- [B-10](docs/backlog/B-10-docker-and-jib-recipe.md) `[x]` - Docker и Jib: рецепт образа, в котором кэш принимается, и aotVerify внутри контейнера
- [B-11](docs/backlog/B-11-aot-report-task.md) `[x]` - aotReport: таблица «холодный / с кэшем» по времени готовности — артефакт для публикации
- [B-14](docs/backlog/B-14-windows-training.md) `[-]` - Тренировка на Windows: без SIGTERM нужен другой способ закончить прогон
- [B-24](docs/backlog/B-24-aot-report-warmup-curve.md) `[x]` - aotReport: показывать не только готовность, но и прогрев — работу JIT после старта
- [B-25](docs/backlog/B-25-train-and-verify-without-gradle.md) `[x]` - Тренировка и проверка без Gradle: раннер в lib/, чтобы JRE-стадия образа тренировала кэш сама
- [B-26](docs/backlog/B-26-jib-and-ktor-plugin-mode.md) `[x]` - Режим для Jib и Ktor-плагина: тренировка в образе без стартового скрипта, кэш вторым слоем
- [B-27](docs/backlog/B-27-workload-headers-and-captures.md) `[x]` - Workload: заголовки запроса и захват значения из ответа — чтобы тренировать за авторизацией
- [B-28](docs/backlog/B-28-pin-jar-mtimes-in-installdist.md) `[x]` - Пинить mtime jar-ов уже в installDist, а не только в aotTrain — образ, обученный в контейнере, иначе отвергает кэш
- [B-29](docs/backlog/B-29-jib-docker-run-args.md) `[x]` - Jib-режим: сеть и окружение стенда для контейнера тренировки — zavarnik { jib { dockerRunArgs() } }
- [B-30](docs/backlog/B-30-assemble-without-training.md) `[x]` - training { onAssemble = false }: assemble и build не тренируют, если приложению негде стартовать
- [B-31](docs/backlog/B-31-refuse-wildcard-classpath.md) `[x]` - Wildcard в CLASSPATH стартового скрипта отказывается: порядок раскрытия разный у разных рантаймов

**Выпуск**

- [B-12](docs/backlog/B-12-name-and-coordinates.md) `[x]` - Имя плагина и координаты Maven: io.github.youndie.zavarnik или website.kotlin.leyden

**Вторая фаза**

- [B-16](docs/backlog/B-16-rq0-gate-profile-split-and-r8.md) `[x]` - RQ0 — ворота второй фазы: доля пользовательского кода в профиле и базовая линия R8
- [B-17](docs/backlog/B-17-r8-invokespecial-rebinding-upstream.md) `[x]` - R8 ломает invokespecial на default-методы интерфейсов Kotlin — issue в трекере R8
- [B-18](docs/backlog/B-18-rq6-inline-bloat-diagnostic.md) `[x]` - RQ6 — диагностика размеров методов против порогов C2: всегда полезна, но проверить, что порог что-то значит
- [B-19](docs/backlog/B-19-rq5-constant-hoisting.md) `[-]` - RQ5 — константа Regex в хендлере: 6,45 % байт, единственный кандидат с весом выше порога
- [B-20](docs/backlog/B-20-rq3-collection-chains.md) `[-]` - RQ3 — промежуточные коллекции цепочек: ≈ 4,6 % байт на /business
- [B-21](docs/backlog/B-21-rq4-lazy-logging-lint.md) `[-]` - RQ4 — ленивое логирование: ≤ 1,57 % байт при трёх debug на запрос — линт, не переписывание
- [B-22](docs/backlog/B-22-rq2-boxing-diagnostic.md) `[-]` - RQ2 — боксинг: 1,26 % байт, ниже красного порога — только диагностика
- [B-23](docs/backlog/B-23-dispatcher-spin-hypothesis.md) `[x]` - 36 % CPU /business — опрос очереди LimitedDispatcher: артефакт закрепления на 8 ядрах или свойство CIO?

**Третья фаза**

- [B-32](docs/backlog/B-32-konekt-hikari-exposed-restore.md) `[x]` - Ворота третьей фазы: konekt (Ktor CIO + HikariCP + Exposed + Postgres) под checkpoint/restore на стенде
- [B-34](docs/backlog/B-34-crac-plugin-form.md) `[x]` - Форма плагина для CRaC: cracCheckpoint / cracVerify / слой снимка поверх того же образа
- [B-35](docs/backlog/B-35-crac-cpu-features.md) `[x]` - Снимок привязан к CPU тренировки: цена и семантика -XX:CPUFeatures на checkpoint

**Четвёртая фаза**

- [B-36](docs/backlog/B-36-engine-stand-and-honest-pinning.md) `[x]` - Стенд на три движка: один процесс, один -D, честное закрепление ядер и цена запроса в абсолюте
- [B-37](docs/backlog/B-37-three-engines-under-one-protocol.md) `[x]` - CIO против Netty и Jetty: профиль, пропускная способность и цена запроса под одним протоколом
- [B-38](docs/backlog/B-38-io-parallelism-lever.md) `[x]` - Рычаг kotlinx.coroutines.io.parallelism: если очередь стоит трети CPU, что её снимает

<!-- END INDEX -->

## Решения, которые не стоит пересматривать

**Стенд блокирует плагин, и это факт, а не пожелание.**
[B-04](docs/backlog/B-04-plugin-skeleton-and-toolchain-checks.md) заблокирована
[B-01](docs/backlog/B-01-ktor-stand-and-readiness-timing.md), потому что бриф назвал RQ6 критерием
остановки: меньше 20 % выигрыша на Ktor — публиковать нечего, и написанный к тому моменту плагин
будет кодом без причины. Последовательность «сначала измерить» здесь не предпочтение, а условие
существования проекта.

**Открытый вопрос — статус, а не застрявшая задача.**
[B-12](docs/backlog/B-12-name-and-coordinates.md) стоит в `question`, потому что ответ — чей:
имя плагина и группа Maven задают адрес, по которому проект будут искать, и его не переименовать
после публикации на Central.

**Вторая фаза закрыта владельцем по критерию остановки 1 (07.09.2026).**
[B-16](docs/backlog/B-16-rq0-gate-profile-split-and-r8.md) — вердикт RQ0: пользовательский код
владеет 1–4 % CPU и 3–10 % аллокаций (стенд и настоящий сервис konekt), R8 на этом стеке не
запускается. B-18 переехала в kapkan (issue в sborka), B-19–B-21 сняты, B-22 снята раньше.
Вопросы о репортах в чужие трекеры решает только владелец: B-17 он 08.09.2026 решил в другую
сторону, чем накануне, и завёл issue в трекере R8
([558351430](https://issuetracker.google.com/issues/558351430)); B-23 остаётся открытым.

**Отложенное остаётся `open` с записанной причиной, а не исчезает.** [B-13](docs/backlog/B-13-release-v0-1-and-four-week-watch.md)
(выпуск) и [B-09](docs/backlog/B-09-cpu-portability-adapter-caching.md) (положительный контроль
`SIGILL`) отложены владельцем 07.09.2026 — статус `open`, приоритет `P3`, в работу не берутся;
Windows (B-14) снят его же решением, а B-17 из снятых вернулся: решение о внешнем репорте
переменилось на следующий день.

**Проверять то, что JVM проверять не будет.**
[B-06](docs/backlog/B-06-aot-verify-task.md) сверяет хэши jar-ов сама, а не полагается на
проверку classpath в HotSpot, потому что на JDK 25.0.0–25.0.3 и 26.0.0–26.0.1 той проверки нет
вовсе (JDK-8377932, ресёрч §1.2): устаревший кэш принимается молча, с кодом выхода 0.
