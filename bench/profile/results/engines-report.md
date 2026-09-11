# Отчёт фазы движков — сгенерирован engines-report.py из журналов прогонов

## Основной протокол: закрепление 0-7, 64 соединения
## Цена запроса — чистое окно без профайлера (`engine-*`)

| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |
|---|---|---|---|---|---|---|---|---|---|---|
| `/echo` | cio | 55 185 | 0.71 мс | 11.29 мс | 125 | 6.89 | 103 | 3.12 | 774 МиБ | 14 |
| `/echo` | netty | 101 868 | 0.58 мс | 1.39 мс | 56 | 5.66 | 41 | 0.66 | 832 МиБ | 14 |
| `/echo` | jetty | 82 939 | 0.67 мс | 2.39 мс | 88 | 7.34 | 206 | 5.18 | 820 МиБ | 14 |
| `/items` | cio | 49 772 | 0.87 мс | 9.54 мс | 142 | 7.06 | 103 | 3.21 | 825 МиБ | 1521 |
| `/items` | netty | 66 368 | 0.89 мс | 2.28 мс | 88 | 5.83 | 43 | 0.75 | 875 МиБ | 1521 |
| `/items` | jetty | 60 982 | 0.93 мс | 3.00 мс | 120 | 7.33 | 207 | 5.40 | 900 МиБ | 1521 |
| `/business` | cio | 43 472 | 0.83 мс | 13.50 мс | 166 | 7.20 | 103 | 2.53 | 879 МиБ | 516 |
| `/business` | netty | 79 337 | 0.73 мс | 2.05 мс | 82 | 6.49 | 43 | 0.63 | 897 МиБ | 516 |
| `/business` | jetty | 52 383 | 1.13 мс | 2.94 мс | 146 | 7.64 | 206 | 9.60 | 988 МиБ | 516 |

## CPU по владельцу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/echo` | cio | 0.0 | 66.7 | 0.0 | 0.0 | 0.0 | 22.1 | 4.9 | 6.1 | 0.0 | 0.0 | 0.0 | 0.1 |
| `/echo` | netty | 0.0 | 7.5 | 0.0 | 69.4 | 0.0 | 17.4 | 0.0 | 5.6 | 0.0 | 0.0 | 0.0 | 0.1 |
| `/echo` | jetty | 0.0 | 30.4 | 0.0 | 0.0 | 27.2 | 9.4 | 0.0 | 5.3 | 3.4 | 0.0 | 0.0 | 24.3 |
| `/items` | cio | 1.2 | 52.8 | 5.8 | 0.0 | 0.0 | 26.0 | 4.4 | 9.7 | 0.0 | 0.0 | 0.0 | 0.1 |
| `/items` | netty | 3.5 | 5.8 | 10.4 | 55.0 | 0.0 | 15.9 | 0.0 | 9.2 | 0.0 | 0.0 | 0.0 | 0.2 |
| `/items` | jetty | 1.3 | 25.2 | 6.6 | 0.0 | 26.3 | 9.8 | 0.0 | 8.4 | 2.7 | 0.0 | 0.0 | 19.8 |
| `/business` | cio | 3.1 | 56.9 | 4.4 | 0.0 | 0.0 | 21.0 | 3.1 | 11.3 | 0.0 | 0.0 | 0.0 | 0.2 |
| `/business` | netty | 6.1 | 8.9 | 9.6 | 43.1 | 0.0 | 16.0 | 1.3 | 14.7 | 0.0 | 0.1 | 0.0 | 0.3 |
| `/business` | jetty | 3.3 | 29.6 | 4.8 | 0.0 | 15.5 | 7.1 | 1.2 | 8.6 | 5.1 | 0.1 | 0.0 | 24.7 |

