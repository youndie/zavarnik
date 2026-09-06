---
id: research-architecture
title: zavarnik — архитектурный research
type: research
status: active
date: 2026-09-06
---

# Research: архитектура zavarnik

`zavarnik` — Gradle-плагин, который делает для обычного JVM-приложения на плагине `application`
(Ktor, http4k, CLI) то, что Spring Boot и Quarkus сделали для себя внутри своих сборок: тренирует
AOT-кэш Project Leyden, **проверяет, что прод его примет**, и кладёт его в дистрибутив.
Ниша определяется не прилагательными, а тем, кто уже стоит рядом (§1.7): для Compose Desktop это
есть в официальном плагине JetBrains и в Nucleus, для Spring и Quarkus — в самих фреймворках, для
серверов и CLI на `application` — ничего. Заварник — потому что тренировочный прогон здесь
называется заваркой: настоящий стартовый скрипт, настоящий classpath, кипяток — и кэш готов.

Документ фиксирует **проверенные факты** (прочитанное в исходниках HotSpot и Gradle, в JEP, и
полученное прогоном на этой машине и на Linux-машине — журналы в `experiments/`), **принятые
решения** и **риски**. Всё непроверенное названо гипотезой и имеет адрес, где будет проверено.

Исходный замысел — [source-brief](source-brief.md). Он не совпадает с тем, что здесь: одна его
посылка перевернулась (RQ5, §1.3), одна оказалась верной по выводу и неверной по механизму (RQ2,
§1.1 и §1.5), одна ослабла (RQ7, §1.7), и нашлись две вещи, о которых бриф не спрашивал, — JDK,
который не проверяет jar-ы (§1.2), и кэш, который несёт машинный код тренировочной машины (§1.6).
Каждое расхождение помечено как *отклонение от брифа*.

Все прогоны — 06.09.2026. Машины: мак (Darwin arm64, OpenJDK 25.0.2 и JBR 25.0.4.1) и
Linux-машина (Ubuntu 24.04 x86_64, OpenJDK 25.0.4, Docker 29.1.3, Intel Core Ultra 7 255HX без
AVX-512). Скрипт эксперимента и три журнала: `experiments/aot-validation/`. Случаи ниже
цитируются по меткам из журнала (`R3`, `E2`, `D1`, `G4` …).

---

## 1. Проверенные факты

### 1.1 Что HotSpot проверяет, прежде чем принять кэш

Проверено по исходникам `src/hotspot/share/cds/aotClassLocation.cpp` и `filemap.cpp` на теге
`jdk-25-ga` репозитория `openjdk/jdk` и прогоном `experiments/aot-validation/run.sh` на 25.0.4
(Linux и JBR на маке). На 25.0.2 половина проверок не работает — см. §1.2, там же объяснено,
почему журнал 25.0.2 для этого раздела ничего не доказывает.

| Факт | Где проверено |
|---|---|
| Для каждого jar на app-classpath записываются размер и mtime; при запуске сравниваются оба (`_check_time = !is_jrt`) | `aotClassLocation.cpp` — `AOTClassLocation::allocate`, `AOTClassLocation::check`; журнал R3 «timestamp has changed», R4 «timestamp has changed, size has changed» |
| Имя записи сравнивается через `os::same_files` **после подстановки общего префикса** — поэтому кэш переживает переезд каталога, даже по абсолютным путям и даже когда тренировочный каталог удалён | `aotClassLocation.cpp` — `check_classpaths`, `need_lcp_match`, `find_lcp`; журнал D1 «Longest common prefix substitution … yes», `shared=894 file=0`; G4 — то же на дистрибутиве Gradle |
| Записи **в конец** classpath разрешены (jar, каталог); запись **в начало** — отказ «The name of app classpath [1] does not match» | журнал R5, R7 (`shared>0`), R6 (`shared=0`) |
| `--add-modules` и `-javaagent` (он добавляет `java.instrument`) — «Mismatched values for property jdk.module.addmods» → «AOT cache has aot-linked classes. It cannot be used when archived full module graph is not used» → кэш не маппится целиком | журнал R8, R9 (`shared=0 file=1`) |
| JDWP-агент — та же судьба: «AOT cache has aot-linked classes. It cannot be used with JDWP agent» | paketo-buildpacks/spring-boot#578, 06.01.2026 (цитата строки журнала) |
| Агент, присутствующий **и на тренировке, и на запуске**, кэш не ломает: `App source: shared objects file` на 25.0.2 и 25.0.4. Отказ R9 — про несовпадение модульного графа, а не про агенты как таковые | прогон 06.09.2026 вечером (`-javaagent` в обеих командах, тот же `ag.jar`); TestKit `InvalidationFunctionalTest`, случай R9 |
| GC можно менять (тренировка на G1, запуск на Serial — принят), но кэш, натренированный под G1, под ZGC отвергается на 25 **и на 26.0.2.1**: «The saved state of UseCompressedOops and UseCompressedClassPointers is different from runtime, CDS will be disabled» — граница не «GC», а сжатые указатели, и `-XX:+AOTStreamableObjects` (диагностический, по умолчанию выключен) её не снимает | журналы R10 (`shared>0`), R11 (`shared=0`) на 25.0.4 и 26.0.2.1; `filemap.cpp:2052`; прогон 06.09.2026: streamable-кэш под G1 и Serial — `918`, под ZGC — отказ |
| **Симметричный ZGC работает**: кэш, натренированный под `-XX:+UseZGC`, под ZGC принимается — на 25.0.4 `767` классов из кэша (без архивированной кучи; под G1 — `894`), на 26.0.2.1 — `918`, как под G1 (JEP 516) | прогон 06.09.2026, `hello world`, Linux |
| `-Xmx` менять можно | журнал R12 |
| Сборка JDK сравнивается по строке `_jvm_ident`; другой билд — отказ. JDK 21 флага не знает вовсе («Unrecognized VM option 'AOTCache=…'») | `filemap.cpp:674` — прочитано, прогоном на двух билдах 25 **не** проверено; JDK 21 — прогон |
| Кроме строки сборки сравнивается **размер `$JAVA_HOME/lib/modules`** (запись [0] classpath, mtime у неё не проверяется): у образов `eclipse-temurin:25.0.4_7-jdk` и `-jre` одной сборки он разный, и кэш, натренированный на jdk-образе, jre-образ отвергает — «This file is not the one used while building the AOT cache: '/opt/java/openjdk/lib/modules', size has changed» | `samples/ktor/docker-check.sh`, Linux, Docker 29.1.3, 06.09.2026; `aotClassLocation.cpp` — `check_time = !is_jrt` |
| Отказ по умолчанию **тихий**: три строки `[error][aot]` в stderr и код выхода 0 | журнал R16 |
| `-XX:AOTMode=on` делает отказ фатальным: «Error occurred during initialization of VM / Unable to use AOT cache», код 1 — для ZGC, подменённого jar, отсутствующего файла | журнал R13–R15 (`exit=1`) |
| Каталог на classpath при тренировке — кэша нет: «Error: non-empty directory … Cannot have non-empty directory in paths» | журнал E4; JEP 483 «Class paths must contain only JAR files» |
| `-XX:AOTCache` и `-XX:AOTCacheOutput` вместе — «Only one of AOTCache or AOTCacheOutput can be specified», и `-XX:AOTMode=off` этого не снимает | журнал G5, G6 |
| Относительный путь в `-XX:AOTCache` разрешается от текущего каталога, не от jar | журнал D6 |

