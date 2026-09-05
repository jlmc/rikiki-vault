#!/usr/bin/env bash
# Derives an X25519 public key from its private key. Unlike most key types, this is possible
# because an X25519 public key is a deterministic function of the private key - useful when you
# backed up ~/.rikiki-vault/identity/private.key but not the matching public.key.
#
# Requires a real OpenSSL (3.x) - macOS ships LibreSSL by default, which doesn't reliably handle
# X25519 the same way. Install one with: brew install openssl@3
#
# Both arguments are FILESYSTEM PATHS, not key content: a path to your existing private.key file
# (raw PKCS8 DER, e.g. your backup of ~/.rikiki-vault/identity/private.key) to read, and a path to
# write the derived public.key file to (created/overwritten).
#
# Usage: derive-public-key.sh <path-to-private.key> <path-to-output-public.key>
#
# Example: derive-public-key.sh ~/recovered/private.key ~/.rikiki-vault/identity/public.key

set -euo pipefail

OSSL="${OSSL:-openssl}"
if ! "$OSSL" version 2>/dev/null | grep -qi '^OpenSSL'; then
  # Not on PATH (or PATH resolves to LibreSSL, macOS's default) - ask Homebrew directly where it
  # put openssl@3, instead of guessing its install prefix (which differs between Apple Silicon
  # and Intel Macs, and isn't on PATH by default since it would shadow the system's openssl).
  brew_prefix=""
  if command -v brew >/dev/null 2>&1; then
    brew_prefix="$(brew --prefix openssl@3 2>/dev/null || true)"
  fi
  if [[ -n "$brew_prefix" && -x "$brew_prefix/bin/openssl" ]]; then
    OSSL="$brew_prefix/bin/openssl"
  else
    echo "Error: need a real OpenSSL (not LibreSSL). Install with: brew install openssl@3" >&2
    exit 1
  fi
fi

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <path-to-private.key> <path-to-output-public.key>" >&2
  echo "(the first argument is the PATH to your private.key file, not its content)" >&2
  exit 1
fi
if [[ ! -f "$1" ]]; then
  echo "Error: '$1' is not a file. Pass the PATH to your private.key file, not its content." >&2
  exit 1
fi

"$OSSL" pkey -in "$1" -inform DER -pubout -outform DER -out "$2"
chmod 600 "$2" 2>/dev/null || true   # matches the app's own key-file permissions (best-effort on non-POSIX filesystems)
echo "Wrote $2"
echo "Fingerprint: $("$OSSL" dgst -sha256 -binary "$2" | xxd -p -c 256)"
