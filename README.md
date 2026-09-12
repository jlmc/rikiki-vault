# Rikiki Vault

*[Ler em português](README.pt.md)*

A local-first, end-to-end encrypted file vault synced through a private Git repository. You
keep plaintext files in a `local/` working copy; `publish` encrypts them (X25519 + AES-GCM) into
`documents/` and pushes the result. Any authorized machine can `clone`/`pull` the repository and
decrypt back to its own `local/` copy. Nothing ever reaches Git in plaintext.

## Contents

- [Requirements](#requirements)
- [Building](#building)
- [Machine identity](#machine-identity)
- [CLI](#cli)
  - [Commands](#commands)
  - [A full walkthrough](#a-full-walkthrough)
  - [Pushing to a real remote](#pushing-to-a-real-remote)
  - [Explicit Git authentication](#explicit-git-authentication)
  - [Protecting the private key with a passphrase](#protecting-the-private-key-with-a-passphrase)
  - [Configuration files](#configuration-files)
  - [Hardening notes](#hardening-notes)
  - [Logging](#logging)
- [Desktop app (JavaFX)](#desktop-app-javafx)
- [Distribution](#distribution)
  - [Native apps (no Java required on the target machine)](#native-apps-no-java-required-on-the-target-machine)
- [License](#license)
- [FAQ](#faq)

## Requirements

- Java 25
- Maven

## Building

From the repository root:

```
mvn install
```

This builds all three modules:

- **`core`** (Maven artifactId `rikiki-vault-core`) — domain model, ports & adapters, all the
  application use cases. No UI, no `main`.
- **`cli`** (Maven artifactId `rikiki-vault-cli`) — a console application
  (`cli/target/rikiki-vault.jar`) exposing every use case as a command.
- **`gui-javafx`** (Maven artifactId `rikiki-vault-gui-javafx`) — a desktop JavaFX application
  wrapping the same use cases.

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

Or skip the manual build: `scripts/run-cli.sh -- -C /path/to/vault status` builds the jar the
first time it's missing and runs it the same way (see "Logging" below for its `--log-level` flag).

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
| `set-passphrase` | Protects (or changes) this machine's identity with a passphrase - prompts for the current one first if it's already protected, then the new one (twice, to confirm). See "Protecting the private key with a passphrase" below. |
| `remove-passphrase` | Removes passphrase protection, given the current passphrase. |
| `unwrap-key <input-file> <output-file>` | Disaster recovery: decrypts a passphrase-protected `private.key` (any file path, not just the live identity) to plain PKCS8 - see "Protecting the private key with a passphrase" below. |
| `migrate-format [--dry-run] [--yes]` | One-way: converts a vault from the older format (plaintext `manifest.json`, real filenames under `documents/`) to the current one, where paths and filenames are encrypted too - see [FAQ 11](docs/faq/11-migrating-to-encrypted-paths.md). |

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

### Protecting the private key with a passphrase

By default `private.key` is unprotected (see the ["private key password" FAQ entry](docs/faq/07-private-key-is-not-password-protected.md)
for the full trade-off). To turn on protection:

```
rikiki-vault set-passphrase       # prompts for a new passphrase (twice, to confirm)
rikiki-vault remove-passphrase    # prompts for the current passphrase, then removes protection
```

Once protected, any command that needs the identity (`whoami`, `export-key`, `init`, `clone`,
`pull`, `restore`) prompts for the passphrase once per invocation — masked, via the terminal's own
password entry when a real console is attached, with a visibly-echoed stdin fallback (announced)
when there isn't one (piped input, IDE run configs, CI). Right after generating a brand-new
identity (`init`/`clone` only), you're also offered the chance to protect it on the spot, when
running interactively.

Desktop app: Configurações → **Segurança** offers the same set/change/remove, applied immediately.
Opening a vault whose identity is protected shows a full-screen unlock prompt before anything else
loads; once unlocked, the identity stays usable for the rest of that session (background pull/push,
auto-refresh) without asking again - closing the app clears it.

There's no recovery path if the passphrase is forgotten - changing or removing one always requires
the current passphrase first, by design.

**Disaster recovery with a protected key:** `unwrap-key <input-file> <output-file>` decrypts a
passphrase-protected `private.key` to plain PKCS8 - it works on any file path (not just the live
`~/.rikiki-vault/identity/`), so it also handles a lone, protected *backup* of a key whose
`public.key` is gone. See [FAQ 03](docs/faq/03-recovering-with-only-the-private-key.md) and
[FAQ 04](docs/faq/04-decrypting-without-the-app.md) for full step-by-step recovery walkthroughs,
including a pure OpenSSL/bash alternative (`docs/faq/scripts/unwrap-private-key.sh`) for when
building this app's own CLI isn't an option.

### Configuration files

Everything outside a vault itself lives under `~/.rikiki-vault/` — machine-global, shared by every
vault you open and by both the CLI and the GUI:

| File | What it's for | How to configure it |
|---|---|---|
| `~/.rikiki-vault/identity/private.key` / `public.key` | This machine's X25519 keypair (owner-only permissions) - see "Machine identity" above. `private.key` is optionally passphrase-protected - see "Protecting the private key with a passphrase" above. | Generated automatically the first time it's needed; not user-editable directly. `whoami`/`export-key` read it; `set-passphrase`/`remove-passphrase` (or Configurações → Segurança) change its protection. |
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

The CLI and the desktop app both ship a plain [slf4j-simple](https://www.slf4j.org/) binding, and
the app's own code (`core`/`gui-javafx`) logs its operations through it too — vault opens,
clone/pull/push, publish/authorize/revoke, and every error dialog shown in the GUI. Two layers,
each with its own default: third-party Git/SSH libraries (JGit, mina-sshd) stay quiet (only
warnings/errors), while the app's own `io.github.jlmc` packages default to `info`. No separate
config file to set up for distribution — it's all baked into the `simplelogger.properties`
shipped in each module's jar.

The simplest way to change the app's own log level is `scripts/run.sh`, which wraps both the CLI
and the GUI:

```bash
scripts/run.sh cli -- -C <vault> pull                    # info by default
scripts/run.sh cli --log-level=debug -- -C <vault> pull
scripts/run.sh gui --log-level=debug
```

`scripts/run-cli.sh`/`scripts/run-gui.sh` are thin shortcuts for `scripts/run.sh cli`/`scripts/run.sh gui` — same flags, one less word to type.

For raw control (e.g. to also turn up the Git/SSH libraries), pass the underlying `-D` flags
directly:

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

Or `scripts/run-gui.sh` — builds the packaged jar the first time it's missing, then launches it
with plain `java -jar` (no Maven at runtime); see "Logging" above for its `--log-level` flag.

On launch, pick a folder — either an existing vault or an empty one to initialize/clone into. The
main window shows the vault's file tree with a status badge per file (synced/added/modified/
deleted), a preview pane for the selected file (text, JSON, XML, Markdown, images, and the first
page of PDFs) that can also be switched into an editor (Save/Encrypt/Revert/Diff), a "Remoto: ..."
sync-status badge, and toolbar actions for Pull, Publish (with a review-and-approve step before
anything is encrypted, and a separate prompt before pushing to the remote), managing machine
access (authorize/revoke), and Configurações (Git authentication + language).

## Distribution

Neither module ships a fat/shaded jar. `mvn package` produces a thin jar per module (only the
project's own classes) plus a `target/lib/` folder with every dependency as a separate jar; the
jar's manifest already points at `lib/`, so it runs exactly like before:

```bash
mvn package
java -jar cli/target/rikiki-vault.jar -C <vault> status
java -jar gui-javafx/target/rikiki-vault-gui.jar
```

Just keep `target/lib/` next to the jar - copy the whole `target/` folder (or `lib/` + the jar)
if you move it elsewhere. Java 25 still needs to be installed on that machine.

### Native apps (no Java required on the target machine)

`scripts/package.sh` (macOS/Linux) and `scripts/package.ps1` (Windows) wrap `mvn package` and
then run `jpackage` (bundled with the JDK) to produce a self-contained native app - the JVM ships
inside it, so the machine that runs it doesn't need Java installed at all. `jpackage` never
cross-compiles: each script only produces artifacts for the OS it runs on, so run the matching
script manually on each target OS - there's no CI in this repository to do that centrally.

```bash
scripts/package.sh              # macOS/Linux, app-image (fast, for local testing)
scripts/package.sh installer    # macOS/Linux, the real installer (.dmg / .deb)
```

```powershell
scripts\package.ps1              # Windows, app-image
scripts\package.ps1 -Mode installer  # Windows, .msi
```

Output goes to `dist/` (already git-ignored). Prerequisites per OS:

- **macOS**: Xcode Command Line Tools (`xcode-select --install`) - already required by `jpackage`
  itself.
- **Windows**: the [WiX Toolset](https://wixtoolset.org/) installed, for `-Mode installer`
  (`--type msi`).
- **Linux**: `dpkg-dev` and `fakeroot` installed, for `installer` mode (`--type deb`).

The CLI's packaged app is still a console tool - run it from a terminal (e.g.
`dist/rikiki-vault-cli.app/Contents/MacOS/rikiki-vault-cli` on macOS), it just no longer needs a
separate Java installation. The app icon lives at `branding/icon.svg` (source) with the
platform-specific `branding/icon.icns`/`.ico`/`.png` derived from it.

## License

[PolyForm Strict License 1.0.0](https://polyformproject.org/licenses/strict/1.0.0) (full text in
[`LICENSE`](LICENSE)) — a source-available license, not an OSI-approved open source one. You can
download, read, and run this code for any noncommercial purpose (personal use, research, hobby
projects, nonprofits/education/government), but you may not modify it, distribute a changed
version, or use it commercially. None of this restricts the copyright holder's own use of the
code — only third parties who obtain it under this license.

## FAQ

Practical questions about actually using a vault, answered in [`docs/faq/`](docs/faq/README.md):

1. [What if my SSD dies — how do I make sure I never lose access to my data?](docs/faq/01-disk-failure-and-backups.md)
2. [I lost my SSD, the remote is up to date, and I backed up both `private.key` and `public.key` — how do I recover?](docs/faq/02-recovering-with-both-keys-backed-up.md)
3. [I lost my SSD, the remote is up to date, but I only backed up `private.key`, not `public.key` — how do I recover?](docs/faq/03-recovering-with-only-the-private-key.md)
4. [I don't want to use the app anymore. I have the data and both keys — how do I decrypt everything without it?](docs/faq/04-decrypting-without-the-app.md)
5. [Does GitHub (or whoever hosts the remote) see my filenames or folder structure?](docs/faq/05-filenames-and-metadata-are-not-encrypted.md)
6. [I deleted a file from the vault — is it really gone?](docs/faq/06-deleting-a-file-is-not-permanent.md)
7. [Is my private key on disk protected by a password?](docs/faq/07-private-key-is-not-password-protected.md)
8. [A machine was stolen/compromised — how do I make sure it can no longer read new files?](docs/faq/08-revoking-a-stolen-machine.md)
9. [What happens if two machines edit the same file before syncing?](docs/faq/09-conflicting-edits.md)
10. [I accidentally deleted my `local/` folder on this machine — how do I get the files back?](docs/faq/10-restoring-a-deleted-local-folder.md)
