# Rikiki Vault

*[Ler em português](README.pt.md)*

A local-first, end-to-end encrypted file vault synced through a private Git repository. You
keep plaintext files in a `local/` working copy; `publish` encrypts them (X25519 + AES-GCM) into
`documents/` and pushes the result. Any authorized machine can `clone`/`pull` the repository and
decrypt back to its own `local/` copy. Nothing ever reaches Git in plaintext.

## Requirements

- Java 25
- Maven

## Building

From the repository root:

```
mvn install
```

This builds all three modules:

- **`core`** — domain model, ports & adapters, all the application use cases. No UI, no `main`.
- **`cli`** — a console application (`cli/target/rikiki-vault.jar`) exposing every use case as a
  command.
- **`gui-javafx`** — a desktop JavaFX application wrapping the same use cases.

## Machine identity

Every machine has one X25519 keypair, stored under `~/.rikiki-vault/identity` — shared across
every vault you open on that machine, since the identity represents *the machine*, not a
particular vault. Both the CLI and the GUI read/write it from the same place.

## CLI

Build the jar once:

```
mvn -pl cli package
```

Then run it as `java -jar cli/target/rikiki-vault.jar <command> [args]`. Every command operates
on the current directory as the vault root, unless you pass `-C <path>` first (like `git -C`):

```
java -jar cli/target/rikiki-vault.jar -C /path/to/vault status
```

### Commands

| Command | What it does |
|---|---|
| `init [--git] [--remote <url>] <machine-label>` | Initializes a brand-new vault in the current directory. `--git` also runs `git init` locally; `--remote` (requires `--git`) additionally registers that URL as `origin`, so the very first `publish` already pushes for real. Seeds the recipient registry with this machine as the sole recipient. |
| `whoami` | Prints this machine's identity fingerprint, generating one first if it doesn't exist yet. |
| `export-key <output-file>` | Writes this machine's public key to a file, to hand to whoever manages `authorize` on another machine. |
| `clone <remote-uri>` | Joins an *already-initialized* vault (use `init` to start a brand-new one instead). |
| `status` | Lists pending local changes (added/modified/deleted) against the last publish. |
| `publish -m "<message>"` | Encrypts every pending change for all currently authorized recipients, commits, and pushes. |
| `pull` | Pulls the latest encrypted state and decrypts what changed remotely into `local/`. Never overwrites a file you've also changed locally — that's reported as a conflict instead. |
| `authorize <label> <public-key-file>` | Grants another machine access: adds it to the recipient registry and re-encrypts every already-published file for the updated set. |
| `revoke <fingerprint-hex>` | Removes a machine's access, re-encrypting everything so its key is no longer able to decrypt anything new. |
| `git-auth show\|set-ssh-key <path>\|clear-ssh-key\|set-token\|clear-token` | Configures explicit Git authentication, overriding implicit discovery - see "Explicit Git authentication" below. `set-token` reads the token from stdin, never an argument, to avoid it ending up in shell history. |

### A full walkthrough

```
mkdir my-vault && cd my-vault
java -jar rikiki-vault.jar init --git machine-a

mkdir local
echo "hello" > local/notes.txt

java -jar rikiki-vault.jar status
java -jar rikiki-vault.jar publish -m "first publish"
```

A remote is optional: `publish`/`authorize`/`revoke` commit locally and print
`(guardado localmente - sem remoto configurado)` when none is configured, instead of failing. You
can register one right at `init` time with `--remote` (GitRepositoryPort's only remote-related
operation), or add one later with plain `git`:

```
java -jar rikiki-vault.jar init --git --remote git@github.com:you/my-vault-encrypted.git machine-a
```

```
git remote add origin git@github.com:you/my-vault-encrypted.git
java -jar rikiki-vault.jar publish -m "sync to remote"
```

To let a second machine in:

```
# on machine B, once it has its own identity (run any command once, e.g. `whoami`):
java -jar rikiki-vault.jar export-key machine-b.pub
# send machine-b.pub to whoever runs machine A

# on machine A:
java -jar rikiki-vault.jar authorize machine-b machine-b.pub

# on machine B:
java -jar rikiki-vault.jar clone git@github.com:you/my-vault-encrypted.git
```

### Pushing to a real remote

Set `RIKIKI_VAULT_GITHUB_TOKEN` in your environment before `publish`/`pull`/`clone` against an
HTTPS remote that needs a token. SSH remotes fall back to your system's SSH agent/keys instead —
no extra configuration needed.

### Explicit Git authentication

If implicit discovery doesn't work in your environment (e.g. an SSH key with a non-default
filename that the agent isn't offering, or you'd rather not export an env var), configure it
explicitly instead — in the desktop app via "Definições de Git..." (on the Welcome screen and in
the main window's toolbar), or on the CLI:

```
rikiki-vault git-auth set-ssh-key ~/.ssh/id_jc
echo "$MY_GITHUB_TOKEN" | rikiki-vault git-auth set-token
rikiki-vault git-auth show
```

Never pass a token as a `set-token` argument — it would land in shell history. Both settings are
stored in `~/.rikiki-vault/git-auth/settings.json` with owner-only (`rw-------`) permissions, same
as the private key. The remote URL's own scheme decides which applies: `git@`/`ssh://` uses the
configured key (falling back to automatic discovery when none is set), `https://` uses the
configured token (falling back to `RIKIKI_VAULT_GITHUB_TOKEN`).

### Hardening notes

Decrypted files written to `local/` are created with owner-only permissions (`rw-------` on
POSIX systems, matching how the machine's private key is already stored) and written atomically —
a crash or interruption mid-write can never leave a truncated file in place. When `pull` reports a
conflict, both the local and remote SHA-256 hashes are shown so you can tell the two versions apart
before manually reconciling them; there's still no "keep remote"/"compare" action built in — the
local version is always what's kept automatically, and you resolve the rest by hand.

## Desktop app (JavaFX)

```
mvn -pl gui-javafx javafx:run
```

On launch, pick a folder — either an existing vault or an empty one to initialize/clone into. The
main window shows the vault's file tree with a status badge per file (synced/added/modified/
deleted), a preview pane for the selected file (text, JSON, XML, Markdown, images, and the first
page of PDFs), and toolbar actions for Pull, Publish (with a review-and-approve step before
anything is encrypted), and managing machine access (authorize/revoke).

Editing files and diffing changes in the GUI aren't implemented yet — for now, edit files in
`local/` with your own editor and use `publish` to review and push the result.
