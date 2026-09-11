#!/usr/bin/env bash
# Throughput and CPU cost of the three engines, without profilers, variants alternating (D2 of the
# optimizer research: one run per variant is not a measurement, and this machine's spread is ±15 %).
# Every window also reports what the process burned: utime+stime over the window divided by the
# requests served in it, which is the number a profile of ratios cannot give.
#
# Usage:  JAVA_HOME=… [VARIANTS="cio netty jetty"] [REPS=3] [WARMUP=30] [MEASURE=60] [CONNS=64] [LABEL=engines-ab] ./engines-ab.sh
set -u
cd "$(dirname "$0")/.."
# A variant is `<engine>` or `<engine>@<extra JVM options>`; what follows `@` goes to the JVM of
# that variant only, which is how a knob (`-Dkotlinx.coroutines.io.parallelism=8`) is compared with
# the default by alternating the two rather than by two separate runs on a machine that drifts.
VARIANTS=${VARIANTS:-"cio netty jetty"}
key() { printf '%s' "$1" | tr -c 'a-zA-Z0-9' '_'; }   # a variant name that can be a file name
REPS=${REPS:-3}; WARMUP=${WARMUP:-30}; MEASURE=${MEASURE:-60}; CONNS=${CONNS:-64}; LABEL=${LABEL:-engines-ab}
PORT=18100; OHA=${OHA:-$HOME/tools/oha}; JVM_CPUS=${JVM_CPUS:-0-7}; LOAD_CPUS=${LOAD_CPUS:-8-15}
JAVA=${JAVA_HOME:?set JAVA_HOME}/bin/java
START=${START:-"$(pwd)/build/install/bench/bin/bench"}
OUT=${RESULTS:-$HOME/bench-results}/$LABEL; rm -rf "$OUT"; mkdir -p "$OUT"
ENDPOINTS=${ENDPOINTS:-"echo items business"}
TICK=$(getconf CLK_TCK)
echo "# $(date -u +%FT%TZ) label=$LABEL variants='$VARIANTS' reps=$REPS warmup=${WARMUP}s measure=${MEASURE}s conns=$CONNS" | tee "$OUT/summary.md"
"$JAVA" -version 2>&1 | sed -n 2p | sed 's/^/# /' | tee -a "$OUT/summary.md"

cpu_ticks() { sed 's/.*) //' "/proc/$PID/stat" | awk '{print $12+$13}'; }
seed() { for i in $(seq 200); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d "{\"sku\":\"AB-$(printf %04d $i)\",\"name\":\"item $i\",\"price\":$i.5,\"tags\":[\"a\",\"b\"]}" "http://127.0.0.1:$PORT/items"; done; }

measure() { # $1 variant $2 rep $3 name, rest = oha args
  local v=$1 r=$2 name=$3; shift 3
  taskset -c "$LOAD_CPUS" "$OHA" -z "${WARMUP}s" -c "$CONNS" --no-tui "$@" > /dev/null 2>&1
  local t0 t1; t0=$(cpu_ticks)
  taskset -c "$LOAD_CPUS" "$OHA" -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json "$@" > "$OUT/$v.$r.$name.json" 2>&1
  t1=$(cpu_ticks)
  echo "$((t1 - t0)) $TICK" > "$OUT/$v.$r.$name.cpu"
  python3 - "$OUT/$v.$r.$name.json" "$v" "$r" "$name" "$t0" "$t1" "$TICK" <<'PY' | tee -a "$OUT/summary.md"
import json,sys
d=json.load(open(sys.argv[1])); s=d["summary"]; p=d["latencyPercentiles"]
t0,t1,tick=(int(x) for x in sys.argv[5:8]); cpu=(t1-t0)/tick
reqs=sum(int(v) for v in d["statusCodeDistribution"].values())
print(f"{sys.argv[2]} rep{sys.argv[3]} {sys.argv[4]}: rps={s['requestsPerSec']:.0f} p50={p['p50']*1000:.2f}ms "
      f"p99={p['p99']*1000:.2f}ms ok={s['successRate']*100:.1f}% cpu={cpu/reqs*1e6:.0f}us/req cores={cpu/s['total']:.2f}")
PY
}

for r in $(seq "$REPS"); do
  for v in $VARIANTS; do
    PIN=; [ "$JVM_CPUS" = none ] || PIN="taskset -c $JVM_CPUS"   # on the exec: see run.sh
    engine=${v%%@*}; extra=""; [ "$v" = "$engine" ] || extra=${v#*@}; k=$(key "$v")
    JAVA_OPTS="${JAVA_OPTS:-} -Dbench.engine=$engine $extra -Xms1g -Xmx1g -XX:+UseG1GC" $PIN $START > "$OUT/$k.$r.service.log" 2>&1 & PID=$!
    for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health" 2>/dev/null | grep -q 200 && break; sleep 0.1; done
    seed
    for ep in $ENDPOINTS; do
      case $ep in
        echo) measure "$k" "$r" echo "http://127.0.0.1:$PORT/echo?msg=hello-from-oha" ;;
        items) measure "$k" "$r" items "http://127.0.0.1:$PORT/items?limit=20" ;;
        blocking) measure "$k" "$r" blocking "http://127.0.0.1:$PORT/blocking" ;;
        business) measure "$k" "$r" business -m POST -T application/json -D profile/order.json "http://127.0.0.1:$PORT/business" ;;
      esac
    done
    awk '/VmHWM/{print "peak rss " $2/1024 " MiB"}' /proc/$PID/status | sed "s|^|$v rep$r |" | tee -a "$OUT/summary.md"
    kill -TERM $PID; wait $PID 2>/dev/null
  done
done

echo; echo "## medians over $REPS reps" | tee -a "$OUT/summary.md"
python3 - "$OUT" "$REPS" "$VARIANTS" "$ENDPOINTS" <<'PY' | tee -a "$OUT/summary.md"
import json,re,sys,statistics
out,reps,variants,endpoints=sys.argv[1],int(sys.argv[2]),sys.argv[3].split(),sys.argv[4].split()
key=lambda v: re.sub(r"[^A-Za-z0-9]", "_", v)
print("| endpoint | variant | rps median (runs) | p50 | p99 | us cpu/req | cores busy |")
print("|---|---|---|---|---|---|---|")
for name in endpoints:
    for v in variants:
        rps=[];p50=[];p99=[];cpu=[];cores=[]
        for r in range(1,reps+1):
            d=json.load(open(f"{out}/{key(v)}.{r}.{name}.json")); s=d["summary"]
            ticks,tick=(int(x) for x in open(f"{out}/{key(v)}.{r}.{name}.cpu").read().split())
            reqs=sum(int(x) for x in d["statusCodeDistribution"].values())
            rps.append(s["requestsPerSec"]); p50.append(d["latencyPercentiles"]["p50"]*1000)
            p99.append(d["latencyPercentiles"]["p99"]*1000)
            cpu.append(ticks/tick/reqs*1e6); cores.append(ticks/tick/s["total"])
        m=statistics.median
        print(f"| {name} | {v} | {m(rps):.0f} ({' '.join(f'{x:.0f}' for x in sorted(rps))}) | {m(p50):.2f} ms | "
              f"{m(p99):.2f} ms | {m(cpu):.0f} ({' '.join(f'{x:.0f}' for x in sorted(cpu))}) | {m(cores):.2f} |")
PY