**Следствие 1.** Переезд дистрибутива — не проблема (RQ1 зелёный, *отклонение от брифа* в
сторону упрощения: тренировать в финальной раскладке Docker не нужно). Проблема — всё, что
меняет **jar** (mtime, размер) или **командную строку JVM** (порядок classpath, модули, агенты, GC
на 25). Плагин делит это на две группы: что видно на конфигурации (→ D8) и что видно только
прогоном (→ D4).

**Следствие 2.** `aotVerify` не может полагаться на код выхода в режиме по умолчанию (R16) и не
может полагаться только на `-XX:AOTMode=on` (§1.2). Нужен подсчёт по `-Xlog:class+load` — доля
классов приложения с `source: shared objects file` — тот же счётчик, что в функции `summ`
скрипта эксперимента.

**Следствие 3.** «Суперсет» из RQ3 работает только в конец (R5, R7) — это документируется, а не
детектируется: `aotVerify` запускает тот же скрипт, что и прод, и увидит любое отклонение.

**Следствие 5 (06.09.2026, B-10).** «Тот же JDK» означает *тот же образ*, а не ту же версию:
многостадийный Dockerfile тренирует кэш в стадии сборки и переносит `installDist` в стадию
рантайма **на том же самом** `eclipse-temurin:<полный тег>-jdk`. Вариант «сборка на jdk, рантайм
на jre» отвергнут по факту выше; вариант «обе стадии на jre» (Gradle на JRE, только Kotlin-исходники)
не проверен — гипотеза, адрес — [B-10](../backlog/B-10-docker-and-jib-recipe.md), продолжение.
Проверено `docker-check.sh`: контейнер стартует под `-XX:AOTMode=on`, 3816 классов из кэша,
20 из 20 классов `sample.*`; образ 647 МБ.

**Следствие 4 (06.09.2026, B-08).** Агент — не запрет, а требование симметрии: `zavarnik.jvmArgs`
кладёт его в `DEFAULT_JVM_OPTS` для обеих сторон, и кэш принимается. То, что прод добавляет
**сверх** скрипта (агент из `JAVA_OPTS`, `--add-modules`), объявляется в `verify { jvmArgs(…) }`,
и `aotVerify` падает с «Mismatched values for property jdk.module.addmods» до того, как это
увидит прод.

### 1.2 JDK-8377932: на части JDK кэш не проверяется против jar-ов вовсе

На маке с OpenJDK 25.0.2 кэш принимался с `touch`-нутым jar (R3), с **подменённым** jar того же
имени (R4 — класс `App` пришёл из кэша, хотя в jar его больше нет), с jar впереди classpath (R6),
и `-XX:AOTMode=on` при этом давал код 0 (R14). Строка «Archived app classpath validation»
в журнале отсутствует: `need_to_check_app_classpath()` возвращает `false`, потому что при
одношаговом создании (`-XX:AOTCacheOutput`) классы на втором шаге грузятся из конфигурации, а не
через `ClassLoader::record_result`, и `_max_used_index` не обновляется.

| Факт | Где проверено |
|---|---|
| Условие проверки: `num_app_classpaths() > 0 && _max_used_index >= app_cp_start_index() && has_platform_or_app_classes()` | `aotClassLocation.hpp:198–199`, тег `jdk-25-ga` |
| Исправление: `dumptime_update_max_used_index(runtime()->_max_used_index)` при `is_dumping_final_static_archive()` | `openjdk/jdk#29728` «8377932: AOT cache is not rejected when JAR file has changed», влит 16.02.2026 |
| В 25u: коммит `2fb3e9c698`, 16.03.2026; строки нет в `jdk-25.0.2-ga` и `jdk-25.0.3-ga`, есть в `jdk-25.0.4-ga` | `openjdk/jdk25u`, `git log -- src/hotspot/share/cds/aotClassLocation.cpp` |
| В 26u: коммит `3d23e5061d`, 05.03.2026; строки нет в `jdk-26+36` (GA) и `jdk-26.0.1-ga`, есть в `jdk-26.0.2-ga`. На 26.0.2.1 проверка есть **прогоном**: R3 «timestamp has changed», R4, R6 — `shared=0` | `openjdk/jdk26u`; `experiments/aot-validation/results/2026-09-06-linux-x86_64-openjdk-26.0.2.1.log` (B-03) |
| JVM сравнивает mtime **в секундах** (`st_mtime`): на JDK 26 весь харнесс до R3 укладывался в одну секунду с созданием jar, `touch` попадал в ту же секунду, и первый прогон показал «принят». Харнесс теперь ждёт 1,1 с и печатает обе метки | тот же журнал, R3: `mtime before=…538`, `after=…540`; `aotClassLocation.cpp` — `_timestamp != st.st_mtime` |
| На 25.0.4 (Linux и JBR 25.0.4.1 на маке) проверка есть: R3, R4, R6 → `shared=0` | `experiments/aot-validation/results/2026-09-06-linux-x86_64-openjdk-25.0.4.log`, `…-macos-aarch64-jbr-25.0.4.1.log` |

