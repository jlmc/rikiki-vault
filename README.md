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
| `init [--git] <machine-label>` | Initializes a brand-new vault in the current directory. `--git` also runs `git init` locally. Seeds the recipient registry with this machine as the sole recipient. |
| `whoami` | Prints this machine's identity fingerprint, generating one first if it doesn't exist yet. |
| `export-key <output-file>` | Writes this machine's public key to a file, to hand to whoever manages `authorize` on another machine. |
| `clone <remote-uri>` | Joins an *already-initialized* vault (use `init` to start a brand-new one instead). |
| `status` | Lists pending local changes (added/modified/deleted) against the last publish. |
| `publish -m "<message>"` | Encrypts every pending change for all currently authorized recipients, commits, and pushes. |
| `pull` | Pulls the latest encrypted state and decrypts what changed remotely into `local/`. Never overwrites a file you've also changed locally — that's reported as a conflict instead. |
| `authorize <label> <public-key-file>` | Grants another machine access: adds it to the recipient registry and re-encrypts every already-published file for the updated set. |
| `revoke <fingerprint-hex>` | Removes a machine's access, re-encrypting everything so its key is no longer able to decrypt anything new. |

### A full walkthrough

```
mkdir my-vault && cd my-vault
java -jar rikiki-vault.jar init --git machine-a

# GitRepositoryPort has no "add remote" operation yet - wire the remote yourself:
git remote add origin git@github.com:you/my-vault-encrypted.git

mkdir local
echo "hello" > local/notes.txt

java -jar rikiki-vault.jar status
java -jar rikiki-vault.jar publish -m "first publish"
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
