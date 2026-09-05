# What happens if two machines edit the same file before syncing?

*[Ler em português](09-conflicting-edits.pt.md)*

`pull` detects it and reports a conflict — with a SHA-256 hash of both versions so you can tell
them apart — but it never overwrites or merges anything automatically. Resolving it is a manual
step.

## How it's detected

`PullVaultService` scans your unpublished local changes *before* pulling, then compares the
manifest before and after the pull to see what changed remotely. If the same path shows up on
both sides — changed locally and changed remotely since your last sync — it's recorded as a
`VaultConflict` instead of being applied: your local file is left exactly as it is, and the
conflict is reported with the change type and SHA-256 hash for each side (visible in the desktop
app's pull-result dialog, or printed by the CLI).

## What it does *not* do

There's no "keep remote", "keep local", or side-by-side compare action built into the app —
"keep local" is simply what happens by default, since a conflicted path is never touched. There's
no merge logic either (this isn't a text-merge tool). If you want the remote version, you have to
fetch and decrypt it yourself — the conflicting `.enc` file the pull just downloaded already
matches the remote's manifest entry, so it's already sitting in `documents/`, decryptable the same
way as the [fourth FAQ entry](04-decrypting-without-the-app.md) describes.

## What to actually do about it

1. Note the two hashes reported for the conflicting path.
2. Decide which version you want (or reconcile the two by hand — copy content between them,
   diff them, whatever the file calls for).
3. Overwrite `local/<path>` with whatever you decided the final content should be.
4. `publish` again — this is now just a normal local change and goes through the usual encrypt
   and push flow.

Until you do that, the next `status`/`pull` will keep reporting the same path as a pending local
change (and the same conflict, if you pull again before publishing) — nothing forces a decision on
its own timeline, but nothing resolves itself either.
