# Rikiki Vault — Implementation Plan

## 1. Project Overview

Build a local-first password/document vault application called **Rikiki Vault**.

The main goal is to securely store personal files in a private GitHub repository while ensuring that GitHub contains ONLY encrypted files.

The application runs locally on trusted machines and provides:

- Git repository synchronization
- Asymmetric encryption/decryption
- Local file management
- File browsing
- File editing
- Change detection
- Encryption of modified files
- Git commit/push with explicit user confirmation
- Multiple machines sharing the same encrypted vault

The project must be implemented as a **Java Maven project**.

The architecture must follow:

- SOLID principles
- Clean Architecture principles where applicable
- Ports & Adapters / Hexagonal Architecture
- Strong separation of domain, application, infrastructure and UI concerns
- Dependency inversion
- No business logic coupled directly to Git, filesystem, cryptography or UI implementations

---

# 2. Core Security Model

The GitHub repository is the source of truth for the encrypted vault.

GitHub MUST NEVER contain original/plaintext files.

Example:

vault repository:

    documents/
        cv/
            cv.pdf.enc
        contracts/
            contract.pdf.enc
        notes/
            notes.md.enc

The original files exist only locally:

    local/
        cv/
            cv.pdf
        contracts/
            contract.pdf
        notes/
            notes.md

`local/` MUST be excluded from Git.

The encrypted repository is safe to synchronize between multiple machines.

---

# 3. Cryptography

Do NOT depend on external encryption tools such as:

- age
- sops
- gpg

The application should implement the required cryptographic functionality using Java's standard cryptographic APIs where possible.

Use asymmetric public/private key cryptography.

Preferred approach:

- RSA or preferably a modern asymmetric algorithm supported cleanly by the chosen Java runtime.
- Use the public key for encryption.
- Use the private key for decryption.

However, do NOT encrypt large files directly using RSA.

Use hybrid encryption:

    random symmetric key
           |
           v
    encrypt file using AES-GCM
           |
           v
    encrypt AES key using public key
           |
           v
    encrypted file format

This allows secure encryption of files of arbitrary size.

AES-GCM should be used for authenticated encryption.

Each encrypted file must have a unique random nonce/IV.

The private key must NEVER be stored in Git.

The public key can be stored as vault metadata.

---

# 4. Multiple Machines

The application must support multiple trusted machines.

Recommended model:

Each machine has its own private key.

Example:

    Machine A
        private-key-A
        public-key-A

    Machine B
        private-key-B
        public-key-B

The vault metadata stores the authorized public keys:

    authorized-keys/
        machine-a.pub
        machine-b.pub

When encrypting a file, the file encryption key can be encrypted for each authorized public key.

This allows the same encrypted file to be decrypted by multiple authorized machines.

Conceptually:

    file
      |
      v
    AES-GCM encryption
      |
      +--> AES key encrypted with public-key-A
      |
      +--> AES key encrypted with public-key-B
      |
      v
    encrypted file

If a machine is revoked, its public key should no longer be included in newly encrypted files.

Do NOT implement key revocation by simply deleting the private key.

---

# 5. Repository Structure

Use the following repository structure:

    rikiki-vault/
    │
    ├── documents/
    │   ├── cv/
    │   │   └── cv.pdf.enc
    │   ├── contracts/
    │   │   └── contract.pdf.enc
    │   └── notes/
    │       └── notes.md.enc
    │
    ├── vault/
    │   └── manifest.json
    │
    ├── .gitignore
    └── README.md

Local plaintext files:

    local/
        cv/
            cv.pdf
        contracts/
            contract.pdf
        notes/
            notes.md

`local/` must be ignored by Git.

The Git repository must never contain plaintext files.

---

# 6. Local Application Data

Machine-specific data should live outside the repository.

Example:

    ~/.rikiki-vault/
        identity/
            private.key
            public.key

        config/
            config.json

The private key must have restrictive filesystem permissions where supported by the OS.

The application must fail safely if the private key is missing.

Never print private keys to logs.

Never include private keys in exceptions.

Never commit private keys.

---

# 7. Encrypted File Format

Define a Rikiki Vault encrypted file format.

