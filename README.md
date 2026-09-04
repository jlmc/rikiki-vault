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
| `git-auth show\|set-ssh-key <path>\|clear-ssh-key\|set-token\|clear-token\|set-http-basic <username>\|clear-http-basic\|use ssh\|token\|http\|none` | Configures explicit Git authentication, overriding implicit discovery - see "Explicit Git authentication" below. `set-token`/`set-http-basic` read the secret from stdin, never an argument, to avoid it ending up in shell history. |

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
filename that the agent isn't offering, or you'd rather not export an env var), configure one of
three explicit methods instead. Only one is ever *active* at a time — you can have all three
configured (switching between them never discards the others), but only the active one is applied
by `clone`/`pull`/`push`, regardless of the remote URL's scheme:

- an **SSH key** (a specific private key file)
- a **GitHub token** (HTTPS)
- an **HTTP username/password** (HTTPS, for non-GitHub remotes)

Desktop app: "Configurações..." (Welcome screen or the main window's toolbar) → the **Git** tab
shows which method is active right now and lets you switch, with a "👁" toggle to reveal the
real value of the token/password fields instead of them always looking empty. CLI:

```
rikiki-vault git-auth set-ssh-key ~/.ssh/id_jc              # configures the key and makes SSH active
echo "$MY_GITHUB_TOKEN" | rikiki-vault git-auth set-token    # configures the token and makes it active
rikiki-vault git-auth set-http-basic myusername               # password is then read from stdin
rikiki-vault git-auth use ssh|token|http|none                 # switch the active method without reconfiguring
rikiki-vault git-auth show
```

Never pass a token or password as a `set-token`/`set-http-basic` argument — the secret is always
read from stdin, to avoid it landing in shell history. See "Configuration files" below for where
this is stored.

### Configuration files

Everything outside a vault itself lives under `~/.rikiki-vault/` — machine-global, shared by every
vault you open and by both the CLI and the GUI:

| File | What it's for | How to configure it |
|---|---|---|
| `~/.rikiki-vault/identity/private.key` / `public.key` | This machine's X25519 keypair (owner-only permissions) - see "Machine identity" above. | Generated automatically the first time it's needed; not user-editable. `whoami`/`export-key` read it. |
| `~/.rikiki-vault/config/config.yaml` | Bootstrap settings for the vault mechanics: where the identity directory lives, and the encryption parameters (algorithm, key sizes). Written once with sensible defaults on first run. | Not exposed in the UI/CLI - most users never need to touch it; edit the YAML directly only if you know what you're changing. |
| `~/.rikiki-vault/preferences/preferences.json` | Everything the Settings screen / `git-auth` command manage: which Git authentication method is active and its values (SSH key path, GitHub token, HTTP username/password), plus the app's display language (`PT`/`EN` - both the GUI and the CLI read it, so switching it in one place changes what both show). Owner-only (`rw-------`) permissions, since it can hold secrets. | Desktop: "Configurações...", including the language. CLI: `git-auth ...` for the Git side (see above); there's no CLI command to *set* the language yet, but the CLI's own output (usage, confirmations, errors) already follows whatever language was last saved from the desktop app's Settings screen. |

A file from before this layout existed (`~/.rikiki-vault/git-auth/settings.json`) is read once,
automatically, the first time `preferences.json` doesn't exist yet - so an SSH key or token
configured before this change keeps working without reconfiguring anything. It's never rewritten
or deleted by the app; it just stops being consulted as soon as you save anything through
Settings/`git-auth`.

### Hardening notes

Decrypted files written to `local/` are created with owner-only permissions (`rw-------` on
POSIX systems, matching how the machine's private key is already stored) and written atomically —
a crash or interruption mid-write can never leave a truncated file in place. When `pull` reports a
conflict, both the local and remote SHA-256 hashes are shown so you can tell the two versions apart
before manually reconciling them; there's still no "keep remote"/"compare" action built in — the
local version is always what's kept automatically, and you resolve the rest by hand.

### Logging

The CLI and the desktop app both ship a plain [slf4j-simple](https://www.slf4j.org/) binding, so
whatever Git/SSH library logging already exists (JGit, mina-sshd) prints to the terminal that
launched them — no separate config file to set up for distribution. By default it's quiet (only
warnings/errors); for troubleshooting a real push/pull/clone against a remote, run with more
detail:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar cli/target/rikiki-vault.jar -C <vault> pull
```

or just the Git/SSH packages, to keep the rest quiet:

```bash
java -Dorg.slf4j.simpleLogger.log.org.apache.sshd=debug -Dorg.slf4j.simpleLogger.log.org.eclipse.jgit=debug -jar cli/target/rikiki-vault.jar -C <vault> pull
```

The same flags work with `mvn -pl gui-javafx javafx:run -D...` for the desktop app — the logs show
up in whatever terminal ran that command, alongside the running window.

## Desktop app (JavaFX)

```
mvn -pl gui-javafx javafx:run
```

On launch, pick a folder — either an existing vault or an empty one to initialize/clone into. The
main window shows the vault's file tree with a status badge per file (synced/added/modified/
deleted), a preview pane for the selected file (text, JSON, XML, Markdown, images, and the first
page of PDFs) that can also be switched into an editor (Save/Encrypt/Revert/Diff), a "Remoto: ..."
sync-status badge, and toolbar actions for Pull, Publish (with a review-and-approve step before
anything is encrypted, and a separate prompt before pushing to the remote), managing machine
access (authorize/revoke), and Configurações (Git authentication + language).
