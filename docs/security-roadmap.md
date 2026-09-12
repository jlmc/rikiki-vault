# Security & Platform Roadmap

*(Ler em português: [security-roadmap.pt.md](security-roadmap.pt.md))*

## How to read this document

This is a living backlog of security hardening ideas and platform directions for Rikiki Vault,
not a committed roadmap — items land here after being discussed and get picked up as dedicated
milestones once prioritized. Each item explains why it matters and, for the top-priority ones,
carries enough technical detail to start implementation without re-deriving the design from
scratch.

## Priority: High

### 1. Encrypt the private key at rest with a passphrase — ✅ Implemented

Shipped: `KeyStorePort.isPassphraseProtected()`/`load(char[])`/`changePassphrase(...)`, the "RVPK"
envelope (`PrivateKeyEnvelopeCodec`, PBKDF2-HMAC-SHA256 + AES-GCM), CLI `set-passphrase`/
`remove-passphrase`, and the desktop app's unlock screen + Configurações → Segurança. Session-cached
via `PassphraseCachingKeyStorePort` (one prompt per CLI invocation / per open GUI session) rather
than the "never cache" design first sketched below — see `docs/faq/07-private-key-is-not-password-protected.md`
for the reasoning. The rest of this section is kept as the original design record.

**Why:** `LocalKeyStoreAdapter`
(`core/src/main/java/io/github/jlmc/rikikivault/core/adapters/keystore/LocalKeyStoreAdapter.java`)
writes `private.key` as plain PKCS8 DER bytes, protected only by owner-only file permissions
(`writeFileSecurely`/`restrictToOwnerBestEffort`). Anyone who can read that file — another OS
account, an already-unlocked session, a disk pulled from the machine — can use it directly; no
secret beyond file permissions stands between them and every file this vault has ever published.
This is the single most exploitable gap in the whole design, already called out as a known
limitation in `docs/faq/07-private-key-is-not-password-protected.md`.

**Current state (confirmed by direct code reading):**
- `LocalKeyStoreAdapter`: `PRIVATE_KEY_FILE="private.key"`, `PUBLIC_KEY_FILE="public.key"`,
  `KEY_ALGORITHM="X25519"`. `save(MachineIdentity)` refuses to overwrite an existing key
  (`exists()` → `MachineIdentityAlreadyExistsException`), writes raw PKCS8/X.509 DER bytes.
  `load()` throws `PrivateKeyNotFoundException` when the file is missing. Implements
  `KeyStorePort { void save(MachineIdentity); MachineIdentity load(); boolean exists(); }`.
- `MachineIdentity` is a plain record `(KeyFingerprint id, PublicKey publicKey, PrivateKey
  privateKey, String keyAlgorithm)` — an in-memory value, no notion of "protected" state.
- Only two composition sites build a `LocalKeyStoreAdapter`: `cli/.../Main.java`'s private
  `VaultContext.at()`, and `gui-javafx/.../gui/VaultContext.java`'s `at(Path)` — the only two
  places that would need to learn how to obtain a passphrase.
- `VaultConfig` (`identityDirectory`, `encryptionSettings`), loaded/saved by
  `YamlConfigFileAdapter`, is a natural place to check "does this identity need a passphrase"
  without a new field — the answer can come from the on-disk key file's own format (see below).
- No KDF (PBKDF2/Argon2/scrypt) exists anywhere in the codebase yet. The existing crypto
  convention to follow lives in `JceHybridEncryptionAdapter`/`X25519HkdfAesGcmKeyWrapStrategy`:
  `Cipher.getInstance("AES/GCM/NoPadding")`, a random 12-byte nonce via `SecureRandom`,
  `GCMParameterSpec(128, nonce)`, and a `wipe(byte[])` helper zeroing key material in a `finally`.

**Design:**
- New self-describing on-disk envelope for `private.key` when passphrase-protected, via a
  magic-byte prefix so `LocalKeyStoreAdapter` can auto-detect plaintext vs. encrypted without any
  new `VaultConfig` field: `"RVPK" (4 bytes) | version (1 byte) | KDF id (1 byte, 0 =
  PBKDF2WithHmacSHA256) | iterations (int) | salt length + salt | nonce (12 bytes) | AES-GCM
  ciphertext of the PKCS8 bytes (tag included)`.
