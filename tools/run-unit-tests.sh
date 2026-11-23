#!/bin/bash
# Run ALL unit tests (plain JUnit + Robolectric/Roborazzi screen tests)
# on this VM.
#
# Why not `./build.sh testDebugUnitTest`? On this VM the Gradle test
# worker process dies at startup (its loopback channel to the Gradle
# process is intercepted — the same reason the daemon is disabled).
# The project practice is therefore: compile with Gradle, then run
# JUnitCore directly with the exact classpath Gradle would have used
# (dumped by tools/test-classpath.init.gradle). On a normal machine
# `./gradlew testDebugUnitTest` (or verifyRoborazziDebug) works as-is.
#
# Usage:
#   tools/run-unit-tests.sh [verify|record] [TestClass ...]
#     verify (default) — Roborazzi compares against the goldens in
#                        app/src/test/roborazzi
#     record           — Roborazzi (re)writes the goldens
#     TestClass        — fully qualified class names; default: all
#                        compiled test classes
set -e
cd "$(dirname "$0")/.."
ROOT="$PWD"
MODE="${1:-verify}"
if [ "$MODE" = "verify" ] || [ "$MODE" = "record" ]; then shift; fi

export JAVA_HOME=~/workspace/tools/jdk21
JAVA="$JAVA_HOME/bin/java"

# --- Robolectric offline android-all (Robolectric's own downloader
# cannot run here: Java networking on this VM is intercepted). -------
DEPS_DIR="$ROOT/.local/robolectric-deps"
ALL_JAR="$DEPS_DIR/android-all-instrumented-15-robolectric-12650502-i7.jar"
if [ ! -f "$ALL_JAR" ]; then
  mkdir -p "$DEPS_DIR"
  echo "Fetching Robolectric android-all (SDK 35) ..."
  if ! curl -s -o /dev/null --max-time 3 http://127.0.0.1:18080/central/; then
    (cd ~/workspace/tools && nohup python3 mirror.py > /tmp/mirror.log 2>&1 &)
    sleep 2
  fi
  curl -s --max-time 900 -o "$ALL_JAR" \
    "http://127.0.0.1:18080/central/org/robolectric/android-all-instrumented/15-robolectric-12650502-i7/android-all-instrumented-15-robolectric-12650502-i7.jar"
fi

# --- Compile (through the build lock, via build.sh) + dump classpath.
# Name the classpath's producer tasks explicitly: running only the
# compile task leaves e.g. the runtime classes jar stale, and the
# tests then silently exercise old code.
./build.sh :app:compileDebugUnitTestKotlin \
  :app:bundleDebugClassesToRuntimeJar \
  :app:packageDebugUnitTestForUnitTest \
  :app:generateDebugUnitTestConfig \
  -I tools/test-classpath.init.gradle :app:printTestClasspath
CP="$(cat app/build/test-classpath.txt)"

# --- Which test classes? ---------------------------------------------
if [ "$#" -gt 0 ]; then
  CLASSES="$@"
else
  TEST_CLASSES_DIR="$ROOT/app/build/tmp/kotlin-classes/debugUnitTest"
  CLASSES="$(cd "$TEST_CLASSES_DIR" && find . -name "*Test.class" \
    ! -name "*\$*" | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)"
fi
echo "Running: $CLASSES"

# --- Run JUnitCore directly. The working directory must be the app
# module dir: AGP's test_config.properties carries module-relative
# paths (merged manifest / resource apk) that Robolectric resolves
# against the CWD, exactly as in a Gradle test run. -------------------
ROBO_PROP="-Droborazzi.test.verify=true"
[ "$MODE" = "record" ] && ROBO_PROP="-Droborazzi.test.record=true"
cd "$ROOT/app"
# --add-opens: Gradle's test worker normally grants these to
# Robolectric on JDK 17+; a direct JUnitCore launch must pass them
# itself (Robolectric's FileDescriptor interceptor reflects into
# java.io, and test code reading/writing golden PNGs trips it).
"$JAVA" -Xmx1g -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  -Drobolectric.offline=true \
  -Drobolectric.dependency.dir="$DEPS_DIR" \
  -Dtenki.golden.dir="$ROOT/app/src/test/roborazzi" \
  $ROBO_PROP \
  -cp "$CP" org.junit.runner.JUnitCore $CLASSES
