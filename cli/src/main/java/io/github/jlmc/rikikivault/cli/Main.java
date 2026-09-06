package io.github.jlmc.rikikivault.cli;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalGitAuthSettingsAdapter;
import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.format.PrivateKeyEnvelopeCodec;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.PassphraseCachingKeyStorePort;
import io.github.jlmc.rikikivault.core.adapters.keystore.PrivateKeyEnvelopeCrypto;
import io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.ClearLocalFilesService;
import io.github.jlmc.rikikivault.core.application.usecase.CloneVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.RestoreLocalFilesService;
import io.github.jlmc.rikikivault.core.application.usecase.RevokeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.domain.exception.InvalidPassphraseException;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.core.domain.model.ClearLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.RemoteSyncStatus;
import io.github.jlmc.rikikivault.core.domain.model.RestoreLocalFilesResult;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.ClearLocalFilesCommand;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.RestoreLocalFilesCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public final class Main {

    private static final String KEY_ALGORITHM = "X25519";

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (RikikiVaultException e) {
            System.err.println(CliMessages.get("main.error", fullMessage(e)));
            System.exit(1);
        } catch (RuntimeException e) {
            // Anything not already a RikikiVaultException (e.g. an UncheckedIOException from a
            // filesystem-level failure) still gets the same curated format instead of the JVM's
            // default uncaught-exception stack trace.
            System.err.println(CliMessages.get("main.error", fullMessage(e)));
            System.exit(1);
        }
    }

    private static String fullMessage(Throwable error) {
        String topMessage = error.getMessage() != null ? error.getMessage() : error.toString();
        Throwable rootCause = error;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String rootMessage = rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.toString();
        return rootCause == error || rootMessage.equals(topMessage)
                ? topMessage
                : topMessage + " " + CliMessages.get("main.errorCause", rootMessage);
    }

    private static void run(String[] args) {
        stdinFallbackReader = null;
        if (args.length == 0) {
            printUsage();
            System.exit(1);
            return;
        }

        Path vaultRoot;
        String command;
        String[] rest;
        if (args[0].equals("-C")) {
            if (args.length < 3) {
                printUsage();
                System.exit(1);
                return;
            }
            vaultRoot = Path.of(args[1]).toAbsolutePath();
            command = args[2];
            rest = Arrays.copyOfRange(args, 3, args.length);
        } else {
            vaultRoot = Path.of("").toAbsolutePath();
            command = args[0];
            rest = Arrays.copyOfRange(args, 1, args.length);
        }

        VaultContext ctx = VaultContext.at(vaultRoot);
        if (COMMANDS_NEEDING_IDENTITY.contains(command)) {
            ctx = withUnlockedKeyStore(ctx);
        }

        switch (command) {
            case "init" -> runInit(ctx, rest);
            case "whoami" -> runWhoami(ctx);
            case "export-key" -> runExportKey(ctx, rest);
            case "clone" -> runClone(ctx, rest);
            case "status" -> runStatus(ctx);
            case "publish" -> runPublish(ctx, rest);
            case "pull" -> runPull(ctx);
            case "restore" -> runRestore(ctx, rest);
            case "clear-local" -> runClearLocal(ctx, rest);
            case "authorize" -> runAuthorize(ctx, rest);
            case "revoke" -> runRevoke(ctx, rest);
            case "git-auth" -> runGitAuth(ctx, rest);
            case "set-passphrase" -> runSetPassphrase(ctx);
            case "remove-passphrase" -> runRemovePassphrase(ctx);
            case "unwrap-key" -> runUnwrapKey(rest);
            default -> {
                System.err.println(CliMessages.get("run.unknownCommand", command));
                printUsage();
                System.exit(1);
            }
        }
    }

    /**
     * Only these commands ever touch the private key - gating the passphrase prompt to just them
     * means a protected identity never adds friction to git-auth/status/publish/authorize/revoke,
     * which don't need it.
     */
    private static final Set<String> COMMANDS_NEEDING_IDENTITY = Set.of(
            "init", "whoami", "export-key", "clone", "pull", "restore");

    /**
     * If the stored identity is passphrase-protected, prompts once (with retries) and returns a
     * {@code ctx} whose {@link KeyStorePort} transparently supplies that passphrase for the rest
     * of this process - every existing use-case service still just calls the no-argument
     * {@code load()}. A no-op, with zero console interaction, when the identity isn't protected.
     */
    private static VaultContext withUnlockedKeyStore(VaultContext ctx) {
        if (!ctx.keyStorePort().isPassphraseProtected()) {
            return ctx;
        }
        char[] passphrase = readCurrentPassphraseWithRetries(ctx.keyStorePort(), 3);
        return ctx.withKeyStorePort(new PassphraseCachingKeyStorePort(ctx.keyStorePort(), passphrase));
    }

    private static void runInit(VaultContext ctx, String[] rest) {
        boolean initGit = false;
        String label = null;
        String remoteUri = null;
        for (int i = 0; i < rest.length; i++) {
            if (rest[i].equals("--git")) {
                initGit = true;
            } else if (rest[i].equals("--remote") && i + 1 < rest.length) {
                remoteUri = rest[++i];
            } else {
                label = rest[i];
            }
        }
        if (label == null) {
            System.err.println(CliMessages.get("init.usage"));
            System.exit(1);
            return;
        }
        if (remoteUri != null && !initGit) {
            System.err.println(CliMessages.get("init.remoteRequiresGit"));
            System.exit(1);
            return;
        }

        boolean hadNoIdentityBefore = !ctx.keyStorePort().exists();

        InitializeVaultService service = new InitializeVaultService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                new LocalFileSystemAdapter(ctx.vaultRoot()),
                ctx.manifestPort(),
                ctx.recipientRegistryPort(),
                ctx.gitRepositoryPort());

        MachineIdentity identity = service.initialize(new InitializeVaultCommand(initGit, label, remoteUri));

        IO.println(CliMessages.get("init.vaultInitializedIn", ctx.vaultRoot()));
        IO.println(CliMessages.get("init.identity", identity.id()));
        if (initGit) {
            IO.println(CliMessages.get("init.gitCreated"));
            if (remoteUri != null) {
                IO.println(CliMessages.get("init.remoteAssociated", remoteUri));
            } else {
                IO.println(CliMessages.get("init.remoteOptionalHint"));
                IO.println("  git -C " + ctx.vaultRoot() + " remote add origin <url>");
            }
        }
        if (hadNoIdentityBefore) {
            offerPassphraseAtCreation(ctx);
        }
    }

    private static void runWhoami(VaultContext ctx) {
        IdentityResolution resolution = loadOrCreateIdentity(ctx);
        IO.println(CliMessages.get("whoami.fingerprint", resolution.identity().id()));
        if (resolution.justCreated()) {
            IO.println(CliMessages.get("whoami.justGenerated"));
            IO.println(CliMessages.get("identity.justCreated.passphraseHint"));
        }
    }

    private static void runExportKey(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println(CliMessages.get("exportKey.usage"));
            System.exit(1);
            return;
        }
        Path outputFile = Path.of(rest[0]);
        IdentityResolution resolution = loadOrCreateIdentity(ctx);
        writePublicKeyFile(resolution.identity().publicKey(), outputFile);

        IO.println(CliMessages.get("exportKey.written", outputFile.toAbsolutePath()));
        IO.println(CliMessages.get("exportKey.fingerprint", resolution.identity().id()));
        IO.println(CliMessages.get("exportKey.hint"));
        if (resolution.justCreated()) {
            IO.println(CliMessages.get("identity.justCreated.passphraseHint"));
        }
    }

    private static void runStatus(VaultContext ctx) {
        ScanChangesService scanChangesService = new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort());
        List<VaultChange> changes = scanChangesService.scan();
        if (changes.isEmpty()) {
            IO.println(CliMessages.get("status.nothing"));
            return;
        }
        for (VaultChange change : changes) {
            String marker = switch (change.type()) {
                case ADDED -> "A ";
                case MODIFIED -> "M ";
                case DELETED -> "D ";
            };
            IO.println(marker + " " + change.path());
        }
    }

    private static void runClone(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println(CliMessages.get("clone.usage"));
            System.exit(1);
            return;
        }
        String remoteUri = rest[0];
        boolean hadNoIdentityBefore = !ctx.keyStorePort().exists();

        CloneVaultService service = new CloneVaultService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                new DecryptFileService(ctx.encryptionPort()),
                ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort());

        MachineIdentity identity = service.clone(new CloneVaultCommand(remoteUri));

        IO.println(CliMessages.get("clone.clonedIn", ctx.vaultRoot()));
        IO.println(CliMessages.get("clone.identity", identity.id()));
        if (hadNoIdentityBefore) {
            offerPassphraseAtCreation(ctx);
        }
    }

    private static void runPublish(VaultContext ctx, String[] rest) {
        String message = null;
        for (int i = 0; i < rest.length; i++) {
            if (rest[i].equals("-m") && i + 1 < rest.length) {
                message = rest[i + 1];
            }
        }
        if (message == null) {
            System.err.println(CliMessages.get("publish.usage"));
            System.exit(1);
            return;
        }

        ScanChangesService scanChangesService = new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort());
        List<VaultChange> changes = scanChangesService.scan();
        if (changes.isEmpty()) {
            reportNothingToCommit(ctx);
            return;
        }

        // Phase 1 - local only, never touches the network. Must succeed and be reported on its
        // own before anything remote is attempted, so a broken remote never hides a successful
        // local commit behind a fatal "Erro:" exit.
        PublishVaultService service = new PublishVaultService(
                ctx.localFiles(), ctx.documentsFiles(), ctx.encryptionPort(), ctx.hashPort(),
                ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort());
        service.publishLocally(new PublishVaultCommand(changes, message));
        IO.println(CliMessages.get("publish.published", changes.size()));

        if (!ctx.gitRepositoryPort().hasRemote()) {
            IO.println(CliMessages.get("publish.noRemote"));
            return;
        }
        try {
            service.pushToRemote();
        } catch (RikikiVaultException e) {
            IO.println(CliMessages.get("publish.pushWarning", fullMessage(e)));
        }
    }

    /**
     * Nothing new to encrypt doesn't mean there's nothing to push - a previous publish may have
     * committed locally but never reached the remote. Checking this means a fetch, so it's
     * best-effort: any failure degrades to the plain "nada para publicar" message instead of a
     * fatal error. No interactive confirmation here (same as the rest of the CLI) - a local-ahead
     * remote is pushed straight away.
     */
    private static void reportNothingToCommit(VaultContext ctx) {
        if (!ctx.gitRepositoryPort().hasRemote()) {
            IO.println(CliMessages.get("nothingToCommit.nothing"));
            return;
        }
        RemoteSyncStatus status;
        try {
            status = ctx.gitRepositoryPort().remoteSyncStatus();
        } catch (RikikiVaultException e) {
            IO.println(CliMessages.get("nothingToCommit.nothing"));
            return;
        }
        if (status.isDiverged()) {
            IO.println(CliMessages.get("nothingToCommit.diverged", status.aheadCount(), status.behindCount()));
        } else if (status.aheadCount() > 0) {
            IO.println(CliMessages.get("nothingToCommit.ahead", status.aheadCount()));
            try {
                ctx.gitRepositoryPort().push();
                IO.println(CliMessages.get("nothingToCommit.pushed"));
            } catch (RikikiVaultException e) {
                IO.println(CliMessages.get("publish.pushWarning", fullMessage(e)));
            }
        } else if (status.behindCount() > 0) {
            IO.println(CliMessages.get("nothingToCommit.behind"));
        } else {
            IO.println(CliMessages.get("nothingToCommit.nothing"));
        }
    }

    private static void runPull(VaultContext ctx) {
        PullVaultService service = new PullVaultService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new DecryptFileService(ctx.encryptionPort()),
                new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()),
                ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.gitRepositoryPort(), ctx.hashPort());

        PullResult result = service.pull();

        if (!result.uncommittedLocalChangesAtStart().isEmpty()) {
            IO.println(CliMessages.get("pull.uncommittedWarning"));
            for (VaultChange change : result.uncommittedLocalChangesAtStart()) {
                IO.println("  " + change.type() + " " + change.path());
            }
        }
        for (String path : result.updatedPaths()) {
            IO.println(CliMessages.get("pull.updated", path));
        }
        for (String path : result.deletedPaths()) {
            IO.println(CliMessages.get("pull.deleted", path));
        }
        for (VaultConflict conflict : result.conflicts()) {
            IO.println(CliMessages.get("pull.conflict", conflict.plaintextPath(), conflict.localChangeType(), conflict.remoteChangeType()));
            if (conflict.localHash() != null) {
                IO.println("  " + CliMessages.get("pull.conflict.localHash", conflict.localHash()));
            }
            if (conflict.remoteHash() != null) {
                IO.println("  " + CliMessages.get("pull.conflict.remoteHash", conflict.remoteHash()));
            }
        }
        if (result.updatedPaths().isEmpty() && result.deletedPaths().isEmpty() && !result.hasConflicts()) {
            IO.println(CliMessages.get("pull.upToDate"));
        }
    }

    private static void runRestore(VaultContext ctx, String[] rest) {
        boolean force = false;
        for (String arg : rest) {
            if (arg.equals("--force")) {
                force = true;
            }
        }

        RestoreLocalFilesService service = new RestoreLocalFilesService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new DecryptFileService(ctx.encryptionPort()),
                ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort());

        RestoreLocalFilesResult result = service.restore(new RestoreLocalFilesCommand(force));

        for (String path : result.restoredPaths()) {
            IO.println(CliMessages.get("restore.restored", path));
        }
        for (String path : result.unauthorizedPaths()) {
            IO.println(CliMessages.get("restore.unauthorized", path));
        }
        if (result.restoredPaths().isEmpty() && result.unauthorizedPaths().isEmpty()) {
            IO.println(CliMessages.get("restore.nothingToRestore"));
        }
        if (!result.skippedPaths().isEmpty() && !force) {
            IO.println(CliMessages.get("restore.skippedSummary", result.skippedPaths().size()));
        }
    }

    private static void runClearLocal(VaultContext ctx, String[] rest) {
        boolean includeUnpublished = false;
        for (String arg : rest) {
            if (arg.equals("--include-unpublished")) {
                includeUnpublished = true;
            }
        }

        ClearLocalFilesService service = new ClearLocalFilesService(
                new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort()), ctx.localFiles());

        ClearLocalFilesResult result = service.clear(new ClearLocalFilesCommand(includeUnpublished));

        for (String path : result.clearedPaths()) {
            IO.println(CliMessages.get("clearLocal.cleared", path));
        }
        if (result.clearedPaths().isEmpty()) {
            IO.println(CliMessages.get("clearLocal.nothingToClear"));
        }
        if (!result.unpublishedPaths().isEmpty() && !includeUnpublished) {
            IO.println(CliMessages.get("clearLocal.unpublishedSummary", result.unpublishedPaths().size()));
            for (String path : result.unpublishedPaths()) {
                IO.println("  " + path);
            }
        }
    }

    private static void runAuthorize(VaultContext ctx, String[] rest) {
        if (rest.length < 2) {
            System.err.println(CliMessages.get("authorize.usage"));
            System.exit(1);
            return;
        }
        String label = rest[0];
        PublicKey publicKey = readPublicKeyFile(Path.of(rest[1]));

        AuthorizeMachineService service = new AuthorizeMachineService(
                ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort());
        boolean pushed = service.authorize(new AuthorizeMachineCommand(label, publicKey));

        IO.println(CliMessages.get("authorize.done", label));
        if (!pushed) {
            IO.println(CliMessages.get("authorize.noRemote"));
        }
    }

    private static void runRevoke(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println(CliMessages.get("revoke.usage"));
            System.exit(1);
            return;
        }
        KeyFingerprint fingerprint = new KeyFingerprint(rest[0]);

        RevokeMachineService service = new RevokeMachineService(
                ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort());
        boolean pushed = service.revoke(new RevokeMachineCommand(fingerprint));

        IO.println(CliMessages.get("revoke.done", fingerprint));
        if (!pushed) {
            IO.println(CliMessages.get("revoke.noRemote"));
        }
    }

    private static void runGitAuth(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println(CliMessages.get("gitAuth.usage"));
            System.exit(1);
            return;
        }
        LocalGitAuthSettingsAdapter port = ctx.gitAuthSettingsPort();
        switch (rest[0]) {
            case "show" -> {
                GitAuthSettings settings = port.load();
                IO.println(CliMessages.get("gitAuth.activeType", describeType(settings.activeType())));
                String sshKeyText = settings.sshPrivateKeyPath() != null
                        ? settings.sshPrivateKeyPath().toString() : CliMessages.get("gitAuth.sshKey.none");
                IO.println(CliMessages.get("gitAuth.sshKey", sshKeyText) + activeSuffix(settings.activeType() == GitAuthType.SSH));
                boolean hasToken = settings.githubToken() != null && !settings.githubToken().isBlank();
                String tokenText = hasToken ? CliMessages.get("gitAuth.configured") : CliMessages.get("gitAuth.notConfigured");
                IO.println(CliMessages.get("gitAuth.tokenLabel", tokenText) + activeSuffix(settings.activeType() == GitAuthType.TOKEN));
                boolean hasHttpBasic = settings.httpUsername() != null && !settings.httpUsername().isBlank();
                String httpText = hasHttpBasic
                        ? CliMessages.get("gitAuth.httpConfigured", settings.httpUsername())
                        : CliMessages.get("gitAuth.notConfigured");
                IO.println(CliMessages.get("gitAuth.httpLabel", httpText) + activeSuffix(settings.activeType() == GitAuthType.HTTP_BASIC));
            }
            case "set-ssh-key" -> {
                if (rest.length < 2) {
                    System.err.println(CliMessages.get("gitAuth.setSshKey.usage"));
                    System.exit(1);
                    return;
                }
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(GitAuthType.SSH, Path.of(rest[1]), current.githubToken(),
                        current.httpUsername(), current.httpPassword()));
                IO.println(CliMessages.get("gitAuth.setSshKey.done"));
            }
            case "clear-ssh-key" -> {
                GitAuthSettings current = port.load();
                GitAuthType newType = current.activeType() == GitAuthType.SSH ? GitAuthType.NONE : current.activeType();
                port.save(new GitAuthSettings(newType, null, current.githubToken(), current.httpUsername(), current.httpPassword()));
                IO.println(CliMessages.get("gitAuth.clearSshKey.done"));
            }
            case "set-token" -> {
                // Reads from stdin, never from an argument - an argument would land in shell
                // history, exactly the mistake that prompted this command in the first place.
                String token = readSecretFromStdin(CliMessages.get("gitAuth.setToken.emptyStdin"));
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(GitAuthType.TOKEN, current.sshPrivateKeyPath(), token,
                        current.httpUsername(), current.httpPassword()));
                IO.println(CliMessages.get("gitAuth.setToken.done"));
            }
            case "clear-token" -> {
                GitAuthSettings current = port.load();
                GitAuthType newType = current.activeType() == GitAuthType.TOKEN ? GitAuthType.NONE : current.activeType();
                port.save(new GitAuthSettings(newType, current.sshPrivateKeyPath(), null, current.httpUsername(), current.httpPassword()));
                IO.println(CliMessages.get("gitAuth.clearToken.done"));
            }
            case "set-http-basic" -> {
                if (rest.length < 2) {
                    System.err.println(CliMessages.get("gitAuth.setHttpBasic.usage"));
                    System.exit(1);
                    return;
                }
                String password = readSecretFromStdin(CliMessages.get("gitAuth.setHttpBasic.emptyStdin"));
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(GitAuthType.HTTP_BASIC, current.sshPrivateKeyPath(), current.githubToken(),
                        rest[1], password));
                IO.println(CliMessages.get("gitAuth.setHttpBasic.done"));
            }
            case "clear-http-basic" -> {
                GitAuthSettings current = port.load();
                GitAuthType newType = current.activeType() == GitAuthType.HTTP_BASIC ? GitAuthType.NONE : current.activeType();
                port.save(new GitAuthSettings(newType, current.sshPrivateKeyPath(), current.githubToken(), null, null));
                IO.println(CliMessages.get("gitAuth.clearHttpBasic.done"));
            }
            case "use" -> {
                if (rest.length < 2) {
                    System.err.println(CliMessages.get("gitAuth.use.usage"));
                    System.exit(1);
                    return;
                }
                GitAuthSettings current = port.load();
                GitAuthType requested = parseRequestedType(rest[1]);
                if (requested == null) {
                    System.err.println(CliMessages.get("gitAuth.use.unknownMethod", rest[1]));
                    System.exit(1);
                    return;
                }
                if (requested == GitAuthType.SSH && current.sshPrivateKeyPath() == null) {
                    System.err.println(CliMessages.get("gitAuth.use.sshNotConfigured"));
                    System.exit(1);
                    return;
                }
                if (requested == GitAuthType.TOKEN && (current.githubToken() == null || current.githubToken().isBlank())) {
                    System.err.println(CliMessages.get("gitAuth.use.tokenNotConfigured"));
                    System.exit(1);
                    return;
                }
                if (requested == GitAuthType.HTTP_BASIC && (current.httpUsername() == null || current.httpUsername().isBlank())) {
                    System.err.println(CliMessages.get("gitAuth.use.httpNotConfigured"));
                    System.exit(1);
                    return;
                }
                port.save(new GitAuthSettings(requested, current.sshPrivateKeyPath(), current.githubToken(),
                        current.httpUsername(), current.httpPassword()));
                IO.println(CliMessages.get("gitAuth.activeType", describeType(requested)));
            }
            default -> {
                System.err.println(CliMessages.get("gitAuth.unknownSubcommand", rest[0]));
                System.exit(1);
            }
        }
    }

    private static String activeSuffix(boolean active) {
        return active ? " " + CliMessages.get("gitAuth.active") : "";
    }

    private static String describeType(GitAuthType type) {
        return switch (type) {
            case SSH -> CliMessages.get("gitAuth.type.ssh");
            case TOKEN -> CliMessages.get("gitAuth.type.token");
            case HTTP_BASIC -> CliMessages.get("gitAuth.type.http");
            case NONE -> CliMessages.get("gitAuth.type.none");
        };
    }

    private static GitAuthType parseRequestedType(String raw) {
        return switch (raw) {
            case "ssh" -> GitAuthType.SSH;
            case "token" -> GitAuthType.TOKEN;
            case "http" -> GitAuthType.HTTP_BASIC;
            case "none" -> GitAuthType.NONE;
            default -> null;
        };
    }

    private static void runSetPassphrase(VaultContext ctx) {
        if (!ctx.keyStorePort().exists()) {
            System.err.println(CliMessages.get("setPassphrase.noIdentity"));
            System.exit(1);
            return;
        }
        char[] current = ctx.keyStorePort().isPassphraseProtected()
                ? readCurrentPassphraseWithRetries(ctx.keyStorePort(), 3)
                : null;
        char[] newPassphrase = readNewPassphraseWithConfirmation();
        try {
            ctx.keyStorePort().changePassphrase(current, newPassphrase);
        } finally {
            wipe(current);
            wipe(newPassphrase);
        }
        IO.println(CliMessages.get("passphrase.setSuccess"));
    }

    private static void runRemovePassphrase(VaultContext ctx) {
        if (!ctx.keyStorePort().isPassphraseProtected()) {
            IO.println(CliMessages.get("removePassphrase.notProtected"));
            return;
        }
        char[] current = readCurrentPassphraseWithRetries(ctx.keyStorePort(), 3);
        try {
            ctx.keyStorePort().changePassphrase(current, null);
        } finally {
            wipe(current);
        }
        IO.println(CliMessages.get("passphrase.removeSuccess"));
    }

    /**
     * Disaster-recovery tool (see FAQ 03/04): decrypts a passphrase-protected {@code private.key}
     * file to plain PKCS8 DER bytes, given only its path - unlike every other identity operation
     * in this class, it does NOT go through {@code KeyStorePort}/{@code VaultContext}, since it
     * deliberately doesn't require a {@code public.key} to sit alongside it, or the file to live
     * under {@code ~/.rikiki-vault/identity/}. Always safe to run: an already-unprotected input is
     * just copied through unchanged, so "run unwrap-key first" is a uniform recipe either way.
     */
    private static void runUnwrapKey(String[] rest) {
        if (rest.length < 2) {
            System.err.println(CliMessages.get("unwrapKey.usage"));
            System.exit(1);
            return;
        }
        Path input = Path.of(rest[0]);
        Path output = Path.of(rest[1]);

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(input);
        } catch (IOException e) {
            throw new UncheckedIOException(CliMessages.get("unwrapKey.readFailed", input), e);
        }

        PrivateKeyEnvelopeCodec codec = new PrivateKeyEnvelopeCodec();
        byte[] pkcs8;
        if (codec.isEnvelope(bytes)) {
            char[] passphrase = readCurrentPassphraseWithRetries(new SingleFileKeyStorePort(codec, bytes), 3);
            try {
                pkcs8 = PrivateKeyEnvelopeCrypto.decrypt(codec.decode(bytes), passphrase);
            } finally {
                wipe(passphrase);
            }
        } else {
            pkcs8 = bytes;
            IO.println(CliMessages.get("unwrapKey.alreadyPlain"));
        }

        try {
            Files.write(output, pkcs8);
        } catch (IOException e) {
            throw new UncheckedIOException(CliMessages.get("unwrapKey.writeFailed", output), e);
        } finally {
            wipe(pkcs8);
        }
        IO.println(CliMessages.get("unwrapKey.written", output));
    }

    /**
     * Adapts a single in-memory {@code private.key} file's bytes to {@link KeyStorePort} just
     * enough to reuse {@link #readCurrentPassphraseWithRetries} - the rest of the interface is
     * never called for this narrow use.
     */
    private record SingleFileKeyStorePort(PrivateKeyEnvelopeCodec codec, byte[] bytes) implements KeyStorePort {
        @Override
        public void save(MachineIdentity identity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MachineIdentity load() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isPassphraseProtected() {
            return true;
        }

        @Override
        public MachineIdentity load(char[] passphrase) {
            PrivateKeyEnvelopeCrypto.decrypt(codec.decode(bytes), passphrase); // throws InvalidPassphraseException if wrong; result unused here
            return null;
        }

        @Override
        public void changePassphrase(char[] currentOrNull, char[] newOrNull) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Offered once, right after a brand-new machine identity is generated (init/clone only - never
     * from whoami/export-key, which must stay script-safe). Silently skipped without touching
     * stdin when there's no real console, so automation is never blocked on an unexpected prompt.
     */
    private static void offerPassphraseAtCreation(VaultContext ctx) {
        Console console = System.console();
        if (console == null) {
            return;
        }
        IO.println(CliMessages.get("passphrase.offerAtCreation"));
        String answer = console.readLine();
        String normalized = answer != null ? answer.strip().toLowerCase() : "";
        if (!(normalized.equals("s") || normalized.equals("sim") || normalized.equals("y") || normalized.equals("yes"))) {
            return;
        }
        char[] newPassphrase = readNewPassphraseWithConfirmation();
        try {
            ctx.keyStorePort().changePassphrase(null, newPassphrase);
        } finally {
            wipe(newPassphrase);
        }
        IO.println(CliMessages.get("passphrase.setSuccess"));
    }

    /**
     * Prompts for the current passphrase and validates it via a trial {@link KeyStorePort#load(char[])}
     * before returning it, retrying on {@link InvalidPassphraseException} up to {@code maxAttempts}.
     */
    private static char[] readCurrentPassphraseWithRetries(KeyStorePort keyStorePort, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            char[] candidate = readPassphrase(CliMessages.get("passphrase.prompt"));
            try {
                keyStorePort.load(candidate);
                return candidate;
            } catch (InvalidPassphraseException e) {
                wipe(candidate);
                if (attempt == maxAttempts) {
                    throw e;
                }
                IO.println(CliMessages.get("passphrase.wrongRetrying"));
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static char[] readNewPassphraseWithConfirmation() {
        char[] first = readPassphrase(CliMessages.get("passphrase.promptNew"));
        char[] second = readPassphrase(CliMessages.get("passphrase.promptConfirm"));
        if (!Arrays.equals(first, second)) {
            wipe(first);
            wipe(second);
            throw new IllegalArgumentException(CliMessages.get("passphrase.mismatch"));
        }
        wipe(second);
        if (first.length < 12) {
            IO.println(CliMessages.get("passphrase.tooShort"));
        }
        return first;
    }

    // Lazily created, reset once per invocation in run(String[]) - a flow like
    // readNewPassphraseWithConfirmation() calls readPassphrase() twice in a row, and a fresh
    // BufferedReader per call would silently drop whatever it had already buffered from System.in
    // beyond the first line when discarded.
    private static BufferedReader stdinFallbackReader;

    /**
     * Uses the real console (no echo) when one is attached; falls back to a plain, visibly-echoed
     * stdin read otherwise (piped input, IDE run configs, CI) - the fallback is announced so
     * nobody is surprised their passphrase was printed to the terminal.
     */
    private static char[] readPassphrase(String prompt) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword(prompt);
        }
        IO.println(CliMessages.get("passphrase.noConsoleFallback"));
        System.out.print(prompt);
        System.out.flush();
        try {
            if (stdinFallbackReader == null) {
                stdinFallbackReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            }
            String line = stdinFallbackReader.readLine();
            return line != null ? line.toCharArray() : new char[0];
        } catch (IOException e) {
            throw new UncheckedIOException(CliMessages.get("stdin.readFailed"), e);
        }
    }

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    private static void wipe(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

    private static String readSecretFromStdin(String errorMessageIfBlank) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                throw new IllegalArgumentException(errorMessageIfBlank);
            }
            return line.strip();
        } catch (IOException e) {
            throw new UncheckedIOException(CliMessages.get("stdin.readFailed"), e);
        }
    }

    private static IdentityResolution loadOrCreateIdentity(VaultContext ctx) {
        LoadMachineIdentityService loadMachineIdentityService = new LoadMachineIdentityService(ctx.keyStorePort());
        try {
            return new IdentityResolution(loadMachineIdentityService.load(), false);
        } catch (PrivateKeyNotFoundException e) {
            InitializeMachineIdentityService initializeMachineIdentityService =
                    new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort());
            return new IdentityResolution(initializeMachineIdentityService.initialize(), true);
        }
    }

    private static PublicKey readPublicKeyFile(Path file) {
        try {
            String base64 = Files.readString(file, StandardCharsets.UTF_8).strip();
            byte[] bytes = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException(CliMessages.get("publicKey.readFailed", file), e);
        }
    }

    private static void writePublicKeyFile(PublicKey publicKey, Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            Files.writeString(file, base64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(CliMessages.get("publicKey.writeFailed", file), e);
        }
    }

    private record IdentityResolution(MachineIdentity identity, boolean justCreated) {
    }

    private static void printUsage() {
        System.err.println(CliMessages.get("usage.text"));
    }

    private record VaultContext(
            Path vaultRoot,
            LocalFileSystemAdapter localFiles,
            LocalFileSystemAdapter documentsFiles,
            JsonManifestFileAdapter manifestPort,
            JsonRecipientRegistryFileAdapter recipientRegistryPort,
            JGitRepositoryAdapter gitRepositoryPort,
            KeyStorePort keyStorePort,
            JceHybridEncryptionAdapter encryptionPort,
            Sha256HashAdapter hashPort,
            LocalGitAuthSettingsAdapter gitAuthSettingsPort) {

        static VaultContext at(Path vaultRoot) {
            VaultConfig config = new YamlConfigFileAdapter(VaultPaths.defaultConfigFile()).load();
            LocalGitAuthSettingsAdapter gitAuthSettingsPort = new LocalGitAuthSettingsAdapter(VaultPaths.defaultPreferencesDirectory());
            return new VaultContext(
                    vaultRoot,
                    new LocalFileSystemAdapter(vaultRoot.resolve("local")),
                    new LocalFileSystemAdapter(vaultRoot.resolve("documents")),
                    new JsonManifestFileAdapter(vaultRoot.resolve("vault").resolve("manifest.json")),
                    new JsonRecipientRegistryFileAdapter(vaultRoot.resolve("vault").resolve("recipients.json")),
                    new JGitRepositoryAdapter(vaultRoot, gitAuthSettingsPort),
                    new LocalKeyStoreAdapter(config.identityDirectory()),
                    new JceHybridEncryptionAdapter(config.encryptionSettings()),
                    new Sha256HashAdapter(),
                    gitAuthSettingsPort);
        }

        VaultContext withKeyStorePort(KeyStorePort newKeyStorePort) {
            return new VaultContext(vaultRoot, localFiles, documentsFiles, manifestPort, recipientRegistryPort,
                    gitRepositoryPort, newKeyStorePort, encryptionPort, hashPort, gitAuthSettingsPort);
        }
    }
}
