package io.github.jlmc.rikikivault.cli;

import io.github.jlmc.rikikivault.core.adapters.configuration.YamlConfigFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.JceHybridEncryptionAdapter;
import io.github.jlmc.rikikivault.core.adapters.encryption.X25519KeyPairGeneratorAdapter;
import io.github.jlmc.rikikivault.core.adapters.filesystem.LocalFileSystemAdapter;
import io.github.jlmc.rikikivault.core.adapters.git.JGitRepositoryAdapter;
import io.github.jlmc.rikikivault.core.adapters.hashing.Sha256HashAdapter;
import io.github.jlmc.rikikivault.core.adapters.keystore.LocalKeyStoreAdapter;
import io.github.jlmc.rikikivault.core.adapters.manifest.JsonManifestFileAdapter;
import io.github.jlmc.rikikivault.core.adapters.recipients.JsonRecipientRegistryFileAdapter;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.InitializeVaultService;
import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.application.usecase.ScanChangesService;
import io.github.jlmc.rikikivault.core.configuration.VaultConfig;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.exception.RikikiVaultException;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import io.github.jlmc.rikikivault.core.ports.in.InitializeVaultCommand;

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
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
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
            case "status" -> runStatus(ctx);
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
            System.out.println("Repositório git local criado. Antes de publicar, associa um remoto com:");
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

    static PublicKey readPublicKeyFile(Path file) {
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
                  clone <remote-uri>
                  status
                  publish -m "<message>"
                  pull
                  authorize <label> <public-key-file>
                  revoke <fingerprint-hex>
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
            Sha256HashAdapter hashPort) {

        static VaultContext at(Path vaultRoot) {
            VaultConfig config = new YamlConfigFileAdapter(VaultPaths.defaultConfigFile()).load();
            return new VaultContext(
                    vaultRoot,
                    new LocalFileSystemAdapter(vaultRoot.resolve("local")),
                    new LocalFileSystemAdapter(vaultRoot.resolve("documents")),
                    new JsonManifestFileAdapter(vaultRoot.resolve("vault").resolve("manifest.json")),
                    new JsonRecipientRegistryFileAdapter(vaultRoot.resolve("vault").resolve("recipients.json")),
                    new JGitRepositoryAdapter(vaultRoot),
                    new LocalKeyStoreAdapter(config.identityDirectory()),
                    new JceHybridEncryptionAdapter(config.encryptionSettings()),
                    new Sha256HashAdapter());
        }
    }
}
