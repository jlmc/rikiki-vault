# Release process

How to cut a release of Rikiki Vault: bump the version, tag it, and move on to the next
development iteration. This is a contributor-facing runbook, not user documentation — see
[`README.md`](../README.md)/[`README.pt.md`](../README.pt.md) for how to build and run the app.

## Why manual

The root `pom.xml` has no `maven-release-plugin`, no `<scm>`, and no `<distributionManagement>` —
there's no artifact repository this project publishes to, and no CI pipeline that could drive an
automated release. The steps below are the established substitute: a short, repeatable sequence of
plain Maven/Git commands, run by hand from the repository root.

## Steps

Replace `<release-version>` (e.g. `1.0.0`) and `<next-version>` (e.g. `1.0.1`) below. Run every
command from the repository root, on a clean working tree.

1. **Set the release version** across the root `pom.xml` and all three modules (`core`, `cli`,
   `gui-javafx`), including the inter-module `core` dependency version referenced from
   `cli/pom.xml`/`gui-javafx/pom.xml`:

   ```bash
   mvn versions:set -DnewVersion=<release-version> -DgenerateBackupPoms=false
   ```

2. **Verify consistency** — every module should now show the same version:

   ```bash
   grep -rn "<version>" pom.xml core/pom.xml cli/pom.xml gui-javafx/pom.xml
   ```

3. **Run the full test suite** — must be green before committing:

   ```bash
   mvn test
   ```

4. **Commit the release version**:

   ```bash
   git commit -am "chore: release version <release-version>"
   ```

5. **Tag it** — an annotated tag, named after the bare version number (no `v` prefix):

   ```bash
   git tag -a <release-version> -m "Release <release-version>"
   ```

6. **Set the next snapshot version**:

   ```bash
   mvn versions:set -DnewVersion=<next-version>-SNAPSHOT -DgenerateBackupPoms=false
   ```

7. **Run the test suite again** — the version bump itself should never break anything, but confirm
   it:

   ```bash
   mvn test
   ```

8. **Commit the new snapshot**:

   ```bash
   git commit -am "chore: prepare for next development iteration (<next-version>-SNAPSHOT)"
   ```

9. **Push** the commits and the tag:

   ```bash
   git push origin master
   git push origin <release-version>
   ```

## Tag naming

A release tag is the bare version number (e.g. `1.0.0`), matching the version string Maven itself
uses — no `v` prefix. This is deliberately distinct from this repository's `milestone-N` tags,
which mark development checkpoints during a work session, not versioned releases.

## Not covered by this process

- **Publishing to a Maven repository** — there's no `<distributionManagement>` configured, so
  `mvn deploy` isn't part of this flow. A release here only produces a tagged commit, not a
  published artifact.
- **Native app packaging** — `.app`/`.dmg`/`.msi`/`.deb` builds via `jpackage` are a separate,
  manual step per OS (`scripts/package.sh`/`scripts/package.ps1`), documented in the
  "Distribution" section of `README.md`. They are not tied to a Maven release version — the
  scripts use a fixed `--app-version` independent of the `pom.xml` version.

## If something goes wrong

Before the first `git push` (step 9), everything above is local and safely reversible:
`git tag -d <release-version>` removes the tag, `git reset --soft`/`--hard` can undo a commit.
Once a tag has been pushed, treat it as immutable — don't force-move it. If the release turns out
to be wrong, cut a new patch version instead.
