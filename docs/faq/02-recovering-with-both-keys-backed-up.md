# I lost my SSD, the remote is up to date, and I backed up both `private.key` and `public.key` — how do I recover?

*[Ler em português](02-recovering-with-both-keys-backed-up.pt.md)*

This is the easy case, and it's exactly what following the [previous
answer](01-disk-failure-and-backups.md) buys you: restore the identity files onto the new machine
**before** cloning, and the app reuses your existing, already-authorized identity instead of
generating a new one — nobody needs to re-authorize you.

## Why this works

A machine's identity lives at `~/.rikiki-vault/identity/private.key` +
`~/.rikiki-vault/identity/public.key` — global to the machine, not to any one vault. When you
`clone` a vault, the app first checks whether an identity already exists at that path: if it does,
it's reused as-is; only if neither file is there does it generate a brand-new keypair (see the
[first answer](01-disk-failure-and-backups.md) for why that matters). Since your fingerprint
(computed from the public key) is what the vault's recipient list already recognizes, restoring
the exact same key pair means you're already an authorized recipient the moment you clone.

## Steps

These assume a genuinely bare replacement machine (macOS with [Homebrew](https://brew.sh/)
installed) — skip whatever you already have.

### 1. Install the prerequisites

```bash
brew install openjdk@25 maven git
```

`openjdk@25` is keg-only (Homebrew doesn't put it on `PATH` or register it with
`/usr/libexec/java_home` automatically), so link it:

```bash
sudo ln -sfn "$(brew --prefix openjdk@25)/libexec/openjdk.jdk" \
  /Library/Java/JavaVirtualMachines/openjdk-25.jdk
echo 'export PATH="'"$(brew --prefix openjdk@25)"'/bin:$PATH"' >> ~/.zshrc
export PATH="$(brew --prefix openjdk@25)/bin:$PATH"
java -version   # confirm it reports 25
```

(On Linux, use your distribution's package manager for a JDK 25 + Maven + Git instead of `brew`.)

### 2. Get the Rikiki Vault application itself

This is the app's own source code — a different repository from your personal vault (the one with
your encrypted files). Clone and build it:

```bash
git clone <this-app's-source-repository-url>
cd rikiki-vault
mvn -pl cli -am package -DskipTests
```

This produces `cli/target/rikiki-vault.jar` (+ its `lib/` folder). For the desktop app instead —
or for a native installer that doesn't need Java installed at all — see the main
[README](../../README.md) ("Building"/"Distribution").

### 3. Restore the identity files

```bash
mkdir -p ~/.rikiki-vault/identity
cp /path/to/backup/private.key /path/to/backup/public.key ~/.rikiki-vault/identity/
chmod 700 ~/.rikiki-vault/identity
chmod 600 ~/.rikiki-vault/identity/private.key ~/.rikiki-vault/identity/public.key
```

Names matter exactly (`private.key`, `public.key`), and this is a machine-wide location — not
inside any vault folder. The app sets these same permissions itself when it generates a fresh
identity; a manually restored file needs the same treatment.

### 4. Verify before cloning anything

```bash
java -jar cli/target/rikiki-vault.jar whoami
```

It should print your original fingerprint immediately, with no "generating a new identity"
message. If you kept a note of your fingerprint (or still have another machine that can show it),
compare them now — catching a mismatch here is much easier than after cloning.

### 5. Clone your personal vault

```bash
java -jar cli/target/rikiki-vault.jar clone <your-vault-remote-uri>
```

(or, in the desktop app: pick an empty folder → "Entrar num vault já existente" / "Join an
existing vault" → paste the remote URL). This is the *other* remote — your actual data, not the
app's own source cloned in step 2. Because the restored identity was already an authorized
recipient before the disk died, every tracked file decrypts straight into `local/` — **no
`authorize` step needed from anyone**.

### 6. Continue normally

`pull`/`publish` (or the desktop app's toolbar) work exactly as they did on the old machine.

If instead `whoami` shows a *different* fingerprint than expected, something about the restore is
wrong (files swapped, a stale backup, wrong directory) — stop before cloning and re-check the
backup rather than proceeding and hitting `UnauthorizedMachineException` on every file.
