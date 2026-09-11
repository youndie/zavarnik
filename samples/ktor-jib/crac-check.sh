#!/usr/bin/env bash
# Proves the Jib image's CRaC snapshot is what its JVM starts from: two Jib builds through the
# plugin's tasks, then the image run with nothing added — its entrypoint is the restore — and
# `/health` waited for from outside.
# Exit code 1 when either task fails or the restored image does not answer.
#
# Usage (from the repository root):  samples/ktor-jib/crac-check.sh
set -u
cd "$(dirname "$0")/../.."
IMAGE=ktor-jib-sample:latest
PORT=${PORT:-18090}
# Jib 3.5.4 does not support Gradle's configuration cache; -Pcrac swaps in the Zulu CRaC base.
GRADLE=(./gradlew -p samples/ktor-jib -Pcrac --no-configuration-cache --console=plain -q)
"${GRADLE[@]}" jibCracCheckpoint || { echo "jibCracCheckpoint failed"; exit 1; }
"${GRADLE[@]}" jibCracVerify || { echo "jibCracVerify failed"; exit 1; }
docker inspect "$IMAGE" --format 'entrypoint: {{json .Config.Entrypoint}}'
CID=$(docker run -d -p "$PORT:18090" "$IMAGE")
trap 'docker rm -f "$CID" >/dev/null 2>&1' EXIT
t0=$(date +%s%3N); ready=1
for _ in $(seq 400); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health")" = "200" ]; then ready=0; break; fi
  sleep 0.02
done
if [ $ready = 0 ]; then
  echo "restored image ready in $(( $(date +%s%3N) - t0 )) ms; /api/warm answers $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/warm")"
else
  echo "the restored image never answered /health"
fi
docker logs "$CID" 2>&1 | grep -iE "warp:|RestoreException|Exception" | head -5
exit $ready