## CPU по листу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/echo` | cio | 0.0 | 49.5 | 0.0 | 0.0 | 0.0 | 9.4 | 4.9 | 4.0 | 4.2 | 0.0 | 0.0 | 28.1 |
| `/echo` | netty | 0.0 | 6.6 | 0.0 | 19.4 | 0.0 | 11.5 | 0.0 | 4.1 | 10.4 | 0.0 | 0.0 | 48.0 |
| `/echo` | jetty | 0.0 | 5.1 | 0.0 | 0.0 | 7.6 | 6.2 | 0.0 | 4.0 | 12.3 | 0.0 | 0.0 | 64.8 |
| `/items` | cio | 1.1 | 34.9 | 3.1 | 0.0 | 0.0 | 11.1 | 4.4 | 5.1 | 9.2 | 0.0 | 0.0 | 31.1 |
| `/items` | netty | 3.4 | 5.0 | 5.2 | 15.1 | 0.0 | 10.4 | 0.0 | 4.2 | 18.1 | 0.0 | 0.0 | 38.4 |
| `/items` | jetty | 1.3 | 4.5 | 3.5 | 0.0 | 6.7 | 6.4 | 0.0 | 4.2 | 16.6 | 0.0 | 0.0 | 56.8 |
| `/business` | cio | 1.6 | 42.3 | 2.2 | 0.0 | 0.0 | 9.8 | 3.0 | 5.6 | 11.6 | 0.0 | 0.0 | 24.0 |
| `/business` | netty | 3.0 | 7.3 | 4.7 | 12.9 | 0.0 | 10.6 | 0.7 | 5.6 | 22.8 | 0.1 | 0.0 | 32.2 |
| `/business` | jetty | 1.8 | 5.1 | 2.3 | 0.0 | 4.8 | 4.7 | 0.4 | 4.1 | 19.4 | 0.1 | 0.0 | 57.4 |

## Машинерия передачи запроса обработчику — доля self CPU

| эндпоинт | движок | coroutine queue | netty event loop | jetty pool | park/unpark | всего |
|---|---|---|---|---|---|---|
| `/echo` | cio | 43.9 | 0.0 | 0.0 | 4.3 | 48.2 |
| `/echo` | netty | 0.0 | 2.0 | 0.0 | 0.5 | 2.5 |
| `/echo` | jetty | 0.2 | 0.0 | 1.4 | 27.9 | 29.5 |
| `/items` | cio | 28.9 | 0.0 | 0.0 | 4.5 | 33.5 |
| `/items` | netty | 0.0 | 0.8 | 0.0 | 0.5 | 1.3 |
| `/items` | jetty | 0.2 | 0.0 | 1.2 | 23.3 | 24.7 |
| `/business` | cio | 36.3 | 0.0 | 0.0 | 2.6 | 38.9 |
| `/business` | netty | 0.1 | 0.8 | 0.0 | 0.3 | 1.2 |
| `/business` | jetty | 0.4 | 0.0 | 1.3 | 24.9 | 26.6 |

Что сложено в эти доли (кадры выше 0,5 %):

