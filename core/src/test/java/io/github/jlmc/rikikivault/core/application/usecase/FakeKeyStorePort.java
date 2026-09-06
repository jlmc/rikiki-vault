package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.MachineIdentityAlreadyExistsException;
import io.github.jlmc.rikikivault.core.domain.exception.PrivateKeyNotFoundException;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;

final class FakeKeyStorePort implements KeyStorePort {

    int saveCallCount = 0;
    private MachineIdentity stored;

    FakeKeyStorePort() {
    }

    FakeKeyStorePort(MachineIdentity initiallyStored) {
        this.stored = initiallyStored;
    }

    @Override
    public void save(MachineIdentity identity) {
        saveCallCount++;
        if (stored != null) {
            throw new MachineIdentityAlreadyExistsException("Identity already exists in this fake store");
        }
        stored = identity;
    }

    @Override
    public MachineIdentity load() {
        if (stored == null) {
            throw new PrivateKeyNotFoundException("No identity stored in this fake store");
        }
        return stored;
    }

    @Override
    public boolean exists() {
        return stored != null;
    }

    @Override
    public boolean isPassphraseProtected() {
        return false;
    }

    @Override
    public MachineIdentity load(char[] passphrase) {
        return load();
    }

    @Override
    public void changePassphrase(char[] currentOrNull, char[] newOrNull) {
        throw new UnsupportedOperationException("not needed by these tests");
    }
}
