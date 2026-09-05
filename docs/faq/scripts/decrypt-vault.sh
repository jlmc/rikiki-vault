#!/usr/bin/env bash
# Decrypts every .enc file under a Rikiki Vault's documents/ directory, using only OpenSSL and
# coreutils - no Java, no Maven, no rikiki-vault application at all. Understands the RV01 file
# format (magic + per-recipient wrapped AES key + AES-256-GCM sealed content) and the
# X25519 + HKDF-SHA256 + AES-256-GCM key-wrap scheme it uses.
#
# CAVEAT: unlike the real app, this script does not cryptographically verify the AES-GCM
# authentication tag (OpenSSL's `enc` command has no AEAD support at all, and there is no
# practical way to check a GCM tag with stock OpenSSL CLI commands alone). It decrypts using the
# same key material and produces byte-identical plaintext to the app for untampered data, but
# does not detect a corrupted/tampered ciphertext the way real AES-GCM decryption would. Use this
# only to recover your own data from your own trusted repository, not as a general-purpose
# decryption tool.
#
# Requires a real OpenSSL (3.x) - macOS ships LibreSSL by default, which this script does not
# support. Install one with: brew install openssl@3
#
# All four arguments are FILESYSTEM PATHS, not the content of anything - two paths to the raw
# PKCS8/X.509 DER key files (e.g. ~/.rikiki-vault/identity/private.key and public.key, or your
# backups of them), a path to the vault's documents/ folder, and a path to write decrypted output
# under (created if missing).
#
# Usage:
#   decrypt-vault.sh <path-to-private.key> <path-to-public.key> <path-to-documents-dir> <path-to-output-dir>
#
# Example:
#   decrypt-vault.sh ~/.rikiki-vault/identity/private.key ~/.rikiki-vault/identity/public.key \
#     my-vault-checkout/documents ./decrypted

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

