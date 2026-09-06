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
| GC можно менять (тренировка на G1, запуск на Serial — принят), но ZGC на JDK 25 — отказ «The saved state of UseCompressedOops and UseCompressedClassPointers is different from runtime, CDS will be disabled» | журнал R10 (`shared>0`), R11 (`shared=0`); `filemap.cpp:2052` |
| `-Xmx` менять можно | журнал R12 |
| Сборка JDK сравнивается по строке `_jvm_ident`; другой билд — отказ. JDK 21 флага не знает вовсе («Unrecognized VM option 'AOTCache=…'») | `filemap.cpp:674` — прочитано, прогоном на двух билдах 25 **не** проверено; JDK 21 — прогон |
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
| В 26u: коммит `3d23e5061d`, 05.03.2026; строки нет в `jdk-26+36` (GA) и `jdk-26.0.1-ga`, есть в `jdk-26.0.2-ga` | `openjdk/jdk26u` — по исходникам, прогоном **не** проверено ([B-03](../backlog/B-03-jdk26-on-linux-box.md)) |
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

**Следствие.** Один и тот же скрипт — лаунчер тренировки, проверки и прода (→ D1, D2). Без
сторожа это невозможно из-за G5: безусловный `-XX:AOTCache` в скрипте делает `-XX:AOTCacheOutput`
из окружения ошибкой инициализации VM.

### 1.5 Что делает с mtime упаковка

| Факт | Где проверено |
|---|---|
| Docker `COPY` сохраняет mtime файла из контекста сборки | журнал D3 (Docker 29.1.3): «host mtime 946681200 / image mtime 946681200» |
| Jib по умолчанию ставит всем файлам образа `EPOCH_PLUS_SECOND` (1970-01-01T00:00:01Z), свойство `jib.container.filesModificationTime` | README jib-gradle-plugin, GoogleContainerTools/jib |
| jar с mtime, приведённым к константе **до** тренировки, скопированный с сохранением mtime в другой каталог при удалённом тренировочном, — принят | журнал D2 (`shared=894 file=0`) |

**Следствие — *отклонение от брифа* по механизму, не по выводу.** RQ2 «красный» (mtime
проверяется), но красная ветка брифа — «плагин должен владеть шагом упаковки» — не нужна: плагин
владеет **mtime до тренировки**. Нормализация к константе Jib делает Jib-образ правильным без
единой настройки, а Docker `COPY` — правильным, потому что он ничего не меняет (→ D3). Что
остаётся на пользователе: не пересобирать jar-ы между `aotTrain` и упаковкой — это ловит
манифест (D4).

### 1.6 Кэш несёт машинный код, и это включается само

