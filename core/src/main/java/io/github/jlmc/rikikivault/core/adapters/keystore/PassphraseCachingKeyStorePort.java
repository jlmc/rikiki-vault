package io.github.jlmc.rikikivault.core.adapters.keystore;

import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

import java.util.Objects;

/**
 * Wraps a {@link KeyStorePort} with an already-verified passphrase, so callers that only know
 * about the no-argument {@link #load()} (every existing use-case service, unchanged) transparently
 * get a passphrase-protected identity without having to know a passphrase was ever involved.
 *
 * <p>Deliberately not lazy and with no retry logic: this is only ever constructed after a caller
 * (a CLI command, or the GUI's passphrase-prompt screen) has already called
 * {@code delegate.load(passphrase)} once successfully - so the cached passphrase is known-good at
 * construction time. This keeps the class trivial and avoids the problem of a JavaFX dialog being
 * needed from a background thread if a re-prompt were ever attempted from here.
 *
 * <p>Used by both the CLI (cached for one process invocation) and the GUI (cached for one open
 * vault session, until the app closes).
 */
public final class PassphraseCachingKeyStorePort implements KeyStorePort {

    private final KeyStorePort delegate;
    private final char[] passphrase;

    /**
     * @param passphrase the already-verified passphrase, or {@code null} if the identity isn't
     *                    passphrase-protected (in which case this behaves exactly like {@code delegate})
     */
    public PassphraseCachingKeyStorePort(KeyStorePort delegate, char[] passphrase) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.passphrase = passphrase;
    }

    /**
     * Returns a new decorator over the same delegate with a different cached passphrase - used
     * after a passphrase change to keep an already-open session consistent, without re-prompting.
     */
    public PassphraseCachingKeyStorePort withPassphrase(char[] newPassphrase) {
        return new PassphraseCachingKeyStorePort(delegate, newPassphrase);
    }

    /** The wrapped, non-caching port - lets a caller rebuild a fresh decorator after a passphrase change. */
    public KeyStorePort delegate() {
        return delegate;
    }

    @Override
    public void save(MachineIdentity identity) {
        delegate.save(identity);
    }

    @Override
    public MachineIdentity load() {
        return passphrase != null ? delegate.load(passphrase) : delegate.load();
    }

    @Override
    public boolean exists() {
        return delegate.exists();
    }

    @Override
    public boolean isPassphraseProtected() {
        return delegate.isPassphraseProtected();
    }

    @Override
    public MachineIdentity load(char[] explicitPassphrase) {
        return delegate.load(explicitPassphrase);
    }

    @Override
    public void changePassphrase(char[] currentOrNull, char[] newOrNull) {
        delegate.changePassphrase(currentOrNull, newOrNull);
    }
}
