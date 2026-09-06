package io.github.jlmc.rikikivault.core.adapters.encryption.format;

import io.github.jlmc.rikikivault.core.domain.exception.CorruptedPrivateKeyEnvelopeException;
import io.github.jlmc.rikikivault.core.domain.model.PrivateKeyEnvelope;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Encodes/decodes the "RVPK" passphrase-protected private-key envelope. Purely a byte-format
 * codec: it validates structural integrity (magic, kdf id, lengths) but knows nothing about
 * actually deriving keys or performing cryptography - see {@code LocalKeyStoreAdapter}.
 */
public final class PrivateKeyEnvelopeCodec {

    public static final int VERSION = 1;
    public static final int KDF_PBKDF2_HMAC_SHA256 = 0;

    private static final byte[] MAGIC = "RVPK".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_LENGTH = 12;

    /**
     * Whether the given bytes start with the "RVPK" magic - i.e. whether this is a
     * passphrase-protected private key rather than the legacy raw PKCS8 DER format.
     * Never throws: a magic mismatch just means "not this format", not corruption.
     */
    public boolean isEnvelope(byte[] bytes) {
        if (bytes.length < MAGIC.length) {
            return false;
        }
        return Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    public byte[] encode(PrivateKeyEnvelope envelope) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(envelope.kdfId());
            out.writeInt(envelope.iterations());

            if (envelope.salt().length > 0xFF) {
                throw new IllegalArgumentException("salt too large for a 1-byte length prefix: " + envelope.salt().length);
            }
            out.writeByte(envelope.salt().length);
            out.write(envelope.salt());

            if (envelope.nonce().length != NONCE_LENGTH) {
                throw new IllegalArgumentException("nonce must be " + NONCE_LENGTH + " bytes, got " + envelope.nonce().length);
            }
            out.write(envelope.nonce());

            out.writeInt(envelope.ciphertext().length);
            out.write(envelope.ciphertext());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    public PrivateKeyEnvelope decode(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new CorruptedPrivateKeyEnvelopeException("Not an RVPK private key envelope");
            }

            int version = in.readUnsignedByte();
            if (version != VERSION) {
                throw new CorruptedPrivateKeyEnvelopeException("Unsupported RVPK envelope version: " + version);
            }

            int kdfId = in.readUnsignedByte();
            if (kdfId != KDF_PBKDF2_HMAC_SHA256) {
                throw new CorruptedPrivateKeyEnvelopeException("Unsupported KDF id: " + kdfId);
            }

            int iterations = in.readInt();
            if (iterations <= 0) {
                throw new CorruptedPrivateKeyEnvelopeException("Invalid iteration count in private key envelope: " + iterations);
            }

            int saltLength = in.readUnsignedByte();
            byte[] salt = readExactly(in, saltLength);

            byte[] nonce = new byte[NONCE_LENGTH];
            in.readFully(nonce);

            int ciphertextLength = in.readInt();
            if (ciphertextLength < 0) {
                throw new CorruptedPrivateKeyEnvelopeException("Negative ciphertext length in private key envelope");
            }
            byte[] ciphertext = readExactly(in, ciphertextLength);

            return new PrivateKeyEnvelope(kdfId, iterations, salt, nonce, ciphertext);
        } catch (EOFException e) {
            throw new CorruptedPrivateKeyEnvelopeException("Private key envelope is truncated", e);
        } catch (IOException e) {
            throw new CorruptedPrivateKeyEnvelopeException("Failed to parse private key envelope", e);
        }
    }

    private static byte[] readExactly(DataInputStream in, int length) throws IOException {
        // Guard against a corrupted/malicious length field forcing a huge allocation
        // before we even attempt to read: bound it by what's actually left in the stream.
        if (length > in.available()) {
            throw new CorruptedPrivateKeyEnvelopeException("Declared length (" + length + ") exceeds remaining data");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }
}
