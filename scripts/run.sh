#!/usr/bin/env bash
# Starts the CLI or the GUI directly with `java -jar` - never `mvn javafx:run` - with an easy
# switch for the app's own log level (this project's io.github.jlmc packages; see the "Logging"
# section in README.md for the underlying `-Dorg.slf4j.simpleLogger.*` flags this wraps). Third
# -party (JGit/mina-sshd) logging is left at whatever simplelogger.properties already configures
# (quiet, warn by default) - override that separately with the raw -D flags documented in the
# README if you need it.
#
# Both jars are already self-contained for `java -jar`: `mvn package` (maven-jar-plugin +
# maven-dependency-plugin, see cli/pom.xml and gui-javafx/pom.xml) produces a thin jar whose
# manifest Class-Path lists every dependency copied into the sibling target/lib/ folder - no
# --module-path/--add-modules needed even for JavaFX, since this project has no module-info.java.
# Maven is only invoked here to build a jar that doesn't exist yet; once built, running again never
# touches Maven.
#
# Usage:
#   scripts/run.sh cli [--log-level=trace|debug|info|warn|error] [-- <cli args...>]
#   scripts/run.sh gui [--log-level=trace|debug|info|warn|error]
#
# Examples:
#   scripts/run.sh cli -- -C /path/to/vault status
#   scripts/run.sh cli --log-level=debug -- -C /path/to/vault pull
#   scripts/run.sh gui --log-level=debug

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  echo "Usage: $0 <cli|gui> [--log-level=trace|debug|info|warn|error] [-- <args...>]" >&2
  exit 1
}

[[ $# -ge 1 && ( "$1" == "cli" || "$1" == "gui" ) ]] || usage
MODE="$1"; shift

LOG_LEVEL="info"
if [[ "${1:-}" == --log-level=* ]]; then
  LOG_LEVEL="${1#--log-level=}"
  shift
fi
case "$LOG_LEVEL" in
  trace|debug|info|warn|error) ;;
  *) echo "Invalid --log-level: $LOG_LEVEL (use trace|debug|info|warn|error)" >&2; exit 1 ;;
esac

if [[ "${1:-}" == "--" ]]; then
  shift
fi

LOG_PROPERTY="org.slf4j.simpleLogger.log.io.github.jlmc=$LOG_LEVEL"

if [[ "$MODE" == "cli" ]]; then
  MODULE="cli"
  JAR="$ROOT_DIR/cli/target/rikiki-vault.jar"
else
  MODULE="gui-javafx"
  JAR="$ROOT_DIR/gui-javafx/target/rikiki-vault-gui.jar"
fi

if [[ ! -f "$JAR" ]]; then
  echo "==> Building $MODULE (mvn package)"
  mvn -q -pl "$MODULE" -am package -DskipTests
fi

exec java "-D$LOG_PROPERTY" -jar "$JAR" "$@"
