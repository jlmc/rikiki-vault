package io.github.jlmc.rikikivault.cli;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalGitAuthSettingsAdapter;
import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.AuthorizeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.CloneVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.DecryptFileService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.PublishVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.PullVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.RevokeMachineService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.configuration.GitAuthSettings;
import io.github.jlmc.rikikivault.core.configuration.GitAuthType;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
import io.github.jlmc.rikikivault.core.domain.model.RemoteSyncStatus;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.domain.model.VaultConflict;
import io.github.jlmc.rikikivault.core.ports.in.AuthorizeMachineCommand;
import io.github.jlmc.rikikivault.core.ports.in.CloneVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.PublishVaultCommand;
import io.github.jlmc.rikikivault.core.ports.in.RevokeMachineCommand;

import java.io.IOException;
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

        switch (command) {
            case "init" -> runInit(ctx, rest);
            case "whoami" -> runWhoami(ctx);
            case "export-key" -> runExportKey(ctx, rest);
            case "clone" -> runClone(ctx, rest);
            case "status" -> runStatus(ctx);
            case "publish" -> runPublish(ctx, rest);
            case "pull" -> runPull(ctx);
            case "authorize" -> runAuthorize(ctx, rest);
            case "revoke" -> runRevoke(ctx, rest);
            case "git-auth" -> runGitAuth(ctx, rest);
            default -> {
                System.err.println(CliMessages.get("run.unknownCommand", command));
                printUsage();
                System.exit(1);
            }
        }
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

        InitializeVaultService service = new InitializeVaultService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                new LocalFileSystemAdapter(ctx.vaultRoot()),
                ctx.manifestPort(),
                ctx.recipientRegistryPort(),
                ctx.gitRepositoryPort());

        MachineIdentity identity = service.initialize(new InitializeVaultCommand(initGit, label, remoteUri));

        System.out.println(CliMessages.get("init.vaultInitializedIn", ctx.vaultRoot()));
        System.out.println(CliMessages.get("init.identity", identity.id()));
        if (initGit) {
            System.out.println(CliMessages.get("init.gitCreated"));
            if (remoteUri != null) {
                System.out.println(CliMessages.get("init.remoteAssociated", remoteUri));
            } else {
                System.out.println(CliMessages.get("init.remoteOptionalHint"));
                System.out.println("  git -C " + ctx.vaultRoot() + " remote add origin <url>");
            }
        }
    }

    private static void runWhoami(VaultContext ctx) {
        IdentityResolution resolution = loadOrCreateIdentity(ctx);
        System.out.println(CliMessages.get("whoami.fingerprint", resolution.identity().id()));
        if (resolution.justCreated()) {
            System.out.println(CliMessages.get("whoami.justGenerated"));
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

        System.out.println(CliMessages.get("exportKey.written", outputFile.toAbsolutePath()));
        System.out.println(CliMessages.get("exportKey.fingerprint", resolution.identity().id()));
        System.out.println(CliMessages.get("exportKey.hint"));
    }

    private static void runStatus(VaultContext ctx) {
        ScanChangesService scanChangesService = new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort());
        List<VaultChange> changes = scanChangesService.scan();
        if (changes.isEmpty()) {
            System.out.println(CliMessages.get("status.nothing"));
            return;
        }
        for (VaultChange change : changes) {
            String marker = switch (change.type()) {
                case ADDED -> "A ";
                case MODIFIED -> "M ";
                case DELETED -> "D ";
            };
            System.out.println(marker + " " + change.path());
        }
    }

    private static void runClone(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println(CliMessages.get("clone.usage"));
            System.exit(1);
            return;
        }
        String remoteUri = rest[0];

        CloneVaultService service = new CloneVaultService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                new DecryptFileService(ctx.encryptionPort()),
                ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort());

        MachineIdentity identity = service.clone(new CloneVaultCommand(remoteUri));

        System.out.println(CliMessages.get("clone.clonedIn", ctx.vaultRoot()));
        System.out.println(CliMessages.get("clone.identity", identity.id()));
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
        System.out.println(CliMessages.get("publish.published", changes.size()));

        if (!ctx.gitRepositoryPort().hasRemote()) {
            System.out.println(CliMessages.get("publish.noRemote"));
            return;
        }
        try {
            service.pushToRemote();
        } catch (RikikiVaultException e) {
            System.out.println(CliMessages.get("publish.pushWarning", fullMessage(e)));
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
            System.out.println(CliMessages.get("nothingToCommit.nothing"));
            return;
        }
        RemoteSyncStatus status;
        try {
            status = ctx.gitRepositoryPort().remoteSyncStatus();
        } catch (RikikiVaultException e) {
            System.out.println(CliMessages.get("nothingToCommit.nothing"));
            return;
        }
        if (status.isDiverged()) {
            System.out.println(CliMessages.get("nothingToCommit.diverged", status.aheadCount(), status.behindCount()));
        } else if (status.aheadCount() > 0) {
            System.out.println(CliMessages.get("nothingToCommit.ahead", status.aheadCount()));
            try {
                ctx.gitRepositoryPort().push();
                System.out.println(CliMessages.get("nothingToCommit.pushed"));
            } catch (RikikiVaultException e) {
                System.out.println(CliMessages.get("publish.pushWarning", fullMessage(e)));
            }
        } else if (status.behindCount() > 0) {
            System.out.println(CliMessages.get("nothingToCommit.behind"));
        } else {
            System.out.println(CliMessages.get("nothingToCommit.nothing"));
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
            System.out.println(CliMessages.get("pull.uncommittedWarning"));
            for (VaultChange change : result.uncommittedLocalChangesAtStart()) {
                System.out.println("  " + change.type() + " " + change.path());
            }
        }
        for (String path : result.updatedPaths()) {
            System.out.println(CliMessages.get("pull.updated", path));
        }
        for (String path : result.deletedPaths()) {
            System.out.println(CliMessages.get("pull.deleted", path));
        }
        for (VaultConflict conflict : result.conflicts()) {
            System.out.println(CliMessages.get("pull.conflict", conflict.plaintextPath(), conflict.localChangeType(), conflict.remoteChangeType()));
            if (conflict.localHash() != null) {
                System.out.println("  " + CliMessages.get("pull.conflict.localHash", conflict.localHash()));
            }
            if (conflict.remoteHash() != null) {
                System.out.println("  " + CliMessages.get("pull.conflict.remoteHash", conflict.remoteHash()));
            }
        }
        if (result.updatedPaths().isEmpty() && result.deletedPaths().isEmpty() && !result.hasConflicts()) {
            System.out.println(CliMessages.get("pull.upToDate"));
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

        System.out.println(CliMessages.get("authorize.done", label));
        if (!pushed) {
            System.out.println(CliMessages.get("authorize.noRemote"));
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

        System.out.println(CliMessages.get("revoke.done", fingerprint));
        if (!pushed) {
            System.out.println(CliMessages.get("revoke.noRemote"));
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
                System.out.println(CliMessages.get("gitAuth.activeType", describeType(settings.activeType())));
                String sshKeyText = settings.sshPrivateKeyPath() != null
                        ? settings.sshPrivateKeyPath().toString() : CliMessages.get("gitAuth.sshKey.none");
                System.out.println(CliMessages.get("gitAuth.sshKey", sshKeyText) + activeSuffix(settings.activeType() == GitAuthType.SSH));
                boolean hasToken = settings.githubToken() != null && !settings.githubToken().isBlank();
                String tokenText = hasToken ? CliMessages.get("gitAuth.configured") : CliMessages.get("gitAuth.notConfigured");
                System.out.println(CliMessages.get("gitAuth.tokenLabel", tokenText) + activeSuffix(settings.activeType() == GitAuthType.TOKEN));
                boolean hasHttpBasic = settings.httpUsername() != null && !settings.httpUsername().isBlank();
                String httpText = hasHttpBasic
                        ? CliMessages.get("gitAuth.httpConfigured", settings.httpUsername())
                        : CliMessages.get("gitAuth.notConfigured");
                System.out.println(CliMessages.get("gitAuth.httpLabel", httpText) + activeSuffix(settings.activeType() == GitAuthType.HTTP_BASIC));
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
                System.out.println(CliMessages.get("gitAuth.setSshKey.done"));
            }
            case "clear-ssh-key" -> {
                GitAuthSettings current = port.load();
                GitAuthType newType = current.activeType() == GitAuthType.SSH ? GitAuthType.NONE : current.activeType();
                port.save(new GitAuthSettings(newType, null, current.githubToken(), current.httpUsername(), current.httpPassword()));
                System.out.println(CliMessages.get("gitAuth.clearSshKey.done"));
            }
            case "set-token" -> {
                // Reads from stdin, never from an argument - an argument would land in shell
                // history, exactly the mistake that prompted this command in the first place.
                String token = readSecretFromStdin(CliMessages.get("gitAuth.setToken.emptyStdin"));
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(GitAuthType.TOKEN, current.sshPrivateKeyPath(), token,
                        current.httpUsername(), current.httpPassword()));
                System.out.println(CliMessages.get("gitAuth.setToken.done"));
            }
            case "clear-token" -> {
                GitAuthSettings current = port.load();
                GitAuthType newType = current.activeType() == GitAuthType.TOKEN ? GitAuthType.NONE : current.activeType();
                port.save(new GitAuthSettings(newType, current.sshPrivateKeyPath(), null, current.httpUsername(), current.httpPassword()));
                System.out.println(CliMessages.get("gitAuth.clearToken.done"));
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
                System.out.println(CliMessages.get("gitAuth.setHttpBasic.done"));
            }
            case "clear-http-basic" -> {
                GitAuthSettings current = port.load();
                GitAuthType newType = current.activeType() == GitAuthType.HTTP_BASIC ? GitAuthType.NONE : current.activeType();
                port.save(new GitAuthSettings(newType, current.sshPrivateKeyPath(), current.githubToken(), null, null));
                System.out.println(CliMessages.get("gitAuth.clearHttpBasic.done"));
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
                System.out.println(CliMessages.get("gitAuth.activeType", describeType(requested)));
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
            LocalKeyStoreAdapter keyStorePort,
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
    }
}
