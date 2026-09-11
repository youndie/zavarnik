# 2026-09-06T22:22:02Z konekt screens rate=50 warmup=60s measure=120s container=konekt-server-1
# OpenJDK Runtime Environment Temurin-25.0.4+7 (build 25.0.4+7-LTS)
# image=ghcr.io/youndie/konekt-server:v0.1.40 cpus=1000000000 mem=1073741824
# java pid in container: 1
   ✓ checks.........................: 100.00% 16531 out of 16531
     http_req_duration..............: avg=2.51ms   min=548.31µs med=1.68ms  max=1.91s   p(90)=3.56ms  p(95)=3.97ms 
     http_reqs......................: 16681   47.022442/s
## /home/youndie/bench-results/konekt-screens-50/screens.cpu.collapsed  (samples: 2817)
| category | self | owner |
|---|---|---|
| user |   2.5% |   4.0% |
| kompot |   0.5% |   0.5% |
| exposed |   2.1% |   4.7% |
| postgres |   1.8% |  18.2% |
| ktor |   5.5% |  13.0% |
| kotlinx |   8.3% |  21.7% |
| kotlin |   4.8% |   8.8% |
| jdk |  12.6% |   0.2% |
| other |   0.5% |   2.0% |
| jvm |  61.4% |  26.9% |
| user code anywhere on the stack |  47.8% | |

## /home/youndie/bench-results/konekt-screens-50/screens.alloc.collapsed  (samples: 568851395)
| category | self | owner |
|---|---|---|
| user |   1.0% |   3.4% |
| kompot |   1.1% |   1.1% |
| exposed |   3.6% |  13.5% |
| postgres |   1.9% |   5.0% |
| ktor |  13.3% |  24.8% |
| kotlinx |   4.1% |  13.6% |
| kotlin |   6.5% |  26.6% |
| jdk |  29.4% |   0.0% |
| other |   3.2% |  11.9% |
| jvm |  35.9% |   0.0% |
| user code anywhere on the stack |  53.8% | |

cpu self:  22.6% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   4.2% pthread_cond_signal
cpu self:   2.5% itable stub
cpu self:   0.8% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.7% java/lang/String.length
cpu self:   0.7% IndexSetIterator::advance_and_next
cpu self:   0.6% kotlin/jvm/internal/Intrinsics.checkNotNullParameter
cpu self:   0.6% PhaseChaitin::Split
