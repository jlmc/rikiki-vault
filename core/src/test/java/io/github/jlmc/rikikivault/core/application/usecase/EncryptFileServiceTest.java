package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.ports.in.EncryptFileCommand;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncryptFileServiceTest {

    @Test
    void delegatesToEncryptionPortWithTheCommandsArguments() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(NamedParameterSpec.X25519, new SecureRandom());
        PublicKey recipient = generator.generateKeyPair().getPublic();

        FakeEncryptionPort port = new FakeEncryptionPort();
        EncryptFileService service = new EncryptFileService(port);
        PlaintextFile file = new PlaintextFile("x.txt", new byte[]{1, 2, 3});

        EncryptedFile result = service.encrypt(new EncryptFileCommand(file, List.of(recipient)));

        assertEquals(1, port.encryptCallCount);
        assertEquals("x.txt", result.originalFileName());
    }
}
