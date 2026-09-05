package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.ClearLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.ClearLocalFilesCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearLocalFilesServiceTest {

    @Test
    void anUnchangedFileIsAlwaysCleared() {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("notes.md", "published content");
        FakeScanChangesUseCase scanChangesUseCase = new FakeScanChangesUseCase();
        ClearLocalFilesService service = new ClearLocalFilesService(scanChangesUseCase, localFiles);

        ClearLocalFilesResult result = service.clear(new ClearLocalFilesCommand(false));

        assertEquals(List.of("notes.md"), result.clearedPaths());
        assertTrue(result.unpublishedPaths().isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void withoutIncludeUnpublishedAnAddedFileIsKept() {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("new.txt", "never published");
        FakeScanChangesUseCase scanChangesUseCase = new FakeScanChangesUseCase()
                .withChanges(new VaultChange(VaultChange.ChangeType.ADDED, "new.txt"));
        ClearLocalFilesService service = new ClearLocalFilesService(scanChangesUseCase, localFiles);

        ClearLocalFilesResult result = service.clear(new ClearLocalFilesCommand(false));

        assertTrue(result.clearedPaths().isEmpty());
        assertEquals(List.of("new.txt"), result.unpublishedPaths());
        assertEquals("never published", new String(localFiles.readFile("new.txt")));
    }

    @Test
    void withoutIncludeUnpublishedAModifiedFileIsKept() {
        FakeFileStoragePort localFiles = new FakeFileStoragePort().withFile("notes.md", "unpublished edit");
        FakeScanChangesUseCase scanChangesUseCase = new FakeScanChangesUseCase()
                .withChanges(new VaultChange(VaultChange.ChangeType.MODIFIED, "notes.md"));
        ClearLocalFilesService service = new ClearLocalFilesService(scanChangesUseCase, localFiles);

        ClearLocalFilesResult result = service.clear(new ClearLocalFilesCommand(false));

        assertTrue(result.clearedPaths().isEmpty());
        assertEquals(List.of("notes.md"), result.unpublishedPaths());
    }

    @Test
    void includeUnpublishedClearsEverythingRegardless() {
        FakeFileStoragePort localFiles = new FakeFileStoragePort()
                .withFile("new.txt", "never published")
                .withFile("notes.md", "unchanged");
        FakeScanChangesUseCase scanChangesUseCase = new FakeScanChangesUseCase()
                .withChanges(new VaultChange(VaultChange.ChangeType.ADDED, "new.txt"));
        ClearLocalFilesService service = new ClearLocalFilesService(scanChangesUseCase, localFiles);

        ClearLocalFilesResult result = service.clear(new ClearLocalFilesCommand(true));

        assertEquals(List.of("new.txt", "notes.md"), result.clearedPaths());
        assertTrue(result.unpublishedPaths().isEmpty());
        assertTrue(localFiles.listFiles().isEmpty());
    }

    @Test
    void anEmptyLocalFolderClearsNothing() {
        FakeFileStoragePort localFiles = new FakeFileStoragePort();
        FakeScanChangesUseCase scanChangesUseCase = new FakeScanChangesUseCase();
        ClearLocalFilesService service = new ClearLocalFilesService(scanChangesUseCase, localFiles);

        ClearLocalFilesResult result = service.clear(new ClearLocalFilesCommand(false));

        assertTrue(result.clearedPaths().isEmpty());
        assertTrue(result.unpublishedPaths().isEmpty());
    }
}