Example conceptual structure:

    RV01
    algorithm metadata
    encrypted file key(s)
    nonce
    ciphertext
    authentication tag

Do not simply rename an encrypted file to `.enc`.

The format must contain enough metadata to know:

- format version
- encryption algorithm
- key wrapping algorithm
- nonce/IV
- encrypted symmetric key
- authorized recipient information if required

The format must be versioned.

Example:

    RV01

This allows future migration of the encryption format.

---

# 8. File Naming

Plaintext:

    cv.pdf

Encrypted:

    cv.pdf.enc

The original extension must be preserved before `.enc`.

Examples:

    document.pdf.enc
    notes.md.enc
    photo.jpeg.enc
    archive.zip.enc

The application must correctly restore:

    document.pdf.enc -> document.pdf
    notes.md.enc -> notes.md
    photo.jpeg.enc -> photo.jpeg

---

# 9. Initial Vault Setup

Provide a command/use case such as:

    rikiki-vault init

It should:

1. Generate a machine identity.
2. Generate public/private key pair.
3. Store private key locally.
4. Store public key locally.
5. Create the required vault structure.
6. Create `.gitignore`.
7. Create initial manifest.
8. Optionally initialize the Git repository.

Private key generation must use a cryptographically secure random source.

---

# 10. Clone Existing Vault

Provide:

    rikiki-vault clone <repository>

The application should:

1. Clone the private GitHub repository.
2. Validate repository structure.
3. Load vault metadata.
4. Load local private key.
5. Verify that the machine's public key is authorized.
6. Decrypt encrypted files.
7. Recreate the plaintext structure under `local/`.

Example:

    documents/cv/cv.pdf.enc

becomes:

    local/cv/cv.pdf

---

# 11. Synchronization

Provide:

    rikiki-vault pull

The flow:

1. Detect local modifications.
2. Warn if there are uncommitted local changes.
3. Pull latest encrypted repository state.
4. Detect encrypted files added/removed/changed.
5. Decrypt affected files.
6. Update local plaintext files.
7. Update local manifest/state.

The application must protect local changes from being silently overwritten.

Never automatically overwrite modified local files.

If both remote and local versions changed, report a conflict.

---

# 12. Change Detection

The application must detect modifications to plaintext files.

Use SHA-256 hashes.

For each tracked file store:

    path
    plaintext hash
    encrypted file hash
    metadata/version if necessary

Example:

    local/cv/cv.pdf
    SHA-256: abc123...

When the application scans the local directory:

    current hash != stored hash

means the file was modified.

Do not rely only on timestamps.

---

# 13. File Browser

The application should provide a UI allowing the user to browse:

    local/

The UI should show:

- folders
- files
- file type
- modified state
- encrypted/synchronized state

Example:

    CV
      ├── CV-Joao-Costa.pdf       ✓
      ├── CV-Joao-Costa.docx      ✓
      └── old/
          └── CV-2020.pdf         ⚠ modified

The UI should make it obvious which files have pending changes.

---

# 14. File Visualization

The application should support file previews where practical.

Start with:

- Markdown
- TXT
- JSON
- XML
- images
- PDF

Architecture must use a `FileViewer` abstraction.

Example:

    interface FileViewer {
        boolean supports(Path file);
        ViewerResult view(Path file);
    }

Implementations:

    MarkdownViewer
    TextViewer
    JsonViewer
    ImageViewer
    PdfViewer

Do not couple the domain/application layer to JavaFX/Swing/browser technologies.

The UI adapter should decide how the viewer result is rendered.

---

# 15. File Editing

The application should allow editing supported text files.

For example:

    notes.md

The user edits:

    local/notes.md

The application detects:

    modified

The UI should display:

    File modified locally.

and allow:

    Save
    Encrypt
    Revert
    Diff

Binary files should not be edited directly unless a suitable external editor integration is explicitly implemented later.

---

# 16. Diff

Before encrypting and pushing changes, show a diff where possible.

For text files:

    --- previous
    +++ current

For binary files:

    File changed

Do not attempt to display binary content as text.

The diff functionality must be behind a port/interface.

---

# 17. Encrypt & Publish Flow

The user must explicitly approve changes before publishing.

