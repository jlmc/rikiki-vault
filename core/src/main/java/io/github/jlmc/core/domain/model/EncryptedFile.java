package io.github.jlmc.core.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record EncryptedFile(
        String formatVersion,
        int symmetricAlgorithmId,
        int keyWrapAlgorithmId,
        String originalFileName,
        List<RecipientKeyEntry> recipientEntries,
        byte[] contentNonce,
        byte[] sealedContent
) {

    public EncryptedFile {
        Objects.requireNonNull(formatVersion, "formatVersion must not be null");
        Objects.requireNonNull(originalFileName, "originalFileName must not be null");
        Objects.requireNonNull(recipientEntries, "recipientEntries must not be null");
        Objects.requireNonNull(contentNonce, "contentNonce must not be null");
        Objects.requireNonNull(sealedContent, "sealedContent must not be null");
        recipientEntries = List.copyOf(recipientEntries);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncryptedFile other)) return false;
        return symmetricAlgorithmId == other.symmetricAlgorithmId
                && keyWrapAlgorithmId == other.keyWrapAlgorithmId
                && formatVersion.equals(other.formatVersion)
                && originalFileName.equals(other.originalFileName)
                && recipientEntries.equals(other.recipientEntries)
                && Arrays.equals(contentNonce, other.contentNonce)
                && Arrays.equals(sealedContent, other.sealedContent);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(formatVersion, symmetricAlgorithmId, keyWrapAlgorithmId, originalFileName, recipientEntries);
        result = 31 * result + Arrays.hashCode(contentNonce);
        result = 31 * result + Arrays.hashCode(sealedContent);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptedFile[formatVersion=" + formatVersion
                + ", symmetricAlgorithmId=" + symmetricAlgorithmId
                + ", keyWrapAlgorithmId=" + keyWrapAlgorithmId
                + ", originalFileName=" + originalFileName
                + ", recipients=" + recipientEntries.size()
                + ", sealedContentLength=" + sealedContent.length + "]";
    }
}
