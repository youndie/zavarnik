#!/usr/bin/env bash
# The runner's own CRaC commands, on the JVM that would use them: `checkpoint` warms the sample up
# and snapshots it, `restore-verify` restores the snapshot and puts the restored process through
# the same workload. Both run inside a container of a CRaC JDK with the installed distribution
# copied in, which is the shape the Dockerfile recipe has (`samples/ktor/Dockerfile`).
#
#     ./runner-check.sh          # from the repository root, after -p samples/ktor installDist
set -euo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
APP=${APP:-$PWD/samples/ktor/build/install/ktor-sample}
OUT=$(mktemp -d); chmod 777 "$OUT"
trap 'rm -rf "$OUT"' EXIT
test -f "$APP/lib/zavarnik-runner.jar" || { echo "no runner jar in $APP/lib"; exit 1; }
# As the host user, so the snapshot on the mounted directory belongs to whoever runs this —
# the same reason JibImage passes --user for the AOT training container.
docker run --rm --user "$(id -u):$(id -g)" -v "$APP:/src:ro" -v "$OUT:/out" "$IMG" bash -c '
  set -e
  cp -r /src /tmp/app
  # The sample ships an AOT cache trained by another JDK build; this JVM would reject it, noisily
  # and for a reason that has nothing to do with what is being checked here.
  rm -f /tmp/app/lib/app.aot /tmp/app/lib/app.aot.jars
  R="java -cp /tmp/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main"
  echo "== checkpoint"; $R checkpoint /tmp/app --out /out
  echo "== restore-verify"; $R restore-verify /tmp/app --out /out
  echo "== the snapshot, as the image would carry it:"; ls -l /out/crac | head -5
'
