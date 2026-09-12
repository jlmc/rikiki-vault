# I have an older vault (from before file paths were encrypted) — how do I migrate it?

*[Ler em português](11-migrating-to-encrypted-paths.pt.md)*

Versions of this app before the RV02 format shipped stored `manifest.json` as plain JSON — with
every file's real path in the clear — and named each encrypted file in `documents/` after its real
path (`documents/<real-path>.enc`), directory structure and all. Anyone with read access to the
remote could see every filename and folder name, even though they still couldn't decrypt any
content. See [FAQ 05](05-filenames-and-metadata-are-not-encrypted.md) for the full story of what
that used to expose.

Starting with the RV02 format, `manifest.json` is itself encrypted (the same way any tracked file
already was), and every file under `documents/` gets a random, meaningless id instead of its real
name. `migrate-format` is the one-time, one-way command that converts an existing vault from the
old shape to the new one.

## Before you run it

- **Every machine that uses this vault must be updated to an app version that supports RV02
  first.** An older app version cannot open an RV02 vault at all — it doesn't know how to decrypt
  the manifest, and will fail with a clear "this vault isn't in the current format" error if you
  try the other way around (an up-to-date app opening a not-yet-migrated vault gives the same kind
  of clear error, telling you to migrate).
- **Make sure every machine is fully pulled up to date and has nothing unpublished**, and pick one
  machine to actually run the migration from. This is not a distributed operation — it rewrites the
  vault once, from whichever machine runs it, and pushes the result. Any other machine that publishes
  from a stale, pre-migration checkout after that will conflict badly.
- **Back up the vault folder first** (a plain `cp -r`, or just note the current commit hash so
  `git reset`/`git checkout` is available). The migration is one-way going forward, but nothing is
  destroyed from Git's own history — the pre-migration commit is still there — so this is a cheap
  extra safety net, not a strict requirement.

## Running it

From the machine you picked, with the CLI:

```bash
# See what would happen, without changing anything:
rikiki-vault -C /path/to/your/vault migrate-format --dry-run

# Once you're ready:
rikiki-vault -C /path/to/your/vault migrate-format --yes
```

This decrypts every existing file with your current identity, re-encrypts it under a fresh random
id (dropping the old plaintext-named `.enc` file), rewrites `manifest.json` as an encrypted RV02
blob, and publishes the result as a single commit. If anything fails partway through, nothing is
deleted and nothing is pushed until every file has migrated successfully — your vault stays exactly
as readable as it was before you ran the command.

Any file this identity currently isn't authorized to decrypt is reported and left in its old form,
same as `restore` already does for an unauthorized entry — it doesn't block the rest of the
migration.

## After migrating

Every other machine's next action against this vault should be a `pull` (or a fresh `clone`), not a
`publish` — that's how it picks up the new format instead of trying to keep working from a stale,
pre-migration manifest it can still (for now) read locally.

There's no GUI action for this yet — `migrate-format` is CLI-only for now, precisely because of the
"coordinate every machine first" requirement above, which doesn't fit well into a single button in
the desktop app without more supporting UI than exists today.
