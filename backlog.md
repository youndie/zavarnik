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

## Отметки

`[ ]` открыта · `[~]` в работе · `[x]` сделана · `[?]` открытый вопрос · `[-]` снята

<!-- BEGIN INDEX -->

## Open (9)

| Task | | Priority | Size | Blocked by |
|---|---|---|---|---|
| [B-09](docs/backlog/B-09-cpu-portability-adapter-caching.md) `[ ]` | Переносимость кэша между CPU: AOTAdapterCaching включается сам, и кэш несёт машинный код | P1 | M | - |
| [B-13](docs/backlog/B-13-release-v0-1-and-four-week-watch.md) `[ ]` | Выпуск v0.1: Plugin Portal / Central, README с измерением, объявление — и четыре недели наблюдения | P1 | M | B-12 |
| [B-19](docs/backlog/B-19-rq5-constant-hoisting.md) `[ ]` | RQ5 — константа Regex в хендлере: 6,45 % байт, единственный кандидат с весом выше порога | P1 | M | B-16 |
| [B-23](docs/backlog/B-23-dispatcher-spin-hypothesis.md) `[ ]` | 36 % CPU /business — опрос очереди LimitedDispatcher: артефакт закрепления на 8 ядрах или свойство CIO? | P1 | S | - |
| [B-17](docs/backlog/B-17-r8-invokespecial-rebinding-upstream.md) `[?]` | R8 ломает invokespecial на default-методы интерфейсов Kotlin — заводить ли issue в r8 | P2 | XS | - |
| [B-18](docs/backlog/B-18-rq6-inline-bloat-diagnostic.md) `[ ]` | RQ6 — диагностика размеров методов против порогов C2: всегда полезна, но проверить, что порог что-то значит | P2 | S | B-16 |
| [B-20](docs/backlog/B-20-rq3-collection-chains.md) `[ ]` | RQ3 — промежуточные коллекции цепочек: ≈ 4,6 % байт на /business | P2 | L | B-16 |
| [B-14](docs/backlog/B-14-windows-training.md) `[ ]` | Тренировка на Windows: без SIGTERM нужен другой способ закончить прогон | P3 | M | - |
| [B-21](docs/backlog/B-21-rq4-lazy-logging-lint.md) `[ ]` | RQ4 — ленивое логирование: ≤ 1,57 % байт при трёх debug на запрос — линт, не переписывание | P3 | S | B-16 |

## Closed (14)

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

**Выпуск**

- [B-12](docs/backlog/B-12-name-and-coordinates.md) `[x]` - Имя плагина и координаты Maven: io.github.youndie.zavarnik или website.kotlin.leyden

**Вторая фаза**

- [B-16](docs/backlog/B-16-rq0-gate-profile-split-and-r8.md) `[x]` - RQ0 — ворота второй фазы: доля пользовательского кода в профиле и базовая линия R8
- [B-22](docs/backlog/B-22-rq2-boxing-diagnostic.md) `[-]` - RQ2 — боксинг: 1,26 % байт, ниже красного порога — только диагностика

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

**Вторая фаза стоит на решении владельца, а не на бэклоге.**
[B-16](docs/backlog/B-16-rq0-gate-profile-split-and-r8.md) закрыта вердиктом RQ0 (ресёрч
второй фазы, D1): пользовательский код — десятая часть аллокаций и одна пятидесятая CPU, R8
на этом стеке не запускается. Задачи B-18…B-21 формально разблокированы, но строить плагин
про производительность ресёрч не рекомендует; продолжение — линт или закрытие — выбирает
владелец, и до этого выбора в работу идёт только B-23.

**Проверять то, что JVM проверять не будет.**
[B-06](docs/backlog/B-06-aot-verify-task.md) сверяет хэши jar-ов сама, а не полагается на
проверку classpath в HotSpot, потому что на JDK 25.0.0–25.0.3 и 26.0.0–26.0.1 той проверки нет
вовсе (JDK-8377932, ресёрч §1.2): устаревший кэш принимается молча, с кодом выхода 0.
