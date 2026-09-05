# FAQ

*[Ler em português](README.pt.md)*

Practical answers to questions that come up when actually using a vault day to day — as opposed to
[`README.md`](../../README.md) (how to build/run/use the app) or
[`docs/release-process.md`](../release-process.md) (how a release is cut).

1. [What if my SSD dies — how do I make sure I never lose access to my data?](01-disk-failure-and-backups.md)
   — why the private key, not the encrypted files, is the real thing you need to back up, and the
   two ways to actually protect it.
2. [I lost my SSD, the remote is up to date, and I backed up both `private.key` and `public.key` — how do I recover?](02-recovering-with-both-keys-backed-up.md)
   — restore both files before cloning and your existing, already-authorized identity is reused,
   no re-authorization needed.
3. [I lost my SSD, the remote is up to date, but I only backed up `private.key`, not `public.key` — how do I recover?](03-recovering-with-only-the-private-key.md)
   — two paths: get re-authorized by another machine (simplest), or derive the public key from the
   private key yourself (works alone, but depends on external tooling).
4. [I don't want to use the app anymore. I have the data and both keys — how do I decrypt everything without it?](04-decrypting-without-the-app.md)
   — the encrypted file format explained, plus a ready-to-run OpenSSL + shell script that decrypts
   your whole vault with no Java, Maven, or app source code involved.
5. [Does GitHub (or whoever hosts the remote) see my filenames or folder structure?](05-filenames-and-metadata-are-not-encrypted.md)
   — yes: only file content is encrypted, paths/filenames/commit messages/sizes are all visible.
6. [I deleted a file from the vault — is it really gone?](06-deleting-a-file-is-not-permanent.md)
   — not from Git history; what it takes to actually purge it, and what purging doesn't undo.
7. [Is my private key on disk protected by a password?](07-private-key-is-not-password-protected.md)
   — no, only by filesystem permissions; why, and where the real protection has to come from.
8. [A machine was stolen/compromised — how do I make sure it can no longer read new files?](08-revoking-a-stolen-machine.md)
   — `revoke` re-encrypts everything with fresh keys, but can't reach data the machine already
   downloaded before you revoked it.
9. [What happens if two machines edit the same file before syncing?](09-conflicting-edits.md)
   — `pull` reports the conflict with a hash of each side and leaves both untouched; resolving it
   is a manual step.
10. [I accidentally deleted my `local/` folder on this machine — how do I get the files back?](10-restoring-a-deleted-local-folder.md)
    — `restore` rebuilds `local/` from `documents/`, offline, without needing your backup key.