- `/echo`, cio: `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 24.6 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 9.8 % (coroutine queue), `pthread_cond_signal` 3.7 % (park/unpark), `kotlinx/coroutines/scheduling/WorkQueue.pollBuffer` 3.5 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull` 1.6 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 1.3 % (coroutine queue), `kotlinx/coroutines/scheduling/CoroutineScheduler.parkedWorkersStackPop` 0.6 % (coroutine queue)
- `/echo`, netty: `io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom` 1.0 % (netty event loop), `pthread_cond_signal` 0.5 % (park/unpark)
- `/echo`, jetty: `pthread_cond_signal` 22.8 % (park/unpark), `java/util/concurrent/locks/LockSupport.unpark` 2.5 % (park/unpark), `java/util/concurrent/locks/LockSupport.park` 1.8 % (park/unpark), `java/util/concurrent/ThreadPoolExecutor.runWorker` 0.7 % (jetty pool), `jdk/internal/misc/Unsafe.park` 0.5 % (park/unpark)
- `/items`, cio: `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 14.6 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 5.9 % (coroutine queue), `pthread_cond_signal` 4.0 % (park/unpark), `kotlinx/coroutines/scheduling/WorkQueue.pollBuffer` 3.6 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 1.2 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull` 1.0 % (coroutine queue), `kotlinx/coroutines/scheduling/CoroutineScheduler.parkedWorkersStackPop` 0.6 % (coroutine queue)
- `/items`, jetty: `pthread_cond_signal` 18.9 % (park/unpark), `java/util/concurrent/locks/LockSupport.unpark` 2.4 % (park/unpark), `java/util/concurrent/locks/LockSupport.park` 1.3 % (park/unpark), `java/util/concurrent/ThreadPoolExecutor.runWorker` 0.6 % (jetty pool)
- `/business`, cio: `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 17.3 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 6.9 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 4.3 % (coroutine queue), `pthread_cond_signal` 2.4 % (park/unpark), `kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull` 2.3 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.pollBuffer` 2.2 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast` 1.1 % (coroutine queue), `kotlinx/coroutines/scheduling/CoroutineScheduler.parkedWorkersStackPop` 0.7 % (coroutine queue)
- `/business`, jetty: `pthread_cond_signal` 20.4 % (park/unpark), `java/util/concurrent/locks/LockSupport.park` 2.0 % (park/unpark), `java/util/concurrent/locks/LockSupport.unpark` 1.9 % (park/unpark), `java/util/concurrent/ThreadPoolExecutor.getTask` 0.6 % (jetty pool)

## Без закрепления (протокол второй фазы), /business
## Цена запроса — чистое окно без профайлера (`engine-*-unpinned`)

| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |
|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 34 495 | 1.15 мс | 9.95 мс | 363 | 12.52 | 125 | 5.71 | 852 МиБ | 516 |
| `/business` | netty | 69 429 | 0.85 мс | 2.15 мс | 145 | 10.08 | 73 | 1.50 | 995 МиБ | 516 |
| `/business` | jetty | 52 960 | 1.11 мс | 3.02 мс | 276 | 14.64 | 227 | 8.87 | 910 МиБ | 516 |

## CPU по владельцу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 1.9 | 72.4 | 2.7 | 0.0 | 0.0 | 14.1 | 1.2 | 7.5 | 0.0 | 0.0 | 0.0 | 0.2 |
| `/business` | netty | 4.8 | 7.2 | 7.1 | 53.6 | 0.0 | 12.4 | 1.0 | 13.5 | 0.0 | 0.1 | 0.0 | 0.2 |
| `/business` | jetty | 2.8 | 30.7 | 4.1 | 0.0 | 22.8 | 6.8 | 0.7 | 8.0 | 4.1 | 0.0 | 0.0 | 19.9 |

## CPU по листу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 0.9 | 54.1 | 1.6 | 0.0 | 0.0 | 6.6 | 1.2 | 4.5 | 6.0 | 0.0 | 0.0 | 25.1 |
| `/business` | netty | 2.1 | 6.4 | 4.0 | 11.9 | 0.0 | 8.5 | 0.8 | 7.7 | 15.2 | 0.1 | 0.0 | 43.3 |
| `/business` | jetty | 1.3 | 4.9 | 2.4 | 0.0 | 4.8 | 4.9 | 0.5 | 4.9 | 14.9 | 0.0 | 0.0 | 61.4 |

## Машинерия передачи запроса обработчику — доля self CPU

| эндпоинт | движок | coroutine queue | netty event loop | jetty pool | park/unpark | всего |
|---|---|---|---|---|---|---|
| `/business` | cio | 49.6 | 0.0 | 0.0 | 4.3 | 53.9 |
| `/business` | netty | 0.1 | 1.6 | 0.0 | 0.5 | 2.1 |
| `/business` | jetty | 0.5 | 0.0 | 3.6 | 26.7 | 30.9 |

Что сложено в эти доли (кадры выше 0,5 %):

- `/business`, cio: `kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull` 23.3 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 9.4 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 7.5 % (coroutine queue), `pthread_cond_signal` 3.8 % (park/unpark), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 3.5 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.tryStealLastScheduled` 2.8 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast` 0.6 % (coroutine queue)
- `/business`, netty: `io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom` 0.7 % (netty event loop)
- `/business`, jetty: `pthread_cond_signal` 24.4 % (park/unpark), `java/util/concurrent/SynchronousQueue$Transferer.xferLifo` 1.7 % (jetty pool), `java/util/concurrent/locks/LockSupport.unpark` 1.6 % (park/unpark), `java/util/concurrent/SynchronousQueue.xfer` 1.1 % (jetty pool)

## 8 соединений, /business
## Цена запроса — чистое окно без профайлера (`engine-*-c8`)

| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |
|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 20 110 | 0.37 мс | 0.73 мс | 231 | 4.64 | 60 | 10.80 | 783 МиБ | 516 |
| `/business` | netty | 23 613 | 0.32 мс | 0.58 мс | 111 | 2.62 | 42 | 2.53 | 845 МиБ | 516 |
| `/business` | jetty | 18 105 | 0.43 мс | 0.75 мс | 247 | 4.47 | 70 | 13.43 | 799 МиБ | 516 |

## CPU по владельцу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 2.9 | 54.0 | 4.1 | 0.0 | 0.0 | 24.5 | 1.8 | 12.6 | 0.0 | 0.0 | 0.0 | 0.1 |
| `/business` | netty | 5.0 | 7.0 | 7.3 | 52.8 | 0.0 | 11.7 | 1.0 | 14.9 | 0.0 | 0.1 | 0.0 | 0.2 |
| `/business` | jetty | 3.3 | 26.7 | 3.8 | 0.0 | 24.1 | 8.0 | 0.5 | 9.5 | 4.8 | 0.0 | 0.0 | 19.2 |

## CPU по листу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 1.2 | 22.8 | 2.4 | 0.0 | 0.0 | 11.4 | 1.7 | 7.4 | 10.6 | 0.0 | 0.0 | 42.6 |
| `/business` | netty | 2.2 | 6.4 | 4.0 | 11.7 | 0.0 | 7.9 | 0.8 | 8.0 | 17.3 | 0.1 | 0.0 | 41.6 |
| `/business` | jetty | 1.5 | 5.9 | 2.1 | 0.0 | 4.8 | 5.8 | 0.4 | 5.8 | 16.4 | 0.0 | 0.0 | 57.2 |

## Машинерия передачи запроса обработчику — доля self CPU

| эндпоинт | движок | coroutine queue | netty event loop | jetty pool | park/unpark | всего |
|---|---|---|---|---|---|---|
| `/business` | cio | 16.0 | 0.0 | 0.0 | 9.0 | 24.9 |
| `/business` | netty | 0.1 | 1.1 | 0.0 | 0.1 | 1.3 |
| `/business` | jetty | 0.7 | 0.0 | 4.0 | 22.8 | 27.5 |

Что сложено в эти доли (кадры выше 0,5 %):

- `/business`, cio: `pthread_cond_signal` 8.1 % (park/unpark), `kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull` 5.5 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.pollBuffer` 3.9 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 1.3 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 1.0 % (coroutine queue), `kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.findAnyTask` 0.8 % (coroutine queue), `kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.runWorker` 0.7 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.add` 0.6 % (coroutine queue), `jdk/internal/misc/Unsafe.park` 0.6 % (park/unpark)
- `/business`, netty: `io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom` 0.6 % (netty event loop)
- `/business`, jetty: `pthread_cond_signal` 20.3 % (park/unpark), `java/util/concurrent/SynchronousQueue.xfer` 2.0 % (jetty pool), `java/util/concurrent/locks/LockSupport.unpark` 1.4 % (park/unpark), `java/util/concurrent/ThreadPoolExecutor.runWorker` 0.7 % (jetty pool), `jdk/internal/misc/Unsafe.park` 0.5 % (park/unpark), `java/util/concurrent/SynchronousQueue$Transferer.xferLifo` 0.5 % (jetty pool)

## 256 соединений, /business
## Цена запроса — чистое окно без профайлера (`engine-*-c256`)

| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |
|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 66 532 | 2.67 мс | 37.30 мс | 110 | 7.30 | 110 | 1.19 | 822 МиБ | 516 |
| `/business` | netty | 103 757 | 2.12 мс | 7.88 мс | 69 | 7.14 | 42 | 0.26 | 856 МиБ | 516 |
| `/business` | jetty | 71 193 | 2.91 мс | 18.46 мс | 111 | 7.90 | 292 | 3.06 | 918 МиБ | 516 |

## CPU по владельцу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 3.7 | 42.7 | 6.6 | 0.0 | 0.0 | 28.6 | 4.0 | 14.0 | 0.0 | 0.0 | 0.0 | 0.4 |
| `/business` | netty | 6.2 | 9.6 | 11.1 | 40.1 | 0.0 | 14.0 | 1.9 | 16.5 | 0.0 | 0.0 | 0.0 | 0.4 |
| `/business` | jetty | 4.5 | 17.4 | 7.9 | 0.0 | 27.6 | 10.8 | 1.5 | 11.9 | 7.0 | 0.0 | 0.0 | 11.4 |

## CPU по листу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 1.8 | 34.2 | 3.9 | 0.0 | 0.0 | 13.2 | 3.8 | 8.3 | 10.8 | 0.0 | 0.0 | 24.1 |
| `/business` | netty | 2.3 | 8.9 | 6.9 | 15.4 | 0.0 | 9.5 | 1.5 | 9.0 | 18.1 | 0.0 | 0.0 | 28.5 |
| `/business` | jetty | 1.9 | 6.0 | 3.9 | 0.0 | 10.8 | 7.4 | 1.1 | 6.6 | 23.4 | 0.0 | 0.0 | 38.9 |

## Машинерия передачи запроса обработчику — доля self CPU

| эндпоинт | движок | coroutine queue | netty event loop | jetty pool | park/unpark | всего |
|---|---|---|---|---|---|---|
| `/business` | cio | 25.0 | 0.0 | 0.0 | 2.7 | 27.7 |
| `/business` | netty | 0.1 | 1.5 | 0.0 | 0.3 | 1.8 |
| `/business` | jetty | 0.4 | 0.0 | 1.6 | 11.8 | 13.8 |

Что сложено в эти доли (кадры выше 0,5 %):

- `/business`, cio: `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 12.9 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 6.1 % (coroutine queue), `pthread_cond_signal` 2.5 % (park/unpark), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast` 2.0 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 1.1 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.trySteal` 0.6 % (coroutine queue)
- `/business`, netty: `io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom` 1.0 % (netty event loop)
- `/business`, jetty: `pthread_cond_signal` 9.1 % (park/unpark), `java/util/concurrent/locks/LockSupport.unpark` 2.3 % (park/unpark)

