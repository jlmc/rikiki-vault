# I don't want to use the app anymore. I have the data in the remote repository and both keys — how do I decrypt everything without the app?

*[Ler em português](04-decrypting-without-the-app.pt.md)*

You don't need Java, Maven, or this project's source code to get your data back — the encryption
format is simple enough to decrypt with nothing but OpenSSL, jq, and standard shell tools. This
answer explains the format, then gives you a ready-to-run script that does it.

## The format, briefly

Since the RV02 format (see [FAQ 11](11-migrating-to-encrypted-paths.md) if you have an older vault
still on RV01), real file paths are themselves encrypted, not just content — so recovering
everything is a two-step process: decrypt `manifest.json` first to learn which id under
`documents/` corresponds to which real path, then decrypt each of those.

`manifest.json` and every file under `documents/` share the exact same binary envelope (format
`RV02`), a flat layout:

1. Magic bytes `RV02`, then two 1-byte algorithm ids (always AES-GCM + X25519/HKDF for every file
   this project has ever produced).
2. A list of **recipient entries** — one per machine authorized when the file was published. Each
   entry has that machine's fingerprint (SHA-256 of its public key) and a "wrapped key" blob.
3. A 12-byte nonce and the AES-256-GCM–sealed content (ciphertext + 16-byte tag) — for
   `manifest.json`, that content is itself a JSON document; for anything under `documents/`, it's
   the real file's bytes.

