package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.ClearLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.ClearLocalFilesCommand;
import io.github.jlmc.rikikivault.core.ports.in.ClearLocalFilesUseCase;
import io.github.jlmc.rikikivault.core.ports.in.ScanChangesUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deletes plaintext from {@code local/} - the inverse of {@link RestoreLocalFilesService}. Purely
 * local, no Git/network involved. A file is only ever deleted here if it can be brought back
 * byte-for-byte via {@code restore} - anything {@link VaultChange.ChangeType#ADDED} (never
 * published) or {@link VaultChange.ChangeType#MODIFIED} (edited locally since the last publish) is
 * left untouched unless the caller explicitly opts in via {@link ClearLocalFilesCommand#includeUnpublished()}.
 */
public final class ClearLocalFilesService implements ClearLocalFilesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClearLocalFilesService.class);

    private final ScanChangesUseCase scanChangesUseCase;
    private final FileStoragePort localFiles;

    public ClearLocalFilesService(ScanChangesUseCase scanChangesUseCase, FileStoragePort localFiles) {
        this.scanChangesUseCase = Objects.requireNonNull(scanChangesUseCase, "scanChangesUseCase must not be null");
        this.localFiles = Objects.requireNonNull(localFiles, "localFiles must not be null");
    }

    @Override
    public ClearLocalFilesResult clear(ClearLocalFilesCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Set<String> unpublished = scanChangesUseCase.scan().stream()
                .filter(change -> change.type() == VaultChange.ChangeType.ADDED || change.type() == VaultChange.ChangeType.MODIFIED)
                .map(VaultChange::path)
                .collect(Collectors.toSet());

        List<String> cleared = new ArrayList<>();
        List<String> skippedUnpublished = new ArrayList<>();

        for (String path : localFiles.listFiles()) {
            if (unpublished.contains(path) && !command.includeUnpublished()) {
                skippedUnpublished.add(path);
                continue;
            }
            localFiles.deleteFile(path);
            cleared.add(path);
        }

        log.info("Clear local completed: {} cleared, {} kept (unpublished)", cleared.size(), skippedUnpublished.size());
        return new ClearLocalFilesResult(cleared, skippedUnpublished);
    }
}