## Рычаг io.parallelism=8 (cio и контроль netty), /business
## Цена запроса — чистое окно без профайлера (`engine-*-p8`)

| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |
|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 72 466 | 0.82 мс | 2.24 мс | 92 | 6.65 | 50 | 1.23 | 799 МиБ | 516 |
| `/business` | netty | 82 889 | 0.71 мс | 1.82 мс | 79 | 6.52 | 43 | 0.64 | 855 МиБ | 516 |

## CPU по владельцу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 4.3 | 33.5 | 7.8 | 0.0 | 0.0 | 33.9 | 3.1 | 16.5 | 0.0 | 0.0 | 0.0 | 1.0 |
| `/business` | netty | 5.7 | 8.5 | 10.1 | 44.7 | 0.0 | 12.7 | 1.6 | 16.3 | 0.0 | 0.0 | 0.0 | 0.2 |

## CPU по листу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 1.8 | 21.3 | 4.5 | 0.0 | 0.0 | 15.5 | 2.9 | 9.5 | 13.6 | 0.0 | 0.0 | 30.8 |
| `/business` | netty | 2.4 | 7.8 | 6.3 | 14.3 | 0.0 | 8.7 | 1.3 | 8.7 | 17.4 | 0.0 | 0.0 | 33.2 |

## Машинерия передачи запроса обработчику — доля self CPU

