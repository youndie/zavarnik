# 2026-09-11T00:52:54Z label=engine-jetty-c256 warmup=60s measure=120s conns=256
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=71193 p50=2.91ms p99=18.46ms requests=100.0% ok
cost: cpu=947.8s over 120.0s = 7.90 cores busy, 111 us cpu/req, 3.06 ctx/req, threads=292, peak rss=918 MiB, requests=8544237
rps=68836 p50=2.95ms p99=20.27ms requests=100.0% ok
## /home/youndie/bench-results/engine-jetty-c256/business.cpu.collapsed  (samples: 221889)
| category | self | owner |
|---|---|---|
| coroutines |   6.0% |  17.4% |
| serialization |   3.9% |   7.9% |
| jetty |  10.8% |  27.6% |
| user |   1.9% |   4.5% |
| ktor |   7.4% |  10.8% |
| kotlinx |   1.1% |   1.5% |
| kotlin |   6.6% |  11.9% |
| jdk |  23.4% |   7.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  38.9% |  11.4% |
| user code anywhere on the stack |  44.8% | |

cpu self:  10.0% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   9.1% pthread_cond_signal
cpu self:   6.5% writev
cpu self:   5.6% java/util/concurrent/LinkedTransferQueue$DualNode.await
cpu self:   4.0% org/eclipse/jetty/util/ArrayTrie.getBest
cpu self:   2.6% read
cpu self:   2.5% java/util/HashMap.getNode
cpu self:   2.3% java/util/concurrent/locks/LockSupport.unpark
cpu self:   2.2% itable stub
cpu self:   1.0% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.7% org/eclipse/jetty/util/ArrayTrie.lookup
cpu self:   0.7% kotlinx/coroutines/internal/LockFreeLinkedListNode.correctPrev
cpu self:   0.7% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.7% jbyte_disjoint_arraycopy
cpu self:   0.7% java/util/ArrayList.grow
cpu self:   0.7% java/lang/String.equalsIgnoreCase
cpu self:   0.7% epoll_ctl
cpu self:   0.7% bench/Pricing.quote
cpu self:   0.6% org/eclipse/jetty/util/ConcurrentPool.acquire
cpu self:   0.6% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
## GC and JIT from the service log
lines=6
