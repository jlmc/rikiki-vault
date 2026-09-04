#!/usr/bin/env bash
# Shortcut for `scripts/run.sh gui` - see that script for the actual logic/usage.
set -euo pipefail
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run.sh" gui "$@"
