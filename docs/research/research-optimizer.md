---
id: research-optimizer
title: zavarnik, вторая фаза — research плагина оптимизации байткода Kotlin/JVM
type: research
status: active
date: 2026-09-06
---

# Research: плагин оптимизации байткода для сервисов на Ktor CIO

Вторая фаза проекта — [бриф](source-brief-optimizer.md): Gradle-плагин из двух движков,
K2 IR-плагина для пользовательского кода и ASM-трансформации всего classpath, который должен дать
измеримый выигрыш по пропускной способности и аллокациям на настоящем сервисе на Ktor CIO. Бриф
ставит ворота — RQ0: какую долю профиля вообще занимает пользовательский код, и что уже даёт R8.
Первая фаза, AOT-кэш, — [research-architecture](research-architecture.md); стенд этой фазы
вырос из её образца.

Документ фиксирует **проверенные факты** (прочитанное в исходниках и документации и полученное
прогоном на Linux-машине — журналы в `bench/profile/results/`), **принятые решения** и **риски**.
Всё непроверенное названо гипотезой и имеет адрес.

Машина: Linux-машина первой фазы (Ubuntu 24.04, Core Ultra 7 255HX, 20 потоков, OpenJDK
25.0.4+7-1-24.04-Ubuntu). Стенд — `bench/` (Ktor 3.5.2 на CIO, kotlinx.serialization 1.11.0,
slf4j-simple, три вида эндпоинтов из брифа §5), харнесс — `bench/profile/run.sh`, атрибуция —
`bench/profile/attribute.py`.

---

## 1. Проверенные факты

### 1.1 Инструменты и пороги

| Факт | Где проверено |
|---|---|
| Пороги C2 на JDK 25.0.4: `FreqInlineSize=325`, `MaxInlineSize=35`, `InlineSmallCode=2500`, `MaxInlineLevel=15`, `C1MaxInlineSize=35`; `DontCompileHugeMethods=true` | `-XX:+PrintFlagsFinal`, Linux-машина |
| Флаги kotlinc 2.4.10 из RQ1 существуют: `-Xno-param-assertions`, `-Xno-call-assertions`, `-Xno-receiver-assertions`; рядом `-Xno-unified-null-checks`, `-Xstring-concat`, `-Xlambdas`, `-Xno-optimize` | `strings` по `K2JVMCompilerArguments.class` из `kotlin-compiler-embeddable-2.4.10.jar` |
| async-profiler 4.5 в WSL2 работает в режимах `cpu` (perf_events при `perf_event_paranoid=2`), `itimer` и `alloc` | прогон на `Busy.java`, Linux-машина |
| JFR в 25.0.4 пишет `jdk.ObjectAllocationSample` и `jdk.ExecutionSample` (`settings=profile`) | `jfr summary` |
| oha 1.16.0: JSON — `--output-format json`, keep-alive включён по умолчанию (`--disable-keepalive` — булев флаг) | `oha --help`; первый прогон харнесса упал на `--disable-keepalive=false` и `-j` |
| R8: стабильные версии 9.4.x (последняя 9.4.17), 9.5.x — только `-dev`; jar 20 МБ с `dl.google.com/dl/android/maven2` | `maven-metadata.xml` |
| kotlinx-atomicfu — прецедент IR-плагина оптимизации: с 0.24 только IR, «The bytecode transformation is deprecated and is no longer supported»; Kotlin ≥ 2.2.0 | README kotlinx-atomicfu |
| API компиляторных плагинов: «The Kotlin compiler plugin API is unstable and introduces breaking changes in every release»; бэкенд — единственная точка `IrGenerationExtension`; фронтенд K2 — восемь FIR-расширений; шаблон `github.com/Kotlin/compiler-plugin-template`; «you can develop custom compiler plugins only with Gradle» | kotlinlang.org/docs/custom-compiler-plugins.html, 12.08.2026 |

**Следствие.** IR-плагин — это обязательство переписывать его под каждый релиз компилятора; atomicfu
это делает силами JetBrains. Цена входа известна до того, как написана первая строка.

### 1.2 Атрибуция профиля: как считать

async-profiler пишет Java-кадры через слэши (`io/ktor/Foo.bar`), а лист аллокации — точками
(`java.lang.Integer`); классификатор обязан нормализовать оба. Два взгляда на каждый сэмпл:
**self** — лист (кто исполняется или какой класс аллоцируется), **owner** — первый кадр от листа,
который не JDK и не JVM (чей код это попросил); второй и есть потолок для плагина над этим кодом.
Третье число — доля стеков, где пользовательский код есть где угодно.

### 1.3 R8 на серверном classpath *(результат — отрицательный)*

Конфигурация — `bench/profile/r8.pro`: без обфускации, keep на serialization, Ktor, coroutines,
slf4j, `-assumenosideeffects` на `Intrinsics.check*`. R8 9.4.17 отрабатывает за 26 с: 26 jar-ов,
6503 класса → 5416, 11 МБ → 8,0 МБ; в 7 из 39 классов `bench.*` остались обращения к `Intrinsics`.

