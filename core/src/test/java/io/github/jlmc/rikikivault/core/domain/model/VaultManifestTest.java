package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultManifestTest {

    private static ManifestEntry someEntry() {
        return new ManifestEntry("id-cv", "cv/CV.pdf", FileHash.of(new byte[]{1, 2, 3}), "RV02");
    }

    @Test
    void emptyHasVersion1AndNoFilesAndAGeneratedHmacKey() {
        VaultManifest manifest = VaultManifest.empty();

        assertEquals(1, manifest.version());
        assertTrue(manifest.files().isEmpty());
        assertEquals(VaultManifest.HMAC_KEY_LENGTH, manifest.hmacKey().length);
    }

    @Test
    void filesListIsDefensivelyCopied() {
        List<ManifestEntry> mutable = new ArrayList<>();
        mutable.add(someEntry());
        VaultManifest manifest = new VaultManifest(1, VaultManifest.generateHmacKey(), mutable);

        mutable.clear();

        assertEquals(1, manifest.files().size());
    }

    @Test
    void rejectsNullFiles() {
        assertThrows(NullPointerException.class, () -> new VaultManifest(1, VaultManifest.generateHmacKey(), null));
    }

    @Test
    void rejectsNullHmacKey() {
        assertThrows(NullPointerException.class, () -> new VaultManifest(1, null, List.of()));
    }

    @Test
    void rejectsWrongLengthHmacKey() {
        assertThrows(IllegalArgumentException.class, () -> new VaultManifest(1, new byte[]{1, 2, 3}, List.of()));
    }

    @Test
    void generateHmacKeyProducesDistinctKeys() {
        assertTrue(!java.util.Arrays.equals(VaultManifest.generateHmacKey(), VaultManifest.generateHmacKey()));
    }
}
