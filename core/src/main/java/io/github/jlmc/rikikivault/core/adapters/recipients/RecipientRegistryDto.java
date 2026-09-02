package io.github.jlmc.rikikivault.core.adapters.recipients;

import java.util.List;

/**
 * On-disk shape of recipients.json (Plan.md §4). Adapter-private (Jackson-bound) - kept separate
 * from {@link io.github.jlmc.rikikivault.core.domain.model.RecipientRegistry} so the domain model
 * stays free of any serialization concern.
 */
record RecipientRegistryDto(int version, List<RecipientDto> recipients) {
}
