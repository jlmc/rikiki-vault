#!/usr/bin/env bash
# Converts an existing vault from the older plaintext-path format to the current one, where real
# file paths/names are encrypted too (not just content) - see
# docs/faq/11-migrating-to-encrypted-paths.md for the full walkthrough and, importantly, the
# multi-machine coordination warnings to read BEFORE running this with --yes on a real vault.
#
# Thin wrapper around `scripts/run-cli.sh -- -C <vault-dir> migrate-format ...` - builds the CLI
# jar if it doesn't exist yet (same as run-cli.sh/run.sh already do) and takes the vault directory
# as a plain first argument instead of requiring `-- -C <dir>` to be typed by hand. Does not
# reimplement the --dry-run/--yes safety check - that already lives in the migrate-format command
# itself (Main.runMigrateFormat) and stays the single source of truth for it.
#
# Usage:
#   scripts/migrate-format.sh <vault-dir> --dry-run   # see what would happen, changes nothing
#   scripts/migrate-format.sh <vault-dir> --yes       # actually migrate (one-way)
#
# Example:
#   scripts/migrate-format.sh ~/my-vault --dry-run

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <vault-dir> --dry-run|--yes" >&2
  echo "Read docs/faq/11-migrating-to-encrypted-paths.md before running with --yes." >&2
  exit 1
fi

VAULT_DIR="$1"
shift

if [[ ! -d "$VAULT_DIR" ]]; then
  echo "Error: '$VAULT_DIR' is not a directory." >&2
  exit 1
fi

exec "$ROOT_DIR/run-cli.sh" -- -C "$VAULT_DIR" migrate-format "$@"
