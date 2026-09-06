set -u
J=/usr/lib/jvm/java-25-openjdk-amd64/bin/java
cd /tmp && rm -rf b09 && tar xzf b09.tar.gz && cd b09
echo "# $(date -u +%FT%TZ) training-box $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2)"
$J -version 2>&1 | sed -n 2p | sed 's/^/# /'
echo "# JVM view: $($J -XX:+PrintFlagsFinal -version 2>/dev/null | grep -E 'UseAVX ' | tr -s ' ')"
PORT=18090; (ss -ltn 2>/dev/null || netstat -ltn) | grep -q ":$PORT " && { echo "port $PORT busy"; exit 1; }
PORTABLE="-XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching"
codeinfo() { $J $2 -XX:AOTCache=$1 -Xlog:aot+codecache*=info -Xlog:aot=debug -cp hello/lib/app.jar App 2>&1 | grep -E "AOT code entries|\(Code\)" | sed -E 's/^\[[0-9.]+s\]//' | tr '\n' ';'; echo; }
echo; echo "=== hello world"
cd hello
$J -XX:AOTCacheOutput=lib/native.aot -cp lib/app.jar App >/dev/null 2>&1; echo "native  : $(wc -c < lib/native.aot) bytes; $(cd .. && codeinfo hello/lib/native.aot '')"
$J $PORTABLE -XX:AOTCacheOutput=lib/portable.aot -cp lib/app.jar App >/dev/null 2>&1; echo "portable: $(wc -c < lib/portable.aot) bytes; $(cd .. && codeinfo hello/lib/portable.aot "$PORTABLE")"
cd ..
train_sample() { # $1 dir
  local d=$1; rm -f $d/lib/app.aot; local log=/tmp/b09/$d.train.log
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 JAVA_OPTS="-XX:AOTCacheOutput=/tmp/b09/$d/lib/app.aot" $d/bin/ktor-sample > $log 2>&1 & local pid=$!
  local ok=1; for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$PORT/health 2>/dev/null | grep -q 200 && { ok=0; break; }; sleep 0.1; done
  [ $ok = 0 ] || { echo "$d: not ready"; kill -9 $pid; return; }
  curl -s -o /dev/null http://127.0.0.1:$PORT/api/warm; curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5}],"note":"n"}' http://127.0.0.1:$PORT/api/order
  kill -TERM $pid; wait $pid 2>/dev/null; for i in $(seq 100); do grep -q "creation is complete" $log && break; sleep 0.2; done
  echo "$d: $(grep -o 'AOTCache creation is complete.*' $log | sed 's#/tmp/b09/##'); $(grep -c AOTAdapterCaching $d/bin/ktor-sample) portability flag lines in script"
}
echo; echo "=== sample"; train_sample sample-native; train_sample sample-portable
echo; echo "=== code region in the sample caches (probe via hello classpath is meaningless; header only)"
for d in sample-native sample-portable; do echo "$d: $(ls -la $d/lib/app.aot | awk '{print $5}') bytes"; done
cd /tmp && tar czf b09-caches.tar.gz b09/hello/lib/native.aot b09/hello/lib/portable.aot b09/sample-native/lib/app.aot b09/sample-portable/lib/app.aot b09/*.train.log && ls -la b09-caches.tar.gz | awk '{print $5}'
