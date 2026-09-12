package io.github.jlmc.rikikivault.core.domain.exception;

/**
 * Thrown when {@code manifest.json} exists but doesn't decode as the current encrypted (RV02+)
 * envelope format - almost always because it's still the older plaintext-JSON manifest from
 * before path/filename encryption shipped. The fix is to run the vault-format migration, not to
 * treat this as generic corruption.
 */
public class VaultNotMigratedException extends RikikiVaultException {

    public VaultNotMigratedException(String message, Throwable cause) {
        super(message, cause);
    }
}
