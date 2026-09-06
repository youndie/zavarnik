#!/usr/bin/env bash
# The methodology of run.sh on a real service: konekt's measurement stand (its own compose with
# the chart's limits, its own k6 scenario), profiled from inside the server container.
#
# Usage, on the box that runs the stand, from a checkout of konekt:
#     KONEKT=~/konekt ZAVARNIK=~/zavarnik [SCENARIO=screens] [RATE=50] [WARMUP=60] [MEASURE=120] konekt.sh
# What it does: copies async-profiler into the container, starts the k6 scenario at one constant
# arrival rate for the whole window, waits out the warm-up (k6's setup signs subscribers in),
# samples CPU (itimer — perf_event_open is blocked by the container's seccomp profile) and then
# allocations, copies the collapsed stacks out and attributes them with konekt's own categories:
# `io.konekt` is the user code; kompot and the other youndie toolkits are the owner's libraries
# but libraries all the same, and the question is what an IR plugin over io.konekt could move.
set -u
KONEKT=${KONEKT:-$HOME/konekt}; ZAVARNIK=${ZAVARNIK:-$HOME/zavarnik}
SCENARIO=${SCENARIO:-screens}; RATE=${RATE:-50}; WARMUP=${WARMUP:-60}; MEASURE=${MEASURE:-120}
CONT=${CONT:-konekt-server-1}; ASPROF=${ASPROF:-$HOME/tools/async-profiler-4.5-linux-x64}
OUT=${RESULTS:-$HOME/bench-results}/konekt-$SCENARIO-$RATE; rm -rf "$OUT"; mkdir -p "$OUT"
echo "# $(date -u +%FT%TZ) konekt $SCENARIO rate=$RATE warmup=${WARMUP}s measure=${MEASURE}s container=$CONT" | tee "$OUT/summary.md"
docker exec "$CONT" java -version 2>&1 | sed -n 2p | sed 's/^/# /' | tee -a "$OUT/summary.md"
docker inspect "$CONT" --format '# image={{.Config.Image}} cpus={{.HostConfig.NanoCpus}} mem={{.HostConfig.Memory}}' | tee -a "$OUT/summary.md"
docker cp "$ASPROF" "$CONT:/tmp/asprof" && docker exec -u root "$CONT" chmod -R a+rX /tmp/asprof
PID=$(docker exec "$CONT" sh -c 'pgrep -o java'); echo "# java pid in container: $PID" | tee -a "$OUT/summary.md"

HOLD=$((WARMUP + 2 * MEASURE + 30))
(cd "$KONEKT" && scripts/measure/k6.sh "$SCENARIO" "RATES=$RATE" "HOLD=$HOLD" > "$OUT/k6.log" 2>&1) & K6=$!
sleep "$WARMUP"
docker exec "$CONT" /tmp/asprof/bin/asprof -d "$MEASURE" -e itimer -i 1ms -o collapsed -f /tmp/cpu.collapsed "$PID" 2>&1 | tail -2 | sed 's/^/asprof cpu: /'
docker exec "$CONT" /tmp/asprof/bin/asprof -d "$MEASURE" -e alloc --total -o collapsed -f /tmp/alloc.collapsed "$PID" 2>&1 | tail -2 | sed 's/^/asprof alloc: /'
wait $K6
docker cp "$CONT:/tmp/cpu.collapsed" "$OUT/$SCENARIO.cpu.collapsed"; docker cp "$CONT:/tmp/alloc.collapsed" "$OUT/$SCENARIO.alloc.collapsed"
grep -E "http_reqs|http_req_duration|dropped_iterations|checks" "$OUT/k6.log" | head -8 | tee -a "$OUT/summary.md"
python3 "$ZAVARNIK/bench/profile/attribute.py" --categories "user=io.konekt.;kompot=io.github.youndie.kompot.;youndie-libs=io.github.youndie.;exposed=org.jetbrains.exposed.;postgres=org.postgresql.,com.zaxxer." \
  "$OUT/$SCENARIO.cpu.collapsed" "$OUT/$SCENARIO.alloc.collapsed" | tee -a "$OUT/summary.md"
awk '{n=split($0,a,";"); leaf=a[n]; sub(/ [0-9]+$/,"",leaf); c[leaf]+=$NF; t+=$NF} END{for(k in c) printf "%5.1f%% %s\n", 100*c[k]/t, k}' "$OUT/$SCENARIO.cpu.collapsed" | sort -nr | head -8 | sed 's/^/cpu self: /' | tee -a "$OUT/summary.md"