| Попытка | Итог |
|---|---|
| 9.4.17, полный classpath | `VerifyError` при старте: «Bad invokespecial instruction: interface method to invoke is not in a direct superinterface» в `kotlinx.coroutines.CompletableDeferred.access$cancel$jd` |
| то же с `-dontoptimize` | та же ошибка — это classfile-бэкенд R8, не оптимизации |
| 9.5.10-dev, полный classpath | та же ошибка |
| 9.4.17, coroutines как `--classpath`, `-keep class kotlin.** { *; }` | та же ошибка в другом классе (см. ниже) |

**Механизм — по `javap`.** В оригинальном `CompletableDeferred.class` (coroutines 1.11.0, class
version 52) синтетический аксессор `access$cancel$jd` делает `invokespecial
InterfaceMethod CompletableDeferred.cancel:()V` — вызов default-метода через текущий интерфейс.
R8 перевешивает ссылку на объявляющий интерфейс (*member rebinding*): `invokespecial InterfaceMethod
Job.cancel:()V`. Для `invokespecial` верификатор требует, чтобы интерфейс в ссылке был текущим
классом или **прямым** суперинтерфейсом; `Job` для `CompletableDeferred` — не прямой (через
`Deferred`), отсюда «not in a direct superinterface». При нетронутых coroutines то же происходит
в Ktor: `Routing.access$lineage$jd` → `invokespecial` на не-прямой интерфейс. Это не оптимизация и
не шринкинг — `-dontoptimize -dontshrink` дают ту же ошибку уже в `CompletableDeferred.cancel()`
самом; `--no-desugaring` не влияет; 9.5.10-dev — то же. Затронут любой Kotlin-интерфейс с
`-Xjvm-default=all` и `$jd`-аксессорами, то есть весь стек coroutines/Ktor.

**Следствие.** «R8 с разумной серверной конфигурацией» на этом стеке **не запускается**; базовую
линию по брифу измерить нельзя. Измеримо только усечённое: R8 над одним пользовательским jar-ом,
библиотеки как `--classpath` (§1.5) — это верхняя граница того, что R8 даёт *на территории
IR-плагина*, и ноль на остальной. Заводить issue в R8 — вопрос владельцу, не здесь.

### 1.4 Профиль стенда — RQ0, доля пользовательского кода

Протокол брифа §5: 60 с прогрева, 120 с замера под CPU-профайлером, ещё 120 с под alloc-профайлером,
64 соединения `oha`, JVM на ядрах 0–7, генератор на 8–15, `-Xms1g -Xmx1g -XX:+UseG1GC`. Один
повтор (бриф просит три — см. риск 2). Журналы: `bench/profile/results/baseline/`.

| Эндпоинт | rps | p50 | p99 | user CPU self / owner | user alloc self / owner | user где угодно на стеке (CPU / alloc) |
|---|---|---|---|---|---|---|
| `/echo` | 35 740 | 1,16 мс | 11,40 мс | 0,0 % / 0,0 % | 0,2 % / 0,2 % | 6,9 % / 13,8 % |
| `/items?limit=20` | 38 008 | 1,27 мс | 7,98 мс | 0,8 % / 0,8 % | 0,3 % / 0,3 % | 16,9 % / 45,5 % |
| `/business` | 33 935 | 1,18 мс | 10,24 мс | 1,1 % / 2,1 % | 3,6 % / 9,9 % | 20,0 % / 65,5 % |

Кому принадлежит остальное на `/business`: по «владельцу» CPU — kotlinx 73,9 %, Ktor 15,5 %,
stdlib 8,4 %; аллокации — Ktor 36,8 %, kotlinx 26,4 %, stdlib 26,8 %.

