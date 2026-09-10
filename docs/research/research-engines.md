---
id: research-engines
title: "Диспетчер CIO: три движка Ktor под одним протоколом"
type: research
status: active
date: 2026-09-11
---

# Research: CIO против Netty и Jetty на одном сервисе

Вторая фаза ([research-optimizer](research-optimizer.md)) оставила после себя число, которое к её
собственному вопросу отношения не имело: **треть процессорного времени `/business` — очередь
диспетчера в kotlinx.coroutines** (§1.4 там же, задача
[B-23](../backlog/B-23-dispatcher-spin-hypothesis.md)). Гипотезу «это артефакт закрепления на
восьми ядрах» B-23 отвергла тремя конфигурациями и записала вывод: «свойство CIO под такой
нагрузкой». Вывод сделан на **одном движке**: другого в стенде не было, и фраза «свойство CIO»
опиралась на то, что сравнивать не с чем.

Эта фаза сравнивает. Тот же сервис, те же jar-ы, тот же процесс, тот же протокол замера — и три
движка Ktor, между которыми одна строка `-Dbench.engine`: CIO, Netty, Jetty. Вопрос: очередь
диспетчера — это цена **корутинного** сервера вообще или цена **этого** движка; и что она стоит в
абсолютных единицах, а не в долях профиля.

Машина и инструменты — те же, что во второй фазе: Ubuntu 24.04 (WSL2), Core Ultra 7 255HX, 20
потоков, OpenJDK 25.0.4+7-1-24.04-Ubuntu, async-profiler 4.5, oha 1.16.0. Стенд — `bench/`
(Ktor 3.5.2, kotlinx.serialization 1.11.0, kotlinx.coroutines 1.11.0, slf4j-simple), харнесс —
`bench/profile/run.sh`, атрибуция — `bench/profile/attribute.py`, драйверы фазы —
`bench/profile/engines.sh` и `bench/profile/engines-ab.sh`. Журналы — `bench/profile/results/engine-*`.

---

## 1. Проверенные факты

### 1.1 Что в стенде изменилось и почему

| Факт | Где проверено |
|---|---|
| **Движок — единственное различие вариантов.** Все три движка лежат на classpath одновременно (91 jar вместо 26), сервис выбирает фабрику по `System.getProperty("bench.engine")`; маршруты, сериализация, логирование, флаги JVM и порядок прогрева одинаковы | `bench/src/main/kotlin/bench/Main.kt`, `bench/profile/engines.sh` |
| **`taskset -pc <pid>` закрепляет один поток, а не процесс.** После `taskset -pc 0-7 $PID` 49 потоков из 50 читают `Cpus_allowed_list: 0-19`; процесс жёг 12,6 ядра при «закреплении» на 8. Закрепление на `exec` (`taskset -c 0-7 <cmd>`) наследуют все потоки — и JVM видит 8 процессоров, поэтому создаёт 30 потоков вместо 50 | прогон 11.09.2026, `/proc/$PID/task/*/status`; исправлено в `run.sh` |
| **Все числа второй фазы сняты с незакреплённой JVM.** Протокол статьи говорил «JVM на ядрах 0–7, генератор на 8–15», а на деле сервис и генератор делили все 20 ядер во всех трёх конфигурациях B-23 — включая ту, что называлась «под `taskset`». Это объясняет, почему «закреплённая» и «незакреплённая» конфигурации дали одно и то же (36 % и 33 %) | `bench/profile/results/spin-*`, та же проверка |
| **Цена процесса измеряется отдельно от профиля.** `run.sh` и `engines-ab.sh` берут `utime+stime` из `/proc/$PID/stat` вокруг чистого окна и делят на число ответов: профиль — доли, и доли не отличают движок, который жжёт процессор, от движка, который его ждёт | `bench/profile/run.sh`, строка `cost:` в `summary.md` |
| **«kotlinx 73,9 % CPU» второй фазы — это корутины, а не сериализация.** Пересчёт того же файла с разделёнными категориями: coroutines 69,4 % владельца, kotlinx.serialization 3,0 %, прочий kotlinx 1,4 % | `attribute.py --categories 'coroutines=kotlinx.coroutines.;serialization=kotlinx.serialization.' bench/profile/results/baseline/business.cpu.collapsed` |

### 1.2 Механизм по исходникам: куда каждый движок отправляет обработчик

Прочитано в исходниках Ktor 3.5.2 и kotlinx.coroutines 1.11.0 (jar-ы `-sources` с Central), а не
выведено из профиля.

| Движок | Чем исполняется вызов | Чем это оказывается в профиле |
|---|---|---|
| **CIO** | `CIOApplicationEngine`: `engineDispatcher` и `userDispatcher` — оба `Dispatchers.IOBridge`, на JVM это `Dispatchers.IO` | `Dispatchers.IO` = `DefaultIoScheduler` = `UnlimitedIoScheduler.limitedParallelism(systemProp("kotlinx.coroutines.io.parallelism", 64.coerceAtLeast(AVAILABLE_PROCESSORS)))`. То есть **каждая** отправка идёт через `LimitedDispatcher`, у которого один `LockFreeTaskQueue<Runnable>(singleConsumer = false)` и до 64 воркер-циклов, опрашивающих его: `obtainTaskOrDeallocateWorker → queue.removeFirstOrNull` |
| **Netty** | `NettyDispatcher : CoroutineDispatcher`: `isDispatchNeeded` возвращает `false`, когда код уже в event loop канала, иначе `executor.execute(block)` — исполнитель самого канала, с привязкой к потоку | Общей очереди нет: продолжения либо исполняются на месте, либо кладутся в очередь **своего** event loop. `Dispatchers.IO` — только запасной путь на выключающемся исполнителе |
| **Jetty** | `JettyKtorHandler`: собственный `ThreadPoolExecutor(callGroupSize, callGroupSize * 8, SynchronousQueue)` через `asCoroutineDispatcher()`; вызов — `application.launch(handlerContext)` | `SynchronousQueue` — не буфер, а рандеву: каждая отправка передаёт задачу потоку из пула напрямую (или, если пул полон, исполняется на вызывающем потоке — `RejectedExecutionHandler { r, _ -> r.run() }`) |

Размеры пулов у Netty и Jetty считаются от `parallelism` (это `availableProcessors`) в
`ApplicationEngine.Configuration`: `connectionGroupSize = workerGroupSize = parallelism / 2 + 1`,
`callGroupSize = parallelism`. Все три движка меряются **как поставляются**, без настройки.

**Следствие для гипотезы.** Если очередь — цена корутин вообще, она должна быть видна на всех
трёх. Если это цена архитектуры «одна общая MPMC-очередь на 64 воркера», её не должно быть ни у
Netty (очередь на поток), ни у Jetty (рандеву без опроса). Различие предсказано до замера.
