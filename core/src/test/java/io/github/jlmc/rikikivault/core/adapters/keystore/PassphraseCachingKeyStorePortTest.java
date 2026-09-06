package io.github.jlmc.rikikivault.core.adapters.keystore;

import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.ports.out.KeyStorePort;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassphraseCachingKeyStorePortTest {

    private static MachineIdentity newIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    private static final class RecordingKeyStorePort implements KeyStorePort {
        final List<String> calls = new ArrayList<>();
        MachineIdentity identity;

        private RecordingKeyStorePort(MachineIdentity identity) {
            this.identity = identity;
        }

        @Override
        public void save(MachineIdentity identity) {
            calls.add("save");
        }

        @Override
        public MachineIdentity load() {
            calls.add("load()");
            return identity;
        }

        @Override
        public boolean exists() {
            calls.add("exists");
            return true;
        }

        @Override
        public boolean isPassphraseProtected() {
            calls.add("isPassphraseProtected");
            return true;
        }

        @Override
        public MachineIdentity load(char[] passphrase) {
            calls.add("load(" + new String(passphrase) + ")");
            return identity;
        }

        @Override
        public void changePassphrase(char[] currentOrNull, char[] newOrNull) {
            calls.add("changePassphrase");
        }
    }

    @Test
    void loadDelegatesToPassphraseOverloadWhenPassphraseIsCached() throws Exception {
        MachineIdentity identity = newIdentity();
        RecordingKeyStorePort delegate = new RecordingKeyStorePort(identity);
        PassphraseCachingKeyStorePort decorator = new PassphraseCachingKeyStorePort(delegate, "s3cret".toCharArray());

        MachineIdentity result = decorator.load();

        assertEquals(identity, result);
        assertEquals(List.of("load(s3cret)"), delegate.calls);
    }

    @Test
    void loadDelegatesToNoArgOverloadWhenNothingIsCached() throws Exception {
        MachineIdentity identity = newIdentity();
        RecordingKeyStorePort delegate = new RecordingKeyStorePort(identity);
        PassphraseCachingKeyStorePort decorator = new PassphraseCachingKeyStorePort(delegate, null);

        MachineIdentity result = decorator.load();

        assertEquals(identity, result);
        assertEquals(List.of("load()"), delegate.calls);
    }

    @Test
    void otherOperationsDelegateDirectly() throws Exception {
        RecordingKeyStorePort delegate = new RecordingKeyStorePort(newIdentity());
        PassphraseCachingKeyStorePort decorator = new PassphraseCachingKeyStorePort(delegate, "x".toCharArray());

        decorator.save(newIdentity());
        decorator.exists();
        decorator.isPassphraseProtected();
        decorator.load("explicit".toCharArray());
        decorator.changePassphrase("a".toCharArray(), "b".toCharArray());

        assertEquals(List.of("save", "exists", "isPassphraseProtected", "load(explicit)", "changePassphrase"), delegate.calls);
    }

    @Test
    void withPassphraseReturnsAFreshDecoratorOverTheSameDelegate() throws Exception {
        RecordingKeyStorePort delegate = new RecordingKeyStorePort(newIdentity());
        PassphraseCachingKeyStorePort original = new PassphraseCachingKeyStorePort(delegate, "old".toCharArray());

        PassphraseCachingKeyStorePort rotated = original.withPassphrase("new".toCharArray());
        original.load();
        rotated.load();

        // Both decorators hit the very same delegate instance, each with its own cached passphrase.
        assertEquals(List.of("load(old)", "load(new)"), delegate.calls);
    }
}
