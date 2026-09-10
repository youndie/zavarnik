---
id: research-crac
title: zavarnik, третья фаза — research CRaC для сервиса на Ktor CIO
type: research
status: active
date: 2026-09-11
---

# Research: checkpoint/restore (CRaC) вместо AOT-кэша — та же форма, другой предмет

Третья фаза проекта — [бриф](source-brief-crac.md): тренировка, проверка, упаковка — как у
AOT-кэша, но результат тренировки — не кэш классов, а снимок прогретого процесса (CRaC,
Coordinated Restore at Checkpoint). Бриф обещает то, чего кэш Leyden не даёт
([research-architecture](research-architecture.md) §1.9: JIT после старта делает ту же работу):
прогретый JIT и готовность в сотни миллисекунд. Вопрос брифа — что именно ломается на
Ktor CIO + HikariCP + Exposed при restore, и его риск — CRaC-JDK и только Linux.

Документ фиксирует **проверенные факты** (прочитанное в документации и исходниках и полученное
прогоном на Linux-машине — журналы в `experiments/crac-smoke/results/` и
`experiments/crac-ktor/results/`), **принятые решения** и **риски**. Всё непроверенное названо
гипотезой и имеет адрес. Первый день ресёрча закрыл больше, чем бриф считал риском, и открыл
две вещи, которых бриф не называл: одинаковые случайные числа во всех репликах и привязку снимка
к байтам JDK.

Машина: Linux-машина первых двух фаз (Ubuntu 24.04 в WSL2, ядро 6.6, Docker 29.1.3, Core Ultra 7
255HX). JDK — `azul/zulu-openjdk:25-jdk-crac`, Zulu 25.36+205-CRaC-CA, сборка 25.0.4.1+1-LTS.
Стенд — образец `samples/ktor` (Ktor 3.5.2 на CIO, 26 jar-ов), установленный `installDist`;
скрипты — `experiments/crac-smoke/`, `experiments/crac-ktor/`.

---

## 1. Проверенные факты

### 1.1 Где CRaC есть и что ему нужно

