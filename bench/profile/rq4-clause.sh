#!/usr/bin/env bash
# RQ4's deciding clause: is at least a third of the Exposed-over-JDBC gap a JIT failure?
#
#   GEN=10.0.0.3 TARGET=10.0.0.2 ./rq4-clause.sh
#
# 1.11 measured the gap and decomposed it into layers; that says WHERE the time goes, not WHY. The
# brief's red needs both halves - above 1.5x, AND at least a third of the gap traceable to failed
# inlining, megamorphic dispatch or failed scalar replacement - and only the first was ever tested.
# This tests the second.
#
# The method is a difference of profiles, not a profile. Two arms behind one binary at the SAME
# fixed rate, so every frame they share cancels and what is left is the layer. Then the extra is
# sorted into "work Exposed does" and "work C2 failed to remove", which is the brief's own
# distinction and the only thing that makes a row a finding rather than a library cost.
#
# PrintInlining runs in a separate pass from the timing, for the reason 1.17 needed: it perturbs
# what it measures, so it may say which refusals happen and must not say what they cost.
set -uo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/warmup.env"
GEN=${GEN:?set GEN}; TARGET=${TARGET:?set TARGET}
PORT=18100; CONNS=${CONNS:-64}; RATE=${RATE:-2000}; WARM=${WARM:-$BENCH_WARMUP}; MEASURE=${MEASURE:-60}
ASPROF=${ASPROF:-$HOME/tools/async-profiler-4.5-linux-x64/bin/asprof}
OUT=${OUT:-$HOME/bench-results}/rq4-clause; mkdir -p "$OUT"
export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
URL="http://$TARGET:$PORT/db/items?limit=50"
gen() { ssh -o BatchMode=yes -o StrictHostKeyChecking=no "$GEN" "~/tools/oha $*"; }

run_arm() { # $1 repo arm, $2 extra JAVA_OPTS, $3 tag
  if ss -ltn | grep -q ":$PORT"; then echo "FATAL: $PORT busy"; exit 1; fi
  bash profile/reset-db.sh >/dev/null 2>&1
  JAVA_OPTS="-Dbench.engine=netty -Dbench.data=real -Dbench.repo=$1 -Dbench.host=$TARGET \
    -Dbench.db.pool=32 -Dkotlinx.coroutines.io.parallelism=16 -Xms1g -Xmx1g -XX:+UseG1GC $2" \
    build/install/bench/bin/bench > "$OUT/$3.service.log" 2>&1 &
  PID=$!
  for i in $(seq 150); do curl -s -o /dev/null "http://$TARGET:$PORT/health" 2>/dev/null && break; sleep 0.2; done
  kill -0 "$PID" 2>/dev/null || { echo "FATAL: $3 died"; tail -5 "$OUT/$3.service.log"; exit 1; }
  gen -z "${WARM}s" -c "$CONNS" -q "$RATE" --no-tui "$URL" >/dev/null 2>&1
}

for arm in jdbc exposed; do
  # timing + CPU + allocation, no diagnostic flags
  run_arm "$arm" "" "$arm"
  TICK=$(getconf CLK_TCK); t() { sed 's/.*) //' "/proc/$PID/stat" | awk '{print $12+$13}'; }
  t0=$(t)
  "$ASPROF" -d "$MEASURE" -e cpu -i 1ms -o collapsed -f "$OUT/$arm.cpu.collapsed" $PID >/dev/null 2>&1 &
  pc=$!
  gen -z "${MEASURE}s" -c "$CONNS" -q "$RATE" --no-tui --output-format json "$URL" > "$OUT/$arm.oha.json" 2>/dev/null
  wait $pc; t1=$(t)
  "$ASPROF" -d "$MEASURE" -e alloc --total -o collapsed -f "$OUT/$arm.alloc.collapsed" $PID >/dev/null 2>&1 &
  pc=$!
  gen -z "${MEASURE}s" -c "$CONNS" -q "$RATE" --no-tui "$URL" >/dev/null 2>&1
  wait $pc
  kill -TERM "$PID" 2>/dev/null; wait "$PID" 2>/dev/null
  for f in "$OUT/$arm.cpu.collapsed" "$OUT/$arm.alloc.collapsed"; do
    [ "$(wc -c < "$f")" -gt 10000 ] || { echo "FATAL: $f is empty - nothing was sampled"; exit 1; }
  done
  TICK=$TICK T0=$t0 T1=$t1 A=$arm S=$MEASURE python3 - "$OUT/$arm.oha.json" <<'PY'
import json, os, sys
d = json.load(open(sys.argv[1])); s = d["summary"]
cpu = (int(os.environ["T1"]) - int(os.environ["T0"])) / float(os.environ["TICK"])
print("%-8s rps=%.0f  %.0f us cpu/req  p50=%.2fms"
      % (os.environ["A"], s["requestsPerSec"], cpu / (s["requestsPerSec"] * float(os.environ["S"])) * 1e6,
         d["latencyPercentiles"]["p50"] * 1000))
PY
  # inlining refusals, separate pass
  run_arm "$arm" "-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining" "$arm-inline"
  gen -z 30s -c "$CONNS" -q "$RATE" --no-tui "$URL" >/dev/null 2>&1
  kill -TERM "$PID" 2>/dev/null; wait "$PID" 2>/dev/null
done
echo DONE
