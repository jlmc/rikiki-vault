package io.github.jlmc.rikikivault.core.adapters.encryption.format;

import io.github.jlmc.rikikivault.core.domain.exception.CorruptedEncryptedFileException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RvEncryptedFileFormatCodecTest {

    private final RvEncryptedFileFormatCodec codec = new RvEncryptedFileFormatCodec();
    private final SecureRandom random = new SecureRandom();

    private KeyFingerprint randomFingerprint() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        return KeyFingerprint.of(keyPair.getPublic());
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    @Test
    void roundTripsWithASingleRecipient() throws Exception {
        RecipientKeyEntry recipient = new RecipientKeyEntry(randomFingerprint(), randomBytes(80));
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "cv.pdf",
                List.of(recipient),
                randomBytes(12),
                randomBytes(256));

        byte[] encoded = codec.encode(original);
        EncryptedFile decoded = codec.decode(encoded);

        assertEquals(original, decoded);
    }

    @Test
    void roundTripsWithMultipleRecipientsAndTrickyFileNames() throws Exception {
        List<RecipientKeyEntry> recipients = List.of(
                new RecipientKeyEntry(randomFingerprint(), randomBytes(80)),
                new RecipientKeyEntry(randomFingerprint(), randomBytes(96)));

        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "archive.tar.gz",
                recipients,
                randomBytes(12),
                randomBytes(1024));

        EncryptedFile decoded = codec.decode(codec.encode(original));

        assertEquals(original, decoded);
        assertEquals(2, decoded.recipientEntries().size());
    }

    @Test
    void roundTripsWithZeroRecipients() {
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "notes.md",
                List.of(),
                randomBytes(12),
                randomBytes(64));

        EncryptedFile decoded = codec.decode(codec.encode(original));

        assertTrue(decoded.recipientEntries().isEmpty());
        assertEquals(original, decoded);
    }

    @Test
    void preservesUnicodeFileNames() throws Exception {
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "éçã relatório final.pdf",
                List.of(new RecipientKeyEntry(randomFingerprint(), randomBytes(64))),
                randomBytes(12),
                randomBytes(32));

        EncryptedFile decoded = codec.decode(codec.encode(original));

        assertEquals(original.originalFileName(), decoded.originalFileName());
    }

    @Test
    void rejectsBadMagic() {
        byte[] bogus = "XXXX".getBytes();
        assertThrows(CorruptedEncryptedFileException.class, () -> codec.decode(bogus));
    }

    @Test
    void rejectsUnsupportedFormatVersion() throws Exception {
        RecipientKeyEntry recipient = new RecipientKeyEntry(randomFingerprint(), randomBytes(16));
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "x.txt",
                List.of(recipient),
                randomBytes(12),
                randomBytes(16));
        byte[] encoded = codec.encode(original);
        // Corrupt the version marker: RV01 -> RV99
        encoded[2] = '9';
        encoded[3] = '9';

        assertThrows(CorruptedEncryptedFileException.class, () -> codec.decode(encoded));
    }

    @Test
    void rejectsTruncatedInput() throws Exception {
        RecipientKeyEntry recipient = new RecipientKeyEntry(randomFingerprint(), randomBytes(16));
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "x.txt",
                List.of(recipient),
                randomBytes(12),
                randomBytes(16));
        byte[] encoded = codec.encode(original);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 10);

        assertThrows(CorruptedEncryptedFileException.class, () -> codec.decode(truncated));
    }

    @Test
    void rejectsUnsupportedKeyWrapAlgorithmId() throws Exception {
        RecipientKeyEntry recipient = new RecipientKeyEntry(randomFingerprint(), randomBytes(16));
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "x.txt",
                List.of(recipient),
                randomBytes(12),
                randomBytes(16));
        byte[] encoded = codec.encode(original);
        // Byte index 5 is keyWrapAlgorithmId (after 4-byte magic + 1-byte symmetricAlgorithmId)
        encoded[5] = (byte) 0x7F;

        assertThrows(CorruptedEncryptedFileException.class, () -> codec.decode(encoded));
    }

    @Test
    void rejectsDeclaredLengthExceedingRemainingData() throws Exception {
        RecipientKeyEntry recipient = new RecipientKeyEntry(randomFingerprint(), randomBytes(16));
        byte[] sealedContent = randomBytes(16);
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "x.txt",
                List.of(recipient),
                randomBytes(12),
                sealedContent);
        byte[] encoded = codec.encode(original);
        // sealedContent's own 32-bit length prefix sits right before its bytes, at the very end
        // of the stream: inflate the declared length far beyond what's actually left, without
        // truncating the stream itself (distinct from rejectsTruncatedInput above).
        int lengthPrefixOffset = encoded.length - sealedContent.length - 4;
        encoded[lengthPrefixOffset] = (byte) 0x7F;
        encoded[lengthPrefixOffset + 1] = (byte) 0xFF;
        encoded[lengthPrefixOffset + 2] = (byte) 0xFF;
        encoded[lengthPrefixOffset + 3] = (byte) 0xFF;

        assertThrows(CorruptedEncryptedFileException.class, () -> codec.decode(encoded));
    }

    @Test
    void sealedContentAndNonceSurviveByteForByte() {
        byte[] nonce = randomBytes(12);
        byte[] sealed = randomBytes(500);
        EncryptedFile original = new EncryptedFile(
                RvEncryptedFileFormatCodec.FORMAT_VERSION,
                RvEncryptedFileFormatCodec.SYMMETRIC_ALGORITHM_AES_GCM,
                RvEncryptedFileFormatCodec.KEY_WRAP_X25519_HKDF_AES_GCM,
                "photo.jpeg",
                List.of(),
                nonce,
                sealed);

        EncryptedFile decoded = codec.decode(codec.encode(original));

        assertArrayEquals(nonce, decoded.contentNonce());
        assertArrayEquals(sealed, decoded.sealedContent());
    }
}