Example UI:

    Changes detected

    M  cv/CV-Joao-Costa.pdf
    M  notes/notes.md
    A  documents/new.pdf

    [Review Changes]

    [Encrypt & Push]

The application must NOT automatically push changes.

The flow:

    local changes
          |
          v
    calculate diff
          |
          v
    user confirmation
          |
          v
    encrypt files
          |
          v
    update encrypted repository
          |
          v
    git status
          |
          v
    git diff
          |
          v
    user confirmation
          |
          v
    git commit
          |
          v
    git push

---

# 18. Git Integration

Do not make Git calls from business logic.

Create a Git port:

    GitRepository

Possible operations:

    clone()
    pull()
    status()
    add()
    commit()
    push()
    diff()

Then provide an infrastructure adapter:

    JGitRepositoryAdapter

Prefer JGit over invoking shell commands such as:

    git commit
    git push

This keeps Git integration inside the infrastructure layer.

---

# 19. GitHub Authentication

The GitHub repository is private.

Authentication must be handled by the infrastructure adapter.

Do not place GitHub credentials in the repository.

Do not place GitHub credentials in application source code.

Support a secure authentication mechanism appropriate for local Git access.

Keep GitHub authentication independent from encryption.

---

# 20. Ports & Adapters Architecture

Use a structure similar to:

    src/main/java/
        .../rikikivault/
            domain/
            application/
            ports/
            adapters/
            configuration/

Possible structure:

    domain/
        model/
            VaultFile
            VaultManifest
            EncryptionMetadata
            MachineIdentity
            VaultChange

    application/
        usecase/
            InitializeVault
            CloneVault
            PullVault
            ScanChanges
            EncryptChanges
            DecryptVault
            CommitChanges
            PublishChanges

    ports/
        in/
            InitializeVaultUseCase
            SyncVaultUseCase
            PublishChangesUseCase
            BrowseFilesUseCase

        out/
            EncryptionPort
            FileStoragePort
            GitPort
            HashPort
            KeyStorePort
            FileViewerPort

    adapters/
        encryption/
            JceEncryptionAdapter

        filesystem/
            LocalFileSystemAdapter

        git/
            JGitAdapter

        keystore/
            LocalKeyStoreAdapter

        hashing/
            Sha256HashAdapter

        ui/
            ...

The exact package structure can evolve, but dependency direction must remain:

    UI
      ↓
    Application
      ↓
    Domain

Infrastructure adapters implement ports.

Domain must not depend on infrastructure.

---

# 21. SOLID Requirements

Apply SOLID deliberately.

Single Responsibility:

Each component should have one reason to change.

Open/Closed:

Adding another encryption implementation should not require changing application services.

Liskov:

Port implementations must honour their contracts.

Interface Segregation:

Prefer small focused interfaces:

    EncryptionPort
    KeyStorePort
    GitPort
    FileStoragePort

Dependency Inversion:

Application/domain depend on ports, never concrete implementations.

Avoid large "God services".

Avoid static utility classes containing business logic.

---

# 22. Encryption Port

Create a dedicated abstraction:

    interface EncryptionPort {

        EncryptedFile encrypt(
            PlaintextFile file,
            Collection<PublicKey> recipients
        );

        PlaintextFile decrypt(
            EncryptedFile file,
            PrivateKey privateKey
        );
    }

The application layer should not know whether encryption uses RSA, AES, ECIES, etc.

---

# 23. Key Management

Create:

    KeyStorePort

Responsibilities:

- create machine identity
- load private key
- load public key
- save identity
- verify identity

Example:

    MachineIdentity generateIdentity();

    MachineIdentity loadIdentity();

The implementation handles the physical storage.

The private key must never be exposed unnecessarily.

Avoid passing private keys throughout the application.

Prefer a dedicated `MachineIdentity` abstraction.

---

# 24. Manifest

Create a manifest file containing vault metadata.

Example:

    {
      "version": 1,
      "files": [
        {
          "path": "cv/CV-Joao-Costa.pdf.enc",
          "plaintextPath": "cv/CV-Joao-Costa.pdf",
          "hash": "...",
          "formatVersion": "RV01"
        }
      ]
    }

Do not store sensitive plaintext information unnecessarily.

The manifest must not contain:

- private keys
- passwords
- secrets
- plaintext file content

