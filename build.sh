#!/bin/bash
# TenkiNoKotae build helper for this VM (same conventions as battery-mochi /
# Scriptlet, see AGENTS.md "Android build in this VM"): local mirror + IPv4 +
# in-process Gradle 8.9 (8.14.3 forks a single-use daemon whose TCP is
# intercepted).
set -e
cd "$(dirname "$0")"
export TENKI_LOCAL_MIRROR=1
export JAVA_HOME=~/workspace/tools/jdk21
export ANDROID_HOME=~/workspace/tools/android-sdk
export GRADLE_USER_HOME=~/workspace/tools/gradle-home
export GRADLE_OPTS="-Xmx768m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true"
if ! curl -s -o /dev/null --max-time 3 http://127.0.0.1:18080/central/; then
  (cd ~/workspace/tools && nohup python3 mirror.py > /tmp/mirror.log 2>&1 &)
  sleep 1
fi
~/workspace/tools/gradle-8.9/bin/gradle "$@" \
  -Dorg.gradle.daemon=false -Dkotlin.daemon.enabled=false --max-workers=1
