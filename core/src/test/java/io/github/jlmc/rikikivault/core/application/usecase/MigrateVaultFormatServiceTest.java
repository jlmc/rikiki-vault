package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.MigrateVaultFormatResult;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.MigrateVaultFormatCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link MigrateVaultFormatService} against a hand-built pre-RV02 vault (plain JSON
 * manifest, legacy RV01 {@code .enc} file) - the scenario none of the other tests cover, since
 * every other fixture in this suite is already RV02.
 */
class MigrateVaultFormatServiceTest {

    private static final RvEncryptedFileFormatCodec CODEC = new RvEncryptedFileFormatCodec();

    private static MachineIdentity someIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    /** Hand-encodes the old RV01 layout, mirroring RvEncryptedFileFormatCodecTest's helper. */
    private static byte[] encodeLegacyRv01(String fileName, RecipientKeyEntry recipient, byte[] nonce, byte[] sealed) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(RvEncryptedFileFormatCodec.LEGACY_FORMAT_VERSION.getBytes(StandardCharsets.US_ASCII));
            out.writeByte(RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM);
            out.writeByte(RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM);
            byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            out.writeShort(nameBytes.length);
            out.write(nameBytes);
            out.writeShort(1);
            out.write(recipient.recipientFingerprint().toBytes());
            out.writeInt(recipient.wrappedKeyBlob().length);
            out.write(recipient.wrappedKeyBlob());
            out.write(nonce);
            out.writeInt(sealed.length);
            out.write(sealed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    @Test
    void migratesALegacyRv01VaultToTheEncryptedPathFormat(@TempDir Path tempDir) throws Exception {
        MachineIdentity identity = someIdentity();
        Recipient recipient = new Recipient("machine-a", identity.id(), identity.publicKey());
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(new RecipientRegistry(1, List.of(recipient)));

        // -- legacy on-disk state: plain JSON manifest.json + an RV01 .enc file --
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, """
                {
                  "version": 1,
                  "files": [
                    { "path": "cv.pdf.enc", "plaintextPath": "cv.pdf", "hash": "%s", "formatVersion": "RV01" }
                  ]
                }
                """.formatted("0".repeat(64)));

        byte[] plaintextContent = "cv content".getBytes(StandardCharsets.UTF_8);
        FakeEncryptionPort encryptionPort = new FakeEncryptionPort();
        encryptionPort.fileToReturnOnDecrypt = new PlaintextFile("cv.pdf", plaintextContent);
        RecipientKeyEntry recipientEntry = new RecipientKeyEntry(identity.id(), new byte[]{1, 2, 3});
        byte[] legacyEncoded = encodeLegacyRv01("cv.pdf", recipientEntry, new byte[12], new byte[]{9, 9, 9});

        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        documentsFiles.writeFile("cv.pdf.enc", legacyEncoded);

        FakeManifestPort newManifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();

        MigrateVaultFormatService service = new MigrateVaultFormatService(
                manifestFile, newManifestPort, documentsFiles, encryptionPort,
                new FakeLoadMachineIdentityUseCase(identity), recipientRegistryPort, gitRepositoryPort);

        MigrateVaultFormatResult result = service.migrate(new MigrateVaultFormatCommand(false));

        assertEquals(1, result.filesMigrated());
        assertTrue(result.failedPaths().isEmpty());

        VaultManifest newManifest = newManifestPort.load();
        assertEquals(1, newManifest.files().size());
        assertEquals("cv.pdf", newManifest.files().getFirst().plaintextPath());
        assertEquals(RvEncryptedFileFormatCodec.FORMAT_VERSION, newManifest.files().getFirst().formatVersion());
        // new id is a fresh UUID, unrelated to the old "cv.pdf.enc" name
        assertTrue(!newManifest.files().getFirst().id().equals("cv.pdf.enc"));

        // old-format file removed, new opaque-id file written in its place
        assertTrue(documentsFiles.listFiles().stream().noneMatch(p -> p.equals("cv.pdf.enc")));
        assertTrue(documentsFiles.listFiles().contains(newManifest.files().getFirst().documentsRelativePath()));

        assertEquals(1, gitRepositoryPort.addedPathBatches.size());
        assertEquals(1, gitRepositoryPort.commitMessages.size());
    }

    @Test
    void anAlreadyMigratedVaultIsLeftUntouched(@TempDir Path tempDir) throws Exception {
        Path manifestFile = tempDir.resolve("manifest.json");
        // Not valid JSON at all - stands in for an RV02 binary blob, which is exactly what
        // tryReadLegacyManifest must recognize as "not the old format" rather than a parse error.
        Files.write(manifestFile, new byte[]{'R', 'V', '0', '2', 1, 2, 3});

        MigrateVaultFormatService service = new MigrateVaultFormatService(
                manifestFile, new FakeManifestPort(), new FakeFileStoragePort(), new FakeEncryptionPort(),
                new FakeLoadMachineIdentityUseCase(someIdentity()), new FakeRecipientRegistryPort(), new FakeGitRepositoryPort());

        MigrateVaultFormatResult result = service.migrate(new MigrateVaultFormatCommand(false));

        assertTrue(result.alreadyMigrated());
        assertEquals(0, result.filesMigrated());
    }

    @Test
    void dryRunReportsCountsWithoutChangingAnything(@TempDir Path tempDir) throws Exception {
        MachineIdentity identity = someIdentity();
        FakeRecipientRegistryPort recipientRegistryPort = new FakeRecipientRegistryPort(
                new RecipientRegistry(1, List.of(new Recipient("machine-a", identity.id(), identity.publicKey()))));

        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, """
                {
                  "version": 1,
                  "files": [
                    { "path": "notes.md.enc", "plaintextPath": "notes.md", "hash": "%s", "formatVersion": "RV01" }
                  ]
                }
                """.formatted("0".repeat(64)));

        FakeEncryptionPort encryptionPort = new FakeEncryptionPort();
        encryptionPort.fileToReturnOnDecrypt = new PlaintextFile("notes.md", "notes content".getBytes(StandardCharsets.UTF_8));
        RecipientKeyEntry recipientEntry = new RecipientKeyEntry(identity.id(), new byte[]{1});
        FakeFileStoragePort documentsFiles = new FakeFileStoragePort();
        documentsFiles.writeFile("notes.md.enc", encodeLegacyRv01("notes.md", recipientEntry, new byte[12], new byte[]{7}));

        FakeManifestPort newManifestPort = new FakeManifestPort();
        FakeGitRepositoryPort gitRepositoryPort = new FakeGitRepositoryPort();

        MigrateVaultFormatService service = new MigrateVaultFormatService(
                manifestFile, newManifestPort, documentsFiles, encryptionPort,
                new FakeLoadMachineIdentityUseCase(identity), recipientRegistryPort, gitRepositoryPort);

        MigrateVaultFormatResult result = service.migrate(new MigrateVaultFormatCommand(true));

        assertEquals(1, result.filesMigrated());
        assertTrue(result.dryRun());
        // nothing actually changed
        assertTrue(newManifestPort.load().files().isEmpty());
        assertTrue(documentsFiles.listFiles().contains("notes.md.enc"));
        assertTrue(gitRepositoryPort.addedPathBatches.isEmpty());
        assertTrue(gitRepositoryPort.commitMessages.isEmpty());
    }
}
