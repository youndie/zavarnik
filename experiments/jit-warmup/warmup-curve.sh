set -u
cd ~/zavarnik/bench
J=/usr/lib/jvm/java-25-openjdk-amd64; S=$(pwd)/build/install/bench/bin/bench; PORT=18100
[ -f build/install/bench/lib/app.aot ] || { JAVA_HOME=$J ~/zavarnik/gradlew -p ~/zavarnik/bench -q aotTrain 2>&1 | grep -v WARNING | tail -2; }
ls -la build/install/bench/lib/app.aot | awk '{print "# cache bytes", $5}'
curve() { # $1 label $2 java opts
  JAVA_OPTS="$2 -Xms1g -Xmx1g -XX:+UseG1GC -Xlog:aot=info" JAVA_HOME=$J $S > /tmp/wc.log 2>&1 & local pid=$!
  for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$PORT/health 2>/dev/null | grep -q 200 && break; sleep 0.05; done
  taskset -pc 0-7 $pid >/dev/null
  local row=""
  for w in $(seq 10); do
    r=$(taskset -c 8-15 ~/tools/oha -z 2s -c 32 --no-tui --output-format json -m POST -T application/json -D profile/order.json http://127.0.0.1:$PORT/business 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print(int(d["summary"]["requestsPerSec"]))')
    row="$row $r"
  done
  kill -TERM $pid; wait $pid 2>/dev/null
  echo "$1 (cache opened: $(grep -c "Opened AOT cache" /tmp/wc.log)):$row"
}
echo "# rps per 2-second window after readiness, /business, 32 connections; windows 1..10"
for rep in 1 2 3; do curve "cold  rep$rep" "-XX:AOTMode=off"; curve "cache rep$rep" ""; done
