# I deleted a file from the vault — is it really gone?

*[Ler em português](06-deleting-a-file-is-not-permanent.pt.md)*

Not from Git's history. `publish`ing a deletion removes the file going forward, but every earlier
commit where it still existed — encrypted content included — stays in the repository.

## What actually happens on delete

When you delete a file and publish, `PublishVaultService` removes its entry from the manifest and
deletes the `.enc` blob from the *current* tree, then commits that change. That new commit is
real: `git log`/`clone`/`pull` from this point on won't show the file at all. But Git doesn't
overwrite history — the *previous* commit, where `documents/<path>.enc` still existed, is still
right there in the repository's history.

Anyone with read access to the repository (any already-authorized machine, or literally anyone
with access to wherever the remote lives) can get that old content back without any special
tooling:

```bash
git log --all --full-history -- documents/<path>.enc   # find the commit where it still existed
git show <that-commit>:documents/<path>.enc > recovered.enc   # pull the old ciphertext back out
```

If that machine was an authorized recipient at the time the file was encrypted, it can decrypt
`recovered.enc` exactly like any other `.enc` file (see the [previous FAQ
entry](04-decrypting-without-the-app.md) for how, if you don't want to use the app itself).

## To actually remove it from history

This app doesn't do this for you — it's a heavier, riskier operation than anything `publish`
does. You'd need a history-rewriting tool (`git filter-repo`, or the older BFG Repo-Cleaner),
followed by a force-push, and then everyone else's existing clones become out of sync with the
rewritten history (they'd need to re-clone, not just `pull`). That's real disruption for a
multi-machine vault, so it's a deliberate, manual step — not something a `revoke` or a `delete`
does as a side effect.

## The part that no history rewrite fixes

Even after rewriting history, anyone who had already pulled the old commit before you rewrote it
already has that ciphertext (and, if they were an authorized recipient, can already decrypt it).
Rewriting history stops *future* clones from getting it — it doesn't reach into machines that
already downloaded it. Once a file has been published to a remote at all, treat that moment as the
point where you've shared it with whoever already had (or later gets, before you clean up) read
access to that remote.