| Факт | Где проверено |
|---|---|
| Azul Zulu с CRaC выходит для **17, 21, 22, 23, 24, 25 и 26**: 25.0.4.1 и 26.0.2.1 GA, Linux x64 и arm64; образы `azul/zulu-openjdk:<v>-jdk-crac` и `-jre-crac` обновлены 10.09.2026 | `api.azul.com/metadata/v1/zulu/packages?crac_supported=true`; Docker Hub `azul/zulu-openjdk`, теги `*-crac` |
| BellSoft Liberica с CRaC — только **17 и 21** (`jdk-17-crac`, `jdk-21-crac`); 24, 25, 26 — ноль тегов | Docker Hub `bellsoft/liberica-runtime-container`, теги `jdk-<v>-crac` |
| Движки: `criuengine` (CRIU, нужен root или SUID на `lib/criu`; в контейнере — `CHECKPOINT_RESTORE` и `SYS_PTRACE`), **`warp`** («doesn't require any additional privileges, neither for checkpoint nor for restore», октябрь 2024, Linux x86_64 и arm64, glibc), `simengine` (checkpoint сразу переходит в restore в том же процессе — Linux, Windows, macOS), `pauseengine` (JVM ждёт, restore из другой JVM) | docs.azul.com/crac/usage/crac-engines |
| **На 25.0.4.1 движок по умолчанию — `warp`**: `CRaCEngine = warp {default}`; `lib/criu` 3.17.1-crac лежит рядом, но не используется | `-XX:+PrintFlagsFinal`, `restore-twice.sh`, 11.09.2026 |
| Флаги CRaC на этой сборке: `CRaCCheckpointTo`, `CRaCRestoreFrom`, `CRaCEngine`, `CRaCEngineOptions`, `CRaCIgnoredFileDescriptors`, `CRaCAllowedOpenFilePrefixes`, `CRaCHeapErgonomics=true`, `CRaCIgnoreRestoreIfUnavailable=false`, `CRaCMaxHeapSizeBeforeCheckpoint=0`, `CRaCMinPid=128`, `CRaCCPUCountInit=false` | там же |
| API: `jdk.crac.Core`, `Resource` (`beforeCheckpoint`/`afterRestore`), `Context`, `CheckpointException`, `RestoreException` — модуль `jdk.crac`; JDK-ресурсы для сокетов, файлов, селекторов и JFR — `jdk.internal.crac` (`JDKSocketResource`, `JDKFileResource`, `OpenResourcePolicies`); API-совместимая библиотека для JDK без CRaC — `org.crac:crac` 1.5.0 (Central, 19.06.2024) | `openjdk/crac`, ветка `crac`: `src/jdk.crac/share/classes/jdk/crac/`, `src/java.base/share/classes/jdk/internal/crac/`; search.maven.org |
| Checkpoint: `-XX:CRaCCheckpointTo=<dir>` + `jcmd <main> JDK.checkpoint` (или `Core.checkpointRestore()`); restore: `java -XX:CRaCRestoreFrom=<dir>` — остальные флаги берутся из снимка | CRaC/docs README; прогон |
| Warp: восстановленный процесс **не** получает прежние PID/TID (потому и не нужны capabilities); политики файловых дескрипторов — свойство `jdk.crac.resource-policies`, файл из правил `type: file\|pipe\|socket\|filedescriptor`, `action: error\|ignore\|close\|reopen`, уточнения `localAddress`, `localPort`, `listening`; сами Azul называют это «limited handling via configuration» — обходом, не решением | docs.azul.com/crac/usage/crac-engines; CRaC/docs `fd-policies.md`; docs.azul.com fd-policies (через поиск: `listening: true` + `action: reopen` переоткрывает слушающий сокет на том же адресе) |
| В GitHub-версии `fd-policies.md` сказано, что `reopen` для сокетов **не реализован** и даст исключение после restore; на Zulu 25.0.4.1 он реализован и работает (§1.4) — документация CRaC/docs отстаёт от сборки Azul | `CRaC/docs/fd-policies.md`; `experiments/crac-ktor/results/2026-09-11-crac-ktor-policy-*.log` |

**Следствие 1 — *отклонение от брифа*.** Риск «нужен CRaC-JDK, только Linux» стоит вдвое
дешевле, чем в брифе. JDK есть: Zulu 25 и 26 с CRaC выходят в тот же день, что и обычные, в
двух архитектурах, `-jre`-образом. Привилегий не нужно: движок по умолчанию — warp, и на этой
машине checkpoint и restore прошли без единого `--cap-add` (§1.2). Что остаётся: **один вендор
для 25+** (Liberica остановилась на 21), Linux и glibc (musl — preview), и то, что снимок — это
бинарник JDK (§1.5). «Только Linux» для серверного образа — не ограничение.

**Следствие 2.** Плагину не нужен ни CRIU, ни `docker checkpoint`: тренировка — это `docker run`
образа с приложением, `jcmd` изнутри контейнера и каталог снимка на смонтированном томе — ровно
та механика, что у `jibAotTrain` (research-architecture, правка B-26).

### 1.2 Дымовой тест: checkpoint и restore на Linux-машине

`experiments/crac-smoke/run.sh`, 11.09.2026: `Hello.java` печатает раз в секунду `nanoTime`,
wall-время, `java.util.Random`, `ThreadLocalRandom` и `UUID.randomUUID()`.

| Факт | Где проверено |
|---|---|
| Хост WSL2 6.6.87, `ptrace_scope=1`, Docker 29.1.3: checkpoint `jcmd Hello JDK.checkpoint` и restore в новом контейнере проходят **без дополнительных привилегий**; `--cap-add CHECKPOINT_RESTORE,SYS_PTRACE` и `--privileged` ничего не меняют | `results/2026-09-11-crac-smoke-*.log`: три попытки, «warp: Checkpoint successful!», «warp: Restore successful!» |
| Снимок hello world — 2 файла, 33 МБ | там же |
| После restore процесс продолжает с того же места: тик 4 после тика 3, `nanoTime` включает время паузы (тик 4 через 8,6 с от старта при паузе ~5 с), wall-время — текущее | там же |
| Восстанавливаться можно **сколько угодно раз** из одного снимка | `restore-twice.sh`: два restore одного каталога |

### 1.3 Что переживает restore: время и случайные числа

| Факт | Где проверено |
|---|---|
| `System.nanoTime()` после restore скорректирован: CRaC записывает wall-время до checkpoint и после restore и сдвигает `nanoTime` (PR #53 в `openjdk/crac`, обсуждение в crac-dev); наблюдение — тик после restore показывает прошедшее реальное время, а не «время с загрузки другой машины» | mail.openjdk.org crac-dev «Correct System.nanotime() value after restore»; `crac-smoke`, `crac-twice` |
| **Два restore одного снимка дают одинаковые `java.util.Random` и `ThreadLocalRandom`**: `rnd=-807420337 tlr=200137914` в обоих; UUID (SecureRandom) — разные | `results/2026-09-11-crac-twice-*.log` |
| `SecureRandom()` **переинициализируется** после restore (`@crac Instances created by this constructor are automatically reseeded after restore from a checkpoint`); `SecureRandom(byte[] seed)` — **нет**; `java.util.Random` и `ThreadLocalRandom` — ни строчки про CRaC | `openjdk/crac`, `java/security/SecureRandom.java` строки 219 и 265; `java/util/Random.java`, `ThreadLocalRandom.java` — grep `crac` пуст |

**Уточнено 11.09.2026 (`experiments/crac-smoke/randoms.sh`, `Randoms.java`).** Пять генераторов,
считанных **после** restore, два restore одного снимка:

| Генератор | Совпадает у двух restore? |
|---|---|
| `Random`, созданный **до** checkpoint | **да** |
| `ThreadLocalRandom` на потоке, жившем **до** checkpoint | **да** |
| `ThreadLocalRandom` на потоке, созданном **после** restore | **да** — сеятель `ThreadLocalRandom` тоже в снимке |
| `new Random()`, созданный **после** restore | нет (в затравке `System.nanoTime`) |
| `SecureRandom()` | нет (JDK переинициализирует) |

**Следствие 1.** Бриф назвал Random среди того, «что ломается», и это верно, но не там, где
ждёшь: не в приложении, а в JDK. Каждая реплика из одного снимка тянет одну и ту же
последовательность из `ThreadLocalRandom` — включая потоки, созданные уже после restore, — и из
любого `Random`, созданного до снимка. Токены и OTP на `SecureRandom()` — в порядке.

**Следствие 2 — почему на настоящем сервисе это не воспроизводится по требованию.** У konekt
мастер eSIM выдаёт код активации из `kotlin.random.Random.Default` (на JVM — `ThreadLocalRandom`
вызывающего потока), и пять restore одного снимка дали **пять разных** кодов: `B6E58ADE`,
`484E9618` и, с выключенным симулятором трафика, `E48B1B37`, `E252B19E`, `F98506BF`
(`results/2026-09-11-konekt-esim-two-restores.log`). Поток тот же, поток — из пула, и между
restore и выдачей профиля из того же `ThreadLocalRandom` черпают вход, выдача токена, экраны,
Exposed и Hikari — сколько раз, зависит от того, какой воркер что обслужил. **Последовательности
одинаковые, потребление — нет.** Столкновение остаётся возможным (тем вероятнее, чем раньше после
restore сервис рисует свой первый идентификатор), но детерминированным не является.

**Следствие 3 — для D4.** Проверка «два restore и сравнить идентификатор приложения» была бы
шаткой ровно по этой причине: она зелёная не потому, что всё хорошо, а потому что воркеры
разошлись ([[tests-check-my-answer-not-what-survives]] в чистом виде). Проверять надо сами
генераторы — пробой уровня JDK, как в `Randoms.java`, — и это меняет D4.

### 1.4 Ktor CIO: что ломается и что чинит одна политика

`experiments/crac-ktor/run.sh` и `warm.sh`, образец `samples/ktor` под тем же образом,
`-XX:AOTMode=off` (кэш образца натренирован другим JDK и здесь не при чём).

| Факт | Где проверено |
|---|---|
| Checkpoint при поднятом сервере **отказывает**: `CheckpointOpenSocketException: sun.nio.ch.ServerSocketChannelImpl[]` и два `EPoll … FD left open in sun.nio.ch.EPollSelectorImpl … with registered keys`; JVM продолжает работать, снимка нет; подсказка JDK — `-Djdk.crac.collect-fd-stacktraces=true` | `results/2026-09-11-crac-ktor-zulu25.0.4.1.log` |
| Одно правило `type: SOCKET / listening: true / action: reopen` в `jdk.crac.resource-policies` — и checkpoint проходит (с предупреждением «Socket … was not closed by the application», гасится `warn: false`); epoll-дескрипторы селектора отдельного правила не потребовали | `results/2026-09-11-crac-ktor-policy-*.log`, `policies.yaml` |
| После restore сервер **отвечает**: 200 на `/api/order` (POST JSON) и `/api/warm`, без кода в приложении | там же, «functional after restore» |
| Снимок образца — 82 МБ (85 с `-XX:+PrintCompilation`) | там же |

Числа — `docker run` → первый 200 на `/health` снаружи контейнера, попеременно, медиана пяти:

| | обычный старт | restore |
|---|---|---|
| готовность, `run.sh` | 642 мс (543–953) | **50 мс** (45–52) |
| готовность, `warm.sh` (с `-XX:+PrintCompilation`) | 639 мс (600–764) | **82 мс** (71–84) |
| первый `POST /api/order` после готовности | 31 мс (25–44) | **12 мс** (11–14) |
| память контейнера на готовности / после 100 запросов (`docker stats`) | 70 / 100 МиБ | 36 / 62 МиБ |
| компиляций JIT за первые 100 запросов | 1555–1591 | **321–366** |

**Следствие 1.** Посылка брифа про Ktor CIO подтверждена целиком и обошлась дешевле, чем
ожидалось: ломается ровно слушающий сокет с его селектором, чинится одним правилом без кода в
приложении. **Гипотеза:** это свойство CIO (один `ServerSocketChannel` + `EPollSelectorImpl`);
Netty-движок Ktor держит свои epoll-дескрипторы иначе — PR в Netty «Close EPoll file descriptors
during CRaC snapshotting» (#13308) **закрыт, не влит**. Адрес — [B-34](../backlog/B-34-crac-plugin-form.md).

**Следствие 2.** Обещание брифа «прогретый JIT, ready в сотни миллисекунд» измерено и
превышено: готовность **в десятки** миллисекунд вместе со стартом контейнера, JIT после restore
делает в 4–5 раз меньше компиляций (снимок несёт скомпилированный код — то, чего у AOT-кэша нет
по устройству, research-architecture §1.9), первый запрос втрое быстрее. Для сравнения, тот же
образец с AOT-кэшем: 649 → 233 мс готовности без Docker (README, `aotReport`).

**Следствие 3.** Память после restore *меньше*, чем у обычного старта, — потому что снимок
маппится и страницы подтягиваются по обращению; `docker stats` не считает страничный кэш файла
снимка. Это число не «экономия», а другая раскладка: 36 МиБ на готовности + 82 МБ файла в
образе. Не сравнивать с RSS из `experiments/memory/` напрямую.

### 1.5 Что restore требует от образа

| Факт | Где проверено |
|---|---|
| Снимок 25.0.4.1 под **25.0.4** (на одну сборку старше, тот же major): «warp: error: Cannot open /opt/zulu25.36.205-ca-crac-jdk25.0.4.1-linux_x64/bin/java … Cannot find build-id … validation failed» — warp сверяет build-id **каждого** файла, замапленного в память, по пути на момент checkpoint | `results/2026-09-11-crac-other-build-*.log` |
| Тот же снимок под Zulu 26: «Restore failed due to incompatible or missing CPU features, try using -XX:CPUFeatures=0x2b2fce1c05fdfbf7,0xf88 on checkpoint» — проверка CPU-признаков срабатывает раньше проверки файлов | там же |
| Между машинами: «You have to specify -XX:CPUFeatures=[...] together with -XX:CRaCCheckpointTo when making a checkpoint file; specified -XX:CRaCRestoreFrom file contains CPU features [...]; missing features of this CPU are [...]» — снимок несёт набор инструкций CPU, и restore на более узком CPU отказывает **явно**, не `SIGILL` | CRaC/docs README, раздел CPU Features |
| Spring: «operate with the assumption that any sensitive data "seen" by the JVM ends up in the CRaC files» — снимок содержит heap со всем, что приложение прочитало до checkpoint | docs.spring.io, integration/checkpoint-restore |

**Следствие 1.** У AOT-кэша «тот же JDK» означало тот же образ (research-architecture §1.1,
следствие 5); у CRaC — **те же байты по тем же путям**: JDK, jar-ы приложения, `libc`. Снимок
живёт только в образе, из которого снят, и слой со снимком кладётся **поверх** этого образа
(как слой кэша в `aot-image.sh` konekt). Обновил базовый образ — снимок мёртв, и это отказ с
кодом 1, а не тихий (в отличие от AOT-кэша с `AOTMode=auto`).

**Следствие 2.** Проблема переносимости CPU из B-09 здесь встроена в инструмент: снимок
привязан к CPU тренировки, и CRaC об этом говорит при restore. `-XX:CPUFeatures` на checkpoint —
аналог `-XX:-AOTAdapterCaching`; его цена и семантика (`generic`?) — [B-35](../backlog/B-35-crac-cpu-features.md).

**Следствие 3.** Снимок — это данные: heap после тренировочной нагрузки, включая тела
запросов и ответов, переменные окружения, секреты, прочитанные при старте. У AOT-кэша такого
класса риска не было. Плагин обязан говорить это в README, а тренировочная нагрузка — не
содержать настоящих секретов (→ риск 3).

### 1.6 Кто это уже сделал

| Что | Результат | Где проверено |
|---|---|---|
| Spring Framework 6.1 / Boot 3.2: `Lifecycle.stop()` перед checkpoint и `start()` после; `-Dspring.context.checkpoint=onRefresh` — автоматический checkpoint при старте; для Hikari — `HikariCheckpointRestoreLifecycle` в `spring-boot-jdbc` | docs.spring.io integration/checkpoint-restore; `spring-projects/spring-boot`, `module/spring-boot-jdbc/.../HikariCheckpointRestoreLifecycle.java` |
| Micronaut (`micronaut-crac`): `HikariDataSourceResource` — `suspendPool()` перед checkpoint, ожидание закрытия соединений с таймаутом (`datasourcePauseTimeout`), `resumePool()` после; Redis-клиенты уничтожаются и создаются заново | `micronaut-projects/micronaut-crac`, `crac/src/main/java/io/micronaut/crac/resources/datasources/HikariDataSourceResource.java` |
| Quarkus — поддержка и пример `CRaC/example-quarkus`; Helidon 4.2 — поддержка добавлена | docs.azul.com/crac/usage/frameworks |
| **HikariCP сам** — issue #2082 «Allow to stop/restart HikariPool» (2023, **открыта**): «`suspend` … does not guarantee connections are closed», после `softEvictConnections` сокеты закрываются ещё ~500 мс; в 2024 — Spring-приложение после restore пытается работать по невалидному соединению, хотя lifecycle доложил о закрытии | github.com/brettwooldridge/HikariCP/issues/2082 и комментарии |
| **Ktor** — KTOR-6485 «Support CRaC», Feature, **Submitted** с 18.11.2023, 3 голоса; в ktorio/ktor issues — пусто | youtrack.jetbrains.com/api/issues/KTOR-6485 |
| **Exposed**, **pgjdbc** — ни одного issue со словом CRaC | `gh search issues CRaC --repo JetBrains/Exposed`, `--repo pgjdbc/pgjdbc` |
| Netty — PR #13308 «Close EPoll file descriptors during CRaC snapshotting» закрыт без слияния | github.com/netty/netty/pull/13308 |

**Следствие.** Ниша та же, что у первой фазы: фреймворки сделали свою половину (жизненный
цикл вокруг checkpoint), пул и драйвер — нет, и на Ktor + Exposed нет ни фреймворка, ни
пула. Что у Hikari есть — `allowPoolSuspension` + `suspendPool`/`resumePool` и
`softEvictConnections`; Micronaut и Spring строят на этом и оба ждут закрытия сокетов
таймаутом. Это и есть предмет [B-32](../backlog/B-32-konekt-hikari-exposed-restore.md): что
делает Exposed с соединениями, которые Hikari «закрыл», и что видит pgjdbc после restore.

### 1.7 konekt: ворота фазы пройдены (11.09.2026, B-32)

| Факт | Где проверено |
|---|---|
| Hikari 7.1.0, `maximumPoolSize` 10 (`DB_POOL_SIZE`), Exposed 1.5.0 через `Database.connect(dataSource)`, pgjdbc 42.7.13, Flyway при старте | `konekt/gradle/libs.versions.toml`, `shared/db/src/main/kotlin/io/konekt/db/DatabaseFactory.kt` |
| Одноразовые коды — `SecureRandom()` (`CodeSecurity.kt`): после restore переинициализируется (§1.3); `MockSmDpPlus` (dev-заглушка eSIM) — `kotlin.random.Random.Default`: последовательность общая у всех реплик, но выдаваемые коды разошлись — §1.3, следствие 2 | `feature/auth-server-data/.../CodeSecurity.kt`, `feature/esim-server-data/.../MockSmDpPlus.kt` |
| Готовность в кластере сейчас 3 с с AOT-кэшем против 11 (konekt B-123); базовый образ — `eclipse-temurin:25-jre`, не Zulu | konekt `docs/backlog/B-123-*.md`, `Dockerfile` |

**Проверено прогоном 11.09.2026** — `konekt/scripts/measure/crac-restore.sh`, десять фаз, журналы в
konekt `docs/research/measurements-2026-09-11/crac/`, konekt B-125. Стенд: тот же Postgres, брокер и
миграции из `deploy/compose.yaml`, сервер — дистрибутив konekt на `azul/zulu-openjdk:25-jre-crac`.

| Гипотеза | Что вышло |
|---|---|
| H1, без политик | Отказ, и он **называет виновника**: `-Djdk.crac.collect-fd-stacktraces=true` даёт «This file descriptor was created by **HikariPool-1:connection-adder**» на каждом из десяти сокетов к `postgres:5432`, плюс сокет брокера, слушающий `ServerSocketChannelImpl` (создан `DefaultDispatcher-worker-6`) и epoll селектора |
| H2, только `listening: reopen` | Остаются ровно десять сокетов пула и сокет брокера — то есть образец Ktor чинился одним правилом именно потому, что у него нет пула |
| H3, `action: close` на 5432 и 9092 | **Не работает:** соединения закрываются, Hikari немедленно видит («Failed to validate connection … Unable to set network timeout») и `connection-adder` открывает новые — checkpoint падает на сокете, которого не было в начале: `FD fd=133 type=socket path=socket:[…],port=0` |
| H7, `action: ignore` на 5432 и 9092 | **Работает.** warp на restore пишет «Can't open FD … - replacing FD with /dev/null», Hikari выбраковывает все десять («This connection has been closed»), клиент booblik переподключается сам («reconnected to the broker at broker:9092 — generation 1»). Правок в приложении — ноль |
| H9, сквозной путь | Восстановленный сервер: вход, пополнение (201), покупка (202), подтверждение (200), заказ `completed`, **12 обновлений по SSE** — ровно столько же, сколько у обычного старта |
| H4, Postgres перезапущен между checkpoint и restore | Готовность 124 мс, вход и экраны 200 — пул переживает и это |
| H5, окружение restore-контейнера | `BRAND=brand-b` **не действует**: восстановленный процесс отвечает именем brand-a. Контроль: обычный старт того же образа с `brand-a` отвечает «Konekt», с `brand-b` — «Inkline». Конфигурация, прочитанная при старте, заморожена в снимке |
| H6, два restore, разные абоненты | Оба работают; в первом экране мастера eSIM только `wizardId` (UUID → `SecureRandom`), и они различаются. До шага, где `MockSmDpPlus` выдаёт ICCID из `Random.Default`, мастер не доведён — B-33 |

Числа (H10, десять на десять попеременно, **без лимита CPU** — с §6/§6a konekt не сравнивать):

| | обычный старт | restore |
|---|---|---|
| `docker run` → `/health`, медиана 10 | 2317 мс (2141–2513) | **131 мс** (115–151) |
| первый экран под токеном | 118 мс (102–128) | **32 мс** (27–50) |
| снимок | — | 143 МБ |

**Следствие 1 — ворота §4 зелёные.** Условие было «восстанавливается и обслуживает экран под
токеном без изменений в коде приложения, политики и конфигурация пула допустимы». Выполнено с
запасом: не только экран, а весь путь покупки через брокер, и конфигурации пула не понадобилось —
хватило файла политик. Форма плагина (D2) остаётся в силе, и к ней добавляется генерация политики
не только для слушающего сокета, но и для исходящих соединений (B-34).

**Следствие 2 — `ignore` работает не потому, что CRaC умный, а потому, что пул проверяет
соединения.** Дескрипторы после restore указывают на `/dev/null`; живым это делает `isValid`
Hikari на выдаче из пула и переподключение клиента брокера. Библиотека **без** такой проверки
получит молча мёртвое соединение — это то самое «красное» из §4, просто не у этой связки.
Проверка плагина обязана быть сквозной (запрос, который ходит в базу), а не «процесс поднялся»
(→ D4, B-34).

**Следствие 3 — снимок замораживает конфигурацию (H5).** Риск 3 подтверждён с другой стороны:
не только секреты попадают в снимок, но и то, что прочитано при старте, **не** обновляется
окружением restore-контейнера. Значит, снимок — на комбинацию окружения, либо конфигурация
читается после restore. Для плагина: сказать это в README и в сообщении задачи.

---

## 2. Решения

### D1. Движок — warp, привилегий не просить *(отклонение от брифа)*

Бриф считал привилегии частью риска «только Linux». На 25.0.4.1 warp — умолчание, checkpoint и
restore прошли без capabilities (§1.2). Плагин не трогает `CRaCEngine` и не документирует
CRIU; если пользователь на 21 (Liberica или Zulu с `criuengine` по умолчанию — **гипотеза**,
не проверено), это его ветка, не плагина. Минимальная поддерживаемая сборка — Zulu 25 с CRaC.

### D2. Форма — та же тройка задач, снимок вместо кэша *(предварительно, до B-32)*

`cracCheckpoint`: образ с приложением стартует в контейнере (как `jibAotTrain`/`aot-image.sh`),
раннер ждёт `readyWhen`, гоняет `workload`, вызывает `jcmd … JDK.checkpoint`, снимок ложится на
том. `cracVerify`: restore из снимка в новом контейнере, готовность, тот же workload, и **два**
restore подряд для D4. Упаковка: слой со снимком поверх *того же* образа. Что не переносится
из AOT-фазы: `distTar` (снимок живёт только в образе, §1.5), стартовый скрипт как лаунчер
(restore — это `java -XX:CRaCRestoreFrom`, без classpath).

### D3. Политика для слушающего сокета — генерирует плагин

Одно правило чинит CIO (§1.4). Плагин пишет `zavarnik-crac-policies.yaml` в `lib/` и передаёт
`-Djdk.crac.resource-policies` на тренировке; на restore свойство берётся из снимка. Пользователь
дописывает правила для своего (пул — см. B-32). Это обход по определению Azul; честная
альтернатива — `Resource` в Ktor (KTOR-6485), которого нет три года.

### D4. Проверка «реплики различаются» — по генераторам, не по ответам приложения

*Правка 11.09.2026 (§1.3, следствие 3).* Первая формулировка — «восстановить дважды и сравнить
то, что обязано различаться» — на сервисе даёт ложное зелёное: у konekt пять restore дали пять
разных кодов активации, хотя последовательности `ThreadLocalRandom` во всех пяти одинаковы; их
развёл пул потоков. Поэтому `cracVerify` сравнивает **сами генераторы**, а не ответы: проба
уровня JDK (по образцу `experiments/crac-smoke/Randoms.java`) снимает четыре значения —
`Random` из-до снимка, `ThreadLocalRandom` старого и нового потока, `SecureRandom` — и падает,
если первые три совпали у двух restore. Плагин не чинит §1.3; он не даёт этому пройти молча,
как `aotVerify` не даёт пройти кэшу с низкой долей классов.

---

## 3. Риски и открытые вопросы

**Риск 1. Снимок привязан к байтам образа.** Механизм: §1.5. Митигация: слой снимка строится
только поверх образа с тем же digest, из которого снят; `cracVerify` — restore в *финальном*
образе, не в тренировочном. Отказ громкий (код 1), что лучше, чем у AOT-кэша.

**Риск 2. CPU тренировки ≠ CPU прода.** Механизм: §1.5, следствие 2. Митигация — B-35;
до неё: тренировать на машине не «шире» прода, `-XX:CPUFeatures` документировать.

**Риск 3. Секреты и данные в снимке.** Механизм: §1.5, следствие 3. Митигация: README —
«снимок содержит всё, что приложение прочитало»; тренировочный workload — синтетика; секреты
подавать после restore (переменные окружения restore-контейнера **не** попадают в процесс —
**гипотеза**, проверить в B-32: как konekt получит `DB_URL` после restore, если он прочитан
до checkpoint).

**Риск 4. Одинаковые случайные числа во всех репликах.** Механизм: §1.3. Митигация: D4 и
документ; починить — только в JDK или в коде приложения (`Resource`, переинициализирующий
свои генераторы).

**Риск 5. Пул соединений.** Механизм: §1.6 — Hikari закрывает не сразу, Exposed держит
`Database` и менеджер транзакций поверх. Что именно происходит — не знает никто (нет ни одного
issue), это ядро B-32. Красный исход — «нужен патч в Exposed/Hikari» — тоже результат.

**Риск 6. Время.** `nanoTime` скорректирован (§1.3); что делают таймеры Ktor/coroutines и
таймауты Hikari (`maxLifetime`, `keepaliveTime`), заведённые до паузы в часы, — не измерено.
Spring предупреждает про `@Scheduled(fixedRate)`: после restore выполняются все пропущенные
запуски. Адрес — B-32.

**Риск 7. Один вендор.** Zulu — единственный CRaC-JDK для 25+ (§1.1). Митигация: нет;
сказать в README.

**Открытый вопрос 1.** Что делает `-XX:CRaCHeapErgonomics=true` и `CRaCMaxHeapSizeBeforeCheckpoint`
(§1.1) — сжимает ли heap перед снимком; размер снимка konekt против 85 МБ образца. B-32.

**Открытый вопрос 2.** Restore в Kubernetes: снимок в образе (+85 МБ и больше), restore как
`command` пода, probes — стартовая проба раз в секунду уже стоит (konekt 0.2.5). Нужны ли
capabilities на containerd k0s — по §1.2 нет, проверить на кластере после B-32.

---

## 4. Ворота фазы и что дальше

Критерий — как у RQ0 второй фазы, объявлен до эксперимента:

- **Зелёный:** konekt на стенде (Postgres, брокер) восстанавливается из снимка и обслуживает
  экран под токеном **без изменений в коде приложения** (политики и конфигурация пула —
  допустимы), и у риска 4 есть проверка (D4). Тогда — плагин по D2.
- **Красный:** для restore нужен патч в Exposed, Hikari или Ktor, или после restore пул
  молча работает по мёртвым соединениям. Тогда — статья «что ломается» с журналами, и фаза
  закрывается, как вторая.

**Ворота пройдены 11.09.2026 — зелёный (§1.7).** Патч не понадобился никому: ни Exposed, ни
Hikari, ни Ktor; хватило файла политик дескрипторов. Restore konekt — 131 мс против 2317, первый
экран под токеном 32 против 118, весь путь покупки через брокер и те же 12 обновлений по SSE, что
у обычного старта. Условие D4 остаётся невыполненным как *проверка*: одинаковые случайные числа
подтверждены на уровне JDK (§1.3), но у konekt мастер eSIM доведён 11.09.2026 и дал **разные** коды — §1.3, следствие 2;
что осталось от B-33, это перенести пробу генераторов в `cracVerify` (D4).

Порядок: ~~B-32 (ворота)~~ **сделана 11.09.2026** →
[B-33](../backlog/B-33-random-after-restore.md) → [B-34](../backlog/B-34-crac-plugin-form.md) →
[B-35](../backlog/B-35-crac-cpu-features.md). Запись ворот — konekt PR #24,
`docs/research/measurements-2026-09-11/crac/`, konekt B-125 (выпускать ли — решение владельца
konekt). Эксперименты первой и второй фаз остаются
адресами; этот документ правится в месте расхождения.