**Следствие 1.** Затронуты 25.0.0–25.0.3 и 26.0.0–26.0.1 — то есть **все** GA-сборки JDK 25
первых семи месяцев и первые полгода JDK 26. Пользователь плагина с таким JDK получит кэш,
который молча переживёт любую пересборку jar-ов: классы старой версии из кэша поверх новых jar.
Это хуже, чем отсутствие кэша, и плагин обязан заметить это сам (→ D4: манифест SHA-256 и
предупреждение по версии тулчейна).

**Следствие 2.** Журнал 25.0.2 для §1.1 — холостой: там, где проверки нет, «принят» ничего не
значит. Это тот случай, когда воспроизводимый результат на одной машине — не механизм; факты §1.1
опираются на 25.0.4 и на исходники.

### 1.3 Чем должна кончаться тренировка

Проверено прогоном на обеих машинах (E1–E3) и чтением Ktor.

| Факт | Где проверено |
|---|---|
| Кэш записывается при `System.exit`, при `Runtime.halt(0)` и при `SIGTERM`; **не** записывается при `SIGKILL` | журнал E1 «written», E2 «written», E3 «not written» |
| Одношаговый режим — два процесса: тренировочная JVM пишет `.aot.config` при выходе, лаунчер запускает вторую («Launching child process … to assemble AOT cache»), и кэш появляется после неё; на `hello world` 0,4–1,1 с | JEP 514; вывод при `JAVA_TOOL_OPTIONS=-XX:AOTCacheOutput=…`; журнал M2 |
| Второй шаг удваивает потребность в памяти: «-Xmx4g … the environment needs 8GB» | JEP 514, раздел о одношаговом режиме |
| Ktor `EmbeddedServer.start(wait = true)` регистрирует JVM shutdown hook, вызывающий `stop()`; отключается свойством `io.ktor.server.engine.ShutdownHook=false` | `ktor-server/ktor-server-core/jvm/src/io/ktor/server/engine/EmbeddedServerJvm.kt` (`start`), `ShutdownHookJvm.kt`, ветка `main` ktorio/ktor |

**Следствие 1 — *отклонение от брифа*.** Бриф считал `Runtime.halt` неприемлемым («cache won't be
written»). Неверно: `.aot.config` пишется в `before_exit` VM, до хуков. Значит, режим «хук внутри
приложения» плагину не нужен, а SIGTERM — не «один из трёх способов», а умолчание: он даёт кэш при
любом коде приложения, а с Ktor — ещё и корректное закрытие HTTP. Остаётся один запасной режим,
`exitAfter`, для приложений без порта (→ D5).

**Следствие 2.** «Процесс приложения завершился» ≠ «кэш готов». `aotTrain` ждёт лаунчер, а не
порт, и задаёт heap тренировки явно (→ [B-05](../backlog/B-05-aot-train-task.md)).

**Следствие 3.** На Windows сигнала нет, `Process.destroy()` — `TerminateProcess`, аналог E3.
Гипотеза «Windows не поддерживается в MVP» подтверждена механизмом, адрес —
[B-14](../backlog/B-14-windows-training.md).

### 1.4 Стартовый скрипт плагина `application`

Проверено по шаблону `org/gradle/api/internal/plugins/unixStartScript.txt` в
`gradle-plugins-application-9.7.1.jar` (дистрибутив Gradle 9.7.1) и сквозным прогоном
`experiments/gradle-start-script/run.sh` (G1–G6, настоящий проект на `application`).

| Факт | Где проверено |
|---|---|
| `APP_HOME` вычисляется абсолютным (`cd -P … && pwd`), classpath собирается как `$APP_HOME/lib/a.jar:$APP_HOME/lib/b.jar` | шаблон, строки 122 и 151; G1 — строка 117 сгенерированного скрипта |
| `DEFAULT_JVM_OPTS` не может нести `$APP_HOME`: «the only way to inject APP_HOME reference into DEFAULT_JVM_OPTS is to post-process the start script; the declaration is a good anchor to do that», «not allowed to contain shell fragments, and any embedded shellness will be escaped» | шаблон, строки 243–244 и 250–253 |
| Скрипт складывает `DEFAULT_JVM_OPTS $JAVA_OPTS $<APP>_OPTS` — окружение добавляет флаги, не переписывая скрипт | шаблон, строка 293; G1 (`JAVA_OPTS=-XX:AOTCacheOutput=…` через скрипт даёт кэш), G3 (`JAVA_OPTS=-XX:AOTMode=on` поверх флага скрипта — код 0) |
| Сторож `if [ -f "$APP_HOME/lib/app.aot" ]; then DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS \"-XX:AOTCache=$APP_HOME/lib/app.aot\""; fi`, вставленный постобработкой, работает: до тренировки флага нет, после — классы из кэша, после переезда всего каталога — тоже | `experiments/gradle-start-script/build.gradle.kts`; G1, G2, G4 |
| Windows-скрипт: `set APP_HOME=%DIRNAME%…`, `set DEFAULT_JVM_OPTS=…`, `set CLASSPATH=…` | `windowsStartScript.txt`, строки 34, 40, 75 — прочитано, прогоном не проверено |
| Каталог-зависимость (`runtimeOnly(files("conf"))`) `installDist` раскладывает **плоско** в `lib/` (файлы рядом с jar-ами), а в `CLASSPATH` оставляет `$APP_HOME/lib/conf` — путь, которого нет. JVM отсутствующую запись терпит, если её нет и при запуске, и кэш тренируется; случай E4 (непустой каталог на classpath) через плагин `application` недостижим | TestKit `InvalidationFunctionalTest`, случай E4, Linux 25.0.4, 06.09.2026 |
| Второй шаг одношагового режима получает свои флаги через `JAVA_TOOL_OPTIONS`: «Picked up JAVA_TOOL_OPTIONS: -Djava.class.path=… -XX:AOTMode=create» в журнале тренировки | тот же тест, `build/zavarnik/aotTrain.log` |

