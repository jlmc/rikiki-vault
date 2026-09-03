package io.github.jlmc.rikikivault.core.adapters.git;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.domain.exception.GitOperationException;
import io.github.jlmc.rikikivault.core.domain.model.GitStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JGitRepositoryAdapterTest {

    @Test
    void initCreatesAGitDirectoryAndAnEmptyRepoIsClean(@TempDir Path root) {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());

        adapter.init();

        assertTrue(Files.isDirectory(root.resolve(".git")));
        assertTrue(adapter.status().isClean());
    }

    @Test
    void addRemoteRegistersTheRemoteUnderTheGivenName(@TempDir Path root) throws Exception {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());
        adapter.init();

        adapter.addRemote("origin", "file:///some/remote.git");

        try (Git git = Git.open(root.toFile())) {
            List<org.eclipse.jgit.transport.RemoteConfig> remotes = git.remoteList().call();
            assertEquals(1, remotes.size());
            assertEquals("origin", remotes.get(0).getName());
            assertEquals("file:///some/remote.git", remotes.get(0).getURIs().get(0).toString());
        }
    }

    @Test
    void addRemoteOnADirectoryThatWasNeverInitializedFails(@TempDir Path root) {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());

        assertThrows(GitOperationException.class, () -> adapter.addRemote("origin", "file:///some/remote.git"));
    }

    @Test
    void addThenCommitMovesAFileFromUntrackedToCleanHistory(@TempDir Path root) throws IOException {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());
        adapter.init();
        Files.writeString(root.resolve("a.txt"), "hello");

        GitStatus beforeAdd = adapter.status();
        assertEquals(java.util.Set.of("a.txt"), beforeAdd.untracked());

        adapter.add(List.of("a.txt"));
        GitStatus afterAdd = adapter.status();
        assertEquals(java.util.Set.of("a.txt"), afterAdd.added());

        adapter.commit("add a.txt");
        assertTrue(adapter.status().isClean());
    }

    @Test
    void cloneRecreatesTheCommittedFileInTheTargetDirectory(@TempDir Path bareRepoDir, @TempDir Path cloneDir) throws Exception {
        seedBareRepoWithOneCommit(bareRepoDir, "cv.pdf", "cv content");

        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(cloneDir.resolve("clone"), new FakeGitAuthSettingsPort());
        adapter.clone("file://" + bareRepoDir);

        assertEquals("cv content", Files.readString(cloneDir.resolve("clone").resolve("cv.pdf")));
    }

    @Test
    void cloneIntoANonEmptyDirectoryFails(@TempDir Path bareRepoDir, @TempDir Path target) throws Exception {
        seedBareRepoWithOneCommit(bareRepoDir, "cv.pdf", "cv content");
        Files.writeString(target.resolve("already-here.txt"), "pre-existing");

        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(target, new FakeGitAuthSettingsPort());

        assertThrows(GitOperationException.class, () -> adapter.clone("file://" + bareRepoDir));
    }

    @Test
    void pushThenPullPropagatesChangesBetweenTwoClones(@TempDir Path bareRepoDir, @TempDir Path workDirs) throws Exception {
        seedBareRepoWithOneCommit(bareRepoDir, "notes.md", "original");
        Path dirA = workDirs.resolve("a");
        Path dirB = workDirs.resolve("b");
        JGitRepositoryAdapter adapterA = new JGitRepositoryAdapter(dirA, new FakeGitAuthSettingsPort());
        JGitRepositoryAdapter adapterB = new JGitRepositoryAdapter(dirB, new FakeGitAuthSettingsPort());
        adapterA.clone("file://" + bareRepoDir);
        adapterB.clone("file://" + bareRepoDir);

        Files.writeString(dirA.resolve("new.txt"), "added from A");
        adapterA.add(List.of("new.txt"));
        adapterA.commit("add new.txt");
        assertTrue(adapterA.push(), "push deveria devolver true quando há um remoto configurado");

        adapterB.pull();

        assertEquals("added from A", Files.readString(dirB.resolve("new.txt")));
    }

    @Test
    void isRemoteAheadIsFalseWithNoRemoteConfigured(@TempDir Path root) {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());
        adapter.init();

        assertFalse(adapter.isRemoteAhead());
    }

    @Test
    void isRemoteAheadIsFalseWhenNothingNewWasPushed(@TempDir Path bareRepoDir, @TempDir Path workDirs) throws Exception {
        seedBareRepoWithOneCommit(bareRepoDir, "notes.md", "original");
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(workDirs.resolve("a"), new FakeGitAuthSettingsPort());
        adapter.clone("file://" + bareRepoDir);

        assertFalse(adapter.isRemoteAhead());
    }

    @Test
    void isRemoteAheadIsTrueAfterAnotherCloneHasPushedAndThisOneHasNotPulled(
            @TempDir Path bareRepoDir, @TempDir Path workDirs) throws Exception {
        seedBareRepoWithOneCommit(bareRepoDir, "notes.md", "original");
        Path dirA = workDirs.resolve("a");
        Path dirB = workDirs.resolve("b");
        JGitRepositoryAdapter adapterA = new JGitRepositoryAdapter(dirA, new FakeGitAuthSettingsPort());
        JGitRepositoryAdapter adapterB = new JGitRepositoryAdapter(dirB, new FakeGitAuthSettingsPort());
        adapterA.clone("file://" + bareRepoDir);
        adapterB.clone("file://" + bareRepoDir);

        Files.writeString(dirA.resolve("new.txt"), "added from A");
        adapterA.add(List.of("new.txt"));
        adapterA.commit("add new.txt");
        adapterA.push();

        assertTrue(adapterB.isRemoteAhead(), "B has not pulled A's new commit yet");

        adapterB.pull();

        assertFalse(adapterB.isRemoteAhead(), "after pulling, B is caught up");
    }

    @Test
    void pushOnARepositoryWithNoRemoteIsSkippedInsteadOfFailing(@TempDir Path root) throws IOException {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());
        adapter.init();
        Files.writeString(root.resolve("a.txt"), "hello");
        adapter.add(List.of("a.txt"));
        adapter.commit("add a.txt");

        assertFalse(adapter.push(), "push sem nenhum remoto configurado deve ser ignorado, não falhar");
    }

    @Test
    void diffReportsAModifiedTrackedFile(@TempDir Path root) throws IOException {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());
        adapter.init();
        Files.writeString(root.resolve("notes.md"), "original content");
        adapter.add(List.of("notes.md"));
        adapter.commit("initial");

        Files.writeString(root.resolve("notes.md"), "changed content", StandardCharsets.UTF_8);

        String diff = adapter.diff();

        assertTrue(diff.contains("-original content"));
        assertTrue(diff.contains("+changed content"));
    }

    @Test
    void diffOnARepoWithNoCommitsYetReflectsTheUntrackedFileAsNew(@TempDir Path root) throws IOException {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());
        adapter.init();
        Files.writeString(root.resolve("new.txt"), "brand new");

        String diff = adapter.diff();

        assertTrue(diff.contains("new.txt"));
    }

    @Test
    void statusOnADirectoryThatWasNeverInitializedFails(@TempDir Path root) {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());

        assertThrows(GitOperationException.class, adapter::status);
    }

    @Test
    void commitOnADirectoryThatWasNeverInitializedFails(@TempDir Path root) {
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, new FakeGitAuthSettingsPort());

        assertThrows(GitOperationException.class, () -> adapter.commit("message"));
    }

    @Test
    void resolveCredentialsPrefersTheConfiguredTokenAsUsername(@TempDir Path root) throws Exception {
        FakeGitAuthSettingsPort authSettings = new FakeGitAuthSettingsPort();
        authSettings.save(new GitAuthSettings(null, "configured-token"));
        JGitRepositoryAdapter adapter = new JGitRepositoryAdapter(root, authSettings);

        CredentialsProvider provider = adapter.resolveCredentials();

        CredentialItem.Username usernameItem = new CredentialItem.Username();
        CredentialItem.Password passwordItem = new CredentialItem.Password();
        provider.get(new URIish("https://github.com/example/repo.git"), usernameItem, passwordItem);

        assertEquals("configured-token", usernameItem.getValue());
        assertEquals("", new String(passwordItem.getValue()));
    }

    private static void seedBareRepoWithOneCommit(Path bareRepoDir, String fileName, String content) throws Exception {
        try (Git bare = Git.init().setDirectory(bareRepoDir.toFile()).setBare(true).call()) {
            Path scratch = Files.createTempDirectory("rikiki-vault-seed");
            try (Git scratchGit = Git.cloneRepository()
                    .setURI("file://" + bareRepoDir)
                    .setDirectory(scratch.toFile())
                    .call()) {
                Files.writeString(scratch.resolve(fileName), content);
                scratchGit.add().addFilepattern(fileName).call();
                scratchGit.commit().setMessage("seed").call();
                scratchGit.push().call();
            }
        }
    }
}
