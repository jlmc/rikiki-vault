#!/usr/bin/env bash
# Decrypts a passphrase-protected Rikiki Vault private.key (the "RVPK" envelope: PBKDF2-HMAC-SHA256
# + AES-256-GCM) back to plain PKCS8 DER bytes, using only OpenSSL and coreutils - no Java, no
# Maven, no rikiki-vault application at all. Companion to decrypt-vault.sh/derive-public-key.sh in
# this same folder: run this FIRST if your private.key is passphrase-protected, then feed its
# output to whichever of those two you actually need.
#
# Always safe to run first: if the input file is NOT passphrase-protected (plain PKCS8 DER, the
# format every private.key had before this feature existed), it's just copied through unchanged -
# no passphrase prompt, no error. "Run unwrap-private-key.sh first" is a uniform recipe either way.
#
# CAVEAT - read this before trusting a "no error" result: unlike the real app (which verifies the
# AES-GCM authentication tag and refuses a wrong passphrase outright), this script CANNOT verify
# it - OpenSSL's `enc` command has no AEAD support at all. A wrong passphrase does not produce an
# error here: it silently produces garbage bytes instead of your real private key. The very next
# step in the FAQ (deriving the public key, or decrypting the vault) will almost certainly fail
# with an OpenSSL parse error if that happens - that failure, or a fingerprint that doesn't match
# what you expect, is your only signal something was wrong. If you want a real "wrong passphrase"
# error instead, use the CLI's `unwrap-key` command (see the main README/FAQ) - it uses the same
# code that encrypted the key in the first place, so it verifies the tag like the app always does.
#
# Requires a real OpenSSL (3.x) - macOS ships LibreSSL by default, which this script does not
# support. Install one with: brew install openssl@3
#
# Both arguments are FILESYSTEM PATHS, not key content - where your (possibly protected)
# private.key sits, and where to write the unwrapped, always-plain result.
#
# Usage:
#   unwrap-private-key.sh <path-to-private.key> <path-to-output-private.key>
#
# Example:
#   unwrap-private-key.sh /path/to/backup/private.key private.key.plain

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
  echo "Usage: $0 <path-to-private.key> <path-to-output-private.key>" >&2
  echo "(both are FILE PATHS - the first is where your private.key sits, the second is where to write the plain result)" >&2
  exit 1
fi
INPUT_KEY="$1"
OUTPUT_KEY="$2"

if [[ ! -f "$INPUT_KEY" ]]; then
  echo "Error: '$INPUT_KEY' is not a file. Pass the PATH to your private.key file, not its content." >&2
  exit 1
fi

# extract_bytes <file> <offset0based> <length> <outfile>
extract_bytes() {
  tail -c "+$(( $2 + 1 ))" "$1" | head -c "$3" > "$4"
}

# be_uint <file> - reads the WHOLE (small) file as a big-endian unsigned integer
be_uint() {
  local hex
  hex="$(xxd -p -c 256 "$1" | tr -d '\n')"
  [[ -z "$hex" ]] && { echo 0; return; }
  echo "$(( 16#$hex ))"
}

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

extract_bytes "$INPUT_KEY" 0 4 "$WORKDIR/magic.bin"
if [[ "$(cat "$WORKDIR/magic.bin")" != "RVPK" ]]; then
  echo "Input isn't passphrase-protected (no RVPK envelope) - copying through unchanged." >&2
  cp "$INPUT_KEY" "$OUTPUT_KEY"
  chmod 600 "$OUTPUT_KEY" 2>/dev/null || true
  echo "Written (unchanged): $OUTPUT_KEY"
  exit 0
fi

extract_bytes "$INPUT_KEY" 4 1 "$WORKDIR/version.bin"
version="$(be_uint "$WORKDIR/version.bin")"
if [[ "$version" != "1" ]]; then
  echo "Error: unsupported RVPK envelope version: $version (this script only understands version 1)" >&2
  exit 1
fi

extract_bytes "$INPUT_KEY" 5 1 "$WORKDIR/kdfid.bin"
kdf_id="$(be_uint "$WORKDIR/kdfid.bin")"
if [[ "$kdf_id" != "0" ]]; then
  echo "Error: unsupported KDF id: $kdf_id (this script only understands PBKDF2-HMAC-SHA256, id 0)" >&2
  exit 1
fi

extract_bytes "$INPUT_KEY" 6 4 "$WORKDIR/iterations.bin"
iterations="$(be_uint "$WORKDIR/iterations.bin")"

extract_bytes "$INPUT_KEY" 10 1 "$WORKDIR/saltlen.bin"
salt_len="$(be_uint "$WORKDIR/saltlen.bin")"
off=11

extract_bytes "$INPUT_KEY" "$off" "$salt_len" "$WORKDIR/salt.bin"
salt_hex="$(xxd -p -c 256 "$WORKDIR/salt.bin" | tr -d '\n')"
off=$(( off + salt_len ))

extract_bytes "$INPUT_KEY" "$off" 12 "$WORKDIR/nonce.bin"
nonce_hex="$(xxd -p -c 256 "$WORKDIR/nonce.bin" | tr -d '\n')"
off=$(( off + 12 ))

extract_bytes "$INPUT_KEY" "$off" 4 "$WORKDIR/ctlen.bin"
ciphertext_len="$(be_uint "$WORKDIR/ctlen.bin")"
off=$(( off + 4 ))

plaintext_len=$(( ciphertext_len - 16 ))  # last 16 bytes are the (unverified) GCM tag
extract_bytes "$INPUT_KEY" "$off" "$plaintext_len" "$WORKDIR/ciphertext_no_tag.bin"

echo "RVPK envelope: $iterations PBKDF2 iterations, ${salt_len}-byte salt, ${plaintext_len}-byte key." >&2

read -r -s -p "Passphrase: " PASSPHRASE
echo >&2

kek_hex="$("$OSSL" kdf -keylen 32 -kdfopt digest:SHA2-256 -kdfopt "pass:$PASSPHRASE" \
  -kdfopt "hexsalt:$salt_hex" -kdfopt "iter:$iterations" PBKDF2 2>/dev/null | tr -d ':\n')"
PASSPHRASE=""  # best-effort clear of the shell variable; not a real memory-wipe guarantee in bash

"$OSSL" enc -aes-256-ctr -d -K "$kek_hex" -iv "${nonce_hex}00000002" \
  -in "$WORKDIR/ciphertext_no_tag.bin" -out "$OUTPUT_KEY" 2>/dev/null
chmod 600 "$OUTPUT_KEY" 2>/dev/null || true

echo "Written: $OUTPUT_KEY"
echo "Reminder: this script cannot verify the passphrase was correct (see the caveat at the top)." >&2
echo "Confirm it worked before trusting it - e.g. derive-public-key.sh's fingerprint should match" >&2
echo "what you expect, or 'openssl pkey -in \"$OUTPUT_KEY\" -inform DER -noout -text' should print a" >&2
echo "readable key instead of a parse error." >&2
