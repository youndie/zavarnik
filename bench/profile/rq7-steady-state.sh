#!/usr/bin/env bash
# RQ7: is steady state stable?
#
#   GEN=10.0.0.3 TARGET=10.0.0.2 ./rq7-steady-state.sh
#
# The brief's green is "under 1 deoptimisation per minute after warmup, and exception construction
# under 2% of request CPU"; its red is "a deoptimisation recurring at the same site, or exception
# construction at 2% or more". So the rate alone does not decide it - a recurring SITE is red at any
# rate, which means the events have to be grouped by method and bci, not counted.
#
# JFR is the instrument the brief names, and 1.3 is why the settings are spelled out here rather than
# taken from a profile: on the shipped settings `jdk.Compilation` has a 100 ms threshold and reports
# nothing on a service that compiles in tens of milliseconds, so a gate phrased against it cannot
# fail. `jdk.Deoptimization` is enabled in profile.jfc, but its threshold is set explicitly anyway,
# for the same reason: a default that silently drops events looks exactly like a stable steady state.
set -uo pipefail
cd "$(dirname "$0")/.."
GEN=${GEN:?set GEN}; TARGET=${TARGET:?set TARGET}
PORT=18100; CONNS=${CONNS:-64}; WARM=${WARM:-90}; MEASURE=${MEASURE:-180}
OUT=${OUT:-$HOME/bench-results}/rq7; mkdir -p "$OUT"
export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
JFR="$JAVA_HOME/bin/jfr"
GEN_BODY=${GEN_BODY:-'~/new-item.json'}
url() { case $1 in
  dbitem) echo "http://$TARGET:$PORT/db/items/42" ;;
  dblist) echo "http://$TARGET:$PORT/db/items?limit=50" ;;
  dbpost) echo "http://$TARGET:$PORT/db/items" ;; esac; }
post_args() { [ "$1" = dbpost ] && echo "-m POST -T application/json -D $GEN_BODY" || echo ""; }
gen() { ssh -o BatchMode=yes -o StrictHostKeyChecking=no "$GEN" "~/tools/oha $*"; }

# A long warm-up on purpose: the whole question is what happens AFTER steady state, and 1.13 showed
# a five-second warm-up gives a profile that is mostly C2 still working. The recording starts only
# once the load has been running for $WARM.
ENDPOINTS=${ENDPOINTS:-"dbitem dblist dbpost"}
for ep in $ENDPOINTS; do
  if ss -ltn | grep -q ":$PORT"; then echo "FATAL: $PORT busy"; exit 1; fi
  bash profile/reset-db.sh >/dev/null 2>&1
  JAVA_OPTS="-Dbench.engine=netty -Dbench.data=real -Dbench.host=$TARGET -Xms1g -Xmx1g -XX:+UseG1GC \
    -XX:FlightRecorderOptions=stackdepth=128" \
    build/install/bench/bin/bench > "$OUT/$ep.service.log" 2>&1 &
  pid=$!
  for i in $(seq 150); do curl -s -o /dev/null "http://$TARGET:$PORT/health" 2>/dev/null && break; sleep 0.2; done
  kill -0 "$pid" 2>/dev/null || { echo "FATAL: $ep died"; tail -5 "$OUT/$ep.service.log"; exit 1; }
  gen -z "${WARM}s" -c "$CONNS" --no-tui $(post_args "$ep") "$(url "$ep")" >/dev/null 2>&1
  # Started AFTER the warm-up, so the recording contains steady state and nothing else.
  "$JAVA_HOME/bin/jcmd" "$pid" JFR.start name=rq7 filename="$OUT/$ep.jfr" \
     settings=profile "+jdk.Deoptimization#enabled=true" "+jdk.Deoptimization#threshold=0ms" \
     "+jdk.Compilation#enabled=true" "+jdk.Compilation#threshold=0ms" \
     "+jdk.CompilationFailure#enabled=true" >/dev/null 2>&1
  gen -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json $(post_args "$ep") "$(url "$ep")" > "$OUT/$ep.oha.json" 2>/dev/null
  "$JAVA_HOME/bin/jcmd" "$pid" JFR.stop name=rq7 >/dev/null 2>&1
  kill -TERM "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  [ -s "$OUT/$ep.jfr" ] || { echo "FATAL: $ep recording is empty - the events were not enabled"; exit 1; }
  "$JFR" summary "$OUT/$ep.jfr" > "$OUT/$ep.summary.txt" 2>&1
  "$JFR" print --events jdk.Deoptimization "$OUT/$ep.jfr" > "$OUT/$ep.deopt.txt" 2>&1
  python3 - "$OUT/$ep.deopt.txt" "$OUT/$ep.oha.json" "$ep" "$MEASURE" <<'PY'
import collections, json, re, sys
txt = open(sys.argv[1]).read()
d = json.load(open(sys.argv[2])); ep, secs = sys.argv[3], float(sys.argv[4])
blocks = txt.split("jdk.Deoptimization {")
sites = collections.Counter(); reasons = collections.Counter()
for b in blocks[1:]:
    m = re.search(r"method = (.+)", b); r = re.search(r"reason = (.+)", b)
    bci = re.search(r"bci = (\d+)", b)
    if m: sites["%s@%s" % (m.group(1).strip(), bci.group(1) if bci else "?")] += 1
    if r: reasons[r.group(1).strip()] += 1
n = len(blocks) - 1
print("== %s  %d deoptimisations in %.0f s = %.2f per minute   (%.0f rps)"
      % (ep, n, secs, n / secs * 60, d["summary"]["requestsPerSec"]))
for s, c in sites.most_common(5):
    print("   %5d  %s" % (c, s))
if reasons:
    print("   reasons: " + ", ".join("%s=%d" % kv for kv in reasons.most_common(5)))
PY
done
echo DONE
