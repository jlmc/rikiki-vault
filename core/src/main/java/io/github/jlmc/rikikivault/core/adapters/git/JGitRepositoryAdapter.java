package io.github.jlmc.rikikivault.core.adapters.git;

import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.domain.exception.GitOperationException;
import io.github.jlmc.rikikivault.core.domain.model.GitStatus;
import io.github.jlmc.rikikivault.core.domain.model.RemoteSyncStatus;
import io.github.jlmc.rikikivault.core.ports.out.GitAuthSettingsPort;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Wraps JGit over a single repository root Path, mirroring the constructor-injected-path style of
 * {@link io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter}. Each method
 * opens its own short-lived {@link Git} handle rather than holding one open for the adapter's
 * lifetime — {@link #clone(String)} in particular targets a directory that isn't a repository yet,
 * so there is nothing to hold open across the adapter's life anyway.
 */
public final class JGitRepositoryAdapter implements GitRepositoryPort {

    private static final String GITHUB_TOKEN_ENV_VAR = "RIKIKI_VAULT_GITHUB_TOKEN";

    private final Path root;
    private final GitAuthSettingsPort gitAuthSettingsPort;

    public JGitRepositoryAdapter(Path root, GitAuthSettingsPort gitAuthSettingsPort) {
        Objects.requireNonNull(root, "root must not be null");
        this.root = root.toAbsolutePath().normalize();
        this.gitAuthSettingsPort = Objects.requireNonNull(gitAuthSettingsPort, "gitAuthSettingsPort must not be null");
    }

    @Override
    public void init() {
        try (Git ignored = Git.init().setDirectory(root.toFile()).call()) {
            // repository created; nothing else to do
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to initialize a Git repository at " + root, e);
        }
    }

    @Override
    public void addRemote(String name, String url) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(url, "url must not be null");
        try (Git git = openGit()) {
            git.remoteAdd().setName(name).setUri(new URIish(url)).call();
        } catch (GitAPIException | URISyntaxException e) {
            throw new GitOperationException("Failed to add remote '" + name + "' (" + url + ") in " + root, e);
        }
    }

    @Override
    public void clone(String remoteUri) {
        Objects.requireNonNull(remoteUri, "remoteUri must not be null");
        try (Git ignored = Git.cloneRepository()
                .setURI(remoteUri)
                .setDirectory(root.toFile())
                .setCredentialsProvider(resolveCredentials())
                .setTransportConfigCallback(resolveSshTransportConfigCallback())
                .call()) {
            // repository cloned; nothing else to do
        } catch (GitAPIException | JGitInternalException e) {
            throw new GitOperationException("Failed to clone into " + root, e);
        }
    }

    @Override
    public void pull() {
        try (Git git = openGit()) {
            PullResult result = git.pull()
                    .setCredentialsProvider(resolveCredentials())
                    .setTransportConfigCallback(resolveSshTransportConfigCallback())
                    .call();
            if (!result.isSuccessful()) {
                throw new GitOperationException("git pull did not complete successfully in " + root);
            }
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to pull in " + root, e);
        }
    }

    @Override
    public boolean hasRemote() {
        try (Git git = openGit()) {
            return !git.remoteList().call().isEmpty();
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to read remotes of " + root, e);
        }
    }

    @Override
    public RemoteSyncStatus remoteSyncStatus() {
        try (Git git = openGit()) {
            if (git.remoteList().call().isEmpty()) {
                return RemoteSyncStatus.noRemote();
            }
            git.fetch()
                    .setCredentialsProvider(resolveCredentials())
                    .setTransportConfigCallback(resolveSshTransportConfigCallback())
                    .call();
            String branch = git.getRepository().getBranch();
            BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(git.getRepository(), branch);
            if (trackingStatus == null) {
                return new RemoteSyncStatus(true, 0, 0);
            }
            return new RemoteSyncStatus(true, trackingStatus.getAheadCount(), trackingStatus.getBehindCount());
        } catch (GitAPIException | IOException e) {
            throw new GitOperationException("Failed to check remote status of " + root, e);
        }
    }

    @Override
    public GitStatus status() {
        try (Git git = openGit()) {
            Status status = git.status().call();
            return new GitStatus(
                    status.getAdded(),
                    status.getChanged(),
                    status.getRemoved(),
                    status.getModified(),
                    status.getMissing(),
                    status.getUntracked(),
                    status.getConflicting()
            );
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to read status of " + root, e);
        }
    }

    @Override
    public void add(List<String> relativePaths) {
        Objects.requireNonNull(relativePaths, "relativePaths must not be null");
        try (Git git = openGit()) {
            AddCommand addCommand = git.add();
            relativePaths.forEach(addCommand::addFilepattern);
            addCommand.call();
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to stage files in " + root, e);
        }
    }

    @Override
    public void commit(String message) {
        Objects.requireNonNull(message, "message must not be null");
        try (Git git = openGit()) {
            git.commit().setMessage(message).call();
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to commit in " + root, e);
        }
    }

    @Override
    public boolean push() {
        // Does not inspect per-ref PushResult status (e.g. rejected non-fast-forward updates) —
        // that level of scrutiny is deferred to Phase 8 (Hardening), same spirit as the symlink
        // caveat left on LocalFileSystemAdapter in Milestone 2.
        try (Git git = openGit()) {
            if (git.remoteList().call().isEmpty()) {
                return false;
            }
            git.push()
                    .setCredentialsProvider(resolveCredentials())
                    .setTransportConfigCallback(resolveSshTransportConfigCallback())
                    .call();
            return true;
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to push from " + root, e);
        }
    }

    @Override
    public String diff() {
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            ObjectId headTree = repository.resolve("HEAD^{tree}");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(repository);
                formatter.format(treeIteratorFor(repository, headTree), new FileTreeIterator(repository));
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GitOperationException("Failed to diff working tree against HEAD in " + root, e);
        }
    }

    private Git openGit() {
        try {
            return Git.open(root.toFile());
        } catch (IOException e) {
            throw new GitOperationException("No Git repository found at " + root, e);
        }
    }

    private static AbstractTreeIterator treeIteratorFor(Repository repository, ObjectId treeId) throws IOException {
        if (treeId == null) {
            return new EmptyTreeIterator();
        }
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, treeId);
        }
        return treeParser;
    }

    /**
     * Resolves GitHub credentials for HTTPS remotes: an explicit token configured in the app
     * takes priority, falling back to the {@code RIKIKI_VAULT_GITHUB_TOKEN} env var
     * for backward compatibility. Absent either, {@code null} is returned, which is
     * correct both for local {@code file://} remotes (used in tests) and for {@code ssh://}/
     * {@code git@} remotes, which never use this provider at all. Package-private so
     * {@code JGitRepositoryAdapterTest} can verify the token-priority rule without any real
     * network call.
     */
    CredentialsProvider resolveCredentials() {
        GitAuthSettings settings = gitAuthSettingsPort.load();
        if (settings.activeType() == GitAuthType.TOKEN
                && settings.githubToken() != null && !settings.githubToken().isBlank()) {
            return new UsernamePasswordCredentialsProvider(settings.githubToken(), "");
        }
        if (settings.activeType() == GitAuthType.HTTP_BASIC
                && settings.httpUsername() != null && !settings.httpUsername().isBlank()) {
            String password = settings.httpPassword() != null ? settings.httpPassword() : "";
            return new UsernamePasswordCredentialsProvider(settings.httpUsername(), password);
        }
        String envToken = System.getenv(GITHUB_TOKEN_ENV_VAR);
        if (envToken == null || envToken.isBlank()) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(envToken, "");
    }

    /**
     * When the app has an explicit SSH key configured (Milestone 13), forces every SSH transport
     * to use exactly that identity file instead of relying entirely on implicit discovery
     * (default-named keys / {@code ~/.ssh/config} / the running SSH agent) - useful when the key
     * has a non-standard name and automatic discovery doesn't find it. A configured key that's
     * agent-protected still works: only the default identity *file* list is overridden below, the
     * base class's own agent support is untouched. Without a configured key, this is a no-op and
     * today's fully automatic behavior is unchanged.
     */
    private TransportConfigCallback resolveSshTransportConfigCallback() {
        GitAuthSettings settings = gitAuthSettingsPort.load();
        if (settings.activeType() != GitAuthType.SSH || settings.sshPrivateKeyPath() == null) {
            return transport -> {
            };
        }
        SshdSessionFactory sessionFactory = new FixedIdentitySshdSessionFactory(settings.sshPrivateKeyPath());
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                sshTransport.setSshSessionFactory(sessionFactory);
            }
        };
    }

    private static final class FixedIdentitySshdSessionFactory extends SshdSessionFactory {
        private final Path identity;

        FixedIdentitySshdSessionFactory(Path identity) {
            this.identity = identity;
        }

        @Override
        protected List<Path> getDefaultIdentities(File sshDir) {
            return List.of(identity);
        }
    }
}
