# Não quero mais usar a app. Tenho os dados no repositório remoto e as duas chaves — como decifro tudo sem usar a app?

*[Read in English](04-decrypting-without-the-app.md)*

Não precisas de Java, Maven, nem do código-fonte deste projeto para recuperares os teus dados - o
formato de cifra é simples o suficiente para decifrar só com OpenSSL e ferramentas de shell
normais. Esta resposta explica o formato, e depois dá-te um script pronto a correr que faz isso.

## O formato, resumidamente

Cada ficheiro publicado fica na pasta `documents/` do vault como `<caminho-relativo>.enc` - a
estrutura de pastas espelha exatamente os teus ficheiros originais, só com `.enc` acrescentado a
cada nome. Cada ficheiro `.enc` (formato `RV01`) é um layout binário simples e plano:

1. Bytes mágicos `RV01`, depois dois ids de algoritmo de 1 byte cada (sempre AES-GCM +
   X25519/HKDF em qualquer ficheiro que este projeto alguma vez produziu).
2. O nome de ficheiro original (com o comprimento à frente).
3. Uma lista de **entradas de destinatário** - uma por máquina autorizada quando o ficheiro foi
   publicado. Cada entrada tem o fingerprint dessa máquina (SHA-256 da sua chave pública) e um
   blob de "chave embrulhada".
4. Um nonce de 12 bytes e o conteúdo do ficheiro cifrado com AES-256-GCM (texto cifrado + tag de
   16 bytes).

A cifra é híbrida: o conteúdo do ficheiro em si é cifrado uma única vez com uma chave AES-256
aleatória (a "chave de conteúdo"); essa chave de conteúdo é depois cifrada ("embrulhada")
separadamente para cada destinatário autorizado, para que quem tiver o ficheiro precise da sua
própria chave privada para recuperar primeiro a chave de conteúdo. Cada blob de chave embrulhada é,
por si só: uma chave pública X25519 efémera de uso único, um nonce de 12 bytes, e a chave de
conteúdo cifrada com AES-256-GCM. Desembrulhá-la significa: acordo de chaves X25519 (a tua chave
privada + essa chave pública efémera) → HKDF-SHA256 (com um rótulo fixo ligado à chave pública
efémera) → decifra AES-256-GCM.

Não precisas de reimplementar nada disto à mão - o script abaixo já faz tudo.

## O script

Guarda isto como `decrypt-vault.sh` (também está incluído no próprio repositório deste projeto em
[`docs/faq/scripts/decrypt-vault.sh`](scripts/decrypt-vault.sh), se preferires ir buscá-lo daí em
vez de copiar/colar):

```bash
#!/usr/bin/env bash
# Decrypts every .enc file under a Rikiki Vault's documents/ directory, using only OpenSSL and
# coreutils - no Java, no Maven, no rikiki-vault application at all. Understands the RV01 file
# format (magic + per-recipient wrapped AES key + AES-256-GCM sealed content) and the
# X25519 + HKDF-SHA256 + AES-256-GCM key-wrap scheme it uses.
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
```

Isto foi testado de ponta a ponta contra dados reais de um vault (vários destinatários, pastas
aninhadas, conteúdo de texto e binário) e reproduz os ficheiros originais byte a byte.

## Uso

```bash
brew install openssl@3   # o macOS traz LibreSSL por omissão - este script precisa do OpenSSL a sério
chmod +x decrypt-vault.sh

git clone <uri-do-remoto-do-teu-vault> checkout-do-meu-vault   # um git clone normal, não a app rikiki-vault
./decrypt-vault.sh /caminho/para/private.key /caminho/para/public.key checkout-do-meu-vault/documents ./decifrado
```

### Se o `private.key` estiver protegido por passphrase

Salta isto se não estiver — o `decrypt-vault.sh` acima já funciona diretamente sobre um ficheiro
PKCS8 DER simples. Se o protegeste (`set-passphrase` — ver a [entrada da FAQ sobre proteção por
password](07-private-key-is-not-password-protected.pt.md)), desencripta-o para PKCS8 em claro
primeiro, e depois aponta o `decrypt-vault.sh` para *esse* ficheiro em vez do protegido. Duas
formas, a mesma troca descrita na [FAQ 03](03-recovering-with-only-the-private-key.pt.md#passo-0):

```bash
# Receita A - o próprio comando unwrap-key da app (verifica a passphrase, falha claramente se errada):
java -jar cli/target/rikiki-vault.jar unwrap-key /caminho/para/private.key private.key.plain

# Receita B - OpenSSL/bash puro, sem Java nenhum (não consegue verificar a passphrase - ver o
# comentário no cabeçalho do próprio script para o que "sem erro, mas resultado errado" significa):
docs/faq/scripts/unwrap-private-key.sh /caminho/para/private.key private.key.plain

# De qualquer forma, usa o ficheiro desencriptado com o decrypt-vault.sh:
./decrypt-vault.sh private.key.plain /caminho/para/public.key checkout-do-meu-vault/documents ./decifrado
```

A Receita B mantém a promessa original desta entrada da FAQ ("sem app nenhuma") para o passo de
decifra do vault em si; só a desencriptação única precisa da app se escolheres a Receita A. Se a
Receita A não for opção (sem JDK, não queres compilar a app) e o risco de lixo silencioso da
Receita B com uma passphrase errada não for aceitável, restaurar os dois ficheiros de chave numa
instalação a sério e usar a app diretamente (ver [FAQ 02](02-recovering-with-both-keys-backed-up.pt.md))
é a alternativa mais segura.

Os quatro argumentos são **caminhos no sistema de ficheiros** — os dois primeiros apontam para os
teus ficheiros `private.key` e `public.key` reais (ex. `~/.rikiki-vault/identity/private.key`, ou
os teus backups deles), não o conteúdo da chave colado diretamente; o script lê o que quer que
esteja no ficheiro apontado por cada caminho. Os últimos dois são a pasta de onde ler os
ficheiros `.enc` e a pasta onde escrever o resultado decifrado.

Percorre todos os ficheiros `*.enc` sob `documents/`, verifica se o teu fingerprint está entre os
destinatários autorizados desse ficheiro, e se estiver decifra-o para o mesmo caminho relativo
(sem o `.enc`) dentro da pasta de saída. Ficheiros para os quais não estás autorizado são
reportados e ignorados, não tratados como erro.

## A única coisa que este script não faz

A app a sério verifica a tag de autenticação AES-GCM em cada decifra - prova criptográfica de que
o texto cifrado não foi corrompido nem adulterado. Este script salta essa verificação: o comando
`enc` do OpenSSL não tem nenhum suporte para cifras AEAD como o GCM, e não há forma prática de
verificar uma tag GCM só com os comandos normais da CLI do OpenSSL (implicaria reimplementar à mão
a aritmética de corpo finito do GHASH, o que não é um pedido razoável a fazer a um script de
shell). O script decifra com exatamente as mesmas chaves e produz um resultado idêntico byte a
byte ao da app para dados reais e não adulterados - só não o confirma criptograficamente por ti.

Para recuperar os teus próprios dados a partir do teu próprio histórico Git de confiança, esta é
uma troca razoável. Se essa garantia te importa, decifra através da app a sério (ver a
[primeira](01-disk-failure-and-backups.pt.md)/[segunda](02-recovering-with-both-keys-backed-up.pt.md)
respostas desta FAQ) - este script existe especificamente para "não quero que o código-fonte deste
projeto seja uma dependência obrigatória para ler os meus próprios ficheiros", não como
substituto geral dele.
