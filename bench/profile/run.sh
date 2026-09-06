#!/usr/bin/env bash
# RQ0 profile: for each endpoint, warm up under load, then measure rps/latency with oha while
# async-profiler samples CPU and allocations of the service. Results go to results/<label>/.
#
# Usage:  JAVA_HOME=… [WARMUP=60] [MEASURE=120] [CONNS=64] [LABEL=baseline] [START=<cmd>] [RESULTS=<dir>] ./run.sh
#   START overrides how the service is launched (default: the installDist start script), which
#   is how the R8-processed jar is measured with the same harness.
set -u
cd "$(dirname "$0")/.."
WARMUP=${WARMUP:-60}; MEASURE=${MEASURE:-120}; CONNS=${CONNS:-64}; LABEL=${LABEL:-baseline}
PORT=18100; OHA=${OHA:-$HOME/tools/oha}; ASPROF=${ASPROF:-$HOME/tools/async-profiler-4.5-linux-x64/bin/asprof}
JAVA=${JAVA_HOME:?set JAVA_HOME}/bin/java
# Outside the source tree on purpose: a one-way replica (mutagen) deletes files the run writes
# into the synced directory, mid-run. Copy the label's directory into bench/profile/results/
# afterwards, from the machine that owns the tree.
OUT=${RESULTS:-$HOME/bench-results}/$LABEL; rm -rf "$OUT"; mkdir -p "$OUT"
START=${START:-"$(pwd)/build/install/bench/bin/bench"}
echo "# $(date -u +%FT%TZ) label=$LABEL warmup=${WARMUP}s measure=${MEASURE}s conns=$CONNS" | tee "$OUT/summary.md"
"$JAVA" -version 2>&1 | sed -n 2p | sed 's/^/# /' | tee -a "$OUT/summary.md"
grep -m1 "model name" /proc/cpuinfo | sed 's/^/# /' | tee -a "$OUT/summary.md"

JAVA_OPTS="${JAVA_OPTS:-} -Xms1g -Xmx1g -XX:+UseG1GC" $START > "$OUT/service.log" 2>&1 & PID=$!
trap 'kill -TERM $PID 2>/dev/null; wait $PID 2>/dev/null' EXIT
for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health" 2>/dev/null | grep -q 200 && break; sleep 0.1; done
# The store needs rows for the read endpoints.
for i in $(seq 200); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d "{\"sku\":\"AB-$(printf %04d $i)\",\"name\":\"item $i\",\"price\":$i.5,\"tags\":[\"a\",\"b\"]}" "http://127.0.0.1:$PORT/items"; done

# JVM pinned to 8 cores, the load generator to 8 others: the profile is of the service, not of oha.
JVM_CPUS=${JVM_CPUS:-0-7}; LOAD_CPUS=${LOAD_CPUS:-8-15}
[ "$JVM_CPUS" = none ] || taskset -pc "$JVM_CPUS" $PID >/dev/null

run_endpoint() { # $1 name, rest = oha args
  local name=$1; shift
  echo; echo "## $name" | tee -a "$OUT/summary.md"
  taskset -c "$LOAD_CPUS" "$OHA" -z "${WARMUP}s" -c "$CONNS" --no-tui "$@" > /dev/null 2>&1
  # A clean window first: the CPU profiler at 1 ms costs throughput (echo: 35.7k rps under it,
  # 44.9k without), so rps and latency come from here and the profiles from the two windows after.
  taskset -c "$LOAD_CPUS" "$OHA" -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json "$@" > "$OUT/$name.oha0.json" 2>&1
  python3 - "$OUT/$name.oha0.json" <<'PY' | sed 's/^/clean: /' | tee -a "$OUT/summary.md"
import json,sys; d=json.load(open(sys.argv[1])); s=d["summary"]; p=d["latencyPercentiles"]
print(f"rps={s['requestsPerSec']:.0f} p50={p['p50']*1000:.2f}ms p99={p['p99']*1000:.2f}ms requests={s['successRate']*100:.1f}% ok")
PY
  "$ASPROF" -d "$MEASURE" -e cpu -i 1ms -o collapsed -f "$OUT/$name.cpu.collapsed" $PID > /dev/null 2>&1 &
  local pc=$!
  taskset -c "$LOAD_CPUS" "$OHA" -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json "$@" > "$OUT/$name.oha.json" 2>&1
  wait $pc
  python3 - "$OUT/$name.oha.json" <<'PY' | tee -a "$OUT/summary.md"
import json,sys; d=json.load(open(sys.argv[1])); s=d["summary"]; p=d["latencyPercentiles"]
print(f"rps={s['requestsPerSec']:.0f} p50={p['p50']*1000:.2f}ms p99={p['p99']*1000:.2f}ms requests={s['successRate']*100:.1f}% ok")
PY
  # Allocation profile in a second window under the same load: the two profilers must not overlap.
  if [[ " ${PROFILES:-cpu alloc} " == *" alloc "* ]]; then
    "$ASPROF" -d "$MEASURE" -e alloc --total -o collapsed -f "$OUT/$name.alloc.collapsed" $PID > /dev/null 2>&1 &
    pc=$!
    taskset -c "$LOAD_CPUS" "$OHA" -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json "$@" > "$OUT/$name.oha2.json" 2>&1
    wait $pc
    python3 profile/attribute.py "$OUT/$name.cpu.collapsed" "$OUT/$name.alloc.collapsed" | tee -a "$OUT/summary.md"
  else
    python3 profile/attribute.py "$OUT/$name.cpu.collapsed" | tee -a "$OUT/summary.md"
  fi
  awk '{n=split($0,a,";"); leaf=a[n]; sub(/ [0-9]+$/,"",leaf); c[leaf]+=$NF; t+=$NF} END{for(k in c) printf "%5.1f%% %s\n", 100*c[k]/t, k}' "$OUT/$name.cpu.collapsed" | sort -nr | head -5 | sed 's/^/cpu self: /' | tee -a "$OUT/summary.md"
}
# ENDPOINTS="echo items business" (default: all); PROFILES="cpu alloc" (default: both).
ENDPOINTS=${ENDPOINTS:-"echo items business"}
for ep in $ENDPOINTS; do
  case $ep in
    echo) run_endpoint echo "http://127.0.0.1:$PORT/echo?msg=hello-from-oha" ;;
    items) run_endpoint items "http://127.0.0.1:$PORT/items?limit=20" ;;
    business) run_endpoint business -m POST -T application/json -D profile/order.json "http://127.0.0.1:$PORT/business" ;;
  esac
done
echo; echo "## GC and JIT from the service log" | tee -a "$OUT/summary.md"
grep -c "" "$OUT/service.log" | sed 's/^/lines=/' | tee -a "$OUT/summary.md"