Encryption is hybrid: the actual content is sealed once with a random AES-256 key (the "content
key"); that content key is then encrypted ("wrapped") separately for each authorized recipient, so
anyone with the file needs their own private key to recover the content key first. Each
wrapped-key blob is itself: a one-time ephemeral X25519 public key, a 12-byte nonce, and the
AES-256-GCM–sealed content key. Unwrapping it means: X25519 key agreement (your private key +
that ephemeral public key) → HKDF-SHA256 (with a fixed label bound to the ephemeral public key) →
AES-256-GCM decrypt.

Decrypted, `manifest.json` looks like:

```json
{
  "version": 1,
  "hmacKeyBase64": "...",
  "files": [
    { "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479", "plaintextPath": "taxes/2025/return.pdf",
      "hash": "...", "formatVersion": "RV02" }
  ]
}
```

`files[].id` is the name (minus `.enc`) of the matching file under `documents/`;
`files[].plaintextPath` is where it really belongs. You don't need to hand-roll any of the
decryption above — the script below does it, for the manifest and for every file it points to.

## The script

Save this as `decrypt-vault.sh` (it's also checked into this project's own repository at
[`docs/faq/scripts/decrypt-vault.sh`](scripts/decrypt-vault.sh), if you'd rather grab it from
there than copy-paste):

```bash
#!/usr/bin/env bash
# Decrypts a Rikiki Vault entirely with OpenSSL, coreutils and jq - no Java, no Maven, no
# rikiki-vault application at all. Understands the RV02 file format (magic + per-recipient wrapped
# AES key + AES-256-GCM sealed content) and the X25519 + HKDF-SHA256 + AES-256-GCM key-wrap scheme
# it uses. Since RV02, manifest.json itself is encrypted in this same format (that's what keeps
# real file paths/names out of the git-visible remote) - this script decrypts it first to learn the
# id-to-real-path mapping, then walks documents/*.enc using that mapping, instead of deriving
# output paths from ciphertext filenames (which are now random, opaque ids).
#
# If your private.key is passphrase-protected (set-passphrase), this script can't use it directly
# - run unwrap-private-key.sh (same folder) or `rikiki-vault unwrap-key` first, and point this
# script at the plain-PKCS8 result instead. See "If private.key is passphrase-protected" in
# docs/faq/04-decrypting-without-the-app.md.
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
# Also requires jq, to parse the decrypted manifest.json. Install with: brew install jq (macOS)
# or apt install jq (Debian/Ubuntu).
#
# All four arguments are FILESYSTEM PATHS, not the content of anything - two paths to the raw
# PKCS8/X.509 DER key files (e.g. ~/.rikiki-vault/identity/private.key and public.key, or your
# backups of them), a path to the vault's checkout root (the folder that directly contains
# vault/manifest.json and documents/), and a path to write decrypted output under (created if
# missing).
#
# Usage:
#   decrypt-vault.sh <path-to-private.key> <path-to-public.key> <path-to-vault-checkout> <path-to-output-dir>
#
# Example:
#   decrypt-vault.sh ~/.rikiki-vault/identity/private.key ~/.rikiki-vault/identity/public.key \
#     my-vault-checkout ./decrypted

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

if ! command -v jq >/dev/null 2>&1; then
  echo "Error: this script needs jq to parse the decrypted manifest. Install with: brew install jq (or apt install jq)" >&2
  exit 1
fi

if [[ $# -ne 4 ]]; then
  echo "Usage: $0 <path-to-private.key> <path-to-public.key> <path-to-vault-checkout> <path-to-output-dir>" >&2
  echo "(the first two are paths to the raw key FILES, e.g. ~/.rikiki-vault/identity/private.key - not the key content itself)" >&2
  exit 1
fi
PRIVATE_KEY="$1"
PUBLIC_KEY="$2"
VAULT_DIR="$3"
OUT_DIR="$4"
MANIFEST_FILE="$VAULT_DIR/vault/manifest.json"
DOCS_DIR="$VAULT_DIR/documents"

if [[ ! -f "$PRIVATE_KEY" ]]; then
  echo "Error: '$PRIVATE_KEY' is not a file. Pass the PATH to your private.key file, not its content." >&2
  exit 1
fi
if [[ ! -f "$PUBLIC_KEY" ]]; then
  echo "Error: '$PUBLIC_KEY' is not a file. Pass the PATH to your public.key file, not its content." >&2
  exit 1
fi
if [[ ! -f "$MANIFEST_FILE" ]]; then
  echo "Error: '$MANIFEST_FILE' is not a file. Pass the PATH to the vault checkout root (containing vault/manifest.json)." >&2
  exit 1
fi
if [[ ! -d "$DOCS_DIR" ]]; then
  echo "Error: '$DOCS_DIR' is not a directory. Expected a documents/ folder next to vault/ in the checkout." >&2
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

# decrypt_one <enc_file> <out_file> - RV02 layout: magic(4) + symAlgoId(1) + wrapAlgoId(1) +
# recipientCount(2) + recipients[fingerprint(32) + wrappedKeyLen(4) + wrappedKey] +
# contentNonce(12) + sealedContentLen(4) + sealedContent. No filename field (that's the whole point).
decrypt_one() {
  local enc_file="$1" out_file="$2"
  local off=0

  extract_bytes "$enc_file" 0 4 "$WORKDIR/magic.bin"
  if [[ "$(cat "$WORKDIR/magic.bin")" != "RV02" ]]; then
    echo "  skip: not an RV02 file (older RV01 vault? run the app's migrate-format first)" >&2
    return 1
  fi
  extract_bytes "$enc_file" 4 1 "$WORKDIR/symalg.bin"
  extract_bytes "$enc_file" 5 1 "$WORKDIR/wrapalg.bin"
  if [[ "$(be_uint "$WORKDIR/symalg.bin")" != "1" || "$(be_uint "$WORKDIR/wrapalg.bin")" != "1" ]]; then
    echo "  skip: unrecognized algorithm ids - this script only understands AES-GCM + X25519/HKDF" >&2
    return 1
  fi
  off=6 # magic(4) + symmetricAlgorithmId(1) + keyWrapAlgorithmId(1)

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
    return 1
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

  # -- decrypt the actual content with the recovered key --
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
}

# -- Step 1: decrypt manifest.json itself (same RV02 format as any file) --
echo "Decrypting manifest.json..."
if ! decrypt_one "$MANIFEST_FILE" "$WORKDIR/manifest.decrypted.json"; then
  echo "Error: could not decrypt manifest.json - see message above." >&2
  exit 1
fi

# -- Step 2 & 3: parse the id-to-real-path mapping and decrypt each documents/<id>.enc to its real
# path - reading jq's one-object-per-line output via a plain while/read loop (not `mapfile`, which
# isn't available in the bash 3.2 that macOS still ships as /bin/bash).
file_count=0
while IFS= read -r entry; do
  file_count=$(( file_count + 1 ))
  id="$(jq -r '.id' <<< "$entry")"
  plaintext_path="$(jq -r '.plaintextPath' <<< "$entry")"
  enc_file="$DOCS_DIR/$id.enc"
  if [[ ! -f "$enc_file" ]]; then
    echo "  skip: $plaintext_path -> $enc_file not found" >&2
    continue
  fi
  echo "Decrypting: $plaintext_path"
  out_file="$OUT_DIR/$plaintext_path"
  if decrypt_one "$enc_file" "$out_file"; then
    chmod 600 "$out_file" 2>/dev/null || true   # decrypted content is sensitive, same as the real app's local/
    echo "  decrypted -> $out_file"
  fi
done < <(jq -c '.files[]' "$WORKDIR/manifest.decrypted.json")
echo "Manifest lists $file_count file(s)."

echo "Done. Plaintext written under $OUT_DIR"
```

This has been tested end to end against real vault data (multiple recipients, nested real paths,
text and binary content) and reproduces the original files byte for byte.

## Usage

```bash
brew install openssl@3 jq   # macOS ships LibreSSL by default - this script needs the real OpenSSL, plus jq
chmod +x decrypt-vault.sh

git clone <your-vault-remote-uri> my-vault-checkout   # a plain git clone, not the rikiki-vault app
./decrypt-vault.sh /path/to/private.key /path/to/public.key my-vault-checkout ./decrypted
```

### If `private.key` is passphrase-protected

Skip this if it isn't — `decrypt-vault.sh` above already works directly on a plain PKCS8 DER file.
If you protected it (`set-passphrase` — see the [password protection FAQ
entry](07-private-key-is-not-password-protected.md)), decrypt it to plain PKCS8 first, then point
`decrypt-vault.sh` at *that* file instead of the protected one. Two ways, same trade-off described
in [FAQ 03](03-recovering-with-only-the-private-key.md#step-0):

```bash
# Recipe A - the app's own unwrap-key command (verifies the passphrase, fails loudly if wrong):
java -jar cli/target/rikiki-vault.jar unwrap-key /path/to/private.key private.key.plain

# Recipe B - pure OpenSSL/bash, no Java at all (can't verify the passphrase - see the script's own
# header comment for what "no error, but wrong output" looks like):
docs/faq/scripts/unwrap-private-key.sh /path/to/private.key private.key.plain

# Either way, use the unwrapped file with decrypt-vault.sh:
./decrypt-vault.sh private.key.plain /path/to/public.key my-vault-checkout ./decrypted
```

Recipe B keeps this FAQ entry's original promise ("no app needed at all") for the vault-decryption
step itself; only the one-time unwrap needs the app if you go with Recipe A instead. If Recipe A
isn't an option (no JDK, don't want to build the app) and Recipe B's silent-garbage-on-wrong-
passphrase risk isn't acceptable, restoring both key files onto a real install and using the app
directly (see [FAQ 02](02-recovering-with-both-keys-backed-up.md)) is the safer fallback.

All four arguments are **filesystem paths** — the first two point to your raw `private.key` and
`public.key` files (e.g. `~/.rikiki-vault/identity/private.key`, or your backups of them), not the
key content itself pasted inline; the third is the vault checkout's root folder (containing
`vault/manifest.json` and `documents/`), and the fourth is the folder to write decrypted output
into.

It decrypts `manifest.json` first to learn the id-to-real-path mapping, then walks
`documents/<id>.enc` using that mapping, checking whether your fingerprint is among each file's
authorized recipients and decrypting it to its real relative path under the output directory if
so. Files you weren't authorized for are reported and skipped, not treated as errors.

## The one thing this script doesn't do

The real app verifies the AES-GCM authentication tag on every decrypt — cryptographic proof the
ciphertext wasn't corrupted or tampered with. This script skips that check: OpenSSL's `enc`
command has no support at all for AEAD ciphers like GCM, and there's no practical way to verify a
GCM tag with stock OpenSSL CLI commands alone (it would mean reimplementing GHASH's finite-field
arithmetic by hand, which isn't a reasonable ask of a shell script). The script decrypts with
exactly the same keys and produces byte-identical output to the app for real, untampered data —
it just doesn't cryptographically confirm that for you.

For recovering your own data out of your own trusted Git history, that's a reasonable trade-off.
If that guarantee matters to you, decrypt via the actual app instead (see the
[first](01-disk-failure-and-backups.md)/[second](02-recovering-with-both-keys-backed-up.md) FAQ
entries) — this script exists specifically for "I don't want this project's source code to be a
hard dependency for reading my own files," not as a general replacement for it.