| эндпоинт | движок | coroutine queue | netty event loop | jetty pool | park/unpark | всего |
|---|---|---|---|---|---|---|
| `/business` | cio | 11.0 | 0.0 | 0.0 | 4.4 | 15.4 |
| `/business` | netty | 0.1 | 1.4 | 0.0 | 0.3 | 1.8 |

Что сложено в эти доли (кадры выше 0,5 %):

- `/business`, cio: `pthread_cond_signal` 4.2 % (park/unpark), `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 2.8 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 2.6 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 2.0 % (coroutine queue), `kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.findTask` 0.7 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.pollBuffer` 0.6 % (coroutine queue)
- `/business`, netty: `io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom` 0.8 % (netty event loop)

## Рычаг io.parallelism=16 (cio), /business
## Цена запроса — чистое окно без профайлера (`engine-*-p16`)

| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |
|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 69 211 | 0.73 мс | 6.10 мс | 104 | 7.21 | 66 | 1.25 | 805 МиБ | 516 |

## CPU по владельцу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 4.1 | 39.2 | 6.8 | 0.0 | 0.0 | 30.3 | 3.7 | 15.7 | 0.0 | 0.0 | 0.0 | 0.3 |

## CPU по листу

| эндпоинт | движок | user | coroutines | serialization | netty | jetty | ktor | kotlinx | kotlin | jdk | slf4j | other | jvm |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/business` | cio | 1.9 | 29.1 | 4.2 | 0.0 | 0.0 | 14.0 | 3.5 | 9.1 | 11.8 | 0.0 | 0.0 | 26.3 |

## Машинерия передачи запроса обработчику — доля self CPU

| эндпоинт | движок | coroutine queue | netty event loop | jetty pool | park/unpark | всего |
|---|---|---|---|---|---|---|
| `/business` | cio | 20.1 | 0.0 | 0.0 | 3.1 | 23.2 |

Что сложено в эти доли (кадры выше 0,5 %):

- `/business`, cio: `kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker` 11.5 % (coroutine queue), `kotlinx/coroutines/internal/LimitedDispatcher.dispatch` 4.0 % (coroutine queue), `pthread_cond_signal` 2.9 % (park/unpark), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast` 0.9 % (coroutine queue), `kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull` 0.8 % (coroutine queue), `kotlinx/coroutines/scheduling/WorkQueue.pollBuffer` 0.7 % (coroutine queue)
