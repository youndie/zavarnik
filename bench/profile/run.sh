#!/usr/bin/env bash
# RQ0 profile: for each endpoint, warm up under load, then measure rps/latency with oha while
# async-profiler samples CPU and allocations of the service. Results go to results/<label>/.
#
# Usage:  JAVA_HOME=… [WARMUP=60] [MEASURE=120] [CONNS=64] [LABEL=baseline] [START=<cmd>] [RESULTS=<dir>] ./run.sh
#   START overrides how the service is launched (default: the installDist start script), which
#   is how the R8-processed jar is measured with the same harness. CATEGORIES is passed on to
#   attribute.py as --categories, and SELF_FRAMES is how many leaf frames the summary lists.
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

# Pinning has to be on the exec, not on the running process: `taskset -p <pid>` sets the affinity
# of one *thread* (the main one), and threads created before it — all of the JVM's — keep the full
# mask. Measured 10.09.2026: after `taskset -pc 0-7 $PID`, 49 of the 50 threads still read
# `Cpus_allowed_list: 0-19`, and the process burned 12.6 cores while nominally capped at 8. Pinned
# on the exec the JVM also sizes its pools to the 8 processors it can see (30 threads, not 50).
JVM_CPUS=${JVM_CPUS:-0-7}; LOAD_CPUS=${LOAD_CPUS:-8-15}
PIN=; [ "$JVM_CPUS" = none ] || PIN="taskset -c $JVM_CPUS"
JAVA_OPTS="${JAVA_OPTS:-} -Xms1g -Xmx1g -XX:+UseG1GC" $PIN $START > "$OUT/service.log" 2>&1 & PID=$!
trap 'kill -TERM $PID 2>/dev/null; wait $PID 2>/dev/null' EXIT
for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health" 2>/dev/null | grep -q 200 && break; sleep 0.1; done
# The store needs rows for the read endpoints.
for i in $(seq 200); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d "{\"sku\":\"AB-$(printf %04d $i)\",\"name\":\"item $i\",\"price\":$i.5,\"tags\":[\"a\",\"b\"]}" "http://127.0.0.1:$PORT/items"; done

# JVM pinned to 8 cores (on the exec, above), the load generator to 8 others: the profile is of
# the service, not of oha, and the two do not compete for the same cores.

# What the process costs, not what the profile says it costs: a profile is a set of ratios, and
# ratios cannot separate an engine that burns CPU from one that waits for it. utime+stime over the
# clean window, divided by the requests served in it, is the absolute number. The comm field is
# stripped by sed because it is parenthesised and may contain spaces.
TICK=$(getconf CLK_TCK)
cpu_ticks() { sed 's/.*) //' "/proc/$PID/stat" | awk '{print $12+$13}'; }
ctx_switches() { awk '/ctxt_switches/ {n+=$2} END {print n+0}' /proc/$PID/task/*/status 2>/dev/null; }
threads() { ls /proc/$PID/task 2>/dev/null | wc -l; }

run_endpoint() { # $1 name, rest = oha args
  local name=$1; shift
  echo; echo "## $name" | tee -a "$OUT/summary.md"
  taskset -c "$LOAD_CPUS" "$OHA" -z "${WARMUP}s" -c "$CONNS" --no-tui "$@" > /dev/null 2>&1
  # A clean window first: the CPU profiler at 1 ms costs throughput (echo: 35.7k rps under it,
  # 44.9k without), so rps and latency come from here and the profiles from the two windows after.
  local t0 x0 t1 x1
  t0=$(cpu_ticks); x0=$(ctx_switches)
  taskset -c "$LOAD_CPUS" "$OHA" -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json "$@" > "$OUT/$name.oha0.json" 2>&1
  t1=$(cpu_ticks); x1=$(ctx_switches)
  python3 - "$OUT/$name.oha0.json" <<'PY' | sed 's/^/clean: /' | tee -a "$OUT/summary.md"
import json,sys; d=json.load(open(sys.argv[1])); s=d["summary"]; p=d["latencyPercentiles"]
print(f"rps={s['requestsPerSec']:.0f} p50={p['p50']*1000:.2f}ms p99={p['p99']*1000:.2f}ms requests={s['successRate']*100:.1f}% ok")
PY
  # The cost line of the same clean window: CPU the process actually burned per request.
  python3 - "$OUT/$name.oha0.json" "$t0" "$t1" "$x0" "$x1" "$TICK" "$(threads)" "$(awk '/VmHWM/{print $2}' /proc/$PID/status)" <<'PY' | tee -a "$OUT/summary.md"
import json,sys
d=json.load(open(sys.argv[1])); t0,t1,x0,x1,tick=(int(v) for v in sys.argv[2:7]); thr,hwm=int(sys.argv[7]),int(sys.argv[8])
reqs=sum(int(v) for v in d["statusCodeDistribution"].values()); secs=d["summary"]["total"]; cpu=(t1-t0)/tick
print(f"cost: cpu={cpu:.1f}s over {secs:.1f}s = {cpu/secs:.2f} cores busy, {cpu/reqs*1e6:.0f} us cpu/req, "
      f"{(x1-x0)/reqs:.2f} ctx/req, threads={thr}, peak rss={hwm/1024:.0f} MiB, requests={reqs}")
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
    python3 profile/attribute.py ${CATEGORIES:+--categories "$CATEGORIES"} "$OUT/$name.cpu.collapsed" "$OUT/$name.alloc.collapsed" | tee -a "$OUT/summary.md"
  else
    python3 profile/attribute.py ${CATEGORIES:+--categories "$CATEGORIES"} "$OUT/$name.cpu.collapsed" | tee -a "$OUT/summary.md"
  fi
  awk '{n=split($0,a,";"); leaf=a[n]; sub(/ [0-9]+$/,"",leaf); c[leaf]+=$NF; t+=$NF} END{for(k in c) printf "%5.1f%% %s\n", 100*c[k]/t, k}' "$OUT/$name.cpu.collapsed" | sort -nr | head -${SELF_FRAMES:-5} | sed 's/^/cpu self: /' | tee -a "$OUT/summary.md"
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
