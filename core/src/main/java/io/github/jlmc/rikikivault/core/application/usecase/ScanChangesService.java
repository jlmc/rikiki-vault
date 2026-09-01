package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.ManifestEntry;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange.ChangeType;
import io.github.jlmc.rikikivault.core.domain.model.VaultManifest;
import io.github.jlmc.rikikivault.core.ports.in.ScanChangesUseCase;
import io.github.jlmc.rikikivault.core.ports.out.FileStoragePort;
import io.github.jlmc.rikikivault.core.ports.out.HashPort;
import io.github.jlmc.rikikivault.core.ports.out.ManifestPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ScanChangesService implements ScanChangesUseCase {

    private final FileStoragePort fileStoragePort;
    private final HashPort hashPort;
    private final ManifestPort manifestPort;

    public ScanChangesService(FileStoragePort fileStoragePort, HashPort hashPort, ManifestPort manifestPort) {
        this.fileStoragePort = Objects.requireNonNull(fileStoragePort, "fileStoragePort must not be null");
        this.hashPort = Objects.requireNonNull(hashPort, "hashPort must not be null");
        this.manifestPort = Objects.requireNonNull(manifestPort, "manifestPort must not be null");
    }

    @Override
    public List<VaultChange> scan() {
        VaultManifest manifest = manifestPort.load();
        Map<String, ManifestEntry> byPlaintextPath = manifest.files().stream()
                .collect(Collectors.toMap(ManifestEntry::plaintextPath, entry -> entry));

        List<VaultChange> changes = new ArrayList<>();
        Set<String> discovered = new HashSet<>();

        for (String path : fileStoragePort.listFiles()) {
            discovered.add(path);
            ManifestEntry entry = byPlaintextPath.get(path);
            if (entry == null) {
                changes.add(new VaultChange(ChangeType.ADDED, path));
            } else if (!hashPort.hash(fileStoragePort.readFile(path)).equals(entry.hash())) {
                changes.add(new VaultChange(ChangeType.MODIFIED, path));
            }
            // matching hash -> unchanged, not reported (mirrors the §27 CLI status example,
            // which only ever lists Modified/Added/Deleted lines)
        }

        for (ManifestEntry entry : manifest.files()) {
            if (!discovered.contains(entry.plaintextPath())) {
                changes.add(new VaultChange(ChangeType.DELETED, entry.plaintextPath()));
            }
        }

        return changes;
    }
}
