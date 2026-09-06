package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.exception.InvalidPassphraseException;
import io.github.jlmc.rikikivault.core.domain.exception.PassphraseRequiredException;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;

public interface KeyStorePort {

    void save(MachineIdentity identity);

    /**
     * @throws PassphraseRequiredException if the stored identity is passphrase-protected
     */
    MachineIdentity load();

    boolean exists();

    /**
     * @return false when {@link #exists()} is false - there is nothing to be protected yet
     */
    boolean isPassphraseProtected();

    /**
     * Loads the identity, deriving the decryption key from {@code passphrase} when the stored
     * identity is protected. If it isn't protected, behaves exactly like {@link #load()} and the
     * given array is ignored (still wiped for hygiene).
     *
     * @throws InvalidPassphraseException if the identity is protected and the passphrase is wrong
     */
    MachineIdentity load(char[] passphrase);

    /**
     * Adds, rotates, or removes passphrase protection on the stored identity. See
     * {@code LocalKeyStoreAdapter}'s javadoc for the full contract table.
     *
     * @throws PassphraseRequiredException if the identity is protected and {@code currentOrNull} is null
     * @throws InvalidPassphraseException  if the identity is protected and {@code currentOrNull} is wrong
     * @throws IllegalArgumentException    if {@code currentOrNull} is given for an unprotected identity,
     *                                      or both arguments are null
     */
    void changePassphrase(char[] currentOrNull, char[] newOrNull);
}
