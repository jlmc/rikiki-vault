package io.github.jlmc.rikikivault.cli;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        printUsage();
        System.exit(1);
    }

    private static void printUsage() {
        System.err.println("""
                Usage: rikiki-vault [-C <vault-dir>] <command> [args]

                Commands:
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
}
