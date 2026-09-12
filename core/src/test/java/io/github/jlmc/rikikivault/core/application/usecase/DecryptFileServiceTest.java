package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.exception.UnauthorizedMachineException;
import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.KeyFingerprint;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.domain.model.RecipientKeyEntry;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecryptFileServiceTest {

    private static KeyPair generateX25519KeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static MachineIdentity identityFrom(KeyPair keyPair) {
        return new MachineIdentity(KeyFingerprint.of(keyPair.getPublic()), keyPair.getPublic(), keyPair.getPrivate(), "X25519");
    }

    @Test
    void delegatesWhenIdentityIsAnAuthorizedRecipient() throws Exception {
        MachineIdentity identity = identityFrom(generateX25519KeyPair());
        RecipientKeyEntry entry = new RecipientKeyEntry(identity.id(), new byte[]{1});
        EncryptedFile file = new EncryptedFile("RV02", 1, 1, List.of(entry), new byte[12], new byte[]{1});

        FakeEncryptionPort port = new FakeEncryptionPort();
        PlaintextFile expected = new PlaintextFile("x.txt", new byte[]{9});
        port.fileToReturnOnDecrypt = expected;

        DecryptFileService service = new DecryptFileService(port);
        PlaintextFile result = service.decrypt(new DecryptFileCommand(file, identity));

        assertEquals(1, port.decryptCallCount);
        assertEquals(expected, result);
    }

    @Test
    void throwsUnauthorizedWithoutInvokingThePortWhenIdentityIsNotARecipient() throws Exception {
        MachineIdentity identity = identityFrom(generateX25519KeyPair());
        RecipientKeyEntry entryForSomeoneElse = new RecipientKeyEntry(
                KeyFingerprint.of(generateX25519KeyPair().getPublic()), new byte[]{1});
        EncryptedFile file = new EncryptedFile("RV02", 1, 1, List.of(entryForSomeoneElse), new byte[12], new byte[]{1});

        FakeEncryptionPort port = new FakeEncryptionPort();
        DecryptFileService service = new DecryptFileService(port);

        assertThrows(UnauthorizedMachineException.class, () -> service.decrypt(new DecryptFileCommand(file, identity)));
        assertEquals(0, port.decryptCallCount);
    }
}
