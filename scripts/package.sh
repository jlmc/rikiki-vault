#!/usr/bin/env bash
# Packages the CLI and GUI apps as native, Java-free artifacts via jpackage (bundled with the
# JDK - no fat jar involved). jpackage never cross-compiles, so this only produces artifacts
# for the OS it runs on; run it manually on each target OS (see scripts/package.ps1 for
# Windows). Prerequisites: JDK 17+ with jpackage on PATH; macOS needs the Xcode Command Line
# Tools (xcode-select --install); Linux's "installer" mode (--type deb) needs dpkg-dev and
# fakeroot installed.
#
# Usage: scripts/package.sh [app-image|installer]
#   app-image (default) - fast, produces a runnable .app/folder for local testing.
#   installer            - produces the distributable installer (.dmg on macOS, .deb on Linux).

set -euo pipefail

MODE="${1:-app-image}"
if [[ "$MODE" != "app-image" && "$MODE" != "installer" ]]; then
  echo "Usage: $0 [app-image|installer]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APP_VERSION="1.0.0"
DIST_DIR="$ROOT_DIR/dist"
STAGE_DIR="$DIST_DIR/stage"

case "$(uname -s)" in
  Darwin)
    if [[ "$MODE" == "installer" ]]; then JPACKAGE_TYPE=dmg; else JPACKAGE_TYPE=app-image; fi
    ICON="$ROOT_DIR/branding/icon.icns"
    ;;
  Linux)
    if [[ "$MODE" == "installer" ]]; then JPACKAGE_TYPE=deb; else JPACKAGE_TYPE=app-image; fi
    ICON="$ROOT_DIR/branding/icon.png"
    ;;
  *)
    echo "Unsupported OS for this script: $(uname -s). Use scripts/package.ps1 on Windows." >&2
    exit 1
    ;;
esac

echo "==> Building thin jars + lib/ (mvn package)"
mvn -q -pl cli,gui-javafx -am package -DskipTests

stage() {
  local module="$1" jar="$2" stage_name="$3"
  local target="$STAGE_DIR/$stage_name"
  rm -rf "$target"
  mkdir -p "$target"
  cp "$module/target/$jar" "$target/"
  cp -R "$module/target/lib" "$target/lib"
}

package_app() {
  local stage_name="$1" app_name="$2" main_jar="$3" main_class="$4"
  echo "==> jpackage ($JPACKAGE_TYPE): $app_name"
  jpackage \
    --type "$JPACKAGE_TYPE" \
    --input "$STAGE_DIR/$stage_name" \
    --dest "$DIST_DIR" \
    --name "$app_name" \
    --main-jar "$main_jar" \
    --main-class "$main_class" \
    --app-version "$APP_VERSION" \
    --icon "$ICON"
}

rm -rf "$STAGE_DIR"

stage cli rikiki-vault.jar cli
package_app cli rikiki-vault-cli rikiki-vault.jar io.github.jlmc.rikikivault.cli.Main

stage gui-javafx rikiki-vault-gui.jar gui
package_app gui rikiki-vault-gui rikiki-vault-gui.jar io.github.jlmc.rikikivault.gui.Launcher

rm -rf "$STAGE_DIR"

echo
echo "Done. Output in $DIST_DIR"
echo "  CLI: run from a terminal, e.g. dist/rikiki-vault-cli.app/Contents/MacOS/rikiki-vault-cli (macOS app-image)"
echo "       or the installed .deb's /opt/rikiki-vault-cli/bin/rikiki-vault-cli (Linux installer)."
echo "  GUI: open dist/rikiki-vault-gui.app (macOS) or install/run the .deb (Linux)."
