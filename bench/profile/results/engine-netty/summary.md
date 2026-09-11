# 2026-09-10T22:46:29Z label=engine-netty warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## echo
clean: rps=101868 p50=0.58ms p99=1.39ms requests=100.0% ok
cost: cpu=679.6s over 120.0s = 5.66 cores busy, 56 us cpu/req, 0.66 ctx/req, threads=41, peak rss=832 MiB, requests=12224410
rps=93418 p50=0.62ms p99=1.77ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty/echo.cpu.collapsed  (samples: 142946)
| category | self | owner |
|---|---|---|
| coroutines |   6.6% |   7.5% |
| netty |  19.4% |  69.4% |
| ktor |  11.5% |  17.4% |
| kotlin |   4.1% |   5.6% |
| jdk |  10.4% |   0.0% |
| jvm |  48.0% |   0.1% |
| user code anywhere on the stack |  11.4% | |

## /home/youndie/bench-results/engine-netty/echo.alloc.collapsed  (samples: 120869649267)
| category | self | owner |
|---|---|---|
| coroutines |   5.7% |   7.0% |
| netty |  12.7% |  22.0% |
| user |   0.4% |   0.4% |
| ktor |  39.3% |  60.4% |
| kotlin |   5.1% |  10.3% |
| jdk |  27.8% |   0.0% |
| jvm |   9.0% |   0.0% |
| user code anywhere on the stack |  19.7% | |

cpu self:  22.8% __write
cpu self:  10.3% epoll_wait
cpu self:   5.9% read
cpu self:   2.1% itable stub
cpu self:   1.0% io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom
cpu self:   1.0% io/netty/util/concurrent/DefaultPromise.setValue0
cpu self:   0.9% __vdso_clock_gettime
cpu self:   0.9% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   0.8% kotlinx/coroutines/internal/LockFreeLinkedListNode.finishAdd
cpu self:   0.8% io/netty/channel/AbstractChannelHandlerContext.fireChannelRead
cpu self:   0.7% java/util/HashMap.getNode
cpu self:   0.7% java/util/ArrayList.clear
cpu self:   0.7% io/netty/util/concurrent/DefaultPromise.notifyListener0
cpu self:   0.7% io/netty/channel/nio/NioIoHandler.wakeup
cpu self:   0.7% io/ktor/util/pipeline/Pipeline.execute
cpu self:   0.6% sun/nio/ch/SelectorImpl.processDeregisterQueue
cpu self:   0.6% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   0.6% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.6% kotlin/UnsafeLazyImpl.getValue
cpu self:   0.6% java/util/ArrayList.grow
## items
clean: rps=66368 p50=0.89ms p99=2.28ms requests=100.0% ok
cost: cpu=699.5s over 120.0s = 5.83 cores busy, 88 us cpu/req, 0.75 ctx/req, threads=43, peak rss=875 MiB, requests=7964358
rps=67434 p50=0.87ms p99=2.22ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty/items.cpu.collapsed  (samples: 158270)
| category | self | owner |
|---|---|---|
| coroutines |   5.0% |   5.8% |
| serialization |   5.2% |  10.4% |
| netty |  15.1% |  55.0% |
| user |   3.4% |   3.5% |
| ktor |  10.4% |  15.9% |
| kotlin |   4.2% |   9.2% |
| jdk |  18.1% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  38.4% |   0.2% |
| user code anywhere on the stack |  30.5% | |

## /home/youndie/bench-results/engine-netty/items.alloc.collapsed  (samples: 166340536490)
| category | self | owner |
|---|---|---|
| coroutines |   3.9% |   4.7% |
| serialization |   0.9% |  20.4% |
| netty |   7.6% |  13.3% |
| user |   0.4% |   0.4% |
| ktor |  23.5% |  36.7% |
| kotlin |   3.4% |  24.6% |
| jdk |  31.1% |   0.0% |
| jvm |  29.2% |   0.0% |
| user code anywhere on the stack |  55.2% | |

