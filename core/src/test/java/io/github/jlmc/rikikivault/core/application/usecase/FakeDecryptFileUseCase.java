package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.EncryptedFile;
import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyed by the {@link EncryptedFile} value itself (not a filename - RV02 carries none) - this
 * survives a real {@code RvEncryptedFileFormatCodec} encode/decode round trip byte-for-byte
 * (see {@code RvEncryptedFileFormatCodecTest}), so a test can encode a fake file, stash it under
 * {@link #withResult}, and have this fake recognize the same value after the service under test
 * reads and decodes it back from a {@code FakeFileStoragePort}.
 */
final class FakeDecryptFileUseCase implements DecryptFileUseCase {

    final List<DecryptFileCommand> receivedCommands = new ArrayList<>();
    private final Map<EncryptedFile, PlaintextFile> resultsByFile = new HashMap<>();
    private final Map<EncryptedFile, RuntimeException> failuresByFile = new HashMap<>();

    FakeDecryptFileUseCase withResult(EncryptedFile file, PlaintextFile plaintextFile) {
        resultsByFile.put(file, plaintextFile);
        return this;
    }

    FakeDecryptFileUseCase withFailure(EncryptedFile file, RuntimeException failure) {
        failuresByFile.put(file, failure);
        return this;
    }

    @Override
    public PlaintextFile decrypt(DecryptFileCommand command) {
        receivedCommands.add(command);
        EncryptedFile file = command.file();
        RuntimeException failure = failuresByFile.get(file);
        if (failure != null) {
            throw failure;
        }
        PlaintextFile result = resultsByFile.get(file);
        if (result == null) {
            throw new IllegalArgumentException("No fake decrypt result configured for: " + file);
        }
        return result;
    }
}
