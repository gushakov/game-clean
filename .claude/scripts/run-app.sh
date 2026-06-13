#!/usr/bin/env bash
#
# Launch the game-clean terminal app with a JDK 21+ runtime.
#
# JLine needs a REAL terminal: run this from an interactive shell (Git Bash, Windows
# Terminal, a system console). Do NOT launch the app through Maven (`mvn spring-boot:run`
# / `exec:java` make Maven own stdin/stdout and force JLine into dumb-terminal mode), and
# avoid piping stdin (that also degrades to dumb mode — fine for a content smoke check,
# but you lose line editing, history and colour).
#
# Logging goes to ./logs/game-clean.log (logback-spring.xml) so it never scribbles over
# the console — JLine owns the terminal.
#
# JDK resolution order — first candidate that reports major version >= 21 wins:
#   1. $GAME_CLEAN_JAVA          explicit override: full path to a java[.exe] binary
#   2. $JAVA_HOME/bin/java
#   3. ~/.jdks/openjdk-21*       IntelliJ-managed JDKs (no username hardcoded; $HOME expands)
#   4. java on PATH
#
# Any arguments are forwarded to the application.
set -euo pipefail

# game-clean's configuration is fully bundled in the jar (application.yaml). Ignore any
# additional config location inherited from the ambient shell — on some setups a shell
# path-mangles such a value into a directory that does not exist, which fails startup.
unset SPRING_CONFIG_ADDITIONAL_LOCATION

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

java_major() {
    # First -version line; "21.0.2" -> 21, legacy "1.8.0_x" -> 1 (rejected below).
    "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

candidates=()
[[ -n "${GAME_CLEAN_JAVA:-}" ]] && candidates+=("${GAME_CLEAN_JAVA}")
[[ -n "${JAVA_HOME:-}" ]] && candidates+=("${JAVA_HOME}/bin/java")
shopt -s nullglob
for d in "${HOME}"/.jdks/openjdk-21*; do
    candidates+=("${d}/bin/java")
done
shopt -u nullglob
candidates+=("$(command -v java || true)")

java_bin=""
for c in "${candidates[@]}"; do
    [[ -n "${c}" && -x "${c}" ]] || continue
    major=$(java_major "${c}" 2>/dev/null || echo 0)
    if [[ "${major}" =~ ^[0-9]+$ && "${major}" -ge 21 ]]; then
        java_bin="${c}"
        break
    fi
done

if [[ -z "${java_bin}" ]]; then
    echo "error: no JDK 21+ found." >&2
    echo "  Set GAME_CLEAN_JAVA or JAVA_HOME to a JDK 21+ install, or put one on PATH." >&2
    exit 1
fi

jar=$(ls "${project_root}"/target/game-clean-*.jar 2>/dev/null | head -1 || true)
if [[ -z "${jar}" ]]; then
    echo "error: no application jar in target/." >&2
    echo "  Build it first:  mvn -DskipTests package" >&2
    exit 1
fi

echo "Running ${jar##*/} on $("${java_bin}" -version 2>&1 | head -1)"
cd "${project_root}"
exec "${java_bin}" -jar "${jar}" "$@"
