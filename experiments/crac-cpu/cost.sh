#!/usr/bin/env bash
# What -XX:CPUFeatures=generic costs, measured where it can be measured: one machine, one image,
# two snapshots of the same application differing only by that flag. The snapshot carries compiled
# code, so a baseline instruction set is not free in principle — this says whether it is in practice.
#
#     ./cost.sh [runs]      # on a box with Docker, the CRaC image and samples/ktor installed
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jre-crac}
APP=${APP:-$PWD/samples/ktor/build/install/ktor-sample}
RUNS=${1:-10}
PORT=18090
BASE=zavarnik-crac-cost:local
CTX=$(mktemp -d); OUT=$(mktemp -d); chmod 777 "$OUT"
trap 'rm -rf "$CTX" "$OUT"; docker rm -f cost-cp cost-rs >/dev/null 2>&1' EXIT
cp -r "$APP/." "$CTX/"; rm -f "$CTX/lib/app.aot" "$CTX/lib/app.aot.jars"
printf 'FROM %s\nWORKDIR /opt/app\nCOPY . ./\nEXPOSE 18090\nENTRYPOINT ["/opt/app/bin/ktor-sample"]\n' "$IMG" > "$CTX/Dockerfile"
docker build -q -t "$BASE" "$CTX" >/dev/null
now_ms() { date +%s%3N; }
snapshot() { # flag-or-empty, out dir
  local flag=$1 dir=$2
  rm -rf "$dir"; mkdir -p "$dir"; chmod 777 "$dir"
  docker rm -f cost-cp >/dev/null 2>&1
  docker run -d --name cost-cp -p $PORT:18090 -v "$dir:/cr" \
    -e JAVA_OPTS="-XX:AOTMode=off ${flag} -XX:CRaCCheckpointTo=/cr -Djdk.crac.resource-policies=/opt/app/lib/zavarnik-crac-policies.yaml" \
    "$BASE" >/dev/null
  for _ in $(seq 1 600); do curl -sf -o /dev/null "http://127.0.0.1:$PORT/health" && break; sleep 0.02; done
  for _ in $(seq 1 200); do curl -s -o /dev/null "http://127.0.0.1:$PORT/api/warm"; done
  sleep 2
  docker exec cost-cp jcmd 1 JDK.checkpoint >/dev/null 2>&1 || docker exec cost-cp jcmd sample.MainKt JDK.checkpoint >/dev/null 2>&1
  sleep 4; docker rm -f cost-cp >/dev/null 2>&1
  [ -n "$(ls -A "$dir" 2>/dev/null)" ] || { echo "no snapshot for flag='${flag:-none}'"; return 1; }
}
# Throughput comes from oha and not from a shell loop: a loop of `curl` calls measures process
# creation, which is why its first reading swung between 12 and 120 requests a second on a server
# that was doing the same thing each time.
OHA=${OHA:-$HOME/tools/oha}
restore_once() { # dir -> "ms_to_first_200 rps"
  docker rm -f cost-rs >/dev/null 2>&1
  docker run -d --name cost-rs -p $PORT:18090 -v "$1:/cr" --entrypoint java "$BASE" -XX:CRaCRestoreFrom=/cr >/dev/null
  local t0; t0=$(now_ms)
  for _ in $(seq 1 900); do curl -sf -o /dev/null "http://127.0.0.1:$PORT/health" && break; sleep 0.01; done
  local ready=$(( $(now_ms) - t0 ))
  local rps
  rps=$("$OHA" -z 10s -c 32 --no-tui --output-format json "http://127.0.0.1:$PORT/api/warm" 2>/dev/null |
    python3 -c 'import sys,json; print(round(json.load(sys.stdin)["summary"]["requestsPerSec"]))' 2>/dev/null || echo 0)
  docker rm -f cost-rs >/dev/null 2>&1
  sleep 1   # let the published port go before the next container asks for it
  echo "$ready $rps"
}
echo "image: $(docker run --rm "$IMG" java -version 2>&1 | sed -n 2p)"
snapshot "" "$OUT/plain" || exit 1
snapshot "-XX:CPUFeatures=generic" "$OUT/generic" || exit 1
echo "snapshot sizes: plain $(du -sh "$OUT/plain" | cut -f1), generic $(du -sh "$OUT/generic" | cut -f1)"
echo "variant,run,ready_ms,rps_over_10s"
for i in $(seq 1 "$RUNS"); do
  echo "plain,$i,$(restore_once "$OUT/plain" | tr ' ' ',')"
  echo "generic,$i,$(restore_once "$OUT/generic" | tr ' ' ',')"
done