| Факт | Где проверено |
|---|---|
| **Пользовательский код — 2,1 % CPU и 9,9 % аллокаций** на самом «бизнесовом» эндпоинте по владельцу; на CRUD и echo — доли процента | `baseline/business.*.collapsed`, `attribute.py` |
| 36 % CPU `/business` — опрос очереди диспетчера: `LockFreeTaskQueue.removeFirstOrNull` 30,1 % self + `LockFreeTaskQueueCore.removeFirstOrNull` 5,9 %; ещё `WorkQueue.pollBuffer` 3,6 %, `LimitedDispatcher.dispatch` 1,9 % | `baseline/business.cpu.collapsed`, top self frames |
| Боксинг примитивов (`Integer`/`Long`/`Double`/…) — **1,26 %** всех аллоцированных байт на `/business` | leaf-типы alloc-профиля |
| Самый крупный пользовательский источник — `Regex("…")` внутри хендлера: `Matcher`, `int[]`, `boolean[]`, `byte[]` под `Pattern` — ≈ 6,5 % байт, владелец `Pricing.quote` | `Pricing.quote -> java.util.regex.Matcher` 1,77 %, `int[]` 1,56 %, `boolean[]` 1,49 %, `byte[]` 1,67 % |
| Промежуточные коллекции цепочек — ≈ 4,6 %: `Object[]` 2,06 %, `ArrayList` 0,93 %, `LinkedHashMap$Entry` 0,89 %, `ArrayList$Itr` 0,71 % | те же владельцы |
| Вершина alloc-профиля вообще — `byte[]` 18,4 %, `String` 6,8 %, `Object[]` 5,9 %: буферы ввода-вывода Ktor и разбор JSON | leaf-типы |
| Debug-шаблоны (три `logger.debug("…$x…")` на запрос при выключенном debug): весь string-concat с владельцем `Pricing.quote` — **1,57 %** байт; кадров `org.slf4j` на стеках аллокаций — 0,00 % (вызов `debug(String)` сам ничего не аллоцирует) | стеки с `StringConcatHelper`/`StringBuilder` и владельцем `bench.Pricing` |
| Квирк Ktor: `kotlin.reflect.jvm.internal.KClassImpl.toString` под `call.receive<T>()` — 0,67 % байт на `/business` в хендлере; строка типа строится на каждый запрос | `MainKt$main$1$2$9 -> byte[] via KClassImpl.toString` |
| `Regex` в хендлере целиком: все стеки под `java.util.regex.*` с кадром `bench.*` — **6,45 %** байт | `business.alloc.collapsed` |
| RQ6, статически: из 283 методов `bench.*` **10** длиннее `FreqInlineSize=325` байт — `Pricing.quote` (машина состояний, 1827), хендлеры `invokeSuspend` 447–816, `deserialize` сериализаторов 332–459; длиннее `HugeMethodLimit=8000` — ни одного | `javap -c -p` по `bench.jar`, Linux; не проверено `-XX:+PrintInlining`, влияет ли это на что-то в горячем пути |

**Следствие 1 — ворота RQ0, доля кода.** Порог зелёного «user ≥ 25 % аллокаций» не взят (9,9 %);
порог красного «user < 10 %» — взят на волосок. IR-плагин над пользовательским кодом на этом
сервисе может двигать не больше десятой части аллокаций и полусотой CPU; всё остальное — Ktor,
coroutines и stdlib, куда IR-плагин не дотягивается по определению (бриф §3).

**Следствие 2 — RQ2 закрыт до начала.** Боксинг — 1,26 % против красного порога 3 %: на этом
сервисе `suspend fun (): Int` и `value class` в generic-позициях не стоят отдельного прохода.
Диагностика — да, переписывание — нет.

**Следствие 3 — что осталось на территории IR-плагина.** Два кандидата с измеренным весом:
константа `Regex` в хендлере (RQ5, 6,45 % байт — крупнее всего остального пользовательского
вместе взятого) и цепочки коллекций (RQ3, ≈ 4,6 %). Ленивое логирование (RQ4) при реалистичной
плотности — ≤ 1,57 %, ниже красного порога 2 %: линт, не переписывание. Все три — на одном
эндпоинте, и первые два устраняются одной строкой руками; плагин здесь соревнуется с линтером.

**Следствие 4 — где на самом деле CPU.** Треть процессорного времени `/business` — спин на очереди
`LimitedDispatcher` в kotlinx.coroutines. Это не peephole и не IR: ни один из двух движков брифа
на это не влияет. Гипотеза: доля спина — артефакт закрепления JVM на 8 ядрах при
`availableProcessors`, видящем 20; адрес — проверка без `taskset` после замера R8.

### 1.5 R8, усечённый до пользовательского jar-а

Что можно измерить: R8 9.4.17 над `bench.jar` с 25 библиотечными jar-ами как `--classpath`
(по одному флагу на jar), та же `r8.pro`. Работает, все эндпоинты отвечают.

| Факт | Где проверено |
|---|---|
| Вызовы `Intrinsics.check*` в `bench.*`: 48 `checkNotNullParameter` + 7 `checkNotNull` + 1 `checkNotNullExpressionValue` → **0**; 16 `areEqual` остались (это семантика, не проверка) | `javap -c -p` по classes до и после |
| Байткод классов `bench.*`: 195 207 → 127 549 байт (−35 %); jar 88 → 68 КБ; классов 39 → 39 | `du -cb`, `unzip -l` |
| Замер по тому же протоколу — метка `r8-user-only` | `bench/profile/results/r8-user-only/` (§1.6) |

**Следствие.** Это верхняя граница «что даёт существующий инструмент на территории IR-плагина»:
R8 снимает все null-проверки пользовательского кода и инлайнит внутри него, не трогая библиотек.
Всё, что усечённый R8 не сдвинет, IR-плагину с теми же приёмами двигать негде.

---

## 2. Решения

*(после RQ0)*

---

## 3. Риски и открытые вопросы

*(после RQ0)*
