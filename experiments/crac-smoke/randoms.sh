#!/usr/bin/env bash
# Restore one checkpoint twice and compare five generators; see Randoms.java for what each is.
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
DIR=$(mktemp -d /tmp/crac-randoms.XXXX); chmod 777 "$DIR"; mkdir -p "$DIR/cr"; chmod 777 "$DIR/cr"
cp "$(dirname "$0")/Randoms.java" "$DIR/"
docker run --rm -v "$DIR:/work" -w /work "$IMG" javac Randoms.java
name=crac-randoms-$$
docker run -d --name "$name" -v "$DIR:/work" -w /work "$IMG" java -XX:CRaCCheckpointTo=/work/cr Randoms >/dev/null
sleep 3; docker exec "$name" jcmd Randoms JDK.checkpoint >/dev/null 2>&1; sleep 3
docker logs "$name" 2>/dev/null | grep -E "^(before|old-random)"; docker rm -f "$name" >/dev/null 2>&1
touch "$DIR/go"   # only now, so the checkpoint was taken with the process still waiting
for n in 1 2; do
  echo "restore $n: $(docker run --rm -v "$DIR:/work" -w /work "$IMG" timeout -s TERM 20 java -XX:CRaCRestoreFrom=/work/cr 2>/dev/null | grep '^old-random')"
done
