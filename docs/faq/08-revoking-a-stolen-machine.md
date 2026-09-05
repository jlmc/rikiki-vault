# A machine was stolen/compromised — how do I make sure it can no longer read new files?

*[Ler em português](08-revoking-a-stolen-machine.pt.md)*

Run `revoke <fingerprint>` (CLI) or use "Gerir Acesso" in the desktop app — from a **different**,
still-trusted machine, since the stolen one obviously can't revoke itself. It fully protects
everything published from that point on, and even most of what came before — but there's one
sharp edge worth understanding before you rely on it.

## What revoke actually does

`RevokeMachineService` removes the machine from the recipient registry, then re-encrypts *every*
already-tracked file for the reduced recipient set. This isn't a re-wrap of the same content key —
each file gets an entirely new random AES key and a fresh ciphertext (confirmed in
`JceHybridEncryptionAdapter.encrypt`: a new key is generated on every call, never reused). So after
a revoke completes and is published, even a file that existed since day one now exists in the repo
as a brand-new `.enc` blob, with no wrapped-key entry for the revoked machine's fingerprint at
all. That's real, effective protection — a machine that only has the *new* ciphertext genuinely
cannot decrypt it, no matter what.

## The edge that isn't obvious

If the stolen machine had already run `pull`/`clone` and downloaded the *old* `.enc` files before
you revoked it, its private key still opens exactly those old copies — revoking removes its access
to what gets published *from then on*, it doesn't retroactively invalidate a decryption key against
data that machine already has sitting on its disk. There's no cryptographic way to "unshare" a file
with a key that has already had the chance to open it. This isn't a gap in this app specifically —
it's true of any encryption-based access control: revocation is forward-looking, not
backward-erasing.

## What this means practically

- Revoke as soon as you know a machine is compromised — the sooner, the smaller the window where
  it can pull anything new.
- Assume the stolen machine already has a decrypted (or decryptable) copy of whatever it last
  synced before the theft — treat that content as exposed, the same as you would for any other
  lost device with local files on it.
- Revoking is still worth doing immediately even so: it's what stops the leak from growing to
  include everything published afterward.
