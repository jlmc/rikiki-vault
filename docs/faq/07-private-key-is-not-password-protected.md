# Is my private key on disk protected by a password?

*[Ler em português](07-private-key-is-not-password-protected.pt.md)*

No. `~/.rikiki-vault/identity/private.key` is a plain PKCS8 DER file, protected only by
filesystem permissions — never by a passphrase, and never stored in your OS's keychain/credential
manager.

## What's actually there

`LocalKeyStoreAdapter` writes the private key to disk exactly as generated, with owner-only
permissions (`rw-------` on macOS/Linux — see the ["Hardening notes"](../../README.md) section of
the main README). That's real protection against *other user accounts* on a shared, running
machine, but it's not encryption of the key itself: no master password, no macOS Keychain entry,
no Windows Credential Manager entry, nothing prompting you to unlock it. Anyone who can read files
as you — root, a compromised session while you're logged in, or someone who removes the drive and
mounts it on another machine without disk encryption — can read the key directly, no password
required.

## Why it's built this way

The app needs to use this key non-interactively for every `pull`/`publish`/`clone`, including
background operations in the desktop app — a passphrase-protected key would mean typing a
password (or unlocking some OS-level keychain prompt) on every single sync. That trade-off pushes
the actual protection down a level: onto whatever's protecting your account and disk in the first
place.

## Where the real protection has to come from

**Full-disk encryption** — FileVault (macOS), BitLocker (Windows), LUKS (Linux). With it enabled,
the key file is only readable once the disk has been unlocked with your login password; without
it, anyone with physical access to the drive reads the key as plainly as any other file. This
isn't optional if you actually care about the guarantees this app is trying to give you — a vault
encrypted end-to-end is only as strong as the weakest link, and an unencrypted disk holding the
private key in the clear is that weak link.

This is also exactly why [the first FAQ entry](01-disk-failure-and-backups.md) treats
`private.key` as the thing that needs a backup plan: it's a small, sensitive, unencrypted file —
worth protecting deliberately in both directions, against loss and against exposure.
