package io.github.jlmc.rikikivault.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a {@code clear-local}: which files were deleted from {@code local/}, and which
 * were left untouched because they have unpublished changes (never published, or edited locally
 * since the last publish) - the only two ways plaintext leaves this machine irrecoverably, so they
 * default to being kept unless the caller explicitly opts in via {@code includeUnpublished}.
 */
public record ClearLocalFilesResult(List<String> clearedPaths, List<String> unpublishedPaths) {

    public ClearLocalFilesResult {
        Objects.requireNonNull(clearedPaths, "clearedPaths must not be null");
        Objects.requireNonNull(unpublishedPaths, "unpublishedPaths must not be null");
        clearedPaths = List.copyOf(clearedPaths);
        unpublishedPaths = List.copyOf(unpublishedPaths);
    }
}
