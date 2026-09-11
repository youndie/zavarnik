# 2026-09-10T23:08:13Z label=engine-jetty warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## echo
clean: rps=82939 p50=0.67ms p99=2.39ms requests=100.0% ok
cost: cpu=880.8s over 120.0s = 7.34 cores busy, 88 us cpu/req, 5.18 ctx/req, threads=206, peak rss=820 MiB, requests=9952833
rps=78088 p50=0.71ms p99=2.59ms requests=100.0% ok
## /home/youndie/bench-results/engine-jetty/echo.cpu.collapsed  (samples: 199855)
| category | self | owner |
|---|---|---|
| coroutines |   5.1% |  30.4% |
| jetty |   7.6% |  27.2% |
| user |   0.0% |   0.0% |
| ktor |   6.2% |   9.4% |
| kotlinx |   0.0% |   0.0% |
| kotlin |   4.0% |   5.3% |
| jdk |  12.3% |   3.4% |
| slf4j |   0.0% |   0.0% |
| jvm |  64.8% |  24.3% |
| user code anywhere on the stack |  13.7% | |

## /home/youndie/bench-results/engine-jetty/echo.alloc.collapsed  (samples: 115430695929)
| category | self | owner |
|---|---|---|
| coroutines |   7.3% |   7.3% |
| jetty |   6.6% |  12.2% |
| user |   0.3% |   0.3% |
| ktor |  38.7% |  65.2% |
| kotlinx |   0.8% |   0.8% |
| kotlin |   7.1% |  12.9% |
| jdk |  31.0% |   1.3% |
| jvm |   8.1% |   0.0% |
| user code anywhere on the stack |  14.2% | |

cpu self:  22.8% pthread_cond_signal
cpu self:  21.6% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   6.9% writev
cpu self:   2.5% java/util/concurrent/locks/LockSupport.unpark
cpu self:   2.1% read
cpu self:   1.9% org/eclipse/jetty/util/ArrayTrie.getBest
cpu self:   1.8% java/util/concurrent/locks/LockSupport.park
cpu self:   1.4% itable stub
cpu self:   0.9% pthread_cond_timedwait
cpu self:   0.9% epoll_ctl
cpu self:   0.7% pthread_mutex_lock
cpu self:   0.7% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   0.7% java/util/concurrent/ThreadPoolExecutor.runWorker
cpu self:   0.7% epoll_wait
cpu self:   0.7% __vdso_clock_gettime
cpu self:   0.7% Parker::park
cpu self:   0.6% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.5% kotlin/coroutines/CombinedContext.get
cpu self:   0.5% jdk/internal/misc/Unsafe.park
cpu self:   0.5% java/util/concurrent/LinkedTransferQueue$DualNode.await
## items
clean: rps=60982 p50=0.93ms p99=3.00ms requests=100.0% ok
cost: cpu=880.1s over 120.0s = 7.33 cores busy, 120 us cpu/req, 5.40 ctx/req, threads=207, peak rss=900 MiB, requests=7317966
rps=60823 p50=0.93ms p99=3.01ms requests=100.0% ok
## /home/youndie/bench-results/engine-jetty/items.cpu.collapsed  (samples: 201424)
| category | self | owner |
|---|---|---|
| coroutines |   4.5% |  25.2% |
| serialization |   3.5% |   6.6% |
| jetty |   6.7% |  26.3% |
| user |   1.3% |   1.3% |
| ktor |   6.4% |   9.8% |
| kotlinx |   0.0% |   0.0% |
| kotlin |   4.2% |   8.4% |
| jdk |  16.6% |   2.7% |
| slf4j |   0.0% |   0.0% |
| jvm |  56.8% |  19.8% |
| user code anywhere on the stack |  26.1% | |

## /home/youndie/bench-results/engine-jetty/items.alloc.collapsed  (samples: 140029717682)
| category | self | owner |
|---|---|---|
| coroutines |   5.0% |   5.2% |
| serialization |   0.9% |  19.0% |
| jetty |   3.8% |   7.2% |
| user |   0.3% |   0.3% |
| ktor |  24.6% |  42.0% |
| kotlinx |   0.5% |   0.5% |
| kotlin |   4.9% |  25.1% |
| jdk |  33.0% |   0.7% |
| jvm |  27.0% |   0.0% |
| user code anywhere on the stack |  49.9% | |

