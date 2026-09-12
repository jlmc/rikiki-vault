package io.github.jlmc.rikikivault.core.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.UninitializedVaultException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.FileHash;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.MigrateVaultFormatResult;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.LoadMachineIdentityUseCase;
import io.github.jlmc.rikikivault.core.ports.in.MigrateVaultFormatCommand;
import io.github.jlmc.rikikivault.core.ports.in.MigrateVaultFormatUseCase;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;
import io.github.jlmc.rikikivault.core.ports.out.RecipientRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One-time, one-way conversion of a pre-RV02 vault (plaintext {@code manifest.json},
 * {@code documents/&lt;real-path&gt;.enc} filenames, RV01 envelopes with a plaintext filename
 * header) to the opaque-path format: every real path/filename becomes unreadable to anyone without
 * this vault's encryption key, matching how content already was.
 *
 * <p>Safety design: nothing old is deleted, and nothing new is published, until every entry has
 * been re-encrypted successfully (see {@link #migrate}) - a failure partway through this loop
 * leaves the vault in its original, fully-readable pre-migration state (plus some harmless,
 * unreferenced orphan files under {@code documents/}), never a half-migrated one.
 */
public final class MigrateVaultFormatService implements MigrateVaultFormatUseCase {

    private static final Logger log = LoggerFactory.getLogger(MigrateVaultFormatService.class);

    private final Path manifestFile;
    private final ManifestPort encryptedManifestPort;
    private final FileStoragePort documentsFiles;
    private final EncryptionPort encryptionPort;
    private final LoadMachineIdentityUseCase loadMachineIdentityUseCase;
    private final RecipientRegistryPort recipientRegistryPort;
    private final GitRepositoryPort gitRepositoryPort;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MigrateVaultFormatService(
            Path manifestFile,
            ManifestPort encryptedManifestPort,
            FileStoragePort documentsFiles,
            EncryptionPort encryptionPort,
            LoadMachineIdentityUseCase loadMachineIdentityUseCase,
            RecipientRegistryPort recipientRegistryPort,
            GitRepositoryPort gitRepositoryPort) {
        this.manifestFile = Objects.requireNonNull(manifestFile, "manifestFile must not be null");
        this.encryptedManifestPort = Objects.requireNonNull(encryptedManifestPort, "encryptedManifestPort must not be null");
        this.documentsFiles = Objects.requireNonNull(documentsFiles, "documentsFiles must not be null");
        this.encryptionPort = Objects.requireNonNull(encryptionPort, "encryptionPort must not be null");
        this.loadMachineIdentityUseCase = Objects.requireNonNull(
                loadMachineIdentityUseCase, "loadMachineIdentityUseCase must not be null");
        this.recipientRegistryPort = Objects.requireNonNull(recipientRegistryPort, "recipientRegistryPort must not be null");
        this.gitRepositoryPort = Objects.requireNonNull(gitRepositoryPort, "gitRepositoryPort must not be null");
    }

    @Override
    public MigrateVaultFormatResult migrate(MigrateVaultFormatCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        LegacyManifest legacy = tryReadLegacyManifest();
        if (legacy == null) {
            return MigrateVaultFormatResult.ofAlreadyMigrated();
        }

        MachineIdentity identity = loadMachineIdentityUseCase.load();
        List<PublicKey> recipients = recipientRegistryPort.load().recipients().stream().map(Recipient::publicKey).toList();
        byte[] hmacKey = VaultManifest.generateHmacKey();

        List<ManifestEntry> newEntries = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        List<PendingWrite> pendingWrites = new ArrayList<>();

        for (LegacyManifestEntry oldEntry : legacy.files()) {
            try {
                byte[] oldBytes = documentsFiles.readFile(oldEntry.path());
                RvEncryptedFileFormatCodec.LegacyRv01File decoded = codec.decodeLegacyRv01(oldBytes);
                PlaintextFile plaintext = encryptionPort.decrypt(decoded.file(), identity.privateKey());

                String newId = UUID.randomUUID().toString();
                ManifestEntry newEntry = new ManifestEntry(
                        newId, oldEntry.plaintextPath(), FileHash.hmac(hmacKey, plaintext.content()),
                        RvEncryptedFileFormatCodec.FORMAT_VERSION);

                if (!command.dryRun()) {
                    EncryptedFile reEncrypted = encryptionPort.encrypt(
                            new PlaintextFile(oldEntry.plaintextPath(), plaintext.content()), recipients);
                    pendingWrites.add(new PendingWrite(newEntry.documentsRelativePath(), codec.encode(reEncrypted), oldEntry.path()));
                }
                newEntries.add(newEntry);
            } catch (RuntimeException e) {
                log.warn("Failed to migrate {}: {}", oldEntry.plaintextPath(), e.getMessage());
                failedPaths.add(oldEntry.plaintextPath());
            }
        }

        if (command.dryRun()) {
            return new MigrateVaultFormatResult(false, true, newEntries.size(), failedPaths, false);
        }

        // Point of no return: only now write the new ciphertexts, save the new (encrypted)
        // manifest, and remove the old plaintext-named ones - everything above this line can fail
        // without touching anything already on disk.
        for (PendingWrite write : pendingWrites) {
            documentsFiles.writeFile(write.newPath(), write.bytes());
        }
        encryptedManifestPort.save(new VaultManifest(legacy.version(), hmacKey, newEntries));
        for (PendingWrite write : pendingWrites) {
            documentsFiles.deleteFile(write.oldPath());
        }

        gitRepositoryPort.add(List.of("."));
        gitRepositoryPort.commit("migrate vault to opaque-path format (RV02)");
        boolean pushed = gitRepositoryPort.push();
        log.info("Migrated {} file(s) to the opaque-path format ({} failed)", newEntries.size(), failedPaths.size());
        return new MigrateVaultFormatResult(false, false, newEntries.size(), failedPaths, pushed);
    }

    /**
     * @return {@code null} if the vault is already on the new (encrypted, non-JSON) format.
     * @throws UninitializedVaultException if there's no manifest.json at all at this path - almost
     *         always a wrong path (pointing at the {@code vault/} metadata subfolder instead of the
     *         vault root, for example) rather than a legitimate "nothing to migrate" state, since
     *         even a brand-new vault always gets a manifest.json written at init time.
     */
    private LegacyManifest tryReadLegacyManifest() {
        if (!Files.exists(manifestFile)) {
            throw new UninitializedVaultException(
                    "No manifest.json found at " + manifestFile + " - is this the vault root "
                            + "(not the vault/ metadata subfolder, or some other directory)?");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(manifestFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read manifest file at " + manifestFile, e);
        }
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, LegacyManifest.class);
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    private record LegacyManifest(int version, List<LegacyManifestEntry> files) {
    }

    private record LegacyManifestEntry(String path, String plaintextPath, String hash, String formatVersion) {
    }

    private record PendingWrite(String newPath, byte[] bytes, String oldPath) {
    }
}