**Следствие.** Один и тот же скрипт — лаунчер тренировки, проверки и прода (→ D1, D2). Без
сторожа это невозможно из-за G5: безусловный `-XX:AOTCache` в скрипте делает `-XX:AOTCacheOutput`
из окружения ошибкой инициализации VM.

### 1.5 Что делает с mtime упаковка

| Факт | Где проверено |
|---|---|
| Docker `COPY` сохраняет mtime файла из контекста сборки | журнал D3 (Docker 29.1.3): «host mtime 946681200 / image mtime 946681200» |
| Jib по умолчанию ставит всем файлам образа `EPOCH_PLUS_SECOND` (1970-01-01T00:00:01Z), свойство `jib.container.filesModificationTime` | README jib-gradle-plugin, GoogleContainerTools/jib |
| jar с mtime, приведённым к константе **до** тренировки, скопированный с сохранением mtime в другой каталог при удалённом тренировочном, — принят | журнал D2 (`shared=894 file=0`) |
| `installDist` (Sync) mtime **не** сохраняет: jar в `build/install` получает время копирования | Linux, Gradle 9.7.1, стенд ktor-readiness: `libs: 1788720469 install: 1788720470` |
| `distZip` пишет DOS-время `1980-02-01 00:00:00` **без extra-полей** (Gradle 9 — воспроизводимые архивы по умолчанию); после `unzip` mtime зависит от часового пояса машины: `318211200` в UTC, `318178800` в Asia/Tokyo | тот же прогон, `unzip -Zv`: «length of extra field: 0 bytes» |
| `distTar` пишет константу `86400` (1970-01-02T00:00:00Z), от пояса не зависит | тот же прогон |
| `touch -t 197001010000.01` читает **местное** время: на Linux-машине дал `-3599` | тот же прогон; в плагине — `FileTime.fromMillis(86_400_000)`, в харнессе — `TZ=UTC` |

**Следствие — *отклонение от брифа* по механизму, не по выводу.** RQ2 «красный» (mtime
проверяется), но красная ветка брифа — «плагин должен владеть шагом упаковки» — не нужна: плагин
владеет **mtime до тренировки**. Нормализация к константе Jib делает Jib-образ правильным без
единой настройки, а Docker `COPY` — правильным, потому что он ничего не меняет (→ D3). Что
остаётся на пользователе: не пересобирать jar-ы между `aotTrain` и упаковкой — это ловит
манифест (D4).

**Следствие 2 (найдено 06.09.2026 при B-05).** Архивы дистрибутива — отдельная история. `distZip`
не может нести валидный кэш: формат zip хранит DOS-время в местном поясе, Gradle не пишет extra-поле
с epoch, и распакованный на другой машине jar получает другой mtime — а 1970 в DOS-времени вообще
не выражается. `distTar` может — но не через `preserveFileTimestamps = true`, как здесь было записано сначала:
tar берёт jar-ы из `build/libs` и кэша Gradle с их собственными mtime, а не из `installDist`.
Работает обратное: воспроизводимый tar ставит всем записям `86400` с, и если `aotTrain` нормализует
jar-ы к той же константе, распакованный tar совпадает с кэшем без единой настройки (D3, правка).
Решение — [B-07](../backlog/B-07-start-scripts-and-distribution-wiring.md): кэш и манифест в
`distTar`, в `distZip` — не класть и предупредить; Docker `COPY` из `installDist` — как в D3.
Проверено TestKit-тестом `DistributionFunctionalTest` на JDK 25.0.4: распакованный tar, mtime jar-ов
`86400`, манифест сходится.

### 1.6 Кэш несёт машинный код, и это включается само