---

# 25. Security Requirements

Security is a first-class concern.

Never:

- log private keys
- log decrypted file contents
- commit `local/`
- commit private keys
- store passwords in plaintext
- silently overwrite local modified files
- silently push changes
- disable AES-GCM authentication
- reuse encryption nonces
- use predictable random values

Use:

    SecureRandom

for cryptographic randomness.

Use authenticated encryption.

Validate encrypted file format before decryption.

Handle corrupted/tampered files safely.

If authentication fails, do not write partially decrypted files.

Decrypt to a temporary file and atomically move it into place only after successful authentication.

---

# 26. Error Handling

Define meaningful domain/application errors.

Examples:

    VaultNotInitializedException
    PrivateKeyNotFoundException
    UnauthorizedMachineException
    EncryptionException
    DecryptionException
    CorruptedEncryptedFileException
    FileConflictException
    GitOperationException

Do not leak cryptographic implementation details unnecessarily.

Error messages must never contain sensitive material.

---

# 27. CLI First

Implement the application initially as a CLI.

Commands:

    rikiki-vault init
    rikiki-vault clone
    rikiki-vault pull
    rikiki-vault status
    rikiki-vault encrypt
    rikiki-vault decrypt
    rikiki-vault publish

Example:

    rikiki-vault status

Output:

    Vault status

    Modified:
      M cv/CV-Joao-Costa.pdf
      M notes/personal.md

    Added:
      A documents/new.pdf

    Deleted:
      D old/document.pdf

Then:

    rikiki-vault publish

should show the changes and require explicit confirmation.

---

# 28. UI

After the core application is stable, introduce a desktop UI.

The UI should NOT contain:

- encryption logic
- Git logic
- filesystem business rules
- key management logic

The UI communicates exclusively through application use cases.

Potential technology:

    JavaFX

Use JavaFX only as an adapter/UI layer.

The architecture should make it possible to replace JavaFX later without modifying the domain/application layer.

---

# 29. Concurrency

The application should avoid blocking the UI thread.

Long-running operations such as:

- Git clone
- Git pull
- encryption
- decryption
- hashing large files
- publishing

must execute asynchronously when using the desktop UI.

The domain must remain independent of concurrency implementation.

---

# 30. Testing Strategy

Use a strong automated test suite.

Unit tests:

- encryption/decryption
- key generation
- hash calculation
- encrypted file format
- manifest handling
- change detection
- conflict detection

Integration tests:

- filesystem adapter
- JGit adapter
- complete encryption/decryption flow
- complete publish flow

Security tests:

- wrong private key
- corrupted ciphertext
- modified authentication tag
- modified nonce
- unauthorized machine
- missing key
- malformed encrypted file

End-to-end scenario:

    Machine A
       |
       | create file
       v
    encrypt
       |
       v
    GitHub
       |
       | pull
       v
    Machine B
       |
       v
    decrypt
       |
       v
    original file

---

# 31. Important End-to-End Scenario

The following scenario MUST work:

### Machine A

Create:

    local/cv/cv.pdf

Run:

    rikiki-vault status

Result:

    Added:
      cv/cv.pdf

Run:

    rikiki-vault publish

Application:

1. Detects new file.
2. Shows confirmation.
3. Encrypts it.
4. Creates:

       documents/cv/cv.pdf.enc

5. Updates manifest.
6. Creates Git commit.
7. Asks confirmation before push.
8. Pushes to GitHub.

### Machine B

Run:

    rikiki-vault pull

Application:

1. Pulls repository.
2. Detects new encrypted file.
3. Validates encryption metadata.
4. Uses Machine B private key.
5. Decrypts file.
6. Creates:

       local/cv/cv.pdf

The resulting plaintext file must be byte-for-byte identical.

---

# 32. Conflict Scenario

Machine A:

    modifies cv.pdf

Machine B:

    also modifies cv.pdf

Machine A publishes first.

Machine B attempts:

    rikiki-vault publish

The application must detect the conflict.

It MUST NOT silently overwrite the remote version.

The UI should report:

    Conflict detected

    cv/cv.pdf

    Local version:
        SHA-256: XXXXX

    Remote version:
        SHA-256: YYYYY

