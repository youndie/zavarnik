#!/usr/bin/env bash
# Throughput A/B without profilers: two launch commands, alternated, N repetitions each, one
# warm-up and one clean measurement window per endpoint per repetition. The profiled harness
# (run.sh) answers "where"; this one answers "how much", because a CPU profiler at 1 ms and a
# busy neighbour on the same box both move rps by more than the effects under test.
#
# Usage:  JAVA_HOME=… A="<start cmd>" B="<start cmd>" [REPS=3] [WARMUP=30] [MEASURE=60] [LABEL=ab] ./ab.sh
set -u
cd "$(dirname "$0")/.."
REPS=${REPS:-3}; WARMUP=${WARMUP:-30}; MEASURE=${MEASURE:-60}; CONNS=${CONNS:-64}; LABEL=${LABEL:-ab}
PORT=18100; OHA=${OHA:-$HOME/tools/oha}; JVM_CPUS=${JVM_CPUS:-0-7}; LOAD_CPUS=${LOAD_CPUS:-8-15}
OUT=${RESULTS:-$HOME/bench-results}/$LABEL; rm -rf "$OUT"; mkdir -p "$OUT"
echo "# $(date -u +%FT%TZ) label=$LABEL reps=$REPS warmup=${WARMUP}s measure=${MEASURE}s conns=$CONNS" | tee "$OUT/summary.md"
echo "# A: $A" | tee -a "$OUT/summary.md"; echo "# B: $B" | tee -a "$OUT/summary.md"
seed() { for i in $(seq 200); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d "{\"sku\":\"AB-$(printf %04d $i)\",\"name\":\"item $i\",\"price\":$i.5,\"tags\":[\"a\",\"b\"]}" "http://127.0.0.1:$PORT/items"; done; }
measure() { # $1 variant $2 rep $3 name, rest = oha args
  local v=$1 r=$2 name=$3; shift 3
  taskset -c "$LOAD_CPUS" "$OHA" -z "${WARMUP}s" -c "$CONNS" --no-tui "$@" > /dev/null 2>&1
  taskset -c "$LOAD_CPUS" "$OHA" -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json "$@" > "$OUT/$v.$r.$name.json" 2>&1
  python3 - "$OUT/$v.$r.$name.json" "$v" "$r" "$name" <<'PY' | tee -a "$OUT/summary.md"
import json,sys; d=json.load(open(sys.argv[1])); s=d["summary"]; p=d["latencyPercentiles"]
print(f"{sys.argv[2]} rep{sys.argv[3]} {sys.argv[4]}: rps={s['requestsPerSec']:.0f} p50={p['p50']*1000:.2f}ms p99={p['p99']*1000:.2f}ms ok={s['successRate']*100:.1f}%")
PY
}
for r in $(seq "$REPS"); do
  for v in A B; do
    cmd=${!v}
    JAVA_OPTS="${JAVA_OPTS:-} -Xms1g -Xmx1g -XX:+UseG1GC" $cmd > "$OUT/$v.$r.service.log" 2>&1 & PID=$!
    for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health" 2>/dev/null | grep -q 200 && break; sleep 0.1; done
    taskset -pc "$JVM_CPUS" $PID >/dev/null; seed
    measure $v $r echo "http://127.0.0.1:$PORT/echo?msg=hello-from-oha"
    measure $v $r items "http://127.0.0.1:$PORT/items?limit=20"
    measure $v $r business -m POST -T application/json -D profile/order.json "http://127.0.0.1:$PORT/business"
    kill -TERM $PID; wait $PID 2>/dev/null
  done
done
echo "## medians" | tee -a "$OUT/summary.md"
python3 - "$OUT" "$REPS" <<'PY' | tee -a "$OUT/summary.md"
import json,sys,statistics,glob,os
out,reps=sys.argv[1],int(sys.argv[2])
print("| endpoint | A rps median | B rps median | B/A | A p99 | B p99 |")
print("|---|---|---|---|---|---|")
for name in ["echo","items","business"]:
    m={}
    for v in "AB":
        rps=[];p99=[]
        for r in range(1,reps+1):
            d=json.load(open(f"{out}/{v}.{r}.{name}.json")); rps.append(d["summary"]["requestsPerSec"]); p99.append(d["latencyPercentiles"]["p99"]*1000)
        m[v]=(statistics.median(rps),statistics.median(p99),rps)
    print(f"| {name} | {m['A'][0]:.0f} ({' '.join(f'{x:.0f}' for x in sorted(m['A'][2]))}) | {m['B'][0]:.0f} ({' '.join(f'{x:.0f}' for x in sorted(m['B'][2]))}) | {m['B'][0]/m['A'][0]:.3f} | {m['A'][1]:.2f} ms | {m['B'][1]:.2f} ms |")
PY
