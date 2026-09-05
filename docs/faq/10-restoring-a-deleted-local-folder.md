# I accidentally deleted my `local/` folder on this machine — the published files are still there, how do I get them back?

*[Ler em português](10-restoring-a-deleted-local-folder.pt.md)*

Run `restore` — no network, no backup key needed. This is a different situation from the earlier
"I lost my SSD" entries: here the *machine itself* is fine, `documents/`/`.git`/your identity are
all intact, and only the plaintext working copy got wiped.

## Why `pull` alone doesn't fix this

It's tempting to assume `pull` would just re-download everything, but it won't: `pull` only
re-decrypts a manifest entry whose hash changed since the *last* pull. If nothing was published
remotely in the meantime, every entry still has the same hash it always did, so `pull` skips all
of them — it has no notion of "also check whether the file is actually still sitting in `local/`".
`clone` isn't a fallback either, since it requires an empty target directory, and this vault
already has `.git`/`vault`/`documents` in place.

## What `restore` does instead

Unlike `pull`, `restore` doesn't compare anything to a "before" state — it walks every entry in
the current manifest and decrypts straight from `documents/`, entirely offline. By default it's
non-destructive: a file that's already sitting in `local/` is left alone and just gets counted as
"already there", so running it doesn't risk clobbering work you have that hasn't been published
yet.

```bash
java -jar cli/target/rikiki-vault.jar -C <vault> restore
```

Or in the desktop app: **"Mais ▾" → "Restaurar ficheiros em falta..."** in the main window's
toolbar.

## If a file needs to be forced back to the published version

Pass `--force` (CLI) or accept the follow-up prompt the desktop app shows when some files were
skipped — this re-decrypts *every* entry, overwriting files that already exist in `local/` too.
Useful if a file got corrupted rather than deleted, but be aware: any unpublished local edit to an
overwritten file is gone afterward, replaced by whatever was last published. Without `--force`,
none of that risk exists — it can only ever add files back, never remove or replace one.

```bash
java -jar cli/target/rikiki-vault.jar -C <vault> restore --force
```