if [[ $# -ne 4 ]]; then
  echo "Usage: $0 <path-to-private.key> <path-to-public.key> <path-to-documents-dir> <path-to-output-dir>" >&2
  echo "(the first two are paths to the raw key FILES, e.g. ~/.rikiki-vault/identity/private.key - not the key content itself)" >&2
  exit 1
fi
PRIVATE_KEY="$1"
PUBLIC_KEY="$2"
DOCS_DIR="$3"
OUT_DIR="$4"

if [[ ! -f "$PRIVATE_KEY" ]]; then
  echo "Error: '$PRIVATE_KEY' is not a file. Pass the PATH to your private.key file, not its content." >&2
  exit 1
fi
if [[ ! -f "$PUBLIC_KEY" ]]; then
  echo "Error: '$PUBLIC_KEY' is not a file. Pass the PATH to your public.key file, not its content." >&2
  exit 1
fi
if [[ ! -d "$DOCS_DIR" ]]; then
  echo "Error: '$DOCS_DIR' is not a directory. Pass the PATH to the vault's documents/ folder." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

HKDF_LABEL="RIKIKI-VAULT-V1-FILE-KEY-WRAP"

# extract_bytes <file> <offset0based> <length> <outfile>
extract_bytes() {
  tail -c "+$(( $2 + 1 ))" "$1" | head -c "$3" > "$4"
}

# be_uint <file> — reads the WHOLE (small) file as a big-endian unsigned integer
be_uint() {
  local hex
  hex="$(xxd -p -c 256 "$1" | tr -d '\n')"
  [[ -z "$hex" ]] && { echo 0; return; }
  echo "$(( 16#$hex ))"
}

MY_FINGERPRINT="$("$OSSL" dgst -sha256 -binary "$PUBLIC_KEY" | xxd -p -c 256 | tr -d '\n')"
echo "Machine fingerprint: $MY_FINGERPRINT"

decrypt_one() {
  local enc_file="$1" out_file="$2"
  local off=0

  extract_bytes "$enc_file" 0 4 "$WORKDIR/magic.bin"
  if [[ "$(cat "$WORKDIR/magic.bin")" != "RV01" ]]; then
    echo "  skip: not an RV01 file" >&2
    return
  fi
  extract_bytes "$enc_file" 4 1 "$WORKDIR/symalg.bin"
  extract_bytes "$enc_file" 5 1 "$WORKDIR/wrapalg.bin"
  if [[ "$(be_uint "$WORKDIR/symalg.bin")" != "1" || "$(be_uint "$WORKDIR/wrapalg.bin")" != "1" ]]; then
    echo "  skip: unrecognized algorithm ids - this script only understands AES-GCM + X25519/HKDF" >&2
    return
  fi
  off=6 # magic(4) + symmetricAlgorithmId(1) + keyWrapAlgorithmId(1)

  extract_bytes "$enc_file" "$off" 2 "$WORKDIR/namelen.bin"
  local name_len; name_len="$(be_uint "$WORKDIR/namelen.bin")"
  off=$(( off + 2 + name_len ))

  extract_bytes "$enc_file" "$off" 2 "$WORKDIR/rcount.bin"
  local recipient_count; recipient_count="$(be_uint "$WORKDIR/rcount.bin")"
  off=$(( off + 2 ))

  local blob_off=-1 blob_len=0
  local i
  for (( i = 0; i < recipient_count; i++ )); do
    extract_bytes "$enc_file" "$off" 32 "$WORKDIR/fp.bin"
    local fp; fp="$(xxd -p -c 256 "$WORKDIR/fp.bin" | tr -d '\n')"
    off=$(( off + 32 ))
    extract_bytes "$enc_file" "$off" 4 "$WORKDIR/bloblen.bin"
    local this_blob_len; this_blob_len="$(be_uint "$WORKDIR/bloblen.bin")"
    off=$(( off + 4 ))
    if [[ "$fp" == "$MY_FINGERPRINT" ]]; then
      blob_off=$off
      blob_len=$this_blob_len
    fi
    off=$(( off + this_blob_len ))
  done

  if [[ $blob_off -lt 0 ]]; then
    echo "  skip: this machine is not an authorized recipient" >&2
    return
  fi

  # -- unwrap the per-file AES content key --
  extract_bytes "$enc_file" "$blob_off" "$blob_len" "$WORKDIR/blob.bin"
  extract_bytes "$WORKDIR/blob.bin" 0 2 "$WORKDIR/eph_len.bin"
  local eph_len; eph_len="$(be_uint "$WORKDIR/eph_len.bin")"
  extract_bytes "$WORKDIR/blob.bin" 2 "$eph_len" "$WORKDIR/eph_pub.der"
  extract_bytes "$WORKDIR/blob.bin" $(( 2 + eph_len )) 12 "$WORKDIR/wrap_nonce.bin"
  local wk_start=$(( 2 + eph_len + 12 ))
  local wk_total=$(( blob_len - wk_start ))
  local wk_ct_len=$(( wk_total - 16 ))
  extract_bytes "$WORKDIR/blob.bin" "$wk_start" "$wk_ct_len" "$WORKDIR/wk_ct.bin"

  "$OSSL" pkeyutl -derive -inkey "$PRIVATE_KEY" -keyform DER \
    -peerkey "$WORKDIR/eph_pub.der" -peerform DER -out "$WORKDIR/shared.bin" 2>/dev/null

  printf '%s' "$HKDF_LABEL" > "$WORKDIR/info.bin"
  cat "$WORKDIR/eph_pub.der" >> "$WORKDIR/info.bin"

  local ikm_hex info_hex nonce_hex kek_hex
  ikm_hex="$(xxd -p -c 256 "$WORKDIR/shared.bin" | tr -d '\n')"
  info_hex="$(xxd -p -c 4096 "$WORKDIR/info.bin" | tr -d '\n')"
  local salt_hex; salt_hex="$(printf '00%.0s' $(seq 1 32))"
  kek_hex="$("$OSSL" kdf -keylen 32 -kdfopt digest:SHA2-256 -kdfopt hexkey:"$ikm_hex" \
    -kdfopt hexsalt:"$salt_hex" -kdfopt hexinfo:"$info_hex" -kdfopt mode:EXTRACT_AND_EXPAND HKDF \
    2>/dev/null | tr -d ':\n' | tr 'A-F' 'a-f')"

  nonce_hex="$(xxd -p -c 256 "$WORKDIR/wrap_nonce.bin" | tr -d '\n')"
  "$OSSL" enc -aes-256-ctr -d -K "$kek_hex" -iv "${nonce_hex}00000002" \
    -in "$WORKDIR/wk_ct.bin" -out "$WORKDIR/content_key.bin" 2>/dev/null

  # -- decrypt the actual file content with the recovered key --
  extract_bytes "$enc_file" "$off" 12 "$WORKDIR/content_nonce.bin"
  off=$(( off + 12 ))
  extract_bytes "$enc_file" "$off" 4 "$WORKDIR/sealedlen.bin"
  local sealed_len; sealed_len="$(be_uint "$WORKDIR/sealedlen.bin")"
  off=$(( off + 4 ))
  local sealed_ct_len=$(( sealed_len - 16 ))
  extract_bytes "$enc_file" "$off" "$sealed_ct_len" "$WORKDIR/sealed_ct.bin"

  local content_key_hex content_nonce_hex
  content_key_hex="$(xxd -p -c 256 "$WORKDIR/content_key.bin" | tr -d '\n')"
  content_nonce_hex="$(xxd -p -c 256 "$WORKDIR/content_nonce.bin" | tr -d '\n')"

  mkdir -p "$(dirname "$out_file")"
  "$OSSL" enc -aes-256-ctr -d -K "$content_key_hex" -iv "${content_nonce_hex}00000002" \
    -in "$WORKDIR/sealed_ct.bin" -out "$out_file" 2>/dev/null
  chmod 600 "$out_file" 2>/dev/null || true   # decrypted content is sensitive, same as the real app's local/

  echo "  decrypted -> $out_file"
}

while IFS= read -r -d '' enc_file; do
  rel="${enc_file#"$DOCS_DIR"/}"
  rel="${rel%.enc}"
  echo "Decrypting: $rel"
  decrypt_one "$enc_file" "$OUT_DIR/$rel"
done < <(find "$DOCS_DIR" -type f -name '*.enc' -print0)

echo "Done. Plaintext written under $OUT_DIR"
