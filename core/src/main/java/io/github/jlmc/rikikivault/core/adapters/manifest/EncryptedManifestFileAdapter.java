package io.github.jlmc.rikikivault.core.adapters.manifest;

import io.github.jlmc.rikikivault.core.adapters.encryption.format.RvEncryptedFileFormatCodec;
import io.github.jlmc.rikikivault.core.domain.exception.CorruptedEncryptedFileException;
import io.github.jlmc.rikikivault.core.domain.exception.VaultNotMigratedException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.out.EncryptionPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Decorates {@link JsonManifestFileAdapter} so {@code manifest.json} is encrypted as a whole on
 * disk - the exact same hybrid X25519+AES-GCM envelope already used for every file under
 * {@code documents/} - rather than written as plain JSON. This is what keeps every tracked file's
 * real path ({@link io.github.jlmc.rikikivault.core.domain.model.ManifestEntry#plaintextPath()})
 * out of the git-visible remote entirely: only a machine that can decrypt is anything, and
 * decrypting the manifest is the single, cheap operation that reveals every real path at once
 * (as opposed to per-field encryption, which would need its own key-wrapping machinery).
 *
 * <p>{@code identity}/{@code recipientPublicKeys} are resolved lazily (once per {@link #load()}/
 * {@link #save} call, never cached) so that: (a) the current machine's identity may not exist yet
 * at construction time (mid-init/mid-clone), and (b) {@code save} always wraps for whatever the
 * recipient list is <em>at the moment of saving</em> - critical right after an authorize/revoke,
 * where the registry has just changed and the manifest must be re-wrapped for the new set, not a
 * stale one captured earlier.
 *
 * <p>{@link #load()} lets {@code EncryptionPort}'s unauthorized-recipient failure propagate
 * uncaught - every caller except {@code CloneVaultService} (a freshly cloned, not-yet-authorized
 * machine) should legitimately crash loudly rather than proceed without a manifest.
 */
public final class EncryptedManifestFileAdapter implements ManifestPort {

    private final Path manifestFile;
    private final JsonManifestFileAdapter jsonMapper;
    private final EncryptionPort encryptionPort;
    private final Supplier<PrivateKey> identity;
    private final Supplier<List<PublicKey>> recipientPublicKeys;
    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();

    public EncryptedManifestFileAdapter(
            Path manifestFile,
            EncryptionPort encryptionPort,
            Supplier<PrivateKey> identity,
            Supplier<List<PublicKey>> recipientPublicKeys) {
        this.manifestFile = Objects.requireNonNull(manifestFile, "manifestFile must not be null");
        this.jsonMapper = new JsonManifestFileAdapter(manifestFile);
        this.encryptionPort = Objects.requireNonNull(encryptionPort, "encryptionPort must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.recipientPublicKeys = Objects.requireNonNull(recipientPublicKeys, "recipientPublicKeys must not be null");
    }

    @Override
    public VaultManifest load() {
        if (!Files.exists(manifestFile)) {
            return VaultManifest.empty();
        }
        byte[] envelopeBytes;
        try {
            envelopeBytes = Files.readAllBytes(manifestFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read manifest file at " + manifestFile, e);
        }
        EncryptedFile encrypted;
        try {
            encrypted = codec.decode(envelopeBytes);
        } catch (CorruptedEncryptedFileException e) {
            // By far the most likely cause: this is still the pre-migration plaintext-JSON
            // manifest (or otherwise not an RV02 envelope) - not necessarily "corrupted" data.
            throw new VaultNotMigratedException(
                    "manifest.json at " + manifestFile + " isn't in the current encrypted format "
                            + "- run the vault-format migration first", e);
        }
        PlaintextFile plaintext = encryptionPort.decrypt(encrypted, identity.get());
        return jsonMapper.fromJsonBytes(plaintext.content());
    }

    @Override
    public void save(VaultManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        byte[] jsonBytes = jsonMapper.toJsonBytes(manifest);
        EncryptedFile encrypted = encryptionPort.encrypt(new PlaintextFile("manifest.json", jsonBytes), recipientPublicKeys.get());
        try {
            Path parent = manifestFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(manifestFile, codec.encode(encrypted));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write manifest file at " + manifestFile, e);
        }
    }
}
