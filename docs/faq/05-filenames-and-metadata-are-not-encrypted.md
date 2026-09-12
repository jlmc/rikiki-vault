# Does GitHub (or whoever hosts the remote) see my filenames or folder structure?

*[Ler em português](05-filenames-and-metadata-are-not-encrypted.pt.md)*

No, since the RV02 format (see [FAQ 11](11-migrating-to-encrypted-paths.md) if you have an older
vault still on the previous format) — real paths and filenames are encrypted right alongside file
content, not just visible metadata sitting next to it.

## The manifest is encrypted as a whole

`manifest.json` — the file that maps every tracked entry to its real path — is itself sealed with
the exact same hybrid encryption (X25519 + AES-GCM, wrapped per authorized machine) already used
for file content. On disk and in the git history, it's an opaque binary blob; there is no
plaintext path anywhere in it unless you're an authorized machine that can actually decrypt it.
That's also why revoking a machine's access re-encrypts the manifest, not just file content — see
[FAQ 08](08-revoking-a-stolen-machine.md).

## `documents/` filenames are random, not real paths

Every published file lives at `documents/<opaque-id>.enc` — a random identifier generated once per
file, unrelated to its real name or location, and never nested in folders that mirror your real
directory structure (a folder name can be just as revealing as a filename). If you keep
`taxes/2025/return.pdf` in `local/`, anyone with read access to the Git remote sees a file with a
name like `documents/f47ac10b-58cc-4372-a567-0e02b2c3d479.enc` — nothing about "taxes" or
"return.pdf" survives into anything visible without decryption.

The `.enc` format itself (`RV02`) also no longer carries any filename field in its header at all —
the previous format did, in the clear, which was its own separate leak; the manifest (once
decrypted) is the only source of truth for what a given id actually is.

## What else is visible

This part is unaffected by any of the above — it was never about filenames:

- **Commit messages and dates** — whatever you pass to `publish -m "..."` (or type in the desktop
  app's review screen) is a plain Git commit message, not encrypted.
- **Approximate file size** — AES-GCM ciphertext is only a little bigger than the plaintext (a
  fixed overhead per file, no padding), so file sizes are visible with reasonable precision.
- **When you published, and how often** — commit timestamps and frequency are ordinary Git
  history.

## What's actually protected

Content, real paths, and real filenames — all encrypted the same way, all invisible to anyone
without an authorized machine's private key. Practical takeaway: don't put anything sensitive in a
commit message, and if the mere fact that certain files exist (independent of their name) is
itself sensitive, this tool's model doesn't hide the count or approximate size of files from your
Git host or from anyone else who can read the repository.
