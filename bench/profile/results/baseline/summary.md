# 2026-09-06T20:18:28Z label=baseline warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## echo
rps=35740 p50=1.16ms p99=11.40ms requests=100.0% ok
## /home/youndie/bench-results/baseline/echo.cpu.collapsed  (samples: 231552)
| category | self | owner |
|---|---|---|
| user |   0.0% |   0.0% |
| ktor |   8.8% |  21.2% |
| kotlinx |  40.1% |  72.7% |
| kotlin |   3.8% |   5.8% |
| jdk |   4.7% |   0.0% |
| jvm |  42.7% |   0.3% |
| user code anywhere on the stack |   6.9% | |

## /home/youndie/bench-results/baseline/echo.alloc.collapsed  (samples: 80727090825)
| category | self | owner |
|---|---|---|
| user |   0.2% |   0.2% |
| ktor |  44.0% |  69.6% |
| kotlinx |  11.7% |  14.5% |
| kotlin |   7.7% |  15.7% |
| jdk |  25.9% |   0.0% |
| jvm |  10.3% |   0.0% |
| user code anywhere on the stack |  13.8% | |

## items
rps=38008 p50=1.27ms p99=7.98ms requests=100.0% ok
## /home/youndie/bench-results/baseline/items.cpu.collapsed  (samples: 274904)
| category | self | owner |
|---|---|---|
| user |   0.8% |   0.8% |
| ktor |   9.8% |  22.7% |
| kotlinx |  36.9% |  67.4% |
| kotlin |   4.8% |   9.0% |
| slf4j |   0.0% |   0.0% |
| jdk |   9.0% |   0.0% |
| jvm |  38.7% |   0.1% |
| user code anywhere on the stack |  16.9% | |

## /home/youndie/bench-results/baseline/items.alloc.collapsed  (samples: 114039762518)
| category | self | owner |
|---|---|---|
| user |   0.3% |   0.3% |
| ktor |  30.6% |  48.0% |
| kotlinx |   9.1% |  26.2% |
| kotlin |   6.2% |  25.5% |
| jdk |  28.3% |   0.0% |
| jvm |  25.5% |   0.0% |
| user code anywhere on the stack |  45.5% | |

## business
rps=33935 p50=1.18ms p99=10.24ms requests=100.0% ok
## /home/youndie/bench-results/baseline/business.cpu.collapsed  (samples: 324180)
| category | self | owner |
|---|---|---|
| user |   1.1% |   2.1% |
| ktor |   7.5% |  15.5% |
| kotlinx |  51.4% |  73.9% |
| kotlin |   4.2% |   8.4% |
| slf4j |   0.0% |   0.0% |
| jdk |   8.7% |   0.0% |
| jvm |  27.0% |   0.1% |
| user code anywhere on the stack |  20.0% | |

## /home/youndie/bench-results/baseline/business.alloc.collapsed  (samples: 124440568024)
| category | self | owner |
|---|---|---|
| user |   3.6% |   9.9% |
| ktor |  25.7% |  36.8% |
| kotlinx |  10.9% |  26.4% |
| kotlin |   8.2% |  26.8% |
| jdk |  28.3% |   0.0% |
| jvm |  23.3% |   0.0% |
| user code anywhere on the stack |  65.5% | |

## GC and JIT from the service log
lines=3