| Факт | Где проверено |
|---|---|
| `AOTAdapterCaching` — диагностический флаг, по умолчанию `false` | `-XX:+PrintFlagsFinal` без кэша, 25.0.2 и 25.0.4 |
| С `-XX:AOTCache` и на втором шаге `-XX:AOTCacheOutput` он становится `true {ergonomic}`; кэш маппит регион `(Code)`; «Loaded 326 AOT code entries from AOT Code Cache» | `-XX:AOTCache=… -XX:+PrintFlagsFinal`, `-Xlog:aot+codecache*=info`, `-Xlog:aot=debug` («Mapped static region #4 … (Code)»), Linux 25.0.4 |
| `AOTStubCaching` на 25 остаётся `false` | там же |
| Кэш, натренированный на CPU с AVX-512, падает `SIGILL` в `~AdapterBlob` на CPU без него; обход — `-XX:UseAVX=2` на тренировке | статья coffeesprout.nl о Quarkus на Red Hat 25.0.3+9; Nucleus, issue #400 и флаг `-XX:-AOTAdapterCaching` в `AbstractGenerateAotCacheTask.kt` |
| Linux-машина — без AVX-512 (`/proc/cpuinfo`) | прогон |

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
| Linux-машина: OpenJDK 25.0.4 (`/usr/lib/jvm/java-25-openjdk-amd64`), 21; JDK 26 **нет**; Docker 29.1.3; 20 ядер | ssh, `java -version`, `docker version` |
| Доля классов Kotlin-приложения из кэша (RQ4) — **не измерена** | гипотеза; адрес — [B-02](../backlog/B-02-kotlin-classes-archived-share.md) |

### 1.9 Что измерено — и что это (не) значит

`hello world` (один класс, `java.util.stream` и `HashMap`), время всего процесса `java` от
запуска до выхода, 20 прогонов подряд, отсортированные ряды в журналах (`M1`), медиана — среднее
10-го и 11-го значения.

| Машина, JDK | Без кэша (`-XX:AOTMode=off`), мс | С кэшем, мс | Размер кэша |
|---|---|---|---|
| Linux x86_64, OpenJDK 25.0.4 | 143 (ряд 124–153, один выброс 2107) | 47 | 10,9 МБ |
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

### D3. mtime jar-ов нормализуются до тренировки к `1970-01-01T00:00:01Z`

Решение: перед стартом `aotTrain` ставит всем `lib/*.jar` mtime, равный умолчанию Jib
(`EPOCH_PLUS_SECOND`).

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

### D6. *(гипотеза)* По умолчанию `-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching` на тренировке и в проде

Почему: §1.6 — регион кода включается сам, собран под тренировочный CPU, а сценарий плагина —
разные машины. Nucleus пришёл к тому же умолчанию после issue #400. Отвергнуто: `-XX:UseAVX=2` —
только x86 и только потолок. Цена: диагностический флаг в прод-скрипте и потеря части
выигрыша, размер которой не измерен. Решение станет фактом после
[B-09](../backlog/B-09-cpu-portability-adapter-caching.md); до этого — опция `portability` с этим
умолчанием.

### D7. GC не пинуется; ZGC на JDK < 26 — ошибка конфигурации

Почему: §1.1 — смена GC между тренировкой и запуском разрешена (R10, JEP 483 говорит то же), а
ZGC на 25 — гарантированный отказ (R11). Бриф просил «pin the same GC» — не нужно, и лишнее
правило пользователь нарушит. Пин остаётся один: **тот же билд JDK** (`_jvm_ident`), и это
проверяется прогоном `aotVerify`, а не текстом.

### D8. Что проверяется на конфигурации, до первого запуска

JDK тулчейна < 25 — ошибка (одношаговый режим — JEP 514); ZGC при < 26 — ошибка; плагин
`application` не применён — ошибка (нет раскладки jar-ов); JDK с JDK-8377932 — предупреждение с
номером бага. Почему: всё это известно до `installDist`, и ошибка на конфигурации дешевле ошибки
после тренировки.

### D9. Область: Gradle, плагин `application`, DSL `zavarnik { }`

```kotlin
plugins {
    application
    id("io.github.youndie.zavarnik")          // имя — открытый вопрос 1
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

**Риск 1. Кэш падает `SIGILL` на другом CPU.** Механизм: §1.6. Митигация: D6 и документация
«тренируй там, где запускаешь, или оставь `portability`». Открыт до
[B-09](../backlog/B-09-cpu-portability-adapter-caching.md) — нужна тренировочная машина с
AVX-512.

**Риск 2. Пользователь на JDK 25.0.0–25.0.3 / 26.0.0–26.0.1 получает устаревший кэш молча.**
Механизм: §1.2. Митигация: D4 (3) и предупреждение D8. Не закрывается: если пользователь
пересобрал jar и **не** запустил `aotVerify`, JVM его не спасёт — поэтому `aotVerify` в `check`
по умолчанию.

**Риск 3. Выигрыш на Ktor меньше 20 % — проект закрывается (критерий остановки 2).** Не измерен
(§1.9). Митигация — не митигация, а гейт: [B-01](../backlog/B-01-ktor-stand-and-readiness-timing.md)
блокирует всё в `stage-2-mvp`.

**Риск 4. Kotlin-классы не архивируются (indy-лямбды — hidden-классы).** Гипотеза; заметка
Leyden о тренировочных прогонах называет hidden-классы отдельной сложностью. Митигация: D4 (2)
покажет долю, [B-02](../backlog/B-02-kotlin-classes-archived-share.md) сравнит `-Xlambdas`.
Если доля низкая — это документация по флагам компилятора, не блокер (бриф, RQ4).

**Риск 5. Windows.** §1.3, следствие 3. Митигация: не поддерживать в MVP, сказать это в README;
[B-14](../backlog/B-14-windows-training.md).

**Риск 6. Второй шаг тренировки не помещается в память CI.** JEP 514 удваивает `-Xmx`.
Митигация: `aotTrain` задаёт heap тренировки явно и пишет обе цифры в журнал задачи.

**Риск 7. Сторож в скрипте ломается при смене шаблона Gradle.** D2 опирается на текст
`unixStartScript.txt`. Митигация: TestKit генерирует скрипт и запускает его
([B-08](../backlog/B-08-testkit-invalidation-cases.md)); якорь замены — строка `# Collect all
arguments for the java command:`, а не номер строки.

**Открытый вопрос 1. Имя и координаты.** `io.github.youndie.zavarnik` (прецедент — viddik) или
`website.kotlin.leyden` (бриф). Рекомендация — первое; решение владельца —
[B-12](../backlog/B-12-name-and-coordinates.md).

**Открытый вопрос 2. `-XX:AOTMode=on` в прод-скрипте?** Сейчас — нет (D2): отсутствие кэша
замедляет, а не роняет. Compose делает это опцией `exitAppOnAotFailure`. Вернуться после первого
внешнего пользователя.

**Открытый вопрос 3. JDK 26.** ZGC (JEP 516) и граница исправления JDK-8377932 — по исходникам.
[B-03](../backlog/B-03-jdk26-on-linux-box.md).

---

## 4. Что дальше

Порядок работы и критерии приёмки — в [backlog.md](../../backlog.md). Первое содержательное —
стенд на Ktor ([B-01](../backlog/B-01-ktor-stand-and-readiness-timing.md)): он единственный может
закрыть проект до того, как написана первая строка плагина, и потому стоит раньше плагина.
Параллельно и независимо — JDK 26 на Linux-машине ([B-03](../backlog/B-03-jdk26-on-linux-box.md))
и ответ на вопрос об имени ([B-12](../backlog/B-12-name-and-coordinates.md)).

Когда появится код, слои `features/` и `services/` заводятся в тех же PR, `status: draft` до
слияния; этот документ правится в месте расхождения, а не переписывается.
