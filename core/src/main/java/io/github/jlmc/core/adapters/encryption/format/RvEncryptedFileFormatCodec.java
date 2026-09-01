package io.github.jlmc.core.adapters.encryption.format;

import io.github.jlmc.core.domain.exception.CorruptedEncryptedFileException;
import io.github.jlmc.core.domain.model.EncryptedFile;
import io.github.jlmc.core.domain.model.KeyFingerprint;
import io.github.jlmc.core.domain.model.RecipientKeyEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes/decodes the RV01 encrypted file format. Purely a byte-format codec:
 * it validates structural integrity (magic, algorithm ids, lengths) but knows
 * nothing about actually performing cryptography.
 */
public final class RvEncryptedFileFormatCodec {

    public static final String FORMAT_VERSION = "RV01";
    public static final int SYMMETRIC_ALGORITHM_AES_GCM = 0x01;
    public static final int KEY_WRAP_X25519_HKDF_AES_GCM = 0x01;

    private static final byte[] MAGIC = FORMAT_VERSION.getBytes(StandardCharsets.US_ASCII);
    private static final int FINGERPRINT_LENGTH = 32;
    private static final int CONTENT_NONCE_LENGTH = 12;

    public byte[] encode(EncryptedFile file) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(MAGIC);
            out.writeByte(file.symmetricAlgorithmId());
            out.writeByte(file.keyWrapAlgorithmId());
            writeLengthPrefixed16(out, file.originalFileName().getBytes(StandardCharsets.UTF_8));

            out.writeShort(file.recipientEntries().size());
            for (RecipientKeyEntry entry : file.recipientEntries()) {
                byte[] fingerprintBytes = entry.recipientFingerprint().toBytes();
                out.write(fingerprintBytes);
                writeLengthPrefixed32(out, entry.wrappedKeyBlob());
            }

            if (file.contentNonce().length != CONTENT_NONCE_LENGTH) {
                throw new IllegalArgumentException("contentNonce must be " + CONTENT_NONCE_LENGTH + " bytes, got " + file.contentNonce().length);
            }
            out.write(file.contentNonce());
            writeLengthPrefixed32(out, file.sealedContent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    public EncryptedFile decode(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new CorruptedEncryptedFileException("Unrecognized or unsupported format header (expected " + FORMAT_VERSION + ")");
            }

            int symmetricAlgorithmId = in.readUnsignedByte();
            if (symmetricAlgorithmId != SYMMETRIC_ALGORITHM_AES_GCM) {
                throw new CorruptedEncryptedFileException("Unsupported symmetricAlgorithmId: " + symmetricAlgorithmId);
            }

            int keyWrapAlgorithmId = in.readUnsignedByte();
            if (keyWrapAlgorithmId != KEY_WRAP_X25519_HKDF_AES_GCM) {
                throw new CorruptedEncryptedFileException("Unsupported keyWrapAlgorithmId: " + keyWrapAlgorithmId);
            }

            String originalFileName = new String(readLengthPrefixed16(in), StandardCharsets.UTF_8);

            int recipientCount = in.readUnsignedShort();
            List<RecipientKeyEntry> recipients = new ArrayList<>(recipientCount);
            for (int i = 0; i < recipientCount; i++) {
                byte[] fingerprintBytes = new byte[FINGERPRINT_LENGTH];
                in.readFully(fingerprintBytes);
                byte[] wrappedKeyBlob = readLengthPrefixed32(in);
                recipients.add(new RecipientKeyEntry(KeyFingerprint.ofBytes(fingerprintBytes), wrappedKeyBlob));
            }

            byte[] contentNonce = new byte[CONTENT_NONCE_LENGTH];
            in.readFully(contentNonce);
            byte[] sealedContent = readLengthPrefixed32(in);

            return new EncryptedFile(FORMAT_VERSION, symmetricAlgorithmId, keyWrapAlgorithmId, originalFileName,
                    recipients, contentNonce, sealedContent);
        } catch (EOFException e) {
            throw new CorruptedEncryptedFileException("Encrypted file is truncated", e);
        } catch (IOException e) {
            throw new CorruptedEncryptedFileException("Failed to parse encrypted file", e);
        } catch (IllegalArgumentException e) {
            throw new CorruptedEncryptedFileException("Malformed encrypted file content", e);
        }
    }

    private static void writeLengthPrefixed16(DataOutputStream out, byte[] data) throws IOException {
        if (data.length > 0xFFFF) {
            throw new IllegalArgumentException("Value too large for a 16-bit length prefix: " + data.length);
        }
        out.writeShort(data.length);
        out.write(data);
    }

    private static void writeLengthPrefixed32(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    private static byte[] readLengthPrefixed16(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        return readExactly(in, length);
    }

    private static byte[] readLengthPrefixed32(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new CorruptedEncryptedFileException("Negative length field in encrypted file");
        }
        return readExactly(in, length);
    }

    private static byte[] readExactly(DataInputStream in, int length) throws IOException {
        // Guard against a corrupted/malicious length field forcing a huge allocation
        // before we even attempt to read: bound it by what's actually left in the stream.
        if (length > in.available()) {
            throw new CorruptedEncryptedFileException("Declared length (" + length + ") exceeds remaining data");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }
}
