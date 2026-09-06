# What if my SSD dies — how do I make sure I never lose access to my data?

*[Ler em português](01-disk-failure-and-backups.pt.md)*

Short answer: publish regularly to a remote, and make sure your **machine identity** (the private
key) is never a single point of failure — either by authorizing a second machine, or by backing up
the key file itself. The two halves of your vault survive a lost disk very differently.

## What actually survives a lost disk

A vault has two kinds of local data, and they behave very differently:

- **`local/`** — your plaintext working copy. It never leaves the machine, and it's never part of
  what `publish` sends anywhere. If your disk dies, whatever is only in `local/` and hasn't been
  published yet is gone, exactly like any other unsaved local work.
- **`documents/`** — the encrypted files, tracked by the vault's Git repository. Every `publish`
  encrypts, commits, and (if a remote is configured) pushes this. As long as a change has actually
  reached the remote, it survives a lost disk — GitHub (or wherever the remote lives) keeps its own
  copy.

So step one of "always being safe" is simply: **publish, and confirm it reached the remote**, not
just that it saved locally. The CLI's `publish` prints `(guardado localmente - sem remoto
configurado)` when there's no remote, and the desktop app's "Remoto: ..." badge in the toolbar
tells you the same thing at a glance — if either says local-only, that change is exactly as exposed
to a disk failure as anything in `local/`.

## The real single point of failure: your private key

This is the part that's easy to miss. Every machine has its own X25519 keypair, stored at
`~/.rikiki-vault/identity/` — this identity is **global to the machine**, shared by every vault you
open on it, and it's **never written to the Git repository**. That's precisely what keeps the
encryption end-to-end: nobody who only has access to the repository (including GitHub itself) can
decrypt anything without one of the private keys that were used to encrypt it.

The consequence: if that machine's disk dies and you have no backup of
`~/.rikiki-vault/identity/private.key`, and no *other* machine already holds an authorized copy of
a private key, the encrypted files sitting safely in your Git remote become **permanently
undecryptable**. There is no recovery backdoor — that's the whole point of end-to-end encryption,
but it means the private key is the thing that actually needs a disaster-recovery plan, not the
encrypted data itself.

A common mistake: assuming you can just `clone` the vault again on a replacement machine and be
back in business. A machine with no existing identity generates a **brand-new** keypair on `clone`
— and that new key was never granted access, so decrypting anything fails with "this machine isn't
authorized" until someone who still has access runs `authorize` for it.

## Two ways to actually be safe

**1. Authorize a second machine (recommended).** This is built into the app and needs no extra
tooling. Once a second machine holds its own authorized identity, losing either machine on its own
doesn't cost you access — the surviving machine can still decrypt everything, and can `authorize`
a replacement for the one that was lost. See the "full walkthrough" in the main
[README](../../README.md) for the exact `export-key`/`authorize` steps (or "Gerir Acesso" in the
desktop app).

**2. Back up the private key file itself.** Useful if you only ever use one machine. Copy
`~/.rikiki-vault/identity/private.key` (and `public.key`, though that one isn't sensitive on its
own) to a separate, durable, secure location — a password manager, an encrypted external drive kept
somewhere else, that kind of thing. Never put this backup inside the vault's own Git repository,
and never in plain, unencrypted cloud storage — it's the literal key that decrypts everything.

These aren't mutually exclusive — doing both is the most robust setup.

**A third layer, on top of either one: protect `private.key` with a passphrase** (`rikiki-vault
set-passphrase`, or Settings → Security in the desktop app — see the [password protection FAQ
entry](07-private-key-is-not-password-protected.md) for the full picture). This matters especially
for the backup itself: storing a *protected* copy somewhere less than fully trusted (a cloud drive,
a second device) is meaningfully safer than storing the raw key, since reading the file alone isn't
enough without the passphrase too. It doesn't replace backing up the file — a protected key you
never backed up is still gone forever with the disk — and if you ever forget the passphrase,
recovering the identity from that backup needs the passphrase-aware steps in [FAQ
03](03-recovering-with-only-the-private-key.md), not the plain-DER ones from before this feature
existed.

## A short checklist

- Configure a remote from the start (`init --git --remote <url>`, or add one later with
  `git remote add origin <url>`) so `publish` actually leaves the machine.
- Publish after making changes, and check the sync indicator (CLI `status`/the "Remoto: ..." badge)
  instead of assuming it reached the remote.
- Authorize at least one additional machine early — not as an afterthought once something has
  already gone wrong.
- If you rely on a single machine, keep a secure backup of `~/.rikiki-vault/identity/private.key`.
- Every so often, actually test recovery: on a spare or new machine, `clone` the vault (or restore
  the backed-up key) and confirm you can read the files. An untested backup isn't a real safety
  net.

What's *not* at risk on its own: `vault/manifest.json` and `vault/recipients.json` are tracked in
Git too (file hashes/paths and authorized public keys — no plaintext content), so they come back
automatically with any `clone`, on any machine.