cpu self:  18.9% pthread_cond_signal
cpu self:  17.8% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   6.6% writev
cpu self:   2.4% java/util/concurrent/locks/LockSupport.unpark
cpu self:   1.9% read
cpu self:   1.7% org/eclipse/jetty/util/ArrayTrie.getBest
cpu self:   1.7% itable stub
cpu self:   1.3% java/util/concurrent/locks/LockSupport.park
cpu self:   1.0% epoll_ctl
cpu self:   1.0% bench/ItemStore$list$$inlined$sortedByDescending$1.compare
cpu self:   0.9% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.9% java/util/concurrent/ConcurrentHashMap$Traverser.advance
cpu self:   0.8% pthread_cond_timedwait
cpu self:   0.7% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   0.7% epoll_wait
cpu self:   0.7% Parker::park
cpu self:   0.6% pthread_mutex_lock
cpu self:   0.6% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.6% java/util/concurrent/ThreadPoolExecutor.runWorker
cpu self:   0.6% java/util/Arrays.copyOf
## business
clean: rps=52383 p50=1.13ms p99=2.94ms requests=100.0% ok
cost: cpu=917.1s over 120.0s = 7.64 cores busy, 146 us cpu/req, 9.60 ctx/req, threads=206, peak rss=988 MiB, requests=6286094
rps=50381 p50=1.17ms p99=3.26ms requests=100.0% ok
## /home/youndie/bench-results/engine-jetty/business.cpu.collapsed  (samples: 210417)
| category | self | owner |
|---|---|---|
| coroutines |   5.1% |  29.6% |
| serialization |   2.3% |   4.8% |
| jetty |   4.8% |  15.5% |
| user |   1.8% |   3.3% |
| ktor |   4.7% |   7.1% |
| kotlinx |   0.4% |   1.2% |
| kotlin |   4.1% |   8.6% |
| jdk |  19.4% |   5.1% |
| slf4j |   0.1% |   0.1% |
| jvm |  57.4% |  24.7% |
| user code anywhere on the stack |  32.0% | |

## /home/youndie/bench-results/engine-jetty/business.alloc.collapsed  (samples: 183192693531)
| category | self | owner |
|---|---|---|
| coroutines |   6.2% |   6.3% |
| serialization |   3.0% |  18.0% |
| jetty |   3.0% |   5.6% |
| user |   4.1% |  12.1% |
| ktor |  19.0% |  28.9% |
| kotlinx |   0.4% |   1.9% |
| kotlin |   6.2% |  26.1% |
| jdk |  34.0% |   1.1% |
| jvm |  24.1% |   0.0% |
| user code anywhere on the stack |  72.0% | |

cpu self:  20.9% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:  20.4% pthread_cond_signal
cpu self:   4.1% writev
cpu self:   2.0% java/util/concurrent/locks/LockSupport.park
cpu self:   1.9% java/util/concurrent/locks/LockSupport.unpark
cpu self:   1.9% java/util/concurrent/LinkedTransferQueue$DualNode.await
cpu self:   1.8% itable stub
cpu self:   1.4% org/eclipse/jetty/util/ArrayTrie.getBest
cpu self:   1.1% read
cpu self:   0.9% pthread_cond_timedwait
cpu self:   0.9% bench/Pricing.quote
cpu self:   0.7% pthread_mutex_lock
cpu self:   0.7% java/lang/String.length
cpu self:   0.7% __vdso_clock_gettime
cpu self:   0.7% Parker::park
cpu self:   0.6% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.6% java/util/concurrent/ThreadPoolExecutor.getTask
cpu self:   0.5% jdk/internal/misc/Unsafe.park
cpu self:   0.5% java/util/HashMap.getNode
cpu self:   0.4% org/eclipse/jetty/util/ArrayTrie.lookup
## GC and JIT from the service log
lines=6