Possible actions:

    Keep Local
    Keep Remote
    Compare
    Cancel

For binary files, comparison can simply report that both versions differ.

---

# 33. Repository as Source of Truth

The repository represents the canonical encrypted vault state.

The local directory is a working copy.

Therefore:

    GitHub
       |
       | encrypted state
       v
    local plaintext working copy

Changes flow:

    local plaintext
       |
       v
    encrypted repository

Never:

    plaintext -> GitHub

---

# 34. MVP Scope

The first implementation should NOT attempt to implement everything.

MVP:

1. Maven Java project.
2. Generate asymmetric key pair.
3. Local key storage.
4. AES-GCM file encryption.
5. Asymmetric wrapping of AES key.
6. Versioned `.enc` format.
7. Encrypt/decrypt files.
8. `local/` directory.
9. `documents/` encrypted directory.
10. SHA-256 change detection.
11. Manifest.
12. JGit integration.
13. GitHub private repository support.
14. CLI.
15. Explicit publish confirmation.
16. Unit/integration tests.

Only after this works should we implement:

- desktop UI
- file preview
- editing
- diff viewer
- multiple recipients/key rotation
- advanced conflict resolution

---

# 35. Development Principles

Do not over-engineer the MVP.

However, do establish the correct architectural boundaries from day one.

Prefer:

    small interfaces
    immutable domain objects
    explicit use cases
    dependency injection
    testable components
    secure defaults
    clear error handling

Avoid:

    static global state
    service locator
    God classes
    leaking infrastructure into domain
    direct shell execution for Git
    external encryption CLI tools
    plaintext temporary files when avoidable
    automatic Git push

---

# 36. Suggested Development Phases

## Phase 1 — Project Foundation

- Maven project
- Java version selection
- package structure
- domain models
- ports
- basic configuration
- testing framework

## Phase 2 — Cryptography

- machine identity
- key generation
- local key storage
- AES-GCM
- asymmetric key wrapping
- encrypted file format
- encryption/decryption tests

## Phase 3 — Filesystem

- `local/`
- `documents/`
- file discovery
- hashing
- manifest
- change detection

## Phase 4 — Git

- JGit adapter
- clone
- pull
- status
- commit
- push
- GitHub private repository authentication

## Phase 5 — Application Use Cases

- init
- clone
- pull
- status
- encrypt
- decrypt
- publish

## Phase 6 — Multi-machine Support

- multiple public keys
- recipient metadata
- machine authorization
- unauthorized machine handling
- key rotation

## Phase 7 — Desktop UI

- JavaFX
- file browser
- file preview
- editor
- status indicators
- change review
- confirmation dialogs

## Phase 8 — Hardening

- security review
- corruption tests
- conflict tests
- filesystem permission handling
- logging review
- secret leakage review
- documentation

---

# 37. Definition of Done

The project is considered successful when:

1. A user can initialize a vault.
2. A public/private key pair is generated.
3. The private key never enters Git.
4. A plaintext file can exist under `local/`.
5. The file can be encrypted into `documents/*.enc`.
6. Only encrypted files are committed.
7. The encrypted repository can be pushed to a private GitHub repository.
8. Another machine can clone the repository.
9. The second machine can decrypt the files using its authorized private key.
10. Plaintext files are recreated under `local/`.
11. Local modifications are detected.
12. Changes are reviewed before publishing.
13. No automatic push happens without confirmation.
14. Conflicts are detected.
15. Corrupted encrypted files are rejected.
16. Wrong keys cannot decrypt files.
17. The application has automated tests.
18. Domain/application code has no dependency on Git, JavaFX or the filesystem implementation.
19. Cryptography is isolated behind ports.
20. The project follows SOLID and Ports & Adapters principles.

---

# 38. First Task

Start by creating the Maven project and implementing ONLY the foundation and cryptographic MVP.

Do not start with JavaFX.

Do not implement GitHub integration yet.

First deliver:

    Maven project
    +
    architecture
    +
    key generation
    +
    local key storage
    +
    AES-GCM
    +
    asymmetric key wrapping
    +
    .enc file format
    +
    encrypt/decrypt use cases
    +
    comprehensive tests

After this foundation is validated, proceed incrementally to filesystem and Git integration.
