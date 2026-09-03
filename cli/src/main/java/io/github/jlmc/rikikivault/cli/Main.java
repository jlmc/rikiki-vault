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
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PullResult;
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
            System.err.println("Erro: " + fullMessage(e));
            System.exit(1);
        } catch (RuntimeException e) {
            // Anything not already a RikikiVaultException (e.g. an UncheckedIOException from a
            // filesystem-level failure) still gets the same curated format instead of the JVM's
            // default uncaught-exception stack trace.
            System.err.println("Erro: " + fullMessage(e));
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
                : topMessage + " (causa: " + rootMessage + ")";
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
                System.err.println("Comando desconhecido: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void runInit(VaultContext ctx, String[] rest) {
        boolean initGit = false;
        String label = null;
        for (String token : rest) {
            if (token.equals("--git")) {
                initGit = true;
            } else {
                label = token;
            }
        }
        if (label == null) {
            System.err.println("Uso: init [--git] <machine-label>");
            System.exit(1);
            return;
        }

        InitializeVaultService service = new InitializeVaultService(
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                new LocalFileSystemAdapter(ctx.vaultRoot()),
                ctx.manifestPort(),
                ctx.recipientRegistryPort(),
                ctx.gitRepositoryPort());

        MachineIdentity identity = service.initialize(new InitializeVaultCommand(initGit, label));

        System.out.println("Vault inicializado em " + ctx.vaultRoot());
        System.out.println("Identidade desta máquina: " + identity.id());
        if (initGit) {
            System.out.println("Repositório git local criado. Já podes publicar (fica só local).");
            System.out.println("Opcional - para sincronizar com outra máquina, associa um remoto com:");
            System.out.println("  git -C " + ctx.vaultRoot() + " remote add origin <url>");
        }
    }

    private static void runWhoami(VaultContext ctx) {
        IdentityResolution resolution = loadOrCreateIdentity(ctx);
        System.out.println("Fingerprint: " + resolution.identity().id());
        if (resolution.justCreated()) {
            System.out.println("(identidade gerada agora - ainda não autorizada em nenhum vault)");
        }
    }

    private static void runExportKey(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println("Uso: export-key <ficheiro-saída>");
            System.exit(1);
            return;
        }
        Path outputFile = Path.of(rest[0]);
        IdentityResolution resolution = loadOrCreateIdentity(ctx);
        writePublicKeyFile(resolution.identity().publicKey(), outputFile);

        System.out.println("Chave pública escrita em " + outputFile.toAbsolutePath());
        System.out.println("Fingerprint: " + resolution.identity().id());
        System.out.println("Envia este ficheiro a quem gere o vault para que corra 'authorize'.");
    }

    private static void runStatus(VaultContext ctx) {
        ScanChangesService scanChangesService = new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort());
        List<VaultChange> changes = scanChangesService.scan();
        if (changes.isEmpty()) {
            System.out.println("Nada para publicar.");
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
            System.err.println("Uso: clone <remote-uri>");
            System.exit(1);
            return;
        }
        String remoteUri = rest[0];

        CloneVaultService service = new CloneVaultService(
                new LoadMachineIdentityService(ctx.keyStorePort()),
                new InitializeMachineIdentityService(new X25519KeyPairGeneratorAdapter(), ctx.keyStorePort()),
                new DecryptFileService(ctx.encryptionPort()),
                ctx.localFiles(), ctx.documentsFiles(), ctx.manifestPort(), ctx.gitRepositoryPort());

        MachineIdentity identity = service.clone(new CloneVaultCommand(remoteUri));

        System.out.println("Vault clonado em " + ctx.vaultRoot());
        System.out.println("Identidade desta máquina: " + identity.id());
    }

    private static void runPublish(VaultContext ctx, String[] rest) {
        String message = null;
        for (int i = 0; i < rest.length; i++) {
            if (rest[i].equals("-m") && i + 1 < rest.length) {
                message = rest[i + 1];
            }
        }
        if (message == null) {
            System.err.println("Uso: publish -m \"<mensagem>\"");
            System.exit(1);
            return;
        }

        ScanChangesService scanChangesService = new ScanChangesService(ctx.localFiles(), ctx.hashPort(), ctx.manifestPort());
        List<VaultChange> changes = scanChangesService.scan();
        if (changes.isEmpty()) {
            System.out.println("Nada para publicar.");
            return;
        }

        PublishVaultService service = new PublishVaultService(
                ctx.localFiles(), ctx.documentsFiles(), ctx.encryptionPort(), ctx.hashPort(),
                ctx.manifestPort(), ctx.recipientRegistryPort(), ctx.gitRepositoryPort());
        boolean pushed = service.publish(new PublishVaultCommand(changes, message));

        System.out.println("Publicadas " + changes.size() + " alterações.");
        if (!pushed) {
            System.out.println("(guardado localmente - sem remoto configurado)");
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
            System.out.println("Aviso: tens alterações locais por publicar:");
            for (VaultChange change : result.uncommittedLocalChangesAtStart()) {
                System.out.println("  " + change.type() + " " + change.path());
            }
        }
        for (String path : result.updatedPaths()) {
            System.out.println("Atualizado: " + path);
        }
        for (String path : result.deletedPaths()) {
            System.out.println("Removido: " + path);
        }
        for (VaultConflict conflict : result.conflicts()) {
            System.out.println("Conflito em " + conflict.plaintextPath()
                    + " (local=" + conflict.localChangeType() + ", remoto=" + conflict.remoteChangeType() + ") - ficheiro local não foi tocado.");
            if (conflict.localHash() != null) {
                System.out.println("  Local SHA-256: " + conflict.localHash());
            }
            if (conflict.remoteHash() != null) {
                System.out.println("  Remoto SHA-256: " + conflict.remoteHash());
            }
        }
        if (result.updatedPaths().isEmpty() && result.deletedPaths().isEmpty() && !result.hasConflicts()) {
            System.out.println("Já estás atualizado.");
        }
    }

    private static void runAuthorize(VaultContext ctx, String[] rest) {
        if (rest.length < 2) {
            System.err.println("Uso: authorize <label> <ficheiro-chave-pública>");
            System.exit(1);
            return;
        }
        String label = rest[0];
        PublicKey publicKey = readPublicKeyFile(Path.of(rest[1]));

        AuthorizeMachineService service = new AuthorizeMachineService(
                ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort());
        boolean pushed = service.authorize(new AuthorizeMachineCommand(label, publicKey));

        System.out.println("Máquina '" + label + "' autorizada e publicada.");
        if (!pushed) {
            System.out.println("(guardado localmente - sem remoto configurado)");
        }
    }

    private static void runRevoke(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println("Uso: revoke <fingerprint-hex>");
            System.exit(1);
            return;
        }
        KeyFingerprint fingerprint = new KeyFingerprint(rest[0]);

        RevokeMachineService service = new RevokeMachineService(
                ctx.recipientRegistryPort(), ctx.localFiles(), ctx.documentsFiles(),
                ctx.manifestPort(), ctx.encryptionPort(), ctx.gitRepositoryPort());
        boolean pushed = service.revoke(new RevokeMachineCommand(fingerprint));

        System.out.println("Máquina " + fingerprint + " revogada e alterações publicadas.");
        if (!pushed) {
            System.out.println("(guardado localmente - sem remoto configurado)");
        }
    }

    private static void runGitAuth(VaultContext ctx, String[] rest) {
        if (rest.length < 1) {
            System.err.println("Uso: git-auth show|set-ssh-key <path>|clear-ssh-key|set-token|clear-token");
            System.exit(1);
            return;
        }
        LocalGitAuthSettingsAdapter port = ctx.gitAuthSettingsPort();
        switch (rest[0]) {
            case "show" -> {
                GitAuthSettings settings = port.load();
                System.out.println("Chave SSH: " + (settings.sshPrivateKeyPath() != null ? settings.sshPrivateKeyPath() : "nenhuma"));
                boolean hasToken = settings.githubToken() != null && !settings.githubToken().isBlank();
                System.out.println("Token HTTPS: " + (hasToken ? "configurado" : "não configurado"));
            }
            case "set-ssh-key" -> {
                if (rest.length < 2) {
                    System.err.println("Uso: git-auth set-ssh-key <path>");
                    System.exit(1);
                    return;
                }
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(Path.of(rest[1]), current.githubToken()));
                System.out.println("Chave SSH configurada.");
            }
            case "clear-ssh-key" -> {
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(null, current.githubToken()));
                System.out.println("Chave SSH removida.");
            }
            case "set-token" -> {
                // Reads from stdin, never from an argument - an argument would land in shell
                // history, exactly the mistake that prompted this command in the first place.
                String token = readTokenFromStdin();
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(current.sshPrivateKeyPath(), token));
                System.out.println("Token guardado.");
            }
            case "clear-token" -> {
                GitAuthSettings current = port.load();
                port.save(new GitAuthSettings(current.sshPrivateKeyPath(), null));
                System.out.println("Token removido.");
            }
            default -> {
                System.err.println("Subcomando desconhecido: " + rest[0]);
                System.exit(1);
            }
        }
    }

    private static String readTokenFromStdin() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                throw new IllegalArgumentException("Nenhum token recebido no stdin");
            }
            return line.strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o token do stdin", e);
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
            throw new IllegalArgumentException("Não foi possível ler a chave pública de " + file, e);
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
            throw new UncheckedIOException("Falha ao escrever a chave pública em " + file, e);
        }
    }

    private record IdentityResolution(MachineIdentity identity, boolean justCreated) {
    }

    private static void printUsage() {
        System.err.println("""
                Uso: rikiki-vault [-C <vault-dir>] <comando> [args]

                Comandos:
                  init [--git] <machine-label>
                  whoami
                  export-key <output-file>
                  clone <remote-uri>       (entrar num vault já existente - a primeira máquina usa 'init')
                  status
                  publish -m "<message>"
                  pull
                  authorize <label> <public-key-file>
                  revoke <fingerprint-hex>
                  git-auth show|set-ssh-key <path>|clear-ssh-key|set-token|clear-token
                """);
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
            LocalGitAuthSettingsAdapter gitAuthSettingsPort = new LocalGitAuthSettingsAdapter(VaultPaths.defaultGitAuthDirectory());
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
