# Is my private key on disk protected by a password?

*[Ler em português](07-private-key-is-not-password-protected.pt.md)*

Optionally, yes. `~/.rikiki-vault/identity/private.key` can be protected with a passphrase — but
it's opt-in and off by default: a freshly generated identity is still a plain PKCS8 DER file,
protected only by filesystem permissions, exactly like before. It's never stored in your OS's
keychain/credential manager either way.

## What's actually there

`LocalKeyStoreAdapter` writes the private key to disk with owner-only permissions (`rw-------` on
macOS/Linux — see the ["Hardening notes"](../../README.md) section of the main README) either way.
Without a passphrase, that's the *only* protection: no master password, nothing prompting you to
unlock it. Anyone who can read files as you — root, a compromised session while you're logged in,
or someone who removes the drive and mounts it on another machine without disk encryption — can
read the key directly.

With a passphrase set (`rikiki-vault set-passphrase`, or Settings → Security in the desktop app),
the file becomes a self-describing encrypted envelope instead: a random salt, PBKDF2-HMAC-SHA256
(600,000 iterations) stretches the passphrase into a key-encryption key, and the actual private key
bytes are sealed with AES-GCM under that key. `LocalKeyStoreAdapter` detects which format is on
disk from a magic-byte prefix, so an existing unprotected identity keeps loading exactly as before
until you explicitly protect it — nothing breaks, nothing migrates automatically.

## Why it's opt-in, and how prompting actually works

The app still needs to use this key non-interactively for background operations — the desktop app's
auto-refresh and background push, in particular. A passphrase-protected identity doesn't fight that:
you're prompted once, when a vault with a protected identity is opened (or once per CLI invocation
that needs it), and the unlocked identity is then cached in memory for the rest of that session -
not written back to disk, not stored anywhere persistent. Background actions during that same
session reuse the cached identity instead of prompting again. Closing the app (or ending the CLI
process) clears it - the next open asks again.

This does mean an already-unlocked session, left open and walked away from, still exposes whatever
is on screen to anyone who can use your unlocked computer - a passphrase on the key file protects
the file at rest, not a session that's already running. That's a separate problem (tracked as a
possible future auto-lock feature), not something a passphrase on `private.key` was ever meant to
solve.

## No recovery by design

Changing or removing a passphrase always requires the current one first - there's no backdoor and
no reset. Forgetting it means the identity (and everything it was ever used to decrypt) is only
recoverable from a backup of the unprotected `private.key`, if one exists. Treat setting a
passphrase the same way you'd treat any other credential: worth writing down somewhere safe, not
worth guessing you'll remember it.

## Where the real protection still has to come from

**Full-disk encryption** — FileVault (macOS), BitLocker (Windows), LUKS (Linux) — remains just as
necessary as before, passphrase or not. A protected `private.key` resists someone who only gets the
file itself; it does nothing for someone who has your unlocked, logged-in machine. Disk encryption
is what makes "an attacker got the disk" and "an attacker got a logged-in session" two different,
separately-defended scenarios instead of one.

This is also exactly why [the first FAQ entry](01-disk-failure-and-backups.md) treats
`private.key` as the thing that needs a backup plan: it's a small, sensitive file — worth
protecting deliberately in both directions, against loss and against exposure, whether or not
you've set a passphrase on it.
