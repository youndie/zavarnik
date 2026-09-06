set -u
J=/usr/lib/jvm/java-25-openjdk-amd64/bin/java
cd /tmp && tar xzf b09-caches.tar.gz && cd b09
echo "# $(date -u +%FT%TZ) production-box $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2)"
$J -version 2>&1 | sed -n 2p | sed 's/^/# /'
echo "# JVM view: $($J -XX:+PrintFlagsFinal -version 2>/dev/null | grep -E 'UseAVX ' | tr -s ' ')"
PORTABLE="-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching"
rm -f hs_err_pid*.log
echo; echo "=== hello world, caches trained on Genoa, run here under -XX:AOTMode=on"
for v in native portable; do
  flags=""; [ $v = portable ] && flags="$PORTABLE"
  out=$($J $flags -XX:AOTMode=on -XX:AOTCache=hello/lib/$v.aot -Xlog:class+load -Xlog:aot+codecache*=info -cp hello/lib/app.jar App 2>&1); code=$?
  echo "$v: exit=$code; shared=$(printf '%s\n' "$out" | grep -c 'shared objects file'); $(printf '%s\n' "$out" | grep -E 'AOT code entries|SIGILL|fatal|Unable to use|mismatch|CPU' | sed -E 's/^\[[0-9.]+s\]//' | head -3 | tr '\n' ';')"
  ls hs_err_pid*.log 2>/dev/null | head -1 | sed 's/^/  hs_err: /'; for f in hs_err_pid*.log; do [ -f "$f" ] && grep -m2 -E "SIGILL|Problematic frame|^# *[vVjJ] " "$f" | sed 's/^/  /'; done; rm -f hs_err_pid*.log
done
echo; echo "=== hello native cache, 20 runs — does it crash every time or only sometimes?"
c=0; for i in $(seq 20); do $J -XX:AOTMode=on -XX:AOTCache=hello/lib/native.aot -cp hello/lib/app.jar App >/dev/null 2>&1 || c=$((c+1)); done; echo "non-zero exits: $c of 20"; rm -f hs_err_pid*.log
PORT=18090; (ss -ltn 2>/dev/null) | grep -q ":$PORT " && { echo "port $PORT busy"; exit 1; }
run_sample() { local d=$1; local log=/tmp/b09/$d.run.log
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 JAVA_OPTS="-XX:AOTMode=on -Xlog:class+load=info -Xlog:aot+codecache*=info" $d/bin/ktor-sample > $log 2>&1 & local pid=$!
  local ok=1; for i in $(seq 300); do kill -0 $pid 2>/dev/null || break; curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$PORT/health 2>/dev/null | grep -q 200 && { ok=0; break; }; sleep 0.1; done
  if [ $ok = 0 ]; then curl -s -o /dev/null http://127.0.0.1:$PORT/api/warm; curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5}],"note":"n"}' http://127.0.0.1:$PORT/api/order; kill -TERM $pid; fi
  wait $pid; local code=$?
  echo "$d: ready=$([ $ok = 0 ] && echo yes || echo no) exit=$code; sample classes from cache: $(grep -E '\] sample\.' $log | grep -c 'shared objects file') of $(grep -E '\] sample\.' $log | grep -c 'source:'); $(grep -E 'AOT code entries|SIGILL|Unable to use|Problematic' $log | sed -E 's/^\[[0-9.]+s\]//' | head -3 | tr '\n' ';')"
  for f in hs_err_pid*.log $d/hs_err_pid*.log; do [ -f "$f" ] && { echo "  hs_err: $f"; grep -m3 -E "SIGILL|Problematic frame|^# *[vVjJ] |Current CompileTask" "$f" | sed 's/^/  /'; cp "$f" /tmp/b09/$d.hs_err.log; rm -f "$f"; }; done
}
echo; echo "=== sample, caches trained on Genoa, run here"; run_sample sample-native; run_sample sample-portable
