set -u
cd ~/zavarnik/bench; J=/usr/lib/jvm/java-25-openjdk-amd64; S=$(pwd)/build/install/bench/bin/bench; PORT=18100
run() { # $1 label $2 opts
  JAVA_OPTS="$2 -Xms1g -Xmx1g -XX:+UseG1GC -XX:+CITime -Xlog:aot=info" JAVA_HOME=$J $S > /tmp/ci.log 2>&1 & local pid=$!
  for i in $(seq 300); do curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$PORT/health 2>/dev/null | grep -q 200 && break; sleep 0.05; done
  taskset -pc 0-7 $pid >/dev/null
  taskset -c 8-15 ~/tools/oha -z 20s -c 32 --no-tui -m POST -T application/json -D profile/order.json http://127.0.0.1:$PORT/business >/dev/null 2>&1
  kill -TERM $pid; wait $pid 2>/dev/null; sleep 1
  local opened=$(grep -c "Opened AOT cache" /tmp/ci.log)
  echo "$1 (cache opened: $opened): $(grep -E "^\s+(C1|C2) Compile Time|Total compiled methods|nmethods|Total compiled bytecodes|Tier[1-4] " /tmp/ci.log | tr -s ' ' | tr '\n' '|' | cut -c1-400)"
}
for rep in 1 2 3; do run "cold  rep$rep" "-XX:AOTMode=off"; run "cache rep$rep" ""; done
echo "--- raw CITime block of the last run:"; grep -A40 "Accumulated compiler times" /tmp/ci.log | head -45