- Use `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` (built into the JDK — no new
  dependency) with a high iteration count (≥600,000, current OWASP guidance for PBKDF2-SHA256) to
  derive a 256-bit KEK from the passphrase + random salt; wrap the PKCS8 bytes with
  `Cipher`/`GCMParameterSpec`, following the exact style already used in
  `JceHybridEncryptionAdapter`. The public key file is never encrypted (it isn't secret).
- `KeyStorePort` gains passphrase-aware operations, while the existing zero-arg `save`/`load` keep
  today's exact behavior (backward compatible, strictly opt-in):
  ```java
  MachineIdentity load(); // unchanged behavior; throws PassphraseRequiredException if protected
  MachineIdentity load(char[] passphrase);
  void save(MachineIdentity identity); // unchanged: writes an unencrypted key, today's default
  void save(MachineIdentity identity, char[] passphrase);
  void changePassphrase(char[] currentOrNull, char[] newOrNull); // add / change / remove
  boolean isPassphraseProtected();
  ```
  New `PassphraseRequiredException`/`InvalidPassphraseException extends RikikiVaultException`.
- CLI: new `set-passphrase`/`remove-passphrase` commands; read the passphrase via
  `System.console().readPassword(...)` when a real console is attached (no terminal echo), with a
  documented fallback for environments without one. `Main.VaultContext.at()` catches
  `PassphraseRequiredException` and prompts once per invocation.
- GUI: `App.java`'s `openVault(...)` checks `keyStorePort.isPassphraseProtected()` before building
  the `VaultContext`, and if true shows a small new `PassphrasePromptController`/
  `passphrase-prompt-view.fxml`, reusing the masked/eye-toggle `PasswordField` pattern already
  built in `GitAuthSettingsPanel.wireReveal(...)`. A new "Security" tab in Settings lets the user
  set/change/remove the passphrase via `changePassphrase(...)`.
- The passphrase itself is only ever held as a `char[]`, wiped immediately after deriving the KEK;
  the already-loaded `MachineIdentity` continues to live in memory for the session exactly as
  today (no new caching of the passphrase itself).
- Rollout is strictly opt-in — an existing plaintext `private.key` keeps loading exactly as it
  does today; nothing breaks for anyone who doesn't turn this on.

**Tests:** `LocalKeyStoreAdapterTest` — round-trip with a passphrase; wrong passphrase →
`InvalidPassphraseException`; loading a protected key without one → `PassphraseRequiredException`;
every existing plaintext-format test keeps passing unchanged (proves backward compatibility);
`changePassphrase` covering add/change/remove.

**Open questions to resolve before implementation:** iteration count vs. perceived latency on
older machines; whether the GUI should ever cache the passphrase in memory for the rest of a
session (recommendation: no, re-prompt each time the identity needs to be loaded from disk, to
keep the security property meaningful) — this interacts directly with proposal #3 (auto-lock)
below.

---

### 2. Detect unexpected new recipients during `pull` — ✅ Implemented

Delivered: `PullVaultService` gained a `RecipientRegistryPort` dependency, `PullResult` gained
`newRecipients`/`removedRecipients` (diffed by fingerprint between before/after snapshots around
`gitRepositoryPort.pull()`), and both the CLI (`runPull`) and GUI
(`PullResultController`/`pull-result-view.fxml`) surface both lists. The rest of this section is
kept as a record of the original design.

**Why:** `recipients.json` lives in the same git-tracked `vault/` directory as `manifest.json` and
travels through the exact same `git pull` as everything else. If a compromised git host, a
malicious collaborator, or a MITM ever added an attacker's public key to that file, the next
`publish` from any legitimate machine would silently re-encrypt every file for that attacker too —
and nothing in the app today would ever mention it. `PullVaultService` already has the perfect
place to notice: it already diffs `manifest.json` before/after the pull to detect conflicts; the
exact same before/after snapshot technique works for `recipients.json`.

**Current state (confirmed by direct code reading):** `PullVaultService.pull()`
(`core/src/main/java/io/github/jlmc/rikikivault/core/application/usecase/PullVaultService.java:76-128`)
captures `before = indexByPlaintextPath(manifestPort.load())` before calling
`gitRepositoryPort.pull()`, then `after = indexByPlaintextPath(manifestPort.load())` afterward, and
diffs by hash to build `VaultConflict`s. `RecipientRegistryPort.load()/save()`
(`JsonRecipientRegistryFileAdapter`) is not touched anywhere in this method today. `PullResult`
currently has exactly four fields: `updatedPaths`, `deletedPaths`, `conflicts`
(`List<VaultConflict>`), `uncommittedLocalChangesAtStart`. `Recipient` is `(String label,
KeyFingerprint fingerprint, PublicKey publicKey)`; `RecipientRegistry` is `(int version,
List<Recipient> recipients)`.

**Design:**
- `PullVaultService` gains a `RecipientRegistryPort` constructor dependency.
- In `pull()`, capture `recipientsBefore` (keyed by `fingerprint().hex()`) right alongside the
  existing `before` manifest snapshot, and `recipientsAfter` right alongside `after`. Diff by
  fingerprint set-difference: entries present only in `recipientsAfter` → `newRecipients`; present
  only in `recipientsBefore` → `removedRecipients`.
- `PullResult` gains two new fields: `List<Recipient> newRecipients`, `List<Recipient>
  removedRecipients` (empty when nothing changed, same `List.copyOf` convention as today).
- This is informational/warning, not blocking: by the time `pull()` returns, `git pull` has
  already merged `recipients.json` — the port only exposes a whole-repository `pull()`, so there
  is no clean way to "hold back" a single file mid-pull without much larger surgery to
  `GitRepositoryPort`. The security value is making the change impossible to miss, not preventing
  it from arriving.
- GUI (`MainWindowController.onPull`, `PullResultController`/`pull-result-view.fxml`): a new,
  visually distinct warning section (same weight as the existing conflict section) lists
  `newRecipients` with label + fingerprint; a milder informational section lists
  `removedRecipients`.
- CLI (`Main.runPull`): prints a clearly marked `"New recipients detected in this pull:"` block,
  and a milder `"Recipients removed:"` block, using the `RecipientRegistryPort` already available
  in `VaultContext` (just not wired into `PullVaultService` yet). New i18n keys in both
  `messages_{pt,en}.properties` and `CliMessages`.
- No change to `AuthorizeMachineService`/`RevokeMachineService` — they remain the only legitimate
  way to change `recipients.json`; this feature is about the *pulling* side always noticing when
  that file changed, regardless of why.

**Tests:** `PullVaultServiceTest` (using the existing `FakeRecipientRegistryPort`) — unchanged
recipients → both new lists empty; a recipient added/removed between before/after → shows up
correctly; a pull with both manifest conflicts and recipient changes surfaces both independently.
New `EndToEndClonePullTest` case: machine A authorizes machine C, then machine B (a third,
independent machine) pulls and sees C in `newRecipients` — proves the real-adapter path, not just
fakes.

**Open question:** whether to add a persistent "acknowledged recipients" store so a
revoke-then-re-authorize of the same fingerprint doesn't re-flag — recommendation: skip this for
v1 (always flag on any change since the last local snapshot); re-authorization events are rare
enough that the extra noise is an acceptable, much simpler trade-off.

---

### 3. Auto-lock the GUI after inactivity

**Why:** Even with a passphrase-protected key (#1), an already-unlocked session left open — a
machine walked away from, a laptop left running — exposes every decrypted preview/edit currently
on screen and lets anyone at the keyboard keep working as an authorized machine.
`docs/faq/07-private-key-is-not-password-protected.md` already names this exact scenario as an
unsolved limitation ("protection against someone using your own already-unlocked session"). An
idle auto-lock is the direct mitigation.

**Current state (confirmed by direct code reading):** `App.java` builds a single `Scene` once
(`start(Stage)`) and only ever swaps its root (`stage.getScene().setRoot(...)`) between
Welcome/InitOrClone/MainWindow — no event filter of any kind exists on that `Scene` today.
`MainWindowController`'s `BorderPane` root has the toolbar in `top`, the nav rail in `left`, and
only the files/settings/manage-access content inside the `center` `StackPane` (`centerContainer`)
— so an overlay confined to `centerContainer` would *not* cover the toolbar or rail; a real
full-window lock has to sit above the whole `BorderPane`. `startAutoRefresh()` currently keeps its
3-second `Timeline` as a *local variable*, not a field — it's never paused today.
`GitAuthSettingsPanel.wireReveal(...)` already has a working masked/eye-toggle `PasswordField`
pattern to reuse. `Dialogs` only offers blocking `Alert`-based modals — not suitable for an
embedded lock screen.

**Design:**
- A single `Scene`-level event filter for `MouseEvent.ANY`/`KeyEvent.ANY`, installed once in
  `App.start()` right after `stage.setScene(scene)`, resets an idle timer on every interaction.
- `lock()`/`unlock()` live in `App.java` (not `MainWindowController`), using the exact same
  `stage.getScene().setRoot(...)` mechanism already used for every other screen transition: store
  the current root before swapping to a new lock-screen root, restore it on successful unlock.
  This reuses the app's existing navigation pattern instead of inventing an overlay concept.
- On lock: `MainWindowController`'s auto-refresh `Timeline` must be paused — this requires first
  promoting it from a local variable to a field. Visible decrypted content (editor/preview) is
  cleared from the screen; the underlying `VaultContext`/`MachineIdentity` stay resident in memory
  for the session (clearing already-rendered decrypted bytes from the screen is the realistic
  goal — the JVM gives no clean guarantee of scrubbing arbitrary heap memory).
- Unlock reuses the `PasswordField`+eye-toggle pattern from `GitAuthSettingsPanel`. Two tiers,
  called out explicitly rather than glossed over:
  - If proposal #1 (passphrase-protected identity) is in place: unlocking re-verifies that
    passphrase by attempting to re-derive the key — meaningful access control.
  - If not: unlock can only be a "click to dismiss" gate — a privacy-screen/shoulder-surfing
    deterrent, not real access control. This limitation must be documented plainly wherever the
    feature is described, so it's never oversold.
- A new "Security" section in Settings adds a duration picker (1/5/15/30 min / Never), persisted
  alongside the existing language preference in `~/.rikiki-vault/preferences/settings.json` (same
  adapter pattern as `LocalLanguagePreferenceAdapter`).

**Tests:** No UI automation (established project policy — no TestFX/Monocle). The idle-detection
logic itself (a "should lock now" calculation from a last-interaction timestamp + threshold) can
be extracted into a small pure class and unit-tested without JavaFX, the same way
`FolderTreeBuilder` is kept separate from its controller. Manual verification: leave the app idle
past the threshold and confirm the lock screen covers toolbar + rail + content; unlock with
correct/incorrect passphrase; confirm the auto-refresh timeline doesn't keep firing while locked.

**Recommended build order relative to #1:** implement #1 first — it's what makes #3's unlock step
a real security boundary rather than a cosmetic one. #2 is fully independent and can be built in
any order relative to the other two.

## Priority: Medium

- Plaintext content hash in `manifest.json` allows offline confirmation of known files — each
  entry's `hash` field (SHA-256 of the plaintext content, computed by `ScanChangesService` for
  change detection) can be compared by anyone with read access to the Git repository against the
  SHA-256 of a candidate file, confirming without decryption whether that exact content is in the
  vault. Candidate mitigation: replace plain SHA-256 with HMAC-SHA256 keyed by a vault-derived
  secret, preserving change detection while preventing offline confirmation by anyone without that
  key.
- Out-of-band fingerprint verification in the `authorize` flow (a checklist step to confirm a
  fingerprint over a separate channel before confirming) — complements #2 from the side of who
  gets added, but is mostly a process/UX nudge rather than new cryptography.
- Commit signing (SSH/GPG) for the git history — gives verifiable provenance, but depends on the
  user already having a signing key configured, which is friction outside the app's control.
- An "Activity" screen in the GUI listing the publish/authorize/revoke history — the data already
  exists in `git log`; this is observability/UX more than a new security control.

## Priority: Low / future direction

- Signing the manifest/recipient registry with the publishing machine's key — overlaps with #2's
  goal; only worth the extra design complexity if the simpler "warn on diff" approach in #2 turns
  out to be insufficient in practice.
- OS-native secure storage (macOS Keychain / Windows DPAPI) as an alternative to the file-based
  key store — bigger, platform-specific engineering effort for a marginal gain over #1, which is
  portable and much simpler.
- Secure delete ("shred") for local files removed by "Clear Local" — of doubtful value on modern
  SSDs (wear leveling defeats simple overwrite-before-delete); may be better served by documenting
  the limitation than building it.
- Flagging repeated `UnauthorizedMachineException` occurrences as a possible compromised-machine
  signal — low probability of ever firing in a personal/small-team deployment without another
  attack vector already active.
- Warning explicitly before a force-push/history divergence — more a data-safety feature than a
  cryptographic one; the existing publish flow already surfaces ahead/behind status.

## Platform direction (not a security item)

**A React Native-style cross-platform app** (mobile + desktop) as an alternative or complement to
the current JavaFX GUI. Open question, deliberately not investigated yet: how the `core` module
(Java — cryptography, Git via JGit) would be exposed to a JS/TS app — a local service/REST bridge,
a from-scratch reimplementation of the crypto/git logic in JS/TS, or some other bridge. Recorded
here as a direction to revisit, not a committed plan.
