package io.github.jlmc.rikikivault.core.adapters.encryption.format;

import io.github.jlmc.rikikivault.core.domain.exception.CorruptedPrivateKeyEnvelopeException;
import io.github.jlmc.rikikivault.core.domain.model.PrivateKeyEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateKeyEnvelopeCodecTest {

    private final PrivateKeyEnvelopeCodec codec = new PrivateKeyEnvelopeCodec();
    private final SecureRandom secureRandom = new SecureRandom();

    private PrivateKeyEnvelope sampleEnvelope() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);
        byte[] ciphertext = "pretend-this-is-ciphertext-bytes".getBytes(StandardCharsets.UTF_8);
        return new PrivateKeyEnvelope(PrivateKeyEnvelopeCodec.KDF_PBKDF2_HMAC_SHA256, 600_000, salt, nonce, ciphertext);
    }

    @Test
    void encodeThenDecodeRoundTrips() {
        PrivateKeyEnvelope original = sampleEnvelope();

        byte[] encoded = codec.encode(original);
        PrivateKeyEnvelope decoded = codec.decode(encoded);

        assertEquals(original.kdfId(), decoded.kdfId());
        assertEquals(original.iterations(), decoded.iterations());
        assertArrayEquals(original.salt(), decoded.salt());
        assertArrayEquals(original.nonce(), decoded.nonce());
        assertArrayEquals(original.ciphertext(), decoded.ciphertext());
    }

    @Test
    void isEnvelopeIsTrueForEncodedBytes() {
        assertTrue(codec.isEnvelope(codec.encode(sampleEnvelope())));
    }

    @Test
    void isEnvelopeIsFalseForUnrelatedBytes() {
        assertFalse(codec.isEnvelope("this is not an envelope at all".getBytes(StandardCharsets.UTF_8)));
        assertFalse(codec.isEnvelope(new byte[0]));
        assertFalse(codec.isEnvelope(new byte[]{'R', 'V'}));
    }

    @Test
    void decodeRejectsUnrecognizedMagic() {
        byte[] bytes = "not-an-envelope-but-long-enough".getBytes(StandardCharsets.UTF_8);
        assertThrows(CorruptedPrivateKeyEnvelopeException.class, () -> codec.decode(bytes));
    }

    @Test
    void decodeRejectsUnknownVersion() {
        byte[] encoded = codec.encode(sampleEnvelope());
        encoded[4] = (byte) 99; // version byte, right after the 4-byte magic
        assertThrows(CorruptedPrivateKeyEnvelopeException.class, () -> codec.decode(encoded));
    }

    @Test
    void decodeRejectsUnknownKdfId() {
        byte[] encoded = codec.encode(sampleEnvelope());
        encoded[5] = (byte) 99; // kdfId byte
        assertThrows(CorruptedPrivateKeyEnvelopeException.class, () -> codec.decode(encoded));
    }

    @Test
    void decodeRejectsTruncatedInput() {
        byte[] encoded = codec.encode(sampleEnvelope());
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 5);
        assertThrows(CorruptedPrivateKeyEnvelopeException.class, () -> codec.decode(truncated));
    }
}
