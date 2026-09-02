package io.github.jlmc.rikikivault.core.domain.model;

import java.util.List;
import java.util.Objects;

public record RecipientRegistry(int version, List<Recipient> recipients) {

    public RecipientRegistry {
        Objects.requireNonNull(recipients, "recipients must not be null");
        recipients = List.copyOf(recipients);
    }

    public static RecipientRegistry empty() {
        return new RecipientRegistry(1, List.of());
    }
}
