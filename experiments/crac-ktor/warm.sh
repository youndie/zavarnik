#!/usr/bin/env bash
# Plain start against restore, N runs each in alternation, on the Ktor sample under a CRaC JDK:
# ms to the first 200 on /health, ms of the first POST /api/order after it, the container's memory
# (docker stats) at readiness and after 100 requests, and how many JIT compilations
# (-XX:+PrintCompilation lines) the JVM printed during those 100 requests.
#
#     ./warm.sh 5      # from the repository root, on a box where samples/ktor is installed
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
APP=${APP:-$PWD/samples/ktor/build/install/ktor-sample}
RUNS=${1:-5}
POLICY=$(realpath "$(dirname "$0")/policies.yaml")
DIR=$(mktemp -d /tmp/crac-warm.XXXX); chmod 777 "$DIR"; mkdir -p "$DIR/cr"; chmod 777 "$DIR/cr"
PORT=18090
BODY='{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5}],"note":"n"}'
COMMON=(-p $PORT:$PORT -v "$APP:/app:ro" -v "$DIR/cr:/cr" -v "$POLICY:/policies.yaml:ro")
JVM="-XX:AOTMode=off -XX:+PrintCompilation -Djdk.crac.resource-policies=/policies.yaml"
now_ms() { date +%s%3N; }
wait_ready() { local t0; t0=$(now_ms); for _ in $(seq 1 600); do curl -sf -o /dev/null "http://127.0.0.1:$PORT/health" && { echo $(( $(now_ms) - t0 )); return 0; }; sleep 0.02; done; echo timeout; return 1; }
post() { curl -s -o /dev/null -X POST -H 'content-type: application/json' -d "$BODY" "http://127.0.0.1:$PORT/api/order"; }
mem() { docker stats --no-stream --format '{{.MemUsage}}' "$1" | cut -d/ -f1 | tr -d ' '; }
measure() { # name
  local ready first m1 m2 since n
  ready=$(wait_ready)
  local t0; t0=$(now_ms); post; first=$(( $(now_ms) - t0 ))
  m1=$(mem "$1")
  since=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ)
  for _ in $(seq 1 50); do curl -s -o /dev/null "http://127.0.0.1:$PORT/api/warm"; post; done
  n=$(docker logs --since "$since" "$1" 2>&1 | grep -cE '^\s*[0-9]+\s+[0-9]+\s')
  m2=$(mem "$1")
  echo "ready=${ready}ms first_post=${first}ms mem_ready=$m1 mem_after100=$m2 compilations_during_100=$n"
}
echo "image: $(docker run --rm "$IMG" java -version 2>&1 | sed -n 2p)"
echo "== checkpoint after 200 warm requests"
docker run -d --name crac-warm-cp "${COMMON[@]}" -e JAVA_OPTS="$JVM -XX:CRaCCheckpointTo=/cr" "$IMG" /app/bin/ktor-sample >/dev/null
wait_ready >/dev/null
for _ in $(seq 1 100); do curl -s -o /dev/null "http://127.0.0.1:$PORT/api/warm"; post; done
docker exec crac-warm-cp jcmd sample.MainKt JDK.checkpoint >/dev/null 2>&1; sleep 3
docker logs crac-warm-cp 2>&1 | grep -c "warp: Checkpoint successful" | sed 's/^/checkpoint successful lines: /'
docker rm -f crac-warm-cp >/dev/null 2>&1
echo "image: $(du -sh "$DIR/cr" | cut -f1)"
for i in $(seq 1 "$RUNS"); do
  docker run -d --name crac-warm-plain "${COMMON[@]}" -e JAVA_OPTS="$JVM" "$IMG" /app/bin/ktor-sample >/dev/null
  echo "plain   $i: $(measure crac-warm-plain)"; docker rm -f crac-warm-plain >/dev/null 2>&1
  docker run -d --name crac-warm-rs "${COMMON[@]}" "$IMG" java -XX:CRaCRestoreFrom=/cr >/dev/null
  echo "restore $i: $(measure crac-warm-rs)"; docker rm -f crac-warm-rs >/dev/null 2>&1
done