cpu self:   9.2% writev
cpu self:   8.3% __write
cpu self:   7.9% epoll_wait
cpu self:   4.1% read
cpu self:   3.1% bench/ItemStore$list$$inlined$sortedByDescending$1.compare
cpu self:   2.2% itable stub
cpu self:   1.7% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.9% java/lang/StringLatin1.getChars
cpu self:   0.8% java/util/concurrent/ConcurrentHashMap$Traverser.advance
cpu self:   0.8% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   0.7% io/ktor/util/pipeline/SuspendFunctionGun.loop
cpu self:   0.7% __vdso_clock_gettime
cpu self:   0.6% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   0.6% java/util/HashMap.getNode
cpu self:   0.6% io/netty/util/concurrent/DefaultPromise.notifyListener0
cpu self:   0.6% io/netty/channel/nio/NioIoHandler.wakeup
cpu self:   0.6% io/netty/channel/AbstractChannelHandlerContext.fireChannelRead
cpu self:   0.5% pthread_cond_signal
cpu self:   0.5% kotlinx/coroutines/internal/LockFreeLinkedListNode.finishAdd
cpu self:   0.5% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
## business
clean: rps=79337 p50=0.73ms p99=2.05ms requests=100.0% ok
cost: cpu=778.9s over 120.0s = 6.49 cores busy, 82 us cpu/req, 0.63 ctx/req, threads=43, peak rss=897 MiB, requests=9520958
rps=79462 p50=0.73ms p99=1.99ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty/business.cpu.collapsed  (samples: 185528)
| category | self | owner |
|---|---|---|
| coroutines |   7.3% |   8.9% |
| serialization |   4.7% |   9.6% |
| netty |  12.9% |  43.1% |
| user |   3.0% |   6.1% |
| ktor |  10.6% |  16.0% |
| kotlinx |   0.7% |   1.3% |
| kotlin |   5.6% |  14.7% |
| jdk |  22.8% |   0.0% |
| slf4j |   0.1% |   0.1% |
| jvm |  32.2% |   0.3% |
| user code anywhere on the stack |  40.2% | |

## /home/youndie/bench-results/engine-netty/business.alloc.collapsed  (samples: 286179961802)
| category | self | owner |
|---|---|---|
| coroutines |   4.9% |   5.5% |
| serialization |   2.5% |  17.5% |
| netty |   6.7% |  10.6% |
| user |   4.2% |  12.0% |
| ktor |  18.5% |  27.8% |
| kotlinx |   0.4% |   2.0% |
| kotlin |   5.2% |  24.6% |
| jdk |  32.3% |   0.0% |
| jvm |  25.4% |   0.0% |
| user code anywhere on the stack |  68.9% | |

cpu self:   6.9% writev
cpu self:   5.8% __write
cpu self:   5.6% epoll_wait
cpu self:   3.5% read
cpu self:   3.1% itable stub
cpu self:   1.6% bench/Pricing.quote
cpu self:   1.3% java/util/HashMap.getNode
cpu self:   1.0% io/ktor/util/pipeline/SuspendFunctionGun.loop
cpu self:   0.9% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.9% java/lang/String.equals
cpu self:   0.8% jbyte_disjoint_arraycopy
cpu self:   0.7% java/lang/String.length
cpu self:   0.6% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.6% __vdso_clock_gettime
cpu self:   0.6% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   0.5% kotlinx/coroutines/internal/LockFreeLinkedListNode.finishAdd
cpu self:   0.5% kotlinx/coroutines/CoroutineContextKt$$Lambda.0x000000003d10e328.invoke
cpu self:   0.5% java/lang/StringLatin1.indexOf
cpu self:   0.5% io/netty/util/concurrent/DefaultPromise.notifyListener0
cpu self:   0.5% io/netty/util/AsciiString.contentEqualsIgnoreCase
## GC and JIT from the service log
lines=8
