# Does GitHub (or whoever hosts the remote) see my filenames or folder structure?

*[Ler em português](05-filenames-and-metadata-are-not-encrypted.pt.md)*

Yes, on two levels. Only the *content* of your files is encrypted — everything about where they
are and what they're called stays visible to anyone who can read the repository.

## The path itself

Every published file lives at `documents/<plaintext-path>.enc` — that path mirrors `local/`
exactly, folder for folder, filename for filename, just with `.enc` appended (see
[the fourth FAQ entry](04-decrypting-without-the-app.md) for the full format). If you keep
`taxes/2025/return.pdf` in `local/`, anyone with read access to the Git remote sees a file at
`documents/taxes/2025/return.pdf.enc` — the folder name "taxes" and the filename "return.pdf" are
right there, even though they can't open what's inside it.

## The filename is also stored again, inside the file

Less obvious: the RV01 format writes the original filename into the `.enc` file's own header,
*before* any encryption happens (`RvEncryptedFileFormatCodec` writes it as a plain
length-prefixed UTF-8 string). So even if you renamed the file on disk to something generic before
it left `local/`, the ciphertext still carries whatever name it had at encryption time, in the
clear. This is redundant with the path in practice, but it means the filename genuinely isn't
protected by the encryption at all — it was never inside the sealed part.

## What else is visible

- **Commit messages and dates** — whatever you pass to `publish -m "..."` (or type in the desktop
  app's review screen) is a plain Git commit message, not encrypted.
- **Approximate file size** — AES-GCM ciphertext is only a little bigger than the plaintext (a
  fixed overhead per file, no padding), so file sizes are visible with reasonable precision.
- **When you published, and how often** — commit timestamps and frequency are ordinary Git
  history.

## What's actually protected

Just the content. That's the whole design: a private Git repository (this app never makes claims
about which repository host you use, or how private it is — that's on you) with the *content*
end-to-end encrypted, not a system that hides the existence or shape of your files. Practical
takeaway: don't put anything sensitive in a filename or a commit message, and if the mere fact
that certain files exist is itself sensitive, this tool's model doesn't hide that from your Git
host or from anyone else who can read the repository.
