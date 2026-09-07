#!/usr/bin/env bash
# Proves the Jib image's cache is used by the image's JVM: two Jib builds through the plugin's
# tasks, then the image started with the cache made mandatory (-XX:AOTMode=on through
# JAVA_TOOL_OPTIONS — a Jib entrypoint reads no JAVA_OPTS) and class-load logging, /health waited
# for from outside, SIGTERM, and a count of the classes that came from the cache.
# Exit code 1 when the container did not become ready or not every `sample.` class came from the cache.
#
# Usage (from the repository root):  samples/ktor-jib/jib-check.sh
set -u
cd "$(dirname "$0")/../.."
IMAGE=ktor-jib-sample:latest
PORT=${PORT:-18090}
./gradlew -p samples/ktor-jib jibAotTrain --console=plain -q || { echo "jibAotTrain failed"; exit 1; }
./gradlew -p samples/ktor-jib jibAotVerify --console=plain -q || { echo "jibAotVerify failed"; exit 1; }
docker inspect "$IMAGE" --format 'entrypoint: {{json .Config.Entrypoint}}'
CID=$(docker run -d -p "$PORT:18090" -e JAVA_TOOL_OPTIONS="-XX:AOTMode=on -Xlog:class+load=info" "$IMAGE")
trap 'docker rm -f "$CID" >/dev/null 2>&1' EXIT
ready=1
for i in $(seq 200); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health")" = "200" ]; then ready=0; break; fi
  sleep 0.1
done
docker stop "$CID" >/dev/null 2>&1
LOG=$(docker logs "$CID" 2>&1 || true)
echo "image JVM: $(docker run --rm --entrypoint java "$IMAGE" -version 2>&1 | head -1)"
shared=$(printf '%s\n' "$LOG" | grep -c 'source: shared objects file')
app=$(printf '%s\n' "$LOG" | grep -E '\] sample\.' | grep -c 'source: shared objects file')
app_total=$(printf '%s\n' "$LOG" | grep -E '\] sample\.' | grep -c 'source:')
echo "ready=$([ $ready = 0 ] && echo yes || echo no); classes from the cache: $shared overall, $app of $app_total in sample.*"
printf '%s\n' "$LOG" | grep -E '\[(error|warning)\]\[aot' | head -5
if [ $ready != 0 ] || [ "$app" -eq 0 ] || [ "$app" != "$app_total" ]; then
  echo "--- last lines of the container's output:"; printf '%s\n' "$LOG" | grep -v 'source: ' | tail -15; exit 1
fi
