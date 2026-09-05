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
