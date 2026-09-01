package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultManifestTest {

    private static ManifestEntry someEntry() {
        return new ManifestEntry("cv/CV.pdf.enc", "cv/CV.pdf", FileHash.of(new byte[]{1, 2, 3}), "RV01");
    }

    @Test
    void emptyHasVersion1AndNoFiles() {
        VaultManifest manifest = VaultManifest.empty();

        assertEquals(1, manifest.version());
        assertTrue(manifest.files().isEmpty());
    }

    @Test
    void filesListIsDefensivelyCopied() {
        List<ManifestEntry> mutable = new ArrayList<>();
        mutable.add(someEntry());
        VaultManifest manifest = new VaultManifest(1, mutable);

        mutable.clear();

        assertEquals(1, manifest.files().size());
    }

    @Test
    void rejectsNullFiles() {
        assertThrows(NullPointerException.class, () -> new VaultManifest(1, null));
    }
}