| Факт | Где проверено |
|---|---|
| `AOTAdapterCaching` — диагностический флаг, по умолчанию `false` | `-XX:+PrintFlagsFinal` без кэша, 25.0.2 и 25.0.4 |
| С `-XX:AOTCache` и на втором шаге `-XX:AOTCacheOutput` он становится `true {ergonomic}`; кэш маппит регион `(Code)`; «Loaded 326 AOT code entries from AOT Code Cache» | `-XX:AOTCache=… -XX:+PrintFlagsFinal`, `-Xlog:aot+codecache*=info`, `-Xlog:aot=debug` («Mapped static region #4 … (Code)»), Linux 25.0.4 |
| `AOTStubCaching` на 25 остаётся `false` | там же |
| `-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching` на тренировке **убирает регион кода**: нет «Mapped static region #4 … (Code)», нет «Loaded … AOT code entries», кэш `hello world` 10,43 МБ вместо 10,87; классов из кэша столько же (894). Такой кэш принимается и JVM без флага | прогон 06.09.2026, Linux 25.0.4 |
| Цена переносимости на образце Ktor: медиана готовности 173 мс с флагом против 174 мс без, 10 прогонов на вариант; кэш 32,3 МБ против 33,0 | `./gradlew -p samples/ktor aotReport -Pzavarnik.runs=10 [-PnativeCode]`, Linux 25.0.4, 06.09.2026 |
| Кэш, натренированный на CPU с AVX-512, падает `SIGILL` в `~AdapterBlob` на CPU без него; обход — `-XX:UseAVX=2` на тренировке | статья coffeesprout.nl о Quarkus на Red Hat 25.0.3+9; Nucleus, issue #400 и флаг `-XX:-AOTAdapterCaching` в `AbstractGenerateAotCacheTask.kt` |
| Linux-машина — без AVX-512 (`/proc/cpuinfo`, `UseAVX=2`) | прогон |
| **Не воспроизведено на паре EPYC Genoa (`UseAVX=3`, AVX-512) → Core Ultra 7 255HX (`UseAVX=2`)**, тот же билд `25.0.4+7-1-24.04-Ubuntu`: нативные кэши hello world (326 записей AOT-кода) и образца Ktor (495) загружаются под `-XX:AOTMode=on` и работают, 20 из 20 прогонов, 20 из 20 классов из кэша | `experiments/cpu-portability/results/`, 06.09.2026 |
| В HotSpot 25.0.4 загрузчик AOT-кода **не проверяет набор инструкций CPU**: в `aotCodeCache.cpp` есть проверки GC, сжатых указателей и версии, но не CPU. В mainline (→ JDK 27) появился `Config::verify_cpu_features` с сообщением «AOT Code Cache disabled: cpu features are incompatible» | `openjdk/jdk25u` `jdk-25.0.4-ga` и `openjdk/jdk` `master`, `src/hotspot/share/code/aotCodeCache.cpp` |
| Отчёты о `SIGILL`: Nucleus #400 — тренировка на GitHub CI, падение на Core2 Quad Q9550 (без AVX) и на Ryzen 5 3500X, Windows 11, Nucleus 2.0.5; Quarkus — Red Hat 25.0.3, GitHub-раннер → CPU без AVX-512 | github.com/NucleusFramework/Nucleus/issues/400; coffeesprout.nl |

**Следствие.** Сценарий плагина — тренировка в CI, запуск где угодно — ровно тот, в котором это
стреляет. Само падение здесь **не воспроизведено** (нужны два разных CPU); гипотеза-решение — D6,
адрес — [B-09](../backlog/B-09-cpu-portability-adapter-caching.md).

### 1.7 Кто это уже сделал (RQ7)

Проверено 06.09.2026. Пустые результаты снабжены положительным контролем — той же командой с
запросом, который обязан что-то вернуть.

| Что | Результат | Где проверено |
|---|---|---|
| Gradle Plugin Portal: «leyden», «aot cache», «zavarnik» | «No plugins found» | `plugins.gradle.org/search?term=…` |
| Issues gradle/gradle: «leyden», «AOT cache» | пусто; контроль «configuration cache» — есть | `gh search issues … --repo gradle/gradle` |
| YouTrack KTOR: «leyden», «AOT» | `[]`; контроль «graalvm» — три issue | `youtrack.jetbrains.com/api/issues?query=project: KTOR …` |
| GitHub, код `AOTCacheOutput` на Kotlin | 17 файлов: JetBrains/compose-multiplatform, NucleusFramework/Nucleus, sproctor/potassium (форк того же), rock3r/indexino (личный), остальное — скрипты | `gh api search/code` |
| JetBrains Compose Gradle plugin: `AotMode.AotPrebuild` — `-XX:AOTCacheOutput=$APPDIR/app.aot`, свойство `compose.aot.training-run=true`, приложение обязано выйти само; раскладка jpackage | `gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/dsl/AotSettings.kt` |
| Nucleus (359 звёзд, активен): `enableAotCache`, JDK ≥ 25, «The application **must** self-terminate … `System.exit(0)`», таймаут безопасности 300 с и `destroyForcibly`, умолчание `-XX:-AOTAdapterCaching` («COMPATIBILITY») | `plugin-build/plugin/src/main/kotlin/dev/nucleusframework/desktop/application/tasks/AbstractGenerateAotCacheTask.kt` |
| Spring Framework: `-XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh`; «The timestamps of the JARs must be preserved»; «Additional JARs or directories can be specified at the end»; проверка по `-Xlog:class+load` → `source: shared objects file` | docs.spring.io, integration/aot-cache |
| Quarkus: `aot-jar` + интеграционные тесты как тренировка, `./mvnw verify -Dquarkus.package.jar.aot.enabled=true` | quarkus.io/blog/leyden-2 |
| Репозитории «leyden» на GitHub: анализаторы и бенчмарки (Delawen/leyden-analyzer, simonis/LeydenVsGraalNative, shipilev/leyden-perf), плагинов сборки нет | `gh search repos leyden` |
| Leyden: JEP 483 (24), 514, 515 (25), 516 (26) — delivered; «Ahead-of-Time Code Compilation» 8335368 — in progress, без целевого релиза | openjdk.org/projects/leyden |

**Следствие — *отклонение от брифа*.** «Everything else has nothing» верно наполовину: для
**desktop** (jpackage, `createDistributable`) решение есть у самого JetBrains, и оно
устроено так же, как задумано здесь (тренировка → `$APPDIR/app.aot` → `-XX:AOTCache`). Для
**серверов и CLI на `application`** — пусто, и это уже отдельная раскладка (`lib/*.jar` +
`bin/<app>`), не jpackage. RQ7 не красный, но ниша уже, чем в брифе, и DSL плагина обязан не
выглядеть копией `compose.desktop.application` (→ D9).

### 1.8 Инструменты и версии

| Факт | Где проверено |
|---|---|
| Текущий Gradle — 9.7.1; Java 25 требует Gradle ≥ 9.1.0, Java 26 — ≥ 9.4.0 (запуск и тулчейны) | docs.gradle.org/current/userguide/compatibility.html |
| JDK 26 — GA, 26.0.2.1, есть Linux/x64 и macOS/aarch64 | jdk.java.net/26 |
| С Kotlin 2.0.0 лямбды генерируются через `invokedynamic`; `-Xlambdas=class` возвращает классы | kotlinlang.org/docs/whatsnew20.html |
| Мак: OpenJDK 25.0.2 (`/Users/youndie/Library/Java/JavaVirtualMachines/openjdk-25.0.2`), JBR 25.0.4.1 (`~/.gradle/jdks/jetbrains_s_r_o_-25-aarch64-os_x.2`), Corretto 21; Gradle-дистрибутивы 9.5.1–9.7.1 в `~/.gradle/wrapper/dists` | `/usr/libexec/java_home -V`, `ls` |
| Linux-машина: OpenJDK 25.0.4 (`/usr/lib/jvm/java-25-openjdk-amd64`), 21; OpenJDK 26.0.2.1 в `~/jdks/jdk-26.0.2.1` (поставлен 06.09.2026, B-03); Docker 29.1.3; 20 ядер | ssh, `java -version`, `docker version` |
| Доля классов из кэша на Ktor-стенде (RQ4): 2322 из 2322 классов `stand.*`, `io.ktor.*`, `kotlinx.*`, `kotlin.*`; всего 3824 из 3837 (13 непопавших — классы JDK) — по строкам `source:` в `-Xlog:class+load`, один прогон с нагрузкой | `experiments/ktor-readiness/results/2026-09-06-linux-x86_64-openjdk-25.0.4-run2.log`, T1 |
| **Hidden-классы лямбд (`$$Lambda/0x…`) архивируются**: на образце под indy (умолчание Kotlin 2.x) загружено 269 lambda-прокси, **269 из кэша**; с `-Xlambdas=class` — 265 прокси (библиотечные), все из кэша, и 2264 из 2264 jar-классов (против 2262 под indy). Разницы между режимами нет, флаг компилятора не нужен — RQ4 закрыт | `./gradlew -p samples/ktor aotVerify [-PlambdasClass] --rerun-tasks`, `build/zavarnik/aotVerify.log`, Linux 25.0.4, 06.09.2026 (B-02) |

### 1.9 Что измерено — и что это (не) значит

**Ktor-стенд (B-01, критерий остановки RQ6).** `experiments/ktor-readiness/`: Ktor 3.5.2 на CIO,
kotlinx.serialization, три маршрута, 26 jar-ов, кэш 33,8 МБ. Время от запуска стартового скрипта
до первого `200` на `/health`, снаружи процесса, 20 прогонов на вариант, медиана — среднее 10-го
и 11-го значения. Linux-машина, OpenJDK 25.0.4; тренировка — SIGTERM после 40 запросов.

| Вариант | Без кэша, мс | С кэшем, мс | Медиана меньше на |
|---|---|---|---|
| SerialGC, прогон 2 | 568 (ряд 526–616, выброс 2260) | 211 | 63 % |
| G1, прогон 2 | 796 (ряд 631–1022) | 326 | 59 % |
| SerialGC, прогон 3 | 653 (ряд 539–738) | 203 | 69 % |
| G1, прогон 3 | 688 (ряд 611–833, выброс 2404) | 221 | 68 % |

Журналы: `experiments/ktor-readiness/results/2026-09-06-linux-x86_64-openjdk-25.0.4-run2.log`,
`…-run3.log`. Ряды без кэша гуляют между прогонами на 100–250 мс (машина не изолирована —
на ней же крутятся другие сессии), ряды с кэшем стабильны в пределах 40 мс; отношение держится.
Первый прогон того же дня (журнал не сохранился — реплика стёрла его во время прогона, см.
README стенда) дал 562 → 184 (SerialGC) и 554 → 210 (G1): ряды G1 между прогонами разошлись
почти на 250 мс без кэша, при том что отношение осталось тем же. **Вывод для гейта: оба GC, оба
прогона — больше 40 %, зелёный порог брифа взят; RQ6 закрыт, риск 3 снят.** Что в число не
входит: время до первого ответа маршрута с JIT-прогревом (JEP 515 — профили методов) не
мерилось; ZGC и JDK 26 — [B-03](../backlog/B-03-jdk26-on-linux-box.md).

**Положительный контроль стенда — `hello world`** (один класс, `java.util.stream` и `HashMap`), время всего процесса `java` от
запуска до выхода, 20 прогонов подряд, отсортированные ряды в журналах (`M1`), медиана — среднее
10-го и 11-го значения.

**Образец на плагине** (`samples/ktor`, B-15, Linux 25.0.4, 06.09.2026): готовность на
тренировке 537 мс, нагрузка 65 мс, кэш 31,7 МБ, манифест 26 jar-ов; `aotVerify`: 2262 из 2262
классов приложения (100 %) из кэша, 3823 из 3837 всего. Числа из журнала `./gradlew -p samples/ktor
check`; время готовности **с** кэшем плагин пока не мерит — это `aotReport`
([B-11](../backlog/B-11-aot-report-task.md)).

| Машина, JDK | Без кэша (`-XX:AOTMode=off`), мс | С кэшем, мс | Размер кэша |
|---|---|---|---|
| Linux x86_64, OpenJDK 25.0.4 | 143 (ряд 124–153, один выброс 2107) | 47 | 10,9 МБ |
| Linux x86_64, OpenJDK 26.0.2.1 | 61 (ряд 60–67) | 21 | 11,0 МБ |
| macOS arm64, OpenJDK 25.0.2 | 73 | 33 | 10,8 МБ |
| macOS arm64, JBR 25.0.4.1 | 98 | 37 | — |

Это **положительный контроль стенда**, не ответ на RQ6: программа тривиальна, кэш почти целиком
состоит из классов JDK, и число ничего не говорит о Ktor. Выброс 2107 мс в ряду без кэша — первый
прогон после простоя; он и есть причина брать медиану, а не среднее. Демонстрационный проект
Gradle с gson дал кэш 12,6 МБ (G1) — размер растёт с classpath, и это стоит показывать в
`aotReport` рядом с временем.

---

## 2. Решения

### D1. Тренировка идёт через настоящий стартовый скрипт `installDist`, не через `JavaExec`

Первая идея (бриф): «runs the `installDist` layout» — без уточнения, чем.
Решение: `aotTrain` запускает `build/install/<app>/bin/<app>` с `JAVA_OPTS=-XX:AOTCacheOutput=…`.

Почему:

- каталоги на classpath кэш не дают (E4) — `run` и любой `JavaExec` на `build/classes` отпадают;
- строка classpath в кэше сравнивается с прод-строкой (§1.1); единственный способ получить
  ту же строку в том же порядке — тот же скрипт;
- цена: `aotTrain` зависит от `installDist`, и наследует все причуды скрипта (порядок
  `JAVA_OPTS`, `xargs`-разбор). Проверено сквозным прогоном G1–G4.

### D2. `-XX:AOTCache` попадает в скрипт постобработкой и под сторожем «файл существует»

Первая идея (бриф): «`startScripts` gets `-XX:AOTCache=$APP_HOME/lib/app.aot`».
Решение: то же, но условно — сторож из §1.4.

Почему:

- `$APP_HOME` в `DEFAULT_JVM_OPTS` иначе не выразить — так говорит сам шаблон Gradle;
- безусловный флаг ломает D1: `AOTCache` + `AOTCacheOutput` — ошибка VM (G5, G6), и второй
  скрипт «для тренировки» расходился бы с первым;
- без файла — без флага: нет ни замедления, ни трёх строк `[error][aot]` (R16); отсутствие кэша
  в проде замедляет, а не роняет;
- цена: два места правки (unix, windows) и зависимость от текста шаблона — TestKit на
  сгенерированный скрипт обязателен ([B-08](../backlog/B-08-testkit-invalidation-cases.md)).

### D3. mtime jar-ов нормализуются до тренировки к константе

Решение (первая редакция, 06.09.2026 утром): перед стартом `aotTrain` ставит всем `lib/*.jar`
mtime, равный умолчанию Jib (`EPOCH_PLUS_SECOND`, `1970-01-01T00:00:01Z`).

**Правка при реализации B-05/B-07 (06.09.2026 вечером):** константа — **`1970-01-02T00:00:00Z`**
(`86400` с), та, которой Gradle штампует каждую запись воспроизводимого tar
(`TarCopyAction.CONSTANT_TIME_FOR_TAR_ENTRIES = 86400000`, умолчание с Gradle 9). Причина в §1.5,
следствие 2: `distTar` не может взять mtime из `installDist` — он копирует jar-ы из `build/libs` и
кэша Gradle, а не из установленного каталога, — поэтому единственный способ сделать tar валидным без
настроек — тренировать на той же константе, которую tar поставит сам. Docker `COPY` при этом
по-прежнему сохраняет mtime, а Jib теряет «работает без настроек»: ему нужна одна строка
`jib.container.filesModificationTime = "1970-01-02T00:00:00Z"` — и это ещё не проверено вместе с
раскладкой Jib (`/app/libs`, другой порядок classpath), см. [B-10](../backlog/B-10-docker-and-jib-recipe.md).

Почему:

- HotSpot проверяет mtime (§1.1), Jib переписывает его (§1.5), Docker `COPY` сохраняет (D3);
  единственная константа, при которой правы все трое, — константа Jib;
- отвергнуто: «плагин владеет упаковкой» (красная ветка брифа) — плагин образов не строит;
  отвергнуто: `--mtime` у пользователя — правило, которое каждый второй забудет;
- цена: jar в `build/install` меняет mtime, и `installDist` может счесть его устаревшим — задача
  делает это *после* `installDist` и объявляет выходы явно.

### D4. Проверка — тройная, и хэши считает плагин

Решение: `aotVerify` красный, если (1) запуск скрипта с `-XX:AOTMode=on` вернул не 0, (2) доля
классов приложения из кэша ниже порога, (3) SHA-256 jar-ов не совпадает с `lib/app.aot.jars`,
который написал `aotTrain`. Плюс предупреждение на конфигурации при JDK 25.0.0–25.0.3 и
26.0.0–26.0.1.

Почему:

- по умолчанию отказ тихий (R16), поэтому (1) нужен `AOTMode=on`;
- на JDK с JDK-8377932 и `AOTMode=on` лжёт (R14 в журнале 25.0.2), поэтому (3);
- (1) и (3) молчат, если кэш принят, но пуст для приложения (плохая тренировка) — поэтому (2),
  тем же счётчиком, что стенд;
- цена: манифест — ещё один файл в `lib/`, и порог (2) — число, которое надо будет подобрать по
  [B-02](../backlog/B-02-kotlin-classes-archived-share.md).

### D5. Тренировка заканчивается SIGTERM; `exitAfter` — запасной режим; хука в приложении нет *(отклонение от брифа)*

Бриф: три режима на выбор, `halt` неприемлем.
Решение: готовность по URL → нагрузка → SIGTERM → ждать лаунчер; `exitAfter` для приложений без
порта; таймаут безопасности → `destroyForcibly` **и ошибка задачи**.

Почему: §1.3 — кэш переживает всё, кроме SIGKILL; Ktor на SIGTERM закрывается штатно; хук в
приложении — строка кода, которой бриф хотел избежать, и Compose с Nucleus показывают, во что она
превращается (свойство, `LaunchedEffect`, `System.exit`). Цена: Windows остаётся без умолчания
([B-14](../backlog/B-14-windows-training.md)).

### D6. По умолчанию `-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching` на тренировке и в проде

Почему: §1.6 — регион кода включается сам, собран под тренировочный CPU, а сценарий плагина —
разные машины. Nucleus пришёл к тому же умолчанию после issue #400. Отвергнуто: `-XX:UseAVX=2` —
только x86 и только потолок.

**Что проверено 06.09.2026 (B-09):** флаг действительно убирает регион кода из кэша, а цена на
образце Ktor неизмерима — 173 против 174 мс медианы готовности (§1.6). На паре Genoa (AVX-512) →
Core Ultra (AVX2) с одним билдом JDK падение **не воспроизвелось**: нативные кэши загружаются и
работают. Это отрицательный результат без положительного контроля — ни одна доступная машина не
падает, — и загрузчик 25.0.4 набор инструкций не проверяет вовсе (§1.6), то есть на другой паре
(Core2 без AVX, Ryzen 3500X из отчётов) ничего не мешает упасть. Умолчание `portability = true`
остаётся: оно ничего не стоит и снимает риск, который JVM до 27 не ловит сама.

### D7. GC не пинуется; ZGC на JDK < 26 — предупреждение

Первая редакция: «ZGC на JDK < 26 — ошибка конфигурации», по R11.
**Правка при B-03 (06.09.2026):** R11 показывал кэш, натренированный под G1 и запущенный под
ZGC. Симметричный случай — ZGC с обеих сторон — работает и на 25 (767 классов из кэша, без
архивированной кучи), и на 26 (918, как G1). Плагин кладёт `jvmArgs` в `DEFAULT_JVM_OPTS` для
обеих сторон, то есть симметрию делает сам, поэтому ошибка заменена предупреждением о меньшем
выигрыше до JEP 516. Смена GC между тренировкой и запуском по-прежнему допустима, кроме перехода
через границу сжатых указателей (ZGC ↔ остальные) — её не снимает и `AOTStreamableObjects` на 26.
Бриф просил «pin the same GC» — не нужно. Пин остаётся один: **тот же билд JDK** (`_jvm_ident`
и размер `lib/modules`), и это проверяется прогоном `aotVerify`, а не текстом.

### D8. Что проверяется на конфигурации, до первого запуска

JDK тулчейна < 25 — ошибка (одношаговый режим — JEP 514); ZGC при < 26 — предупреждение (D7, правка); плагин
`application` не применён — ошибка (нет раскладки jar-ов); JDK с JDK-8377932 — предупреждение с
номером бага. Почему: всё это известно до `installDist`, и ошибка на конфигурации дешевле ошибки
после тренировки.

### D9. Область: Gradle, плагин `application`, DSL `zavarnik { }`

```kotlin
plugins {
    application
    id("io.github.youndie.zavarnik")          // B-12, решено 06.09.2026
}

zavarnik {
    jvmArgs("-XX:+UseSerialGC")               // попадает в скрипт: и тренировка, и прод
    portability = true                        // D6; false — нативный код тренировочной машины
    training {
        readyWhen.url("http://localhost:8080/health")
        workload { exec("curl", "-s", "http://localhost:8080/api/warm") }
        // exitAfter = 15.seconds — для приложений без порта
    }
    verify {
        minCachedShare = 0.9                  // D4 (2); число подберёт B-02
    }
}
```

Задачи: `aotTrain`, `aotVerify` (в `check`), `aotReport`. Maven — вне области (бриф, §8); jpackage
— вне области (там уже JetBrains). Почему `zavarnik`, а не `leyden`: имя чужого проекта в id
плагина — вопрос к владельцу (открытый вопрос 1).

---

## 3. Риски и открытые вопросы

**Риск 1. Кэш падает `SIGILL` на другом CPU.** Механизм: §1.6. Митигация: D6 — включена по
умолчанию, убирает регион кода (проверено) и не стоит времени готовности (измерено). Само
падение на паре AVX-512 → AVX2 не воспроизведено, положительного контроля нет
([B-09](../backlog/B-09-cpu-portability-adapter-caching.md)); риск остаётся закрытым митигацией,
а не опровергнутым.

**Риск 2. Пользователь на JDK 25.0.0–25.0.3 / 26.0.0–26.0.1 получает устаревший кэш молча.**
Механизм: §1.2. Митигация: D4 (3) и предупреждение D8. Не закрывается: если пользователь
пересобрал jar и **не** запустил `aotVerify`, JVM его не спасёт — поэтому `aotVerify` в `check`
по умолчанию.

**Риск 3 — снят 06.09.2026.** Выигрыш на Ktor: 59–63 % по медиане готовности на двух GC (§1.9),
порог остановки — 20 %, зелёный — 40 %. Гейт [B-01](../backlog/B-01-ktor-stand-and-readiness-timing.md)
пройден, `stage-2-mvp` разблокирован.

**Риск 4 — снят 06.09.2026.** Hidden-классы лямбд архивируются: 269 из 269 прокси из кэша под
indy, разницы с `-Xlambdas=class` нет (§1.8, [B-02](../backlog/B-02-kotlin-classes-archived-share.md)).

**Риск 5. Windows.** §1.3, следствие 3. Митигация: не поддерживать в MVP, сказать это в README;
[B-14](../backlog/B-14-windows-training.md).

**Риск 6. Второй шаг тренировки не помещается в память CI.** JEP 514 удваивает `-Xmx`.
Митигация: `aotTrain` задаёт heap тренировки явно и пишет обе цифры в журнал задачи.

**Риск 7. Сторож в скрипте ломается при смене шаблона Gradle.** D2 опирается на текст
`unixStartScript.txt`. Митигация: TestKit генерирует скрипт и запускает его
([B-08](../backlog/B-08-testkit-invalidation-cases.md)); якорь замены — строка `# Collect all
arguments for the java command:`, а не номер строки.

**Открытый вопрос 1 — закрыт 06.09.2026.** Имя и координаты: `io.github.youndie.zavarnik`,
группа `io.github.youndie`, модуль `zavarnik-gradle-plugin` (прецедент — viddik; `website.kotlin.leyden`
из брифа отвергнут — чужое имя проекта в id). Решение владельца — [B-12](../backlog/B-12-name-and-coordinates.md).

**Открытый вопрос 2. `-XX:AOTMode=on` в прод-скрипте?** Сейчас — нет (D2): отсутствие кэша
замедляет, а не роняет. Compose делает это опцией `exitAppOnAotFailure`. Вернуться после первого
внешнего пользователя.

**Открытый вопрос 3 — закрыт 06.09.2026.** JDK 26.0.2.1 прогнан ([B-03](../backlog/B-03-jdk26-on-linux-box.md)):
проверка jar-ов есть, симметричный ZGC архивирует кучу, несимметричный отвергается (§1.1, §1.2).

---

## 4. Что дальше

Порядок работы и критерии приёмки — в [backlog.md](../../backlog.md). Гейт пройден 06.09.2026:
стенд на Ktor ([B-01](../backlog/B-01-ktor-stand-and-readiness-timing.md)) дал 59–63 %, имя решено
([B-12](../backlog/B-12-name-and-coordinates.md)). Дальше — каркас плагина
([B-04](../backlog/B-04-plugin-skeleton-and-toolchain-checks.md)) и `aotTrain`
([B-05](../backlog/B-05-aot-train-task.md)); стенд `experiments/ktor-readiness/app` — первый
кандидат в `samples/ktor` ([B-15](../backlog/B-15-ktor-sample-on-the-plugin-in-ci.md)).

Когда появится код, слои `features/` и `services/` заводятся в тех же PR, `status: draft` до
слияния; этот документ правится в месте расхождения, а не переписывается.
