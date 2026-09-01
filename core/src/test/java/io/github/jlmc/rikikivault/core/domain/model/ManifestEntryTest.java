package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestEntryTest {

    private static final FileHash SOME_HASH = FileHash.of(new byte[]{1, 2, 3});

    @Test
    void rejectsBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestEntry("", "cv/CV.pdf", SOME_HASH, "RV01"));
    }

    @Test
    void rejectsBlankPlaintextPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestEntry("cv/CV.pdf.enc", " ", SOME_HASH, "RV01"));
    }

    @Test
    void rejectsBlankFormatVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestEntry("cv/CV.pdf.enc", "cv/CV.pdf", SOME_HASH, ""));
    }

    @Test
    void rejectsNullHash() {
        assertThrows(NullPointerException.class,
                () -> new ManifestEntry("cv/CV.pdf.enc", "cv/CV.pdf", null, "RV01"));
    }
}
