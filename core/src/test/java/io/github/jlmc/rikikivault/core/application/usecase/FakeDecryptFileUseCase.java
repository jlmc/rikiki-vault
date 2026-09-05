package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.PlaintextFile;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileCommand;
import io.github.jlmc.rikikivault.core.ports.in.DecryptFileUseCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FakeDecryptFileUseCase implements DecryptFileUseCase {

    final List<DecryptFileCommand> receivedCommands = new ArrayList<>();
    private final Map<String, PlaintextFile> resultsByFileName = new HashMap<>();
    private final Map<String, RuntimeException> failuresByFileName = new HashMap<>();

    FakeDecryptFileUseCase withResult(String fileName, PlaintextFile plaintextFile) {
        resultsByFileName.put(fileName, plaintextFile);
        return this;
    }

    FakeDecryptFileUseCase withFailure(String fileName, RuntimeException failure) {
        failuresByFileName.put(fileName, failure);
        return this;
    }

    @Override
    public PlaintextFile decrypt(DecryptFileCommand command) {
        receivedCommands.add(command);
        String fileName = command.file().originalFileName();
        RuntimeException failure = failuresByFileName.get(fileName);
        if (failure != null) {
            throw failure;
        }
        PlaintextFile result = resultsByFileName.get(fileName);
        if (result == null) {
            throw new IllegalArgumentException("No fake decrypt result configured for: " + fileName);
        }
        return result;
    }
}
