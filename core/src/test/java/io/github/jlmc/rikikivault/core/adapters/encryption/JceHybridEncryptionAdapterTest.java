package io.github.jlmc.rikikivault.core.adapters.encryption;

import io.github.jlmc.rikikivault.core.configuration.EncryptionSettings;
import io.github.jlmc.rikikivault.core.domain.exception.DecryptionException;
import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JceHybridEncryptionAdapterTest {

    private final SecureRandom random = new SecureRandom();

    private JceHybridEncryptionAdapter adapterWith(int aesKeyBits) {
        return new JceHybridEncryptionAdapter(new EncryptionSettings(aesKeyBits));
    }

    private KeyPair generateX25519KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair();
    }

    private byte[] randomContent(int length) {
        byte[] content = new byte[length];
        random.nextBytes(content);
        return content;
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 192, 256})
    void roundTripReturnsOriginalBytesAtEachSupportedKeySize(int aesKeyBits) throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(aesKeyBits);
        KeyPair recipient = generateX25519KeyPair();
        PlaintextFile original = new PlaintextFile("cv.pdf", randomContent(2048));

        EncryptedFile encrypted = adapter.encrypt(original, List.of(recipient.getPublic()));
        PlaintextFile decrypted = adapter.decrypt(encrypted, recipient.getPrivate());

        assertArrayEquals(original.content(), decrypted.content());
    }

    @Test
    void multiRecipientBothCanIndependentlyDecrypt() throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        KeyPair a = generateX25519KeyPair();
        KeyPair b = generateX25519KeyPair();
        PlaintextFile original = new PlaintextFile("contract.pdf", randomContent(512));

        EncryptedFile encrypted = adapter.encrypt(original, List.of(a.getPublic(), b.getPublic()));

        assertArrayEquals(original.content(), adapter.decrypt(encrypted, a.getPrivate()).content());
        assertArrayEquals(original.content(), adapter.decrypt(encrypted, b.getPrivate()).content());
    }

    @Test
    void decryptWithUnauthorizedPrivateKeyFails() throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        KeyPair recipient = generateX25519KeyPair();
        KeyPair stranger = generateX25519KeyPair();

        EncryptedFile encrypted = adapter.encrypt(new PlaintextFile("notes.md", randomContent(64)), List.of(recipient.getPublic()));

        assertThrows(UnauthorizedMachineException.class, () -> adapter.decrypt(encrypted, stranger.getPrivate()));
    }

    @Test
    void corruptedCiphertextByteFailsAuthentication() throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        KeyPair recipient = generateX25519KeyPair();
        EncryptedFile encrypted = adapter.encrypt(new PlaintextFile("photo.jpeg", randomContent(300)), List.of(recipient.getPublic()));

        encrypted.sealedContent()[0] ^= 0x01;

        assertThrows(DecryptionException.class, () -> adapter.decrypt(encrypted, recipient.getPrivate()));
    }

    @Test
    void corruptedAuthTagFailsAuthentication() throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        KeyPair recipient = generateX25519KeyPair();
        EncryptedFile encrypted = adapter.encrypt(new PlaintextFile("photo.jpeg", randomContent(300)), List.of(recipient.getPublic()));

        // The last 16 bytes of sealedContent are the GCM authentication tag.
        byte[] sealed = encrypted.sealedContent();
        sealed[sealed.length - 1] ^= 0x01;

        assertThrows(DecryptionException.class, () -> adapter.decrypt(encrypted, recipient.getPrivate()));
    }

    @Test
    void corruptedNonceFailsAuthentication() throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        KeyPair recipient = generateX25519KeyPair();
        EncryptedFile encrypted = adapter.encrypt(new PlaintextFile("photo.jpeg", randomContent(300)), List.of(recipient.getPublic()));

        encrypted.contentNonce()[0] ^= 0x01;

        assertThrows(DecryptionException.class, () -> adapter.decrypt(encrypted, recipient.getPrivate()));
    }

    @Test
    void repeatedEncryptionsOfSameContentProduceDifferentNonceAndCiphertext() throws Exception {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        KeyPair recipient = generateX25519KeyPair();
        PlaintextFile original = new PlaintextFile("notes.md", randomContent(128));

        EncryptedFile first = adapter.encrypt(original, List.of(recipient.getPublic()));
        EncryptedFile second = adapter.encrypt(original, List.of(recipient.getPublic()));

        assertNotEquals(toBase64(first.contentNonce()), toBase64(second.contentNonce()));
        assertNotEquals(toBase64(first.sealedContent()), toBase64(second.sealedContent()));

        // both must still decrypt correctly
        assertArrayEquals(original.content(), adapter.decrypt(first, recipient.getPrivate()).content());
        assertArrayEquals(original.content(), adapter.decrypt(second, recipient.getPrivate()).content());
    }

    @Test
    void encryptRejectsEmptyRecipientList() {
        JceHybridEncryptionAdapter adapter = adapterWith(256);
        PlaintextFile file = new PlaintextFile("x.txt", randomContent(8));

        assertThrows(RuntimeException.class, () -> adapter.encrypt(file, List.<PublicKey>of()));
    }

    private static String toBase64(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
}
