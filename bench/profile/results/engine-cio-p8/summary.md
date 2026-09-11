# 2026-09-11T01:03:45Z label=engine-cio-p8 warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=72466 p50=0.82ms p99=2.24ms requests=100.0% ok
cost: cpu=798.1s over 120.0s = 6.65 cores busy, 92 us cpu/req, 1.23 ctx/req, threads=50, peak rss=799 MiB, requests=8696090
rps=69109 p50=0.85ms p99=2.62ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio-p8/business.cpu.collapsed  (samples: 186735)
| category | self | owner |
|---|---|---|
| coroutines |  21.3% |  33.5% |
| serialization |   4.5% |   7.8% |
| user |   1.8% |   4.3% |
| ktor |  15.5% |  33.9% |
| kotlinx |   2.9% |   3.1% |
| kotlin |   9.5% |  16.5% |
| jdk |  13.6% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  30.8% |   1.0% |
| user code anywhere on the stack |  32.3% | |

cpu self:   5.7% __write
cpu self:   4.3% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   4.2% pthread_cond_signal
cpu self:   4.1% itable stub
cpu self:   3.0% epoll_ctl
cpu self:   2.8% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   2.6% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   2.4% read
cpu self:   2.0% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   1.9% java/util/HashMap.getNode
cpu self:   1.2% kotlin/coroutines/CombinedContext.get
cpu self:   1.2% io/ktor/http/cio/internals/CharsKt.equalsLowerCase
cpu self:   1.1% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   1.1% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   1.0% java/lang/AbstractStringBuilder.append
cpu self:   0.9% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.8% kotlinx/serialization/json/internal/StringJsonLexer.consumeKeyString
cpu self:   0.7% kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.findTask
cpu self:   0.7% kotlinx/coroutines/internal/ThreadContextKt$$Lambda.0x000000004b115928.invoke
cpu self:   0.7% kotlin/coroutines/jvm/internal/ContinuationImpl.<init>
## GC and JIT from the service log
lines=3
