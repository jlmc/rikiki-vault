package io.github.jlmc.rikikivault.core.adapters.recipients;

import io.github.jlmc.rikikivault.core.domain.exception.CorruptedRecipientRegistryException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.Recipient;
import io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonRecipientRegistryFileAdapterTest {

    private static PublicKey somePublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return keyPair.getPublic();
    }

    @Test
    void loadWithoutAnExistingFileReturnsEmptyRegistry(@TempDir Path tempDir) {
        JsonRecipientRegistryFileAdapter adapter = new JsonRecipientRegistryFileAdapter(tempDir.resolve("missing.json"));

        assertEquals(RecipientRegistry.empty(), adapter.load());
    }

    @Test
    void saveThenLoadRoundTripsMultipleRecipients(@TempDir Path tempDir) throws Exception {
        Path recipientsFile = tempDir.resolve("vault").resolve("recipients.json");
        JsonRecipientRegistryFileAdapter adapter = new JsonRecipientRegistryFileAdapter(recipientsFile);
        PublicKey publicKeyA = somePublicKey();
        PublicKey publicKeyB = somePublicKey();
        RecipientRegistry original = new RecipientRegistry(1, List.of(
                new Recipient("machine-a", KeyFingerprint.of(publicKeyA), publicKeyA),
                new Recipient("machine-b", KeyFingerprint.of(publicKeyB), publicKeyB)));

        adapter.save(original);
        RecipientRegistry loaded = adapter.load();

        assertEquals(original, loaded);
    }

    @Test
    void malformedJsonOnAnExistingFileThrowsCorruptedRecipientRegistryException(@TempDir Path tempDir) throws IOException {
        Path recipientsFile = tempDir.resolve("recipients.json");
        Files.writeString(recipientsFile, "{ \"version\": 1, \"recipients\": [ { \"label\": \"a\" ");

        assertThrows(CorruptedRecipientRegistryException.class, () -> new JsonRecipientRegistryFileAdapter(recipientsFile).load());
    }

    @Test
    void validJsonWithWrongShapeThrowsCorruptedRecipientRegistryException(@TempDir Path tempDir) throws IOException {
        Path recipientsFile = tempDir.resolve("recipients.json");
        Files.writeString(recipientsFile, """
                {
                  "version": 1,
                  "recipients": [
                    { "label": "machine-a" }
                  ]
                }
                """);

        assertThrows(CorruptedRecipientRegistryException.class, () -> new JsonRecipientRegistryFileAdapter(recipientsFile).load());
    }

    @Test
    void existingButEmptyFileThrowsCorruptedRecipientRegistryException(@TempDir Path tempDir) throws IOException {
        Path recipientsFile = tempDir.resolve("recipients.json");
        Files.writeString(recipientsFile, "");

        assertThrows(CorruptedRecipientRegistryException.class, () -> new JsonRecipientRegistryFileAdapter(recipientsFile).load());
    }

    @Test
    void unreadablePublicKeyBytesThrowCorruptedRecipientRegistryException(@TempDir Path tempDir) throws IOException {
        Path recipientsFile = tempDir.resolve("recipients.json");
        Files.writeString(recipientsFile, """
                {
                  "version": 1,
                  "recipients": [
                    { "label": "machine-a", "fingerprint": "%s", "publicKey": "not-valid-base64-key-bytes!!" }
                  ]
                }
                """.formatted("a".repeat(64)));

        assertThrows(CorruptedRecipientRegistryException.class, () -> new JsonRecipientRegistryFileAdapter(recipientsFile).load());
    }
}
