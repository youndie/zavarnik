#!/usr/bin/env bash
# The check D4 asks for, as a prototype: restore one snapshot twice and compare the generators.
#
# A generator whose value is IDENTICAL in both replicas is one every replica of that snapshot
# shares — the failure. A generator that differs is doing what a restore should make it do. The
# guard names the ones that matched, because "the snapshot is not safe" is useless and
# "old-tlr and math-random repeat" is actionable.
#
# Deliberately not a comparison of the application's own output: konekt's eSIM mock draws from a
# shared generator and still produced five different codes across five restores, because a server
# consumes an unpredictable amount of the stream first. A check like that is green for the wrong
# reason (`docs/research/research-crac.md` §1.3).
#
#     ./generator-guard.sh          # exit 1 if any non-SecureRandom generator repeats
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
DIR=$(mktemp -d /tmp/crac-guard.XXXX); chmod 777 "$DIR"; mkdir -p "$DIR/cr"; chmod 777 "$DIR/cr"
trap 'rm -rf "$DIR"' EXIT
cp "$(dirname "$0")/Randoms.java" "$DIR/"
docker run --rm -v "$DIR:/work" -w /work "$IMG" javac Randoms.java
name=crac-guard-$$
docker run -d --name "$name" -v "$DIR:/work" -w /work "$IMG" java -XX:CRaCCheckpointTo=/work/cr Randoms >/dev/null
sleep 3; docker exec "$name" jcmd Randoms JDK.checkpoint >/dev/null 2>&1; sleep 3
docker rm -f "$name" >/dev/null 2>&1
touch "$DIR/go"
for n in 1 2; do
  docker run --rm -v "$DIR:/work" -w /work "$IMG" timeout -s TERM 20 java -XX:CRaCRestoreFrom=/work/cr 2>/dev/null |
    grep '^old-random' > "$DIR/restore-$n.txt"
done
test -s "$DIR/restore-1.txt" && test -s "$DIR/restore-2.txt" || { echo "guard: no restore output — nothing to compare"; exit 2; }
echo "restore 1: $(cat "$DIR/restore-1.txt")"
echo "restore 2: $(cat "$DIR/restore-2.txt")"
same=$(python3 - "$DIR/restore-1.txt" "$DIR/restore-2.txt" <<'PY'
import sys
def parse(p):
    return dict(kv.split("=", 1) for kv in open(p).read().split())
a, b = parse(sys.argv[1]), parse(sys.argv[2])
print(" ".join(k for k in a if k in b and a[k] == b[k]))
PY
)
if [ -n "$same" ]; then
  echo "guard: FAIL — identical in both replicas: $same"
  exit 1
fi
echo "guard: every generator differed between the two restores"
