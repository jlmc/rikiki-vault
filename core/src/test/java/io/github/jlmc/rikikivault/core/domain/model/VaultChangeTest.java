package io.github.jlmc.rikikivault.core.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultChangeTest {

    @Test
    void rejectsBlankPath() {
        assertThrows(IllegalArgumentException.class, () -> new VaultChange(VaultChange.ChangeType.ADDED, " "));
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class, () -> new VaultChange(null, "cv/CV.pdf"));
    }
}
